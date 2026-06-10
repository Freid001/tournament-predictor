package com.tournamentpredictor.services.snapshot;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.services.io.CsvLoader;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Freezes the external ELO inputs needed by one tournament.
 * Existing tournament stages should read these files when present, so later global
 * ELO refreshes do not silently change an old tournament.
 */
public class TournamentSnapshotHandler {
    private static final Map<String, Set<String>> HISTORICAL_NAMES = Map.of(
            "Czechia", Set.of("Czechia", "Czech Republic"),
            "Serbia", Set.of("Serbia", "Yugoslavia", "Serbia and Montenegro"),
            "United States", Set.of("United States", "USA"),
            "Russia", Set.of("Russia", "CIS"),
            "Ivory Coast", Set.of("Ivory Coast", "Côte d'Ivoire"),
            "Bosnia and Herzegovina", Set.of("Bosnia and Herzegovina", "Bosnia-Herzegovina")
    );
    private static final CSVFormat CSV = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    private final CsvLoader loader;
    private final Path projectRoot;
    private final PredictionConfig config;
    private int effectiveQualSinceYear;
    private int effectiveQualUntilYear;
    private int effectivePreTournamentSinceYear;
    private int effectivePreTournamentUntilYear;
    private LocalDate tournamentStartDate;

    public TournamentSnapshotHandler(CsvLoader loader, Path projectRoot, PredictionConfig config) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.config = config;
    }

    public void handle(String tournament) throws IOException {
        Set<String> teams = new LinkedHashSet<>(loader.loadStartTeams(tournament));
        if (teams.isEmpty()) {
            throw new IOException("No teams found in start.csv for " + tournament);
        }
        effectiveQualSinceYear = loader.resolveTournamentSetting(tournament,
                "qual.form.since.year", config.getQualFormSinceYear());
        effectiveQualUntilYear = loader.resolveTournamentSetting(tournament,
                "qual.form.until.year", config.getQualFormUntilYear());
        effectivePreTournamentSinceYear = loader.resolveTournamentSetting(tournament,
                "pre.tournament.form.since.year", config.getPreTournamentFormSinceYear());
        effectivePreTournamentUntilYear = loader.resolveTournamentSetting(tournament,
                "pre.tournament.form.until.year", config.getPreTournamentFormUntilYear());
        String configuredStartDate = loader.loadTournamentProperties(tournament)
                .getProperty("tournament.start.date", "").trim();
        if (configuredStartDate.isEmpty()) {
            throw new IOException("Missing tournament.start.date in data/predictions/" + tournament
                    + "/tournament.properties (expected YYYY-MM-DD).");
        }
        try {
            tournamentStartDate = LocalDate.parse(configuredStartDate);
        } catch (DateTimeException e) {
            throw new IOException("Invalid tournament.start.date: " + configuredStartDate
                    + " (expected YYYY-MM-DD).", e);
        }

        Path snapshotDir = projectRoot.resolve("data").resolve("elo").resolve("snapshots").resolve(tournament);
        Path historyDir = snapshotDir.resolve("history");
        Files.createDirectories(historyDir);

        int copiedTeams = writeTeamsSnapshot(snapshotDir.resolve("teams.csv"), teams);
        int copiedHistory = writeHistorySnapshot(historyDir, teams);
        int copiedResults = writeResultsSnapshot(snapshotDir.resolve("results.csv"), teams);
        writeMetadata(snapshotDir.resolve("metadata.properties"), teams.size(), copiedTeams, copiedHistory, copiedResults);

        System.out.println("Snapshot refreshed for " + tournament + ": " + copiedTeams
                + " teams, " + copiedHistory + " history files, " + copiedResults + " tournament results.");
    }

    private int writeTeamsSnapshot(Path output, Set<String> teams) throws IOException {
        Path source = projectRoot.resolve("data").resolve("elo").resolve("current").resolve("world.csv");
        if (!Files.exists(source)) {
            throw new IOException("Global ELO file not found: " + source.toAbsolutePath()
                    + ". Run --mode=elo-refresh first or provide data/elo/current/world.csv.");
        }

        List<SnapshotTeam> snapshotTeams = new java.util.ArrayList<>();
        Set<String> found = new LinkedHashSet<>();
        try (Reader reader = Files.newBufferedReader(source); var parser = CSV.parse(reader)) {
            for (CSVRecord record : parser) {
                String team = value(record, "team_name", 2);
                if (!teams.contains(team)) continue;
                int currentRating = Integer.parseInt(value(record, "rating", 3));
                snapshotTeams.add(new SnapshotTeam(
                        value(record, "team_code", 1), team, ratingBeforeTournament(team, currentRating)));
                found.add(team);
            }
        }

        Set<String> missing = new LinkedHashSet<>(teams);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new IOException("Snapshot missing ELO rows for: " + String.join(", ", missing));
        }

        snapshotTeams.sort(java.util.Comparator.comparingInt(SnapshotTeam::rating).reversed());
        try (Writer writer = Files.newBufferedWriter(output);
             CSVPrinter printer = CSVFormat.DEFAULT.print(writer)) {
            printer.printRecord("rank", "team_code", "team_name", "rating");
            for (int i = 0; i < snapshotTeams.size(); i++) {
                SnapshotTeam team = snapshotTeams.get(i);
                printer.printRecord(i + 1, team.code(), team.name(), team.rating());
            }
        }
        return snapshotTeams.size();
    }

    private int ratingBeforeTournament(String team, int fallbackRating) throws IOException {
        Path history = projectRoot.resolve("data").resolve("elo").resolve("current")
                .resolve("history").resolve(team + ".tsv");
        if (!Files.exists(history)) return fallbackRating;

        int rating = fallbackRating;
        boolean found = false;
        List<String> lines = Files.readAllLines(history);
        for (int i = 1; i < lines.size(); i++) {
            String[] cols = lines.get(i).split("\t", -1);
            if (cols.length < 12) continue;
            try {
                LocalDate matchDate = LocalDate.of(Integer.parseInt(cols[0].trim()),
                        Integer.parseInt(cols[1].trim()), Integer.parseInt(cols[2].trim()));
                if (!matchDate.isBefore(tournamentStartDate)) continue;
                Set<String> historicalNames = HISTORICAL_NAMES.getOrDefault(team, Set.of(team));
                if (historicalNames.contains(cols[3].trim())) {
                    rating = Integer.parseInt(cols[10].trim());
                    found = true;
                } else if (historicalNames.contains(cols[4].trim())) {
                    rating = Integer.parseInt(cols[11].trim());
                    found = true;
                }
            } catch (DateTimeException | NumberFormatException ignored) {
            }
        }
        return found ? rating : fallbackRating;
    }

    private record SnapshotTeam(String code, String name, int rating) {}

    private int writeHistorySnapshot(Path outputDir, Set<String> teams) throws IOException {
        Path sourceDir = projectRoot.resolve("data").resolve("elo").resolve("current").resolve("history");
        if (!Files.exists(sourceDir)) {
            return 0;
        }

        int since = Math.min(effectiveQualSinceYear, effectivePreTournamentSinceYear);
        int until = Math.max(effectiveQualUntilYear, effectivePreTournamentUntilYear);
        int count = 0;
        for (String team : teams) {
            Path source = sourceDir.resolve(team + ".tsv");
            if (!Files.exists(source)) {
                continue;
            }
            List<String> lines = Files.readAllLines(source);
            if (lines.isEmpty()) {
                continue;
            }
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            out.add(lines.get(0));
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] cols = line.split("\t", -1);
                if (cols.length < 3) {
                    continue;
                }
                try {
                    int year = Integer.parseInt(cols[0].trim());
                    int month = Integer.parseInt(cols[1].trim());
                    int day = Integer.parseInt(cols[2].trim());
                    LocalDate matchDate = LocalDate.of(year, month, day);
                    if (year >= since && year <= until && matchDate.isBefore(tournamentStartDate)) {
                        out.add(line);
                    }
                } catch (NumberFormatException | DateTimeException ignored) {
                }
            }
            Files.write(outputDir.resolve(team + ".tsv"), out);
            count++;
        }
        return count;
    }

    private int writeResultsSnapshot(Path output, Set<String> teams) throws IOException {
        List<TournamentResult> results = loadResultsFromHistory(teams);
        if (results.isEmpty()) {
            results = loadEloRatingsResults(teams);
        }
        results = normalizeTournamentResults(results);
        try (Writer writer = Files.newBufferedWriter(output);
             CSVPrinter printer = CSVFormat.DEFAULT.print(writer)) {
            printer.printRecord("date", "round", "home_team", "away_team", "home_score", "away_score", "match_type", "neutral");
            for (TournamentResult result : results) {
                printer.printRecord(result.date(), result.round(), result.homeTeam(), result.awayTeam(), result.homeScore(),
                        result.awayScore(), result.matchType(), result.neutral());
            }
        }
        return results.size();
    }

    private List<TournamentResult> loadEloRatingsResults(Set<String> teams) throws IOException {
        Map<String, String> codeToName = loadCurrentWorldCodeMap();
        String slug = eloRatingsResultsSlug();
        if (slug.isBlank()) {
            return List.of();
        }
        URI uri = URI.create("https://www.eloratings.net/" + slug + ".tsv");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(java.time.Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Failed to fetch EloRatings results from " + uri, e);
        } catch (Exception e) {
            return List.of();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
            return List.of();
        }
        List<TournamentResult> results = new java.util.ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try (Reader reader = new StringReader(response.body())) {
            CSVFormat rawFormat = CSVFormat.TDF.builder()
                    .setTrim(true)
                    .setIgnoreEmptyLines(true)
                    .build();
            try (var parser = rawFormat.parse(reader)) {
                for (CSVRecord record : parser) {
                    if (record.size() < 8) continue;
                    String year = value(record, 0).trim();
                    String month = value(record, 1).trim();
                    String day = value(record, 2).trim();
                    String home = codeToName.getOrDefault(value(record, 3).trim(), eloTeamName(value(record, 3)));
                    String away = codeToName.getOrDefault(value(record, 4).trim(), eloTeamName(value(record, 4)));
                    String homeScore = value(record, 5).trim();
                    String awayScore = value(record, 6).trim();
                    String matchType = value(record, 7).trim();
                    String neutral = record.size() > 8 ? value(record, 8).trim() : "";
                    if (home.isBlank() || away.isBlank() || homeScore.isBlank() || awayScore.isBlank()) continue;
                    if (teams != null && (!teams.contains(home) || !teams.contains(away))) continue;
                    try {
                        LocalDate date = LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
                        if (date.isBefore(tournamentStartDate)) continue;
                        String key = tournamentResultKey(date.toString(), home, away, homeScore, awayScore, matchType);
                        if (seen.add(key)) {
                            results.add(new TournamentResult(date, home, away, homeScore, awayScore, matchType, neutral, ""));
                        }
                    } catch (NumberFormatException | DateTimeException ignored) {
                    }
                }
            }
        }
        return results;
    }

    private List<TournamentResult> loadResultsFromHistory(Set<String> teams) throws IOException {
        Path sourceDir = projectRoot.resolve("data").resolve("elo").resolve("current").resolve("history");
        if (!Files.exists(sourceDir)) {
            return List.of();
        }

        List<TournamentResult> results = new java.util.ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String team : teams) {
            Path source = sourceDir.resolve(team + ".tsv");
            if (!Files.exists(source)) {
                continue;
            }
            List<String> lines = Files.readAllLines(source);
            if (lines.isEmpty()) {
                continue;
            }
            for (int i = 1; i < lines.size(); i++) {
                String[] cols = lines.get(i).split("	", -1);
                if (cols.length < 8) {
                    continue;
                }
                String matchType = cols[7].trim();
                if (matchType.equalsIgnoreCase("F") || matchType.equalsIgnoreCase("FT")) {
                    continue;
                }
                try {
                    int year = Integer.parseInt(cols[0].trim());
                    int month = Integer.parseInt(cols[1].trim());
                    int day = Integer.parseInt(cols[2].trim());
                    LocalDate matchDate = LocalDate.of(year, month, day);
                    if (matchDate.isBefore(tournamentStartDate)) {
                        continue;
                    }
                    String home = cols[3].trim();
                    String away = cols[4].trim();
                    if (!teams.contains(home) || !teams.contains(away)) {
                        continue;
                    }
                    String homeScore = cols[5].trim();
                    String awayScore = cols[6].trim();
                    String neutral = cols.length > 8 ? cols[8].trim() : "";
                    String key = tournamentResultKey(matchDate.toString(), home, away, homeScore, awayScore, matchType);
                    if (seen.add(key)) {
                        results.add(new TournamentResult(matchDate, home, away, homeScore, awayScore, matchType, neutral, ""));
                    }
                } catch (NumberFormatException | DateTimeException ignored) {
                }
            }
        }
        return results;
    }

    private static String tournamentResultKey(String date, String home, String away, String homeScore, String awayScore, String matchType) {
        String teamA = home == null ? "" : home.trim();
        String teamB = away == null ? "" : away.trim();
        String scoreA = homeScore == null ? "" : homeScore.trim();
        String scoreB = awayScore == null ? "" : awayScore.trim();
        String type = matchType == null ? "" : matchType.trim();
        if (teamA.compareToIgnoreCase(teamB) <= 0) {
            return date + "|" + teamA + "|" + teamB + "|" + scoreA + "|" + scoreB + "|" + type;
        }
        return date + "|" + teamB + "|" + teamA + "|" + scoreB + "|" + scoreA + "|" + type;
    }

    private List<TournamentResult> normalizeTournamentResults(List<TournamentResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<TournamentResult> ordered = new java.util.ArrayList<>(results);
        ordered.sort(java.util.Comparator.comparing(TournamentResult::date)
                .thenComparing(TournamentResult::homeTeam)
                .thenComparing(TournamentResult::awayTeam)
                .thenComparing(TournamentResult::matchType));
        int maxMatches = expectedTournamentMatchCount();
        if (maxMatches > 0 && ordered.size() > maxMatches) {
            ordered = new java.util.ArrayList<>(ordered.subList(0, maxMatches));
        }
        List<TournamentResult> normalized = new java.util.ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            TournamentResult result = ordered.get(i);
            normalized.add(new TournamentResult(result.date(), result.homeTeam(), result.awayTeam(),
                    result.homeScore(), result.awayScore(), result.matchType(), result.neutral(),
                    roundForMatchIndex(i + 1)));
        }
        return normalized;
    }

    private int expectedTournamentMatchCount() {
        if (tournamentStartDate == null) {
            return 0;
        }
        return tournamentStartDate.getYear() >= 2026 ? 104 : 64;
    }

    private String roundForMatchIndex(int matchIndex) {
        if (matchIndex <= 0) {
            return "";
        }
        if (tournamentStartDate != null && tournamentStartDate.getYear() >= 2026) {
            if (matchIndex <= 72) return "group";
            if (matchIndex <= 88) return "last_32";
            if (matchIndex <= 96) return "last_16";
            if (matchIndex <= 100) return "last_8";
            if (matchIndex <= 102) return "last_4";
            if (matchIndex == 103) return "third_place";
            return "final";
        }
        if (matchIndex <= 48) return "group";
        if (matchIndex <= 56) return "last_16";
        if (matchIndex <= 60) return "last_8";
        if (matchIndex <= 62) return "last_4";
        if (matchIndex == 63) return "third_place";
        return "final";
    }

    private Map<String, String> loadCurrentWorldCodeMap() throws IOException {
        Path source = projectRoot.resolve("data").resolve("elo").resolve("current").resolve("world.csv");
        if (!Files.exists(source)) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(source); var parser = CSV.parse(reader)) {
            for (CSVRecord record : parser) {
                String code = value(record, "team_code", 1);
                String name = value(record, "team_name", 2);
                if (!code.isBlank() && !name.isBlank()) {
                    map.put(code, name);
                }
            }
        }
        return map;
    }

    private String eloRatingsResultsSlug() {
        if (tournamentStartDate == null) {
            return "";
        }
        String slug = switch (tournamentStartDate.getYear()) {
            case 2010 -> "2010_World_Cup_results";
            case 2014 -> "2014_World_Cup_results";
            case 2018 -> "2018_World_Cup_results";
            case 2022 -> "2022_World_Cup_results";
            case 2026 -> "2026_World_Cup_results";
            default -> "";
        };
        return slug;
    }

    private static String eloTeamName(String codeOrName) {
        String v = codeOrName == null ? "" : codeOrName.trim();
        return switch (v) {
            case "USA" -> "United States";
            case "CIV" -> "Ivory Coast";
            case "CZE" -> "Czechia";
            case "RUS" -> "Russia";
            default -> v;
        };
    }

    private void writeMetadata(Path output, int requestedTeams, int copiedTeams, int copiedHistory, int copiedResults) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("created_at", Instant.now().toString());
        values.put("elo_source", "data/elo/current/world.csv");
        values.put("history_source", "data/elo/current/history");
        values.put("requested_team_count", String.valueOf(requestedTeams));
        values.put("team_count", String.valueOf(copiedTeams));
        values.put("history_file_count", String.valueOf(copiedHistory));
        values.put("result_match_count", String.valueOf(copiedResults));
        values.put("tournament_start_date", tournamentStartDate.toString());
        values.put("qual_form_since", String.valueOf(effectiveQualSinceYear));
        values.put("qual_form_until", String.valueOf(effectiveQualUntilYear));
        values.put("pre_tournament_form_since", String.valueOf(effectivePreTournamentSinceYear));
        values.put("pre_tournament_form_until", String.valueOf(effectivePreTournamentUntilYear));

        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            lines.add(entry.getKey() + "=" + entry.getValue());
        }
        Files.write(output, lines);
    }

    private static String value(CSVRecord record, int index) {
        return record.size() > index ? record.get(index).trim() : "";
    }

    private static String value(CSVRecord record, String header, int fallbackIndex) {
        if (record.isMapped(header)) {
            return record.get(header).trim();
        }
        return record.size() > fallbackIndex ? record.get(fallbackIndex).trim() : "";
    }

    private record TournamentResult(LocalDate date, String homeTeam, String awayTeam, String homeScore,
                                    String awayScore, String matchType, String neutral, String round) {}
}

package com.tournamentpredictor.services.io;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.calculation.QualificationFormCalculator;
import com.tournamentpredictor.services.calculation.TeamEloSnapshot;
import com.tournamentpredictor.services.storage.GeneratedDataStore;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.StringReader;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class CsvLoader {

    private static final CSVFormat CSV = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    private final Path projectRoot;
    private final GeneratedDataStore generatedDataStore;

    public CsvLoader() {
        this(Path.of(System.getProperty("user.dir")));
    }

    /** For testing: use a custom root path instead of the working directory. */
    public CsvLoader(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.generatedDataStore = new GeneratedDataStore(projectRoot);
    }

    public CsvLoader withQualYears(int sinceYear, int untilYear) {
        this.qualFormSinceYear = sinceYear;
        this.qualFormUntilYear = untilYear;
        return this;
    }

    public Map<String,String> loadGroups(String tournament) throws IOException {
        Path p = groupsPath(tournament);
        Map<String,String> map = new HashMap<>();
        Map<String,Integer> groupCounters = new HashMap<>();
        try (var parser = parseGeneratedCsv(p)) {
            for (CSVRecord r : parser) {
                String raw = r.get(0).trim();
                String team = r.size() > 1 ? r.get(1).trim() : "";
                if (raw.isEmpty() || team.isEmpty()) continue;
                String pos = resolvePosition(raw, groupCounters);
                map.put(pos, team);
            }
        }
        return map;
    }

    public Map<String,String> loadGroupQualifies(String tournament) throws IOException {
        Path p = groupsPath(tournament);
        Map<String,String> map = new HashMap<>();
        Map<String,Integer> groupCounters = new HashMap<>();
        try (var parser = parseGeneratedCsv(p)) {
            for (CSVRecord r : parser) {
                String raw = r.get(0).trim();
                String qualifies = r.isMapped("qualifies") ? r.get("qualifies").toLowerCase() :
                        (r.size() > 3 ? r.get(3).toLowerCase() : "");
                if (raw.isEmpty()) continue;
                String pos = resolvePosition(raw, groupCounters);
                map.put(pos, qualifies);
            }
        }
        return map;
    }

    public Map<String,String> loadGroupWinner(String tournament) throws IOException {
        return loadGroupsColumn(tournament, "group_winner");
    }

    public Map<String,String> loadRunnerUp(String tournament) throws IOException {
        return loadGroupsColumn(tournament, "runner_up");
    }

    public Map<String,String> loadThirdPlace(String tournament) throws IOException {
        return loadGroupsColumn(tournament, "3rd_place");
    }

    private Map<String,String> loadGroupsColumn(String tournament, String columnName) throws IOException {
        Path p = groupsPath(tournament);
        Map<String,String> map = new HashMap<>();
        Map<String,Integer> groupCounters = new HashMap<>();
        try (var parser = parseGeneratedCsv(p)) {
            for (CSVRecord r : parser) {
                String raw = r.get(0).trim();
                String val = r.isMapped(columnName) ? r.get(columnName).trim().toLowerCase() : "";
                if (raw.isEmpty()) continue;
                String pos = resolvePosition(raw, groupCounters);
                if (val.isBlank()) {
                    val = derivedGroupStatus(columnName, pos, r);
                }
                map.put(pos, val);
            }
        }
        return map;
    }

    private static String derivedGroupStatus(String columnName, String pos, CSVRecord record) {
        String rank = "";
        if (record.isMapped("predicted_position")) {
            rank = record.get("predicted_position").replaceAll("\\s*\\(.*", "").trim();
        }
        if (rank.isBlank() && pos != null && pos.matches("[A-La-l][1-4]")) {
            rank = pos.substring(1);
        }
        return switch (columnName) {
            case "group_winner" -> "1".equals(rank) ? "yes" : "no";
            case "runner_up" -> "2".equals(rank) ? "yes" : "no";
            case "3rd_place" -> "3".equals(rank) ? "yes" : "no";
            default -> "";
        };
    }

    /**
     * Converts a raw position value from groups.csv to a canonical "A1"-style key.
     * Accepts both the legacy "A1" format and the new "A" (group-letter-only) format.
     * For the new format, the numeric suffix is derived from the row's order within its group.
     */
    private static String resolvePosition(String raw, Map<String, Integer> groupCounters) {
        if (raw.matches("[A-La-l][1-4]")) {
            return raw.toUpperCase();
        } else if (raw.matches("[A-La-l]")) {
            String g = raw.toUpperCase();
            int idx = groupCounters.merge(g, 1, Integer::sum);
            return g + idx;
        }
        return raw.toUpperCase();
    }

    public Map<String,String> loadOdds(String tournament, String columnName) throws IOException {
        Path p = projectRoot.resolve("data").resolve("ods").resolve(tournament).resolve("to_reach.csv");
        if (!Files.exists(p)) return new HashMap<>();
        Map<String,String> map = new HashMap<>();
        try (var reader = Files.newBufferedReader(p); var parser = CSV.parse(reader)) {
            for (CSVRecord r : parser) {
                if (!r.isMapped(columnName)) break;
                String country = r.get(0);
                String val = r.get(columnName);
                if (!country.isEmpty()) {
                    map.put(country, val);
                    map.put(normaliseCountryName(country), val);
                }
            }
        }
        return map;
    }

    private static String normaliseCountryName(String name) {
        switch (name.trim()) {
            case "USA": return "United States";
            case "United States": return "USA";
            case "Bosnia & Herzegovina": return "Bosnia and Herzegovina";
            case "Bosnia and Herzegovina": return "Bosnia & Herzegovina";
            case "Korea Republic": return "South Korea";
            case "South Korea": return "Korea Republic";
            case "Ivory Coast": return "Côte d'Ivoire";
            case "Côte d'Ivoire": return "Ivory Coast";
            default: return name;
        }
    }

    public Map<String,String> loadLast16Odds(String tournament) throws IOException {
        return loadOdds(tournament, "last_16");
    }

    public Map<String,Integer> loadElo() throws IOException {
        return loadEloFrom(worldEloPath());
    }

    public Map<String,Integer> loadEloForTournament(String tournament) throws IOException {
        Path snapshot = snapshotDir(tournament).resolve("teams.csv");
        if (Files.exists(snapshot)) {
            return loadEloFrom(snapshot);
        }
        throw new IOException("Tournament snapshot not found: " + snapshot.toAbsolutePath()
                + ". Run --mode=snapshot-refresh --tournament=" + tournament + " first.");
    }

    private Map<String,Integer> loadEloFrom(Path p) throws IOException {
        if (!Files.exists(p)) throw new IOException("ELO file not found: " + p.toAbsolutePath());
        Map<String,Integer> map = new HashMap<>();
        try (var reader = Files.newBufferedReader(p); var parser = CSV.parse(reader)) {
            for (CSVRecord r : parser) {
                if (r.size() < 4) continue;
                String name = r.isMapped("team_name") ? r.get("team_name") : r.get(2);
                String rating = r.isMapped("rating") ? r.get("rating") : r.get(3);
                try { map.put(name, Integer.parseInt(rating)); } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    public Map<String,String> loadTeamCodes() throws IOException {
        return loadTeamCodesFrom(worldEloPath());
    }

    public Map<String,String> loadTeamCodesForTournament(String tournament) throws IOException {
        Path snapshot = snapshotDir(tournament).resolve("teams.csv");
        if (Files.exists(snapshot)) {
            return loadTeamCodesFrom(snapshot);
        }
        throw new IOException("Tournament snapshot not found: " + snapshot.toAbsolutePath()
                + ". Run --mode=snapshot-refresh --tournament=" + tournament + " first.");
    }

    private Map<String,String> loadTeamCodesFrom(Path p) throws IOException {
        if (!Files.exists(p)) return new HashMap<>();
        Map<String,String> map = new HashMap<>();
        try (var reader = Files.newBufferedReader(p); var parser = CSV.parse(reader)) {
            for (CSVRecord r : parser) {
                if (r.size() < 3) continue;
                String name = r.isMapped("team_name") ? r.get("team_name") : r.get(2);
                String code = r.isMapped("team_code") ? r.get("team_code") : r.get(1);
                map.put(name, code);
            }
        }
        return map;
    }


    public boolean hasTournamentSnapshot(String tournament) {
        if (Files.exists(snapshotDir(tournament).resolve("teams.csv"))) return true;
        return false;
    }

    public Properties loadTournamentProperties(String tournament) throws IOException {
        Properties properties = new Properties();
        Path path = projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve("tournament.properties");
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            }
        }
        return properties;
    }

    public Properties loadSnapshotMetadata(String tournament) throws IOException {
        Properties properties = new Properties();
        Path path = snapshotDir(tournament).resolve("metadata.properties");
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            }
            return properties;
        }
        return properties;
    }

    public int resolveTournamentSetting(String tournament, String key, int defaultValue) throws IOException {
        Properties tournamentProperties = loadTournamentProperties(tournament);
        return intProperty(tournamentProperties, key, defaultValue);
    }

    public Set<String> resolveTournamentMatchTypes(String tournament, String key, Set<String> defaults)
            throws IOException {
        String configured = loadTournamentProperties(tournament).getProperty(key, "").trim();
        if (configured.isEmpty()) return defaults;
        Set<String> types = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> value.length() > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return types.isEmpty() ? defaults : types;
    }

    public int resolveSnapshotBackedSetting(String tournament, String key, String metadataKey, int defaultValue) throws IOException {
        if (hasTournamentSnapshot(tournament)) {
            Properties metadata = loadSnapshotMetadata(tournament);
            if (metadata.containsKey(metadataKey)) {
                return intProperty(metadata, metadataKey, defaultValue);
            }
        }
        return resolveTournamentSetting(tournament, key, defaultValue);
    }

    public LocalDate resolveSnapshotBackedDate(String tournament, String key, String metadataKey) throws IOException {
        Properties source = hasTournamentSnapshot(tournament) ? loadSnapshotMetadata(tournament) : loadTournamentProperties(tournament);
        String sourceKey = hasTournamentSnapshot(tournament) ? metadataKey : key;
        String value = source.getProperty(sourceKey, "").trim();
        if (value.isEmpty() && hasTournamentSnapshot(tournament)) {
            source = loadTournamentProperties(tournament);
            sourceKey = key;
            value = source.getProperty(sourceKey, "").trim();
        }
        if (value.isEmpty()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException e) {
            throw new IOException("Invalid " + sourceKey + ": " + value + " (expected YYYY-MM-DD).", e);
        }
    }

    private static int intProperty(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private Path snapshotDir(String tournament) {
        return projectRoot.resolve("data").resolve("elo").resolve("snapshots").resolve(tournament);
    }

    public Path historyDirForTournament(String tournament) throws IOException {
        Path snapshot = snapshotDir(tournament).resolve("history");
        if (Files.exists(snapshot)) {
            return snapshot;
        }
        throw new IOException("Tournament snapshot history not found: " + snapshot.toAbsolutePath()
                + ". Run --mode=snapshot-refresh --tournament=" + tournament + " first.");
    }

    private Path worldEloPath() {
        return projectRoot.resolve("data").resolve("elo").resolve("current").resolve("world.csv");
    }

    /** Loads the team names from start.csv for a tournament (the team column). */
    public Set<String> loadStartTeams(String tournament) throws IOException {
        Path p = projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve("start.csv");
        if (!generatedDataStore.exists(p)) throw new IOException("start.csv not found: " + p.toAbsolutePath());
        Set<String> teams = new LinkedHashSet<>();
        try (var parser = parseGeneratedCsv(p)) {
            for (CSVRecord r : parser) {
                String team = r.isMapped("team") ? r.get("team") : (r.size() > 1 ? r.get(1) : "");
                if (!team.isEmpty()) teams.add(team);
            }
        }
        return teams;
    }

    /** Loads the combined ELO map for a tournament: world.csv as base, overridden by groups.csv values when available. */
    public Map<String,Integer> loadTournamentElo(String tournament) throws IOException {
        Map<String,Integer> eloRatings = loadEloForTournament(tournament);
        Path groups = projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve("groups.csv");
        if (generatedDataStore.exists(groups)) {
            eloRatings.putAll(loadGroupElo(tournament));
        }
        return eloRatings;
    }

    /** Loads team ELO ratings from groups.csv (team name + adjusted ELO). */
    public Map<String,Integer> loadGroupElo(String tournament) throws IOException {
        Path p = groupsPath(tournament);
        Map<String,Integer> map = new HashMap<>();
        try (var parser = parseGeneratedCsv(p)) {
            for (CSVRecord r : parser) {
                if (r.size() < 3) continue;
                String name = r.get(1).trim();
                String elo = r.isMapped("elo_ranking") ? r.get("elo_ranking") : r.get(2);
                try { map.put(name, Integer.parseInt(elo.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    public Map<String, TeamEloSnapshot> loadTeamSnapshots(String tournament) throws IOException {
        Path p = groupsPath(tournament);
        Map<String, TeamEloSnapshot> map = new HashMap<>();
        try (var parser = parseGeneratedCsv(p)) {
            for (CSVRecord r : parser) {
                String name = r.size() > 1 ? r.get(1).trim() : "";
                if (name.isEmpty()) continue;
                try {
                    int baseElo = r.isMapped("base_elo") ? Integer.parseInt(r.get("base_elo").trim()) : 0;
                    int qualBonus = r.isMapped("qual_bonus") ? Integer.parseInt(r.get("qual_bonus").trim()) : 0;
                    int leagueAdjustment = r.isMapped("confederation_adjustment") ? Integer.parseInt(r.get("confederation_adjustment").trim()) : 0;
                    int adjustedElo = r.isMapped("elo_ranking") ? Integer.parseInt(r.get("elo_ranking").trim()) : baseElo + qualBonus + leagueAdjustment;
                    int squadDepthLevel = r.isMapped("squad_depth") ? Integer.parseInt(r.get("squad_depth").trim()) : 0;
                    int legacyQuality = r.isMapped("squad_quality") ? Integer.parseInt(r.get("squad_quality").trim()) : 0;
                    int attackQuality = r.isMapped("attack_quality") ? Integer.parseInt(r.get("attack_quality").trim()) : legacyQuality;
                    int defenceQuality = r.isMapped("defence_quality") ? Integer.parseInt(r.get("defence_quality").trim()) : legacyQuality;
                    map.put(name, new TeamEloSnapshot(baseElo, qualBonus, adjustedElo, squadDepthLevel,
                            attackQuality, defenceQuality));
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    private int[] injuryPenalties       = {0, 22, 45, 90};
    private int[] heatAdvantages        = {0,  9, 18, 35};
    private int[] squadDropoutPenalties = {0, 18, 35, 70};
    private int[] squadAgePenalties     = {0, 12, 8};
    private int[] squadCohesionPenalties = {0, 11, 22, 45};
    private int[] squadDepthPenalties   = {0, 10, 20};
    private int squadDepthExcellentBonus = 10;
    private int   homeAdvantage         = 100;
    private int   preTournamentFormEloMax = 50;
    private int   preTournamentFormMaxGames = 5;
    private static final int   PRE_TOURNAMENT_FORM_SINCE_YEAR = 2026;
    private static final int   PRE_TOURNAMENT_FORM_UNTIL_YEAR = 2026;
    private static final java.util.Set<String> QUALIFIER_TYPES =
            java.util.Set.of("WQ", "WQS", "FQ");

    private int qualFormSinceYear = 2023;
    private int qualFormUntilYear = 2026;

    /**
     * Overrides all penalty/bonus values from a {@link com.tournamentpredictor.config.PredictionConfig}.
     * Call this immediately after construction when running inside the web application so that
     * {@code application.properties} values are actually used instead of the hardcoded defaults.
     */
    public CsvLoader withConfig(com.tournamentpredictor.config.PredictionConfig config) {
        this.injuryPenalties       = config.getInjuryPenalties();
        this.heatAdvantages        = config.getHeatAdvantages();
        this.squadDropoutPenalties = config.getSquadDropoutPenalties();
        this.squadAgePenalties     = new int[]{0, config.getSquadAgeYoungPenalty(), config.getSquadAgeAgingPenalty()};
        this.squadCohesionPenalties = new int[]{0, config.getSquadCohesionUnsettledPenalty(),
                config.getSquadCohesionDisruptedPenalty(), config.getSquadCohesionFracturedPenalty()};
        this.squadDepthPenalties   = new int[]{0, config.getSquadDepthLimitedPenalty(), config.getSquadDepthThinPenalty()};
        this.squadDepthExcellentBonus = config.getSquadDepthExcellentBonus();
        this.homeAdvantage         = config.getHomeAdvantageElo();
        this.preTournamentFormEloMax = config.getPreTournamentFormEloMax();
        this.preTournamentFormMaxGames = config.getPreTournamentFormMaxGames();
        return this;
    }

    /**
     * Loads ELO adjustment breakdowns per team for a tournament.
     * Reads start.csv (host/injury/heat/dropout levels), groups.csv snapshots when available,
     * and tournament snapshot ELO/history files.
     * Returns an empty map if start.csv does not exist.
     */
    public Map<String, EloBreakdown> loadEloBreakdowns(String tournament) throws IOException {
        Path startPath = projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve("start.csv");
        if (!generatedDataStore.exists(startPath)) return new HashMap<>();

        Map<String,Integer> worldElo = new HashMap<>();
        try {
            worldElo.putAll(loadEloForTournament(tournament));
        } catch (IOException ignored) {
        }
        Map<String, TeamEloSnapshot> snapshots;
        try {
            snapshots = loadTeamSnapshots(tournament);
        } catch (IOException e) {
            snapshots = new HashMap<>();
        }

        Path historyDir = historyDirForTournament(tournament);
        int effectiveQualSinceYear = resolveSnapshotBackedSetting(tournament,
                "qual.form.since.year", "qual_form_since", qualFormSinceYear);
        int effectiveQualUntilYear = resolveSnapshotBackedSetting(tournament,
                "qual.form.until.year", "qual_form_until", qualFormUntilYear);
        int effectivePreTournamentSinceYear = resolveSnapshotBackedSetting(tournament,
                "pre.tournament.form.since.year", "pre_tournament_form_since", PRE_TOURNAMENT_FORM_SINCE_YEAR);
        int effectivePreTournamentUntilYear = resolveSnapshotBackedSetting(tournament,
                "pre.tournament.form.until.year", "pre_tournament_form_until", PRE_TOURNAMENT_FORM_UNTIL_YEAR);
        LocalDate tournamentStartDate = resolveSnapshotBackedDate(tournament,
                "tournament.start.date", "tournament_start_date");
        Set<String> qualifierTypes = resolveTournamentMatchTypes(tournament,
                "qual.form.match.types", QUALIFIER_TYPES);
        QualificationFormCalculator friendlyCalc = new QualificationFormCalculator(
                historyDir,
                effectivePreTournamentSinceYear, effectivePreTournamentUntilYear,
                preTournamentFormEloMax,
                Set.of("F"), preTournamentFormMaxGames, tournamentStartDate);

        Map<String, EloBreakdown> result = new LinkedHashMap<>();
        try (var parser = parseGeneratedCsv(startPath)) {
            for (CSVRecord r : parser) {
                if (!r.isMapped("team")) continue;
                String team = r.get("team");
                if (team.isEmpty()) continue;
                boolean isHost = "yes".equalsIgnoreCase(r.isMapped("host") ? r.get("host") : "no");
                int injuryLevel  = safeLevel(r, "injury_impact");
                int heatLevel    = safeLevel(r, "heat_impact");
                int dropoutLevel = safeLevel(r, "squad_dropouts");
                int squadAgeLevel = safeLevel(r, "squad_age_profile");
                int squadCohesionLevel = safeLevel(r, "squad_cohesion");
                int squadDepthLevel = signedDepth(r);
                int legacyQuality = safeLevel(r, "squad_quality");
                int attackQuality = signedQuality(r, "attack_quality", legacyQuality);
                int defenceQuality = signedQuality(r, "defence_quality", legacyQuality);
                TeamEloSnapshot snapshot = snapshots.get(team);
                int baseElo = snapshot != null ? snapshot.baseElo() : worldElo.getOrDefault(team, 0);
                int qualBonus = snapshot != null ? snapshot.qualBonus() : 0;
                int preTournamentBonus = friendlyCalc.getQualBonus(team);
                int homeBonus         = isHost ? homeAdvantage : 0;
                int injuryPenalty     = injuryPenalties[Math.min(injuryLevel, injuryPenalties.length - 1)];
                int heatBonus         = heatAdvantages[Math.min(heatLevel, heatAdvantages.length - 1)];
                int dropoutPenalty    = squadDropoutPenalties[Math.min(dropoutLevel, squadDropoutPenalties.length - 1)];
                int squadAgePenalty   = squadAgePenalties[Math.min(squadAgeLevel, squadAgePenalties.length - 1)];
                int squadCohesionPenalty = squadCohesionPenalties[Math.min(squadCohesionLevel, squadCohesionPenalties.length - 1)];
                int squadDepthPenalty = squadDepthLevel == -1
                        ? -squadDepthExcellentBonus
                        : squadDepthPenalties[Math.min(squadDepthLevel, squadDepthPenalties.length - 1)];
                int defenceQualityInput = defenceQuality;
                String confederation = r.isMapped("confederation") ? r.get("confederation").trim() : "";
                int confederationAdjustment = confederationAdjustment(confederation);
                String dropoutNotes = r.isMapped("dropout_notes") ? r.get("dropout_notes") : "";
                String injuryNotes  = r.isMapped("injury_notes")  ? r.get("injury_notes")  : "";
                String ageNotes     = r.isMapped("age_notes")     ? r.get("age_notes")     : "";
                String cohesionNotes = r.isMapped("cohesion_notes") ? r.get("cohesion_notes") : "";
                String depthNotes = r.isMapped("depth_notes") ? r.get("depth_notes") : "";
                String qualityNotes = r.isMapped("quality_notes") ? r.get("quality_notes") : "";
                List<String[]> qualResults = attachContributions(
                        loadQualResults(historyDir, team, effectiveQualSinceYear, effectiveQualUntilYear, tournamentStartDate, qualifierTypes), qualBonus);
                List<String[]> friendlyResults = attachContributions(
                        loadFriendlyResults(historyDir, team, effectivePreTournamentSinceYear, effectivePreTournamentUntilYear, tournamentStartDate),
                        preTournamentBonus);
                result.put(team, new EloBreakdown(
                        baseElo, isHost, homeBonus,
                        injuryLevel, injuryPenalty,
                        heatLevel, heatBonus,
                        dropoutLevel, dropoutPenalty,
                        qualBonus, preTournamentBonus,
                        squadAgeLevel, squadAgePenalty,
                        squadCohesionLevel, squadCohesionPenalty,
                        squadDepthLevel, squadDepthPenalty,
                        attackQuality, defenceQualityInput,
                        dropoutNotes, injuryNotes, ageNotes, cohesionNotes, depthNotes, qualityNotes,
                        qualResults, friendlyResults,
                        confederation, confederationAdjustment,
                        0, "", ""));
            }
        }
        return result;
    }

    private static int confederationAdjustment(String confederation) {
        if (confederation == null) return 0;
        return switch (confederation.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "UEFA", "CONMEBOL" -> 0;
            case "CAF" -> -10;
            case "AFC", "CONCACAF" -> -25;
            case "OFC" -> -50;
            default -> 0;
        };
    }

    private static boolean isOnOrAfter(String[] cols, LocalDate maxDate) {
        if (maxDate == null) return false;
        try {
            return !LocalDate.of(Integer.parseInt(cols[0].trim()), Integer.parseInt(cols[1].trim()),
                    Integer.parseInt(cols[2].trim())).isBefore(maxDate);
        } catch (DateTimeException | NumberFormatException e) {
            return true;
        }
    }

    private List<String[]> loadFriendlyResults(Path historyDir, String teamName, int sinceYear, int untilYear, LocalDate maxDate) {
        Path tsv = historyDir.resolve(teamName + ".tsv");
        if (!Files.exists(tsv)) return List.of();
        List<String[]> results = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(tsv);
            for (int i = 1; i < lines.size(); i++) {
                String[] cols = lines.get(i).split("\t", -1);
                if (cols.length < 8) continue;
                if (!"F".equals(cols[7].trim())) continue;
                int year;
                try { year = Integer.parseInt(cols[0].trim()); } catch (Exception e) { continue; }
                if (year < sinceYear || year > untilYear || isOnOrAfter(cols, maxDate)) continue;
                int homeScore, awayScore, month, day;
                try {
                    month = Integer.parseInt(cols[1].trim());
                    day = Integer.parseInt(cols[2].trim());
                    homeScore = Integer.parseInt(cols[5].trim());
                    awayScore = Integer.parseInt(cols[6].trim());
                } catch (Exception e) { continue; }
                boolean isHome = teamName.equals(cols[3].trim());
                String opponent = isHome ? cols[4].trim() : cols[3].trim();
                int teamScore = isHome ? homeScore : awayScore;
                int oppScore  = isHome ? awayScore : homeScore;
                String result = teamScore > oppScore ? "W" : teamScore == oppScore ? "D" : "L";
                int pts = teamScore > oppScore ? 3 : teamScore == oppScore ? 1 : 0;
                String score = teamScore + "–" + oppScore;
                String date = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month, day);
                results.add(new String[]{result, opponent, score, String.valueOf(pts), String.valueOf(teamScore), String.valueOf(oppScore), date});
            }
        } catch (IOException ignored) {}
        // Show the same warm-up matches used by the ELO adjustment.
        return results.size() > preTournamentFormMaxGames
                ? results.subList(results.size() - preTournamentFormMaxGames, results.size()) : results;
    }

    private List<String[]> loadQualResults(Path historyDir, String teamName, int sinceYear, int untilYear, LocalDate maxDate, Set<String> qualifierTypes) {
        Path tsv = historyDir.resolve(teamName + ".tsv");
        if (!Files.exists(tsv)) return List.of();
        List<String[]> results = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(tsv);
            for (int i = 1; i < lines.size(); i++) {
                String[] cols = lines.get(i).split("\t", -1);
                if (cols.length < 8) continue;
                int year;
                try { year = Integer.parseInt(cols[0].trim()); } catch (Exception e) { continue; }
                if (year < sinceYear || year > untilYear || isOnOrAfter(cols, maxDate)) continue;
                if (!qualifierTypes.contains(cols[7].trim())) continue;
                int homeScore, awayScore, month, day;
                try {
                    month = Integer.parseInt(cols[1].trim());
                    day = Integer.parseInt(cols[2].trim());
                    homeScore = Integer.parseInt(cols[5].trim());
                    awayScore = Integer.parseInt(cols[6].trim());
                } catch (Exception e) { continue; }
                boolean isHome = teamName.equals(cols[3].trim());
                String opponent = isHome ? cols[4].trim() : cols[3].trim();
                int teamScore = isHome ? homeScore : awayScore;
                int oppScore  = isHome ? awayScore : homeScore;
                String result = teamScore > oppScore ? "W" : teamScore == oppScore ? "D" : "L";
                int pts = teamScore > oppScore ? 3 : teamScore == oppScore ? 1 : 0;
                String score = teamScore + "–" + oppScore;
                String date = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month, day);
                results.add(new String[]{result, opponent, score, String.valueOf(pts), String.valueOf(teamScore), String.valueOf(oppScore), date});
            }
        } catch (IOException ignored) {}
        int limit = 8;
        return results.size() > limit ? results.subList(results.size() - limit, results.size()) : results;
    }

    /**
     * Takes raw result arrays [result, opponent, score, pts, gf, ga, date] and
     * returns arrays [result, opponent, score, eloContrib, date] where contributions
     * are computed using the same linear formula as QualificationFormCalculator and
     * scaled so they sum to totalBonus.
     */
    private static List<String[]> attachContributions(List<String[]> results, int totalBonus) {
        if (results.isEmpty()) return List.of();
        int n = results.size();
        double[] weights = new double[n];
        for (int i = 0; i < n; i++) {
            int pts = Integer.parseInt(results.get(i)[3]);
            int gf  = Integer.parseInt(results.get(i)[4]);
            int ga  = Integer.parseInt(results.get(i)[5]);
            // Linear decomposition: 0.6*(pts/3) + 0.2*(gf/3) - 0.2*(ga/3) - 0.3
            weights[i] = 0.6 * (pts / 3.0) + 0.2 * (gf / 3.0) - 0.2 * (ga / 3.0) - 0.3;
        }
        double totalWeight = 0;
        for (double w : weights) totalWeight += w;

        List<String[]> out = new ArrayList<>(n);
        if (Math.abs(totalWeight) < 0.001) {
            // Degenerate: distribute evenly
            int each = totalBonus / n;
            for (String[] r : results)
                out.add(new String[]{r[0], r[1], r[2], String.valueOf(each), r.length > 6 ? r[6] : ""});
        } else {
            double scale = totalBonus / totalWeight;
            int assigned = 0;
            for (int i = 0; i < n; i++) {
                int contrib = (i < n - 1) ? (int) Math.round(weights[i] * scale) : (totalBonus - assigned);
                assigned += contrib;
                String[] result = results.get(i);
                out.add(new String[]{result[0], result[1], result[2], String.valueOf(contrib), result.length > 6 ? result[6] : ""});
            }
        }
        return out;
    }

    private static int safeLevel(CSVRecord r, String col) {
        if (!r.isMapped(col)) return 0;
        try { return Integer.parseInt(r.get(col).trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static int signedDepth(CSVRecord r) {
        if (!r.isMapped("squad_depth") || r.get("squad_depth").isBlank()) return 0;
        try { return Math.max(-1, Math.min(2, Integer.parseInt(r.get("squad_depth").trim()))); }
        catch (NumberFormatException e) { return 0; }
    }

    private static int signedQuality(CSVRecord r, String col, int fallback) {
        if (!r.isMapped(col) || r.get(col).isBlank()) return Math.max(-2, Math.min(2, fallback));
        try { return Math.max(-2, Math.min(2, Integer.parseInt(r.get(col).trim()))); }
        catch (NumberFormatException e) { return Math.max(-2, Math.min(2, fallback)); }
    }

    private Path groupsPath(String tournament) throws IOException {
        Path p = projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve("groups.csv");
        if (!generatedDataStore.exists(p))
            throw new IOException("Groups data not found: " + p.toAbsolutePath() + ". Expected generated groups data for " + tournament);
        return p;
    }

    private CSVParser parseGeneratedCsv(Path path) throws IOException {
        List<String> lines = generatedDataStore.readLines(path);
        if (lines.isEmpty()) {
            throw new IOException("Generated data not found: " + path.toAbsolutePath());
        }
        return CSV.parse(new StringReader(String.join("\n", lines)));
    }

    /** Validates groups.csv and returns a list of error messages. Empty list means valid. */
    public List<String> validateGroups(String tournament) throws IOException {
        Path p = projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve("groups.csv");
        List<String> lines = generatedDataStore.readLines(p);
        if(lines.isEmpty())
            return List.of("groups data not found: " + p.toAbsolutePath());

        List<String> errors = new ArrayList<>();

        String[] headerCols = lines.isEmpty() ? new String[0] : lines.get(0).split(",", -1);
        List<String> headers = Arrays.stream(headerCols).map(String::trim).collect(Collectors.toList());
        boolean hasPredictedPos = headers.contains("predicted_position");
        boolean hasBaseElo = headers.contains("base_elo");
        boolean hasQualBonus = headers.contains("qual_bonus");
        boolean hasQualForm = headers.contains("qualification_form");
        boolean hasFriendliesForm = headers.contains("friendlies_form");
        boolean hasH2hColumn = headers.contains("h2h");
        boolean hasH2hColumns = headers.contains("history_competitions") || headers.contains("group_h2h_comp");
        int gwIdx;
        int ruIdx;
        int tpIdx;
        int minCols;
        int baseEloIdx = -1;
        int qualBonusIdx = -1;
        int eloRankingIdx = -1;
        int h2hIdx = -1;
        int h2hCompIdx = -1;
        int h2hFriendlyIdx = -1;
        int qualFormIdx = -1;
        int friendliesFormIdx = -1;
        int predictedPosIdx = -1;
        if (hasPredictedPos) {
            predictedPosIdx = headers.indexOf("predicted_position");
            gwIdx = headers.indexOf("group_winner");
            ruIdx = headers.indexOf("runner_up");
            tpIdx = headers.indexOf("3rd_place");
            if (hasBaseElo) {
                baseEloIdx = headers.indexOf("base_elo");
                qualBonusIdx = hasQualBonus ? headers.indexOf("qual_bonus") : -1;
                eloRankingIdx = headers.indexOf("elo_ranking");
                minCols = 9;
            } else if (hasH2hColumn) {
                h2hIdx = headers.indexOf("h2h");
                qualFormIdx = hasQualForm ? headers.indexOf("qualification_form") : -1;
                minCols = hasQualForm ? 9 : 8;
            } else {
                h2hCompIdx = headers.contains("history_competitions") ? headers.indexOf("history_competitions") : headers.indexOf("group_h2h_comp");
                h2hFriendlyIdx = headers.contains("history_friendlies") ? headers.indexOf("history_friendlies") : headers.indexOf("group_h2h_friendly");
                qualFormIdx = hasQualForm ? headers.indexOf("qualification_form") : -1;
                friendliesFormIdx = hasFriendliesForm ? headers.indexOf("friendlies_form") : -1;
                minCols = hasQualForm && hasFriendliesForm ? 11 : hasQualForm ? 10 : 9;
            }
        } else if (hasH2hColumns) {
            gwIdx = 5;
            ruIdx = 6;
            tpIdx = 7;
            minCols = 8;
        } else {
            gwIdx = 3;
            ruIdx = 4;
            tpIdx = 5;
            minCols = 6;
        }

        Set<String> seenPositions = new LinkedHashSet<>();
        Set<String> seenTeams = new LinkedHashSet<>();
        Map<String, Integer> gwYes  = new LinkedHashMap<>();
        Map<String, Integer> ruYes  = new LinkedHashMap<>();
        Map<String, Integer> gwSet  = new LinkedHashMap<>();
        Map<String, Integer> ruSet  = new LinkedHashMap<>();
        Map<String, Integer> groupTeamCount = new LinkedHashMap<>();
        Set<String> validValues = Set.of("yes", "maybe", "no");

        for(int i = 1; i < lines.size(); i++){
            String raw = lines.get(i);
            String stripped = raw.replaceAll("\"", "").trim();
            if(stripped.isEmpty()) continue;
            String[] c = raw.split(",", -1);

            int lastNonEmpty = c.length - 1;
            while(lastNonEmpty > 0 && c[lastNonEmpty].trim().isEmpty()) lastNonEmpty--;
            int usableCols = lastNonEmpty + 1;

            if(usableCols < minCols){
                errors.add("Row " + i + ": expected " + minCols + " columns, got " + usableCols + " — \"" + raw.trim() + "\"");
                continue;
            }

            String pos  = c[0].trim();
            String team = c[1].trim();
            String gw   = c[gwIdx].trim().toLowerCase();
            String ru   = c[ruIdx].trim().toLowerCase();
            String tp   = c[tpIdx].trim().toLowerCase();

            boolean isGroupLetterOnly = pos.matches("[A-La-l]");
            boolean isLegacyPosition  = pos.matches("[A-La-l][1-4]");
            if (!isGroupLetterOnly && !isLegacyPosition) {
                errors.add("Row " + i + ": invalid group/position \"" + pos + "\" (expected A-L or A-L followed by 1-4)");
            }

            if(team.isEmpty()){
                errors.add("Row " + i + ": team name is empty");
            } else if(!seenTeams.add(team)){
                errors.add("Row " + i + ": duplicate team name \"" + team + "\"");
            }

            if(isLegacyPosition && !seenPositions.add(pos)){
                errors.add("Row " + i + ": duplicate position \"" + pos + "\"");
            }

            if(!gw.isBlank() && !validValues.contains(gw))
                errors.add("Row " + i + " (" + pos + "): group_winner must be yes/maybe/no or blank, got \"" + c[gwIdx].trim() + "\"");
            if(!ru.isBlank() && !validValues.contains(ru))
                errors.add("Row " + i + " (" + pos + "): runner_up must be yes/maybe/no or blank, got \"" + c[ruIdx].trim() + "\"");
            if(!tp.isBlank() && !validValues.contains(tp))
                errors.add("Row " + i + " (" + pos + "): 3rd_place must be yes/maybe/no or blank, got \"" + c[tpIdx].trim() + "\"");

            if (baseEloIdx >= 0) validateIntegerColumn(errors, i, pos, c, baseEloIdx, "base_elo");
            if (qualBonusIdx >= 0) validateIntegerColumn(errors, i, pos, c, qualBonusIdx, "qual_bonus");
            if (eloRankingIdx >= 0) validateIntegerColumn(errors, i, pos, c, eloRankingIdx, "elo_ranking");
            if (h2hIdx >= 0) validateH2hPercentColumn(errors, i, pos, c, h2hIdx, "h2h");
            if (hasH2hColumns && h2hCompIdx >= 0 && h2hFriendlyIdx >= 0) {
                validateH2hPercentColumn(errors, i, pos, c, h2hCompIdx, "history_competitions");
                validateH2hPercentColumn(errors, i, pos, c, h2hFriendlyIdx, "history_friendlies");
            }
            if (qualFormIdx >= 0) validateH2hPercentColumn(errors, i, pos, c, qualFormIdx, "qualification_form");
            if (friendliesFormIdx >= 0) validateH2hPercentColumn(errors, i, pos, c, friendliesFormIdx, "friendlies_form");
            if (predictedPosIdx >= 0) validatePredictedPositionColumn(errors, i, pos, c, predictedPosIdx);

            String grp = pos.isEmpty() ? "" : pos.substring(0, 1).toUpperCase();
            if (!grp.isEmpty()) {
                groupTeamCount.merge(grp, 1, Integer::sum);
                if(!gw.isBlank()) gwSet.merge(grp, 1, Integer::sum);
                if(!ru.isBlank()) ruSet.merge(grp, 1, Integer::sum);
                if("yes".equals(gw)) gwYes.merge(grp, 1, Integer::sum);
                if("yes".equals(ru)) ruYes.merge(grp, 1, Integer::sum);
            }
        }

        for(char g = 'A'; g <= 'L'; g++){
            String grp = String.valueOf(g);
            int count = groupTeamCount.getOrDefault(grp, 0);
            if (count != 4) errors.add("Group " + grp + ": expected 4 teams, found " + count);
            int gw = gwYes.getOrDefault(grp, 0);
            if(gwSet.getOrDefault(grp, 0) > 0 && gw != 1) errors.add("Group " + grp + ": must have exactly 1 group_winner=yes, found " + gw);
            int ru = ruYes.getOrDefault(grp, 0);
            if(ruSet.getOrDefault(grp, 0) > 0 && ru != 1) errors.add("Group " + grp + ": must have exactly 1 runner_up=yes, found " + ru);
        }

        return errors;
    }

    private static void validateIntegerColumn(List<String> errors, int rowNum, String pos,
                                              String[] c, int colIdx, String colName) {
        String val = c[colIdx].trim();
        try {
            Integer.parseInt(val);
        } catch (NumberFormatException e) {
            errors.add("Row " + rowNum + " (" + pos + "): " + colName + " must be an integer, got \"" + val + "\"");
        }
    }

    private static void validateH2hPercentColumn(List<String> errors, int rowNum, String pos,
                                                  String[] c, int colIdx, String colName) {
        String val = c[colIdx].trim();
        if ("N/A".equalsIgnoreCase(val)) return;
        String numeric = val.replace("%", "");
        try {
            int pct = Integer.parseInt(numeric);
            if (pct < 0 || pct > 100) {
                errors.add("Row " + rowNum + " (" + pos + "): " + colName + " must be between 0 and 100, got " + pct);
            }
        } catch (NumberFormatException e) {
            errors.add("Row " + rowNum + " (" + pos + "): " + colName + " must be an integer 0-100 or N/A, got \"" + val + "\"");
        }
    }

    private static void validatePredictedPositionColumn(List<String> errors, int rowNum, String pos,
                                                         String[] c, int colIdx) {
        String val = c[colIdx].trim();
        String rankStr = val.replaceAll("\\s*\\(.*", "").trim();
        try {
            int rank = Integer.parseInt(rankStr);
            if (rank < 1 || rank > 4) {
                errors.add("Row " + rowNum + " (" + pos + "): predicted_position must be 1-4, got " + rank);
            }
        } catch (NumberFormatException e) {
            errors.add("Row " + rowNum + " (" + pos + "): predicted_position must be an integer 1-4, got \"" + val + "\"");
        }
    }

    public List<BracketEntry> loadBrackets(String tournament) throws IOException {
        Path p = projectRoot.resolve("data").resolve("bracket").resolve(tournament + ".csv");
        if (!Files.exists(p))
            throw new IOException("Bracket file not found: " + p.toAbsolutePath() + ". Expected data/bracket/" + tournament + ".csv");
        List<BracketEntry> entries = new ArrayList<>();
        try (var reader = Files.newBufferedReader(p); var parser = CSV.parse(reader)) {
            for (CSVRecord r : parser) {
                String stage = r.size() > 1 ? r.get(1) : "";
                String raw   = r.size() > 2 ? r.get(2) : "";
                String matchId = "", t1 = "", t2 = "";

                if (raw.matches("^M\\d+$")) {
                    matchId = raw;
                    t1 = r.size() > 3 ? r.get(3) : "";
                    t2 = r.size() > 4 ? r.get(4) : "";
                } else {
                    t1 = raw;
                }
                entries.add(new BracketEntry(matchId, t1, t2, stage));
            }
        }
        return entries;
    }

    public static class BracketEntry {
        public final String matchId;
        public final String token1;
        public final String token2;
        public final String stage;
        public BracketEntry(String matchId, String token1, String token2, String stage){
            this.matchId = matchId;
            this.token1 = token1;
            this.token2 = token2;
            this.stage = stage;
        }
    }
}

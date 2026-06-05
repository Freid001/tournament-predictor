package com.tournamentpredictor.service.handler;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.loader.CsvLoader;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

        Path snapshotDir = projectRoot.resolve("data").resolve("elo").resolve("snapshots").resolve(tournament);
        Path historyDir = snapshotDir.resolve("history");
        Files.createDirectories(historyDir);

        int copiedTeams = writeTeamsSnapshot(snapshotDir.resolve("teams.csv"), teams);
        int copiedHistory = writeHistorySnapshot(historyDir, teams);
        writeMetadata(snapshotDir.resolve("metadata.properties"), teams.size(), copiedTeams, copiedHistory);

        System.out.println("Snapshot refreshed for " + tournament + ": " + copiedTeams
                + " teams, " + copiedHistory + " history files.");
    }

    private int writeTeamsSnapshot(Path output, Set<String> teams) throws IOException {
        Path source = projectRoot.resolve("data").resolve("elo").resolve("current").resolve("world.csv");
        if (!Files.exists(source)) {
            throw new IOException("Global ELO file not found: " + source.toAbsolutePath()
                    + ". Run --mode=elo-refresh first or provide data/elo/current/world.csv.");
        }

        int count = 0;
        Set<String> found = new LinkedHashSet<>();
        try (Reader reader = Files.newBufferedReader(source);
             Writer writer = Files.newBufferedWriter(output);
             var parser = CSV.parse(reader);
             CSVPrinter printer = CSVFormat.DEFAULT.print(writer)) {
            printer.printRecord("rank", "team_code", "team_name", "rating");
            for (CSVRecord record : parser) {
                String team = value(record, "team_name", 2);
                if (!teams.contains(team)) {
                    continue;
                }
                printer.printRecord(
                        value(record, "rank", 0),
                        value(record, "team_code", 1),
                        team,
                        value(record, "rating", 3));
                found.add(team);
                count++;
            }
        }

        Set<String> missing = new LinkedHashSet<>(teams);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new IOException("Snapshot missing ELO rows for: " + String.join(", ", missing));
        }
        return count;
    }

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
                if (cols.length == 0) {
                    continue;
                }
                try {
                    int year = Integer.parseInt(cols[0].trim());
                    if (year >= since && year <= until) {
                        out.add(line);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            Files.write(outputDir.resolve(team + ".tsv"), out);
            count++;
        }
        return count;
    }

    private void writeMetadata(Path output, int requestedTeams, int copiedTeams, int copiedHistory) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("created_at", Instant.now().toString());
        values.put("elo_source", "data/elo/current/world.csv");
        values.put("history_source", "data/elo/current/history");
        values.put("requested_team_count", String.valueOf(requestedTeams));
        values.put("team_count", String.valueOf(copiedTeams));
        values.put("history_file_count", String.valueOf(copiedHistory));
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

    private static String value(CSVRecord record, String header, int fallbackIndex) {
        if (record.isMapped(header)) {
            return record.get(header).trim();
        }
        return record.size() > fallbackIndex ? record.get(fallbackIndex).trim() : "";
    }
}

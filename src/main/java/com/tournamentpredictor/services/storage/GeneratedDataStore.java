package com.tournamentpredictor.services.storage;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GeneratedDataStore {
    private final Path projectRoot;
    private final SqliteCacheRepository sqlite;

    public static boolean defaultExportCsv() {
        return true;
    }

    public GeneratedDataStore(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.sqlite = new SqliteCacheRepository(projectRoot);
    }

    public boolean exists(Path path) throws IOException {
        GeneratedDataset dataset = generatedDataset(path);
        if (dataset != null && "prediction".equals(dataset.datasetType()) && "start".equals(dataset.round())) {
            return sqlite.teamsAvailable(dataset.tournament());
        }
        if (dataset != null && "prediction".equals(dataset.datasetType()) && "groups".equals(dataset.round())) {
            return sqlite.groupsAvailable(dataset.tournament());
        }
        return Files.exists(path);
    }

    public List<String> readLines(Path path) throws IOException {
        GeneratedDataset dataset = generatedDataset(path);
        if (dataset != null && "prediction".equals(dataset.datasetType()) && "start".equals(dataset.round())) {
            return sqlite.readTeamsAsCsvLines(dataset.tournament());
        }
        if (dataset != null && "prediction".equals(dataset.datasetType()) && "groups".equals(dataset.round())) {
            return sqlite.readGroupsAsCsvLines(dataset.tournament());
        }
        return Files.exists(path) ? Files.readAllLines(path) : List.of();
    }

    public boolean writeLines(Path path, List<String> lines) throws IOException {
        GeneratedDataset dataset = generatedDataset(path);
        if (dataset != null && "prediction".equals(dataset.datasetType()) && "groups".equals(dataset.round())) {
            writeCsvLinesToGroups(dataset.tournament(), lines);
            return true;
        }
        if (dataset != null && "simulation".equals(dataset.datasetType()) && "groups".equals(dataset.round())) {
            sqlite.updateGroupSimulationSummary(dataset.tournament(), parseCsvLines(lines));
        }
        if (dataset != null && "simulation_scoreline".equals(dataset.datasetType())) {
            sqlite.replaceMatchupPredictionSummaries(dataset.tournament(), dataset.round(), parseCsvLines(lines));
        }
        if (dataset != null && "simulation_path".equals(dataset.datasetType())) {
            sqlite.replaceTournamentPaths(dataset.tournament(), dataset.round(), parseCsvLines(lines));
        }
        if (dataset != null && "matchup".equals(dataset.datasetType())) {
            lines = enrichMatchupLikelihoodLines(dataset, lines);
        }
        Files.createDirectories(path.getParent());
        Files.write(path, lines);
        if (dataset != null && "matchup".equals(dataset.datasetType())) {
            sqlite.importDataset(dataset.tournament(), dataset.datasetType(), dataset.round(), path);
            sqlite.updatePathMatchupLikelihoodsFromGroupRoutes(dataset.tournament(), dataset.round(),
                    projectRoot.resolve("data").resolve("simulations").resolve(dataset.tournament()).resolve("simulation_group_routes.csv"));
        }
        return true;
    }

    private List<String> enrichMatchupLikelihoodLines(GeneratedDataset dataset, List<String> lines) throws IOException {
        if (!"last_32".equals(dataset.round()) || lines == null || lines.isEmpty()) return lines;
        Path routes = projectRoot.resolve("data").resolve("simulations").resolve(dataset.tournament()).resolve("simulation_group_routes.csv");
        if (!Files.exists(routes)) return lines;
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        java.util.Set<String> runs = new java.util.LinkedHashSet<>();
        org.apache.commons.csv.CSVFormat readFormat = org.apache.commons.csv.CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true).build();
        try (java.io.Reader reader = Files.newBufferedReader(routes);
             org.apache.commons.csv.CSVParser parser = readFormat.parse(reader)) {
            for (org.apache.commons.csv.CSVRecord record : parser) {
                String run = csvValue(record, "run");
                String matchId = csvValue(record, "match_id");
                String team1 = csvValue(record, "team1");
                String team2 = csvValue(record, "team2");
                if (run.isBlank() || matchId.isBlank() || team1.isBlank() || team2.isBlank()) continue;
                runs.add(run);
                counts.merge(matchupKey(matchId, team1, team2), 1, Integer::sum);
            }
        }
        int totalRuns = runs.size();
        if (totalRuns == 0) return lines;

        List<Map<String, String>> rows = parseCsvLines(lines);
        if (rows.isEmpty()) return lines;
        List<String> headers = new java.util.ArrayList<>(rows.get(0).keySet());
        if (!headers.contains("matchup_pct")) headers.add("matchup_pct");
        if (!headers.contains("matchup_runs")) headers.add("matchup_runs");
        for (Map<String, String> row : rows) {
            int count = counts.getOrDefault(matchupKey(row.getOrDefault("match_id", ""), row.getOrDefault("team1", ""), row.getOrDefault("team2", "")), 0);
            row.put("matchup_pct", formatPathLikelihood(count, totalRuns));
            row.put("matchup_runs", String.valueOf(totalRuns));
        }
        return toCsvLines(headers, rows);
    }

    private String csvValue(org.apache.commons.csv.CSVRecord record, String column) {
        return record.isMapped(column) ? record.get(column).trim() : "";
    }

    private String matchupKey(String matchId, String team1, String team2) {
        return (matchId == null ? "" : matchId.trim()) + "|" + (team1 == null ? "" : team1.trim()) + "|" + (team2 == null ? "" : team2.trim());
    }

    private String formatPathLikelihood(int count, int totalRuns) {
        if (count <= 0) return "0.0";
        double value = count * 100.0 / Math.max(1, totalRuns);
        if (value < 0.1) return String.format(java.util.Locale.ROOT, "%.3f", value);
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private List<String> toCsvLines(List<String> headers, List<Map<String, String>> rows) throws IOException {
        java.io.StringWriter writer = new java.io.StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (Map<String, String> row : rows) {
                printer.printRecord(headers.stream().map(header -> row.getOrDefault(header, "")).collect(Collectors.toList()));
            }
        }
        return writer.toString().lines().collect(Collectors.toList());
    }

    private void writeCsvLinesToGroups(String tournament, List<String> lines) throws IOException {
        if (lines.isEmpty()) {
            sqlite.replaceGroups(tournament, List.of(), List.of());
            return;
        }
        org.apache.commons.csv.CSVFormat format = org.apache.commons.csv.CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build();
        try (java.io.Reader reader = new java.io.StringReader(String.join("\n", lines));
             org.apache.commons.csv.CSVParser parser = format.parse(reader)) {
            List<String> headers = new java.util.ArrayList<>(parser.getHeaderNames());
            sqlite.replaceGroups(tournament, headers, parseCsvRecords(headers, parser));
        }
    }

    private List<Map<String, String>> parseCsvLines(List<String> lines) throws IOException {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        org.apache.commons.csv.CSVFormat format = org.apache.commons.csv.CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build();
        try (java.io.Reader reader = new java.io.StringReader(String.join("\n", lines));
             org.apache.commons.csv.CSVParser parser = format.parse(reader)) {
            return parseCsvRecords(new java.util.ArrayList<>(parser.getHeaderNames()), parser);
        }
    }

    private List<Map<String, String>> parseCsvRecords(List<String> headers, org.apache.commons.csv.CSVParser parser) {
        List<Map<String, String>> rows = new java.util.ArrayList<>();
        for (org.apache.commons.csv.CSVRecord record : parser) {
            Map<String, String> row = new java.util.LinkedHashMap<>();
            for (String header : headers) {
                row.put(header, record.isMapped(header) ? record.get(header) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    public boolean writeRows(Path path, List<String> headers, List<Map<String, String>> rows) throws IOException {
        GeneratedDataset dataset = generatedDataset(path);
        if (dataset != null && "prediction".equals(dataset.datasetType()) && "start".equals(dataset.round())) {
            sqlite.replaceTeams(dataset.tournament(), headers, rows);
            return true;
        }
        if (dataset != null && "prediction".equals(dataset.datasetType()) && "groups".equals(dataset.round())) {
            sqlite.replaceGroups(dataset.tournament(), headers, rows);
            return true;
        }
        Files.createDirectories(path.getParent());
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build();
        try (Writer writer = Files.newBufferedWriter(path); CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (Map<String, String> row : rows) {
                printer.printRecord(headers.stream().map(header -> row.getOrDefault(header, "")).collect(Collectors.toList()));
            }
        }
        return true;
    }

    public List<Map<String, String>> readMatchupPredictionRows(String tournament, String round) throws IOException {
        return sqlite.readMatchupPredictionRows(tournament, round);
    }

    public GeneratedDataset generatedDataset(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path dataRoot = projectRoot.resolve("data").toAbsolutePath().normalize();
        if (!normalized.startsWith(dataRoot)) return null;
        Path relative = dataRoot.relativize(normalized);
        if (relative.getNameCount() < 3) return null;
        String area = relative.getName(0).toString();
        String fileName = relative.getFileName().toString();
        if (!fileName.endsWith(".csv")) return null;
        String baseName = fileName.substring(0, fileName.length() - 4);
        if ("predictions".equals(area) && relative.getNameCount() >= 3) {
            return new GeneratedDataset(relative.getName(1).toString(), "prediction", baseName);
        }
        if ("results".equals(area) && relative.getNameCount() >= 3) {
            return new GeneratedDataset(relative.getName(1).toString(), "result", baseName);
        }
        if ("simulations".equals(area) && relative.getNameCount() >= 3) {
            return simulationDataset(relative.getName(1).toString(), baseName);
        }
        if ("live".equals(area) && relative.getNameCount() >= 4 && "simulations".equals(relative.getName(2).toString())) {
            GeneratedDataset simulation = simulationDataset(relative.getName(1).toString(), baseName);
            return simulation == null ? null : new GeneratedDataset(simulation.tournament(), "live_" + simulation.datasetType(), simulation.round());
        }
        return null;
    }

    private GeneratedDataset simulationDataset(String tournament, String baseName) {
        if (baseName.startsWith("matchup_paths_")) {
            String round = baseName.substring("matchup_paths_".length());
            if (round.endsWith("_view")) round = round.substring(0, round.length() - "_view".length());
            return new GeneratedDataset(tournament, "matchup", round);
        }
        if (baseName.startsWith("simulation_paths_")) {
            return new GeneratedDataset(tournament, "simulation_path", baseName.substring("simulation_paths_".length()));
        }
        if (baseName.startsWith("simulation_scorelines_")) {
            return new GeneratedDataset(tournament, "simulation_scoreline", baseName.substring("simulation_scorelines_".length()));
        }
        if (baseName.startsWith("simulation_")) {
            return new GeneratedDataset(tournament, "simulation", baseName.substring("simulation_".length()));
        }
        return null;
    }

    public record GeneratedDataset(String tournament, String datasetType, String round) {}
}

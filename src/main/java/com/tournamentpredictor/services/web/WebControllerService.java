package com.tournamentpredictor.services.web;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.services.orchestration.MatchResolver;
import com.tournamentpredictor.services.simulation.SimulationHandler;
import com.tournamentpredictor.services.storage.GeneratedDataStore;
import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.report.HtmlReporter;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.calculation.ExpectedGoalsCalculator;
import com.tournamentpredictor.view.SimulationResultsRenderer;
import com.tournamentpredictor.view.KnockoutViewRows;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.model.common.*;
import com.tournamentpredictor.model.tournament.*;
import com.tournamentpredictor.model.history.*;
import com.tournamentpredictor.model.group.*;
import com.tournamentpredictor.model.round.*;
import com.tournamentpredictor.model.results.*;
import com.tournamentpredictor.model.start.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class WebControllerService {
    public static final Set<String> RUN_MODES = Set.of("snapshot-refresh", "tournament-snapshot-refresh", "elo-refresh", "start", "groups", "group-simulation", "tournament", "last_32", "last_16", "last_8", "last_4", "final", "simulate");
    public static final Set<String> ROUND_NAMES = Set.of("groups", "groups_match", "last_32", "last_32_match", "last_16", "last_16_match", "last_8", "last_8_match", "last_4", "last_4_match", "final", "final_match");
    public static final Set<String> RESET_STEPS = Set.of("groups", "group-simulation", "last_32", "last_16", "last_8", "last_4", "final", "simulation");
    public static final Set<String> HISTORICAL_COMPARISONS = Set.of("world_cup_2014", "world_cup_2018", "world_cup_2022");
    public static final List<String> RESULTS_ROUNDS = List.of("last_32", "last_16", "last_8", "last_4", "final");
    public static final int VIEW_PAGE_SIZE = 50;
    public static final CSVFormat CSV = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    public final Path projectRoot;
    public final PredictionConfig predictionConfig;
    public final WebCsvRepository csv;
    private final GeneratedDataStore generatedDataStore;

    @Autowired
    public WebControllerService(PredictionConfig predictionConfig) {
        this(predictionConfig, Path.of(System.getProperty("user.dir")));
    }

    public WebControllerService(PredictionConfig predictionConfig, Path projectRoot) {
        this(predictionConfig, new WebCsvRepository(), projectRoot);
    }

    public WebControllerService(PredictionConfig predictionConfig, WebCsvRepository csv) {
        this(predictionConfig, csv, Path.of(System.getProperty("user.dir")));
    }

    public WebControllerService(PredictionConfig predictionConfig, WebCsvRepository csv, Path projectRoot) {
        this.predictionConfig = predictionConfig;
        this.csv = csv;
        this.projectRoot = projectRoot;
        this.generatedDataStore = new GeneratedDataStore(projectRoot);
    }

    public boolean refreshLiveSimulationAfterResultsSave(String tournament, String completedRound) throws IOException {
        String nextRound = nextResultsRound(tournament, completedRound);
        if (nextRound == null || nextRound.isBlank()) {
            return false;
        }
        deletePaths(liveSimulationFilesFrom(tournament, nextRound));
        if (!isResultsRoundComplete(tournament, completedRound)) {
            return false;
        }
        List<String> liveStartRows = buildLiveSimulationStartRows(tournament, nextRound);
        if (liveStartRows.size() <= 1) {
            return false;
        }
        MatchResolver.forWeb(new HtmlReporter().withConfig(predictionConfig), predictionConfig)
                .resolveAndWriteLiveSimulation(tournament, nextRound, liveStartRows);
        return true;
    }

    public String nextResultsRound(String tournament, String completedRound) {
        String safeRound = WebText.trim(completedRound).toLowerCase(java.util.Locale.ROOT);
        if (safeRound.isBlank() || "final".equals(safeRound)) {
            return null;
        }
        if ("groups".equals(safeRound)) {
            return openingKnockoutRound(tournament);
        }
        return switch (safeRound) {
            case "last_32" -> "last_16";
            case "last_16" -> "last_8";
            case "last_8" -> "last_4";
            case "last_4" -> "final";
            default -> null;
        };
    }

    public boolean isResultsRoundComplete(String tournament, String round) throws IOException {
        String safeRound = WebText.trim(round).toLowerCase(java.util.Locale.ROOT);
        if (safeRound.isBlank()) {
            return false;
        }
        if ("groups".equals(safeRound)) {
            List<GroupMatchResultRow> expectedRows = buildGroupResultsEditorRows(tournament);
            if (expectedRows.isEmpty()) {
                return false;
            }
            Map<String, Map<String, String>> savedRows = loadGroupMatchResultsByMatchId(tournament);
            if (savedRows.size() < expectedRows.size()) {
                return false;
            }
            for (GroupMatchResultRow row : expectedRows) {
                Map<String, String> saved = savedRows.get(row.getMatchId());
                if (saved == null) {
                    return false;
                }
                if (WebText.trim(saved.getOrDefault("winner", "")).isBlank()
                        || WebText.trim(saved.getOrDefault("home_score", "")).isBlank()
                        || WebText.trim(saved.getOrDefault("away_score", "")).isBlank()) {
                    return false;
                }
            }
            return true;
        }
        List<ResultEntryRow> expectedRows = buildActualResultsEditorRows(tournament, safeRound);
        if (expectedRows.isEmpty()) {
            return false;
        }
        Map<String, Map<String, String>> savedRows = loadRoundResultsByKey(tournament, safeRound);
        if (savedRows.size() < expectedRows.size()) {
            return false;
        }
        for (ResultEntryRow row : expectedRows) {
            Map<String, String> saved = savedRows.get(KnockoutViewRows.matchKey(row.getTeam1(), row.getTeam2()));
            if (saved == null) {
                return false;
            }
            String winner = WebText.trim(saved.getOrDefault("winner", ""));
            if (winner.isBlank() || "Draw".equalsIgnoreCase(winner)
                    || WebText.trim(saved.getOrDefault("home_score", "")).isBlank()
                    || WebText.trim(saved.getOrDefault("away_score", "")).isBlank()) {
                return false;
            }
        }
        return true;
    }

    public List<String> buildLiveSimulationStartRows(String tournament, String startRound) throws IOException {
        List<ResultEntryRow> rows = buildActualResultsEditorRows(tournament, startRound);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> fatigueByTeam = actualPathFatigueByTeam(tournament, startRound);
        List<String> lines = new ArrayList<>();
        lines.add("match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent");
        for (ResultEntryRow row : rows) {
            lines.add(String.join(",",
                    WebText.csvValue(row.getMatchId()),
                    WebText.csvValue(row.getTeam1()),
                    WebText.csvValue(row.getTeam2()),
                    "predicted",
                    WebText.csvValue(row.getTeam1()),
                    String.valueOf(fatigueByTeam.getOrDefault(row.getTeam1(), 0)),
                    String.valueOf(fatigueByTeam.getOrDefault(row.getTeam2(), 0)),
                    "",
                    ""));
        }
        return lines;
    }

    public Map<String, Integer> actualPathFatigueByTeam(String tournament, String startRound) throws IOException {
        List<String> priorRounds = knockoutRoundsBefore(tournament, startRound);
        if (priorRounds.isEmpty()) {
            return Map.of();
        }
        CsvLoader csvLoader = new CsvLoader(projectRoot).withConfig(predictionConfig);
        Map<String, Integer> eloRatings = csvLoader.loadTournamentElo(tournament);
        PathFatigueCalculator fatigueCalculator = new PathFatigueCalculator().withConfig(predictionConfig);
        Map<String, Integer> fatigue = new LinkedHashMap<>();
        for (String round : priorRounds) {
            Path file = resultsFile(tournament, round);
            if (!generatedDataStore.exists(file)) {
                continue;
            }
            for (Map<String, String> row : readCsv(file).rows()) {
                String team1 = WebText.trim(row.getOrDefault("team1", ""));
                String team2 = WebText.trim(row.getOrDefault("team2", ""));
                String winner = WebText.trim(row.getOrDefault("winner", ""));
                if (team1.isBlank() || team2.isBlank() || winner.isBlank() || "Draw".equalsIgnoreCase(winner)) {
                    continue;
                }
                String loser = winner.equals(team1) ? team2 : winner.equals(team2) ? team1 : "";
                if (loser.isBlank()) {
                    continue;
                }
                int contribution = fatigueCalculator.knockoutWeightedContribution(
                        eloRatings.getOrDefault(loser, 0), round, false);
                fatigue.merge(winner, contribution, Integer::sum);
            }
        }
        return fatigue;
    }

    public List<String> knockoutRoundsBefore(String tournament, String round) {
        List<String> rounds = new ArrayList<>();
        if (bracketHasStage(tournament, "LAST_32")) {
            rounds.add("last_32");
        }
        rounds.add("last_16");
        rounds.add("last_8");
        rounds.add("last_4");
        rounds.add("final");
        int index = rounds.indexOf(WebText.trim(round).toLowerCase(java.util.Locale.ROOT));
        if (index <= 0) {
            return List.of();
        }
        return new ArrayList<>(rounds.subList(0, index));
    }

    public boolean bracketHasStage(String tournament, String stage) {
        try {
            return new CsvLoader(projectRoot).loadBrackets(tournament).stream()
                    .anyMatch(entry -> stage.equalsIgnoreCase(entry.stage));
        } catch (IOException e) {
            return false;
        }
    }

    public void writeDirectOpeningMatchups(String tournament, String round) throws IOException {
        List<Map<String, String>> scorelines = readCsv(simulationFile(tournament,
                "simulation_scorelines_last_16.csv")).rows();
        Map<String, DirectMatchupSummary> summaries = new LinkedHashMap<>();
        for (Map<String, String> row : scorelines) {
            if (!round.equalsIgnoreCase(row.getOrDefault("stage", ""))) continue;
            String matchId = row.getOrDefault("match_id", "");
            String team1 = row.getOrDefault("team1", "");
            String team2 = row.getOrDefault("team2", "");
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank()) continue;
            String key = matchId + "|" + team1 + "|" + team2;
            DirectMatchupSummary summary = summaries.computeIfAbsent(key,
                    ignored -> new DirectMatchupSummary(matchId, team1, team2));
            summary.matchupRuns = WebText.parseInt(row.getOrDefault("matchup_runs", "0"), 0);
            summary.matchupPct = row.getOrDefault("matchup_pct", "");
            summary.winnerCounts.merge(row.getOrDefault("winner", ""),
                    WebText.parseInt(row.getOrDefault("count", "0"), 0), Integer::sum);
        }
        Map<String, Integer> primaryRuns = new LinkedHashMap<>();
        summaries.values().forEach(summary -> primaryRuns.merge(
                summary.matchId, summary.matchupRuns, Math::max));
        CsvLoader csvLoader = new CsvLoader(projectRoot).withConfig(predictionConfig);
        Map<String, Integer> eloRatings = csvLoader.loadTournamentElo(tournament);
        List<Map<String, String>> groupRows = readCsv(predictionFile(tournament, "groups.csv")).rows();
        Map<String, String> teamGW = teamStatusMap(groupRows, "group_winner");
        Map<String, String> teamRU = teamStatusMap(groupRows, "runner_up");
        Map<String, String> teamTP = teamStatusMap(groupRows, "3rd_place");
        Map<String, CsvLoader.BracketEntry> bracketByMatch = csvLoader.loadBrackets(tournament).stream()
                .filter(entry -> round.equalsIgnoreCase(knockoutRoundForStage(entry.stage)))
                .collect(Collectors.toMap(entry -> entry.matchId, entry -> entry, (first, ignored) -> first, LinkedHashMap::new));
        List<DirectMatchupSummary> ordered = summaries.values().stream()
                .sorted(java.util.Comparator.comparing((DirectMatchupSummary summary) -> summary.matchId)
                        .thenComparing(java.util.Comparator.comparingInt(
                                (DirectMatchupSummary summary) -> summary.matchupRuns).reversed())
                        .thenComparing(summary -> summary.team1)
                        .thenComparing(summary -> summary.team2))
                .toList();
        List<String> headers = List.of("match_id", "team1", "team2", "path", "prediction",
                "team1_path_fatigue", "team2_path_fatigue", "team1_path_opponent",
                "team2_path_opponent", "model_prediction", "selection_source", "matchup_pct", "matchup_runs",
                "team1_slot", "team1_team", "team1_source_match", "team1_group_finish", "team1_bracket_slot",
                "team2_slot", "team2_team", "team2_source_match", "team2_group_finish", "team2_bracket_slot");
        Map<String, Map<String, String>> matchupMetadataByKey = new LinkedHashMap<>();
        Path matchupPathFile = simulationFile(tournament, "matchup_paths_" + round + ".csv");
        if (generatedDataStore.exists(matchupPathFile)) {
            for (Map<String, String> row : readCsv(matchupPathFile).rows()) {
                String key = row.getOrDefault("match_id", "") + "|"
                        + row.getOrDefault("team1", "") + "|" + row.getOrDefault("team2", "");
                matchupMetadataByKey.putIfAbsent(key, row);
            }
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (DirectMatchupSummary summary : ordered) {
            Map.Entry<String, Integer> winner = summary.winnerCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).orElse(Map.entry(summary.team1, 0));
            int pct = summary.matchupRuns == 0 ? 0
                    : (int) Math.round(winner.getValue() * 100.0 / summary.matchupRuns);
            String prediction = winner.getKey() + " (" + pct + "%)";
            Map<String, String> row = new LinkedHashMap<>();
            row.put("match_id", summary.matchId);
            row.put("team1", summary.team1);
            row.put("team2", summary.team2);
            CsvLoader.BracketEntry bracket = bracketByMatch.get(summary.matchId);
            row.put("path", bracket == null
                    ? DirectMatchupPathService.classify(summary, winner.getKey(), primaryRuns, eloRatings)
                    : DirectMatchupPathService.classifyOpeningRoute(summary, winner.getKey(), bracket.token1, bracket.token2,
                            teamGW, teamRU, teamTP, primaryRuns, eloRatings));
            row.put("prediction", prediction);
            row.put("team1_path_fatigue", "0");
            row.put("team2_path_fatigue", "0");
            row.put("team1_path_opponent", "Group stage");
            row.put("team2_path_opponent", "Group stage");
            row.put("model_prediction", prediction);
            row.put("selection_source", "simulation");
            row.put("matchup_pct", summary.matchupPct);
            row.put("matchup_runs", String.valueOf(summary.matchupRuns));
            Map<String, String> metadata = matchupMetadataByKey.getOrDefault(
                    summary.matchId + "|" + summary.team1 + "|" + summary.team2, Map.of());
            copyRouteMetadata(row, metadata, "team1", summary.team1, bracket == null ? "" : bracket.token1);
            copyRouteMetadata(row, metadata, "team2", summary.team2, bracket == null ? "" : bracket.token2);
            rows.add(row);
        }
        Path output = matchupViewFile(tournament, round + ".csv");
        Files.createDirectories(output.getParent());
        writeCsv(output, headers, rows);
    }


    private void copyRouteMetadata(Map<String, String> row, Map<String, String> metadata, String prefix,
                                   String team, String fallbackBracketSlot) {
        row.put(prefix + "_slot", WebText.trim(metadata.getOrDefault(prefix + "_slot", fallbackBracketSlot)));
        row.put(prefix + "_team", WebText.trim(metadata.getOrDefault(prefix + "_team", team)));
        row.put(prefix + "_source_match", WebText.trim(metadata.getOrDefault(prefix + "_source_match", "")));
        row.put(prefix + "_group_finish", WebText.trim(metadata.getOrDefault(prefix + "_group_finish", "")));
        row.put(prefix + "_bracket_slot", WebText.trim(metadata.getOrDefault(prefix + "_bracket_slot", fallbackBracketSlot)));
    }

    private Map<String, String> teamStatusMap(List<Map<String, String>> rows, String column) {
        Map<String, String> status = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String team = WebText.trim(row.getOrDefault("team", ""));
            if (!team.isBlank()) {
                status.put(team, WebText.trim(row.getOrDefault(column, "")).toLowerCase(java.util.Locale.ROOT));
            }
        }
        return status;
    }

    private String knockoutRoundForStage(String stage) {
        if (stage == null) return "";
        return switch (stage.toUpperCase(java.util.Locale.ROOT)) {
            case "LAST_32" -> "last_32";
            case "LAST_16" -> "last_16";
            case "QUARTER" -> "last_8";
            case "SEMI" -> "last_4";
            case "FINAL" -> "final";
            default -> "";
        };
    }

    public StageStatus status(boolean complete, boolean ready) {
        if (complete) return new StageStatus("✅", "Complete", "success");
        if (ready) return new StageStatus("▶", "Ready", "primary");
        return new StageStatus("⬜", "Locked", "secondary");
    }

    public void runGroupStagePipeline(String tournament, HtmlReporter reporter) throws IOException {
        if (!generatedDataStore.exists(predictionFile(tournament, "groups.csv"))) {
            throw new IOException("Run Group Rankings before simulating the group stage.");
        }
        cascadeDeleteAfterGroupsEdit(tournament);
        MatchResolver resolver = MatchResolver.forWeb(reporter, predictionConfig);
        resolver.resolveAndWriteSimulation(tournament, "groups");
        reporter.appendInfo("Group stage complete. Saved " + SimulationHandler.DEFAULT_RUNS
                + " simulated group tables and their opening knockout routes.");
    }

    public void runDirectKnockoutRoundFromGroups(String tournament, String round, HtmlReporter reporter) throws IOException {
        if (!generatedDataStore.exists(simulationFile(tournament, "simulation_group_routes.csv"))) {
            throw new IOException("Run Group Stage before running the knockout rounds.");
        }
        List<String> directRounds = List.of("last_16", "last_8", "last_4", "final");
        if (!directRounds.contains(round)) {
            throw new IOException("Unsupported direct knockout round: " + round);
        }
        if (!"last_16".equals(round)) {
            cascadeDeleteAfterRoundEdit(tournament, "last_16");
        }
        MatchResolver resolver = MatchResolver.forWeb(reporter, predictionConfig);
        resolver.resolveAndWriteKnockoutsFromGroups(tournament);
        for (String directRound : directRounds) {
            resolver.resolveAndWrite(directRound, tournament);
            if (directRound.equals(round)) {
                break;
            }
        }
        if (generatedDataStore.exists(simulationFile(tournament, "simulation_scorelines_last_16.csv"))) {
            writeDirectOpeningMatchups(tournament, "last_16");
        }
        reporter.appendInfo("Updated " + displayMode(round) + " using the saved group-stage routes.");
    }

    public void ensureLast32PredictionSeed(String tournament, HtmlReporter reporter) throws IOException {
        if (generatedDataStore.exists(predictionFile(tournament, "last_32.csv"))) {
            return;
        }
        if (!generatedDataStore.exists(simulationFile(tournament, "simulation_group_routes.csv"))) {
            throw new IOException("Run Group Stage before running Last 32.");
        }
        MatchResolver.forWeb(reporter, predictionConfig).resolveAndWrite("groups", tournament);
        reporter.appendInfo("Generated Last 32 predictions from the completed group stage.");
    }

    public void runTournamentPipeline(String tournament, HtmlReporter reporter) throws IOException {
        boolean startsAtLast16 = !bracketHasStage(tournament, "LAST_32")
                && bracketHasStage(tournament, "LAST_16");
        if (!generatedDataStore.exists(simulationFile(tournament, "simulation_group_routes.csv"))) {
            throw new IOException("Run Group Stage before running the knockout rounds.");
        }
        if (startsAtLast16) {
            MatchResolver resolver = MatchResolver.forWeb(reporter, predictionConfig);
            resolver.resolveAndWriteKnockoutsFromGroups(tournament);
            for (String round : List.of("last_16", "last_8", "last_4", "final")) {
                resolver.resolveAndWrite(round, tournament);
            }
            reporter.appendInfo("Continued the saved group-stage routes through the Last 16, quarter-finals, semi-finals and final.");
            return;
        }
        MatchResolver resolver = MatchResolver.forWeb(reporter, predictionConfig);
        // groups.csv contains an automatically selected display route. Monte Carlo
        // probabilities never depend on that route; they continue every saved group route.
        if (!generatedDataStore.exists(predictionFile(tournament, "last_32.csv"))) {
            resolver.resolveAndWrite("groups", tournament);
        }
        cascadeDeleteAfterRoundEdit(tournament, "last_32");
        resolver.resolveAndWriteKnockoutsFromGroups(tournament);
        reporter.appendInfo("Continued the saved group-stage routes through the knockout rounds.");
        for (String round : List.of("last_32", "last_16", "last_8", "last_4", "final")) {
            resolver.resolveAndWrite(round, tournament);
            autoRunSimulation(round, tournament, reporter);
        }
        reporter.appendInfo("Tournament complete. All knockout rounds and simulations are ready.");
    }

    public void autoRunSimulation(String mode, String tournament, HtmlReporter reporter) throws IOException {
        String startRound = switch (mode) {
            case "groups", "last_32" -> "last_32";
            case "last_16" -> "last_16";
            case "last_8" -> "last_8";
            case "last_4" -> "last_4";
            case "final" -> "final";
            default -> null;
        };
        if (startRound == null || !generatedDataStore.exists(predictionFile(tournament, startRound + ".csv"))) return;
        MatchResolver.forWeb(reporter, predictionConfig).resolveAndWriteSimulation(tournament, startRound);
        reporter.appendInfo("Updated " + displayMode(startRound) + " simulation outputs.");
    }

    public void appendBrowserOnlyMessages(HtmlReporter reporter, String mode, String tournament, boolean lockedBefore) throws IOException {
        if ("start".equals(mode)) {
            if (lockedBefore) {
                reporter.appendWarning("Output already exists: " + predictionFile(tournament, "groups.csv") + " — reset or edit start data to re-run.");
            }
            reporter.appendInfo("Team Setup saved. Run the Group Stage simulation next.");
            return;
        }
        if ("groups".equals(mode)) {
            if (lockedBefore) {
                reporter.appendWarning("Output already exists: " + predictionFile(tournament, "last_32.csv") + " — reset the knockout rounds to re-run.");
            }
            long rowCount = countDataRows(predictionFile(tournament, "last_32.csv"));
            reporter.appendInfo("Generated " + rowCount + " rows in last_32.csv.");
        }
    }

    public void cascadeDeleteAfterStart(String tournament) throws IOException {
        deletePaths(List.of(
                predictionFile(tournament, "groups.csv"),
                predictionFile(tournament, "last_32.csv"),
                matchupFile(tournament, "last_32.csv"),
                predictionFile(tournament, "last_16.csv"),
                matchupFile(tournament, "last_16.csv"),
                matchupViewFile(tournament, "last_16.csv"),
                predictionFile(tournament, "last_8.csv"),
                matchupFile(tournament, "last_8.csv"),
                predictionFile(tournament, "last_4.csv"),
                matchupFile(tournament, "last_4.csv"),
                predictionFile(tournament, "final.csv"),
                matchupFile(tournament, "final.csv")
        ));
        deletePaths(simulationFilesFrom(tournament, "groups"));
    }

    public void cascadeDeleteAfterGroupsEdit(String tournament) throws IOException {
        deletePaths(List.of(
                predictionFile(tournament, "last_32.csv"),
                matchupFile(tournament, "last_32.csv"),
                predictionFile(tournament, "last_16.csv"),
                matchupFile(tournament, "last_16.csv"),
                matchupViewFile(tournament, "last_16.csv"),
                predictionFile(tournament, "last_8.csv"),
                matchupFile(tournament, "last_8.csv"),
                predictionFile(tournament, "last_4.csv"),
                matchupFile(tournament, "last_4.csv"),
                predictionFile(tournament, "final.csv"),
                matchupFile(tournament, "final.csv")
        ));
        deletePaths(simulationFilesFrom(tournament, "last_32"));
        deletePaths(liveSimulationFilesFrom(tournament, openingKnockoutRound(tournament)));
    }

    public void cascadeDeleteAfterRoundEdit(String tournament, String round) throws IOException {
        List<Path> paths = new ArrayList<>();
        switch (round) {
            case "last_32" -> paths.addAll(List.of(
                    matchupFile(tournament, "last_32.csv"),
                    predictionFile(tournament, "last_16.csv"),
                    matchupFile(tournament, "last_16.csv"),
                    matchupViewFile(tournament, "last_16.csv"),
                    predictionFile(tournament, "last_8.csv"),
                    matchupFile(tournament, "last_8.csv"),
                    predictionFile(tournament, "last_4.csv"),
                    matchupFile(tournament, "last_4.csv"),
                    predictionFile(tournament, "final.csv"),
                    matchupFile(tournament, "final.csv")
            ));
            case "last_16" -> paths.addAll(List.of(
                    matchupFile(tournament, "last_16.csv"),
                    matchupViewFile(tournament, "last_16.csv"),
                    predictionFile(tournament, "last_8.csv"),
                    matchupFile(tournament, "last_8.csv"),
                    predictionFile(tournament, "last_4.csv"),
                    matchupFile(tournament, "last_4.csv"),
                    predictionFile(tournament, "final.csv"),
                    matchupFile(tournament, "final.csv")
            ));
            case "last_8" -> paths.addAll(List.of(
                    matchupFile(tournament, "last_8.csv"),
                    predictionFile(tournament, "last_4.csv"),
                    matchupFile(tournament, "last_4.csv"),
                    predictionFile(tournament, "final.csv"),
                    matchupFile(tournament, "final.csv")
            ));
            case "last_4" -> paths.addAll(List.of(
                    matchupFile(tournament, "last_4.csv"),
                    predictionFile(tournament, "final.csv"),
                    matchupFile(tournament, "final.csv")
            ));
            case "final" -> paths.addAll(List.of(
                    matchupFile(tournament, "final.csv")
            ));
            default -> {
                return;
            }
        }
        paths.addAll(simulationFilesFrom(tournament, round));
        String nextRound = nextResultsRound(tournament, round);
        if (nextRound != null) {
            paths.addAll(liveSimulationFilesFrom(tournament, nextRound));
        }
        deletePaths(paths);
    }

    public void cascadeDeleteForReset(String tournament, String step) throws IOException {
        if ("group-simulation".equals(step)) {
            cascadeDeleteAfterGroupsEdit(tournament);
            deletePaths(List.of(
                    simulationFile(tournament, "simulation_groups.csv"),
                    simulationFile(tournament, "simulation_group_routes.csv"),
                    simulationFile(tournament, "simulation_scorelines_groups.csv")
            ));
            return;
        }
        if ("simulation".equals(step)) {
            deletePaths(simulationFilesFrom(tournament, "groups"));
            return;
        }
        if ("groups".equals(step)) {
            deletePaths(List.of(
                    predictionFile(tournament, "groups.csv"),
                    predictionFile(tournament, "last_32.csv"),
                    matchupFile(tournament, "last_32.csv"),
                    predictionFile(tournament, "last_16.csv"),
                    matchupFile(tournament, "last_16.csv"),
                    matchupViewFile(tournament, "last_16.csv"),
                    predictionFile(tournament, "last_8.csv"),
                    matchupFile(tournament, "last_8.csv"),
                    predictionFile(tournament, "last_4.csv"),
                    matchupFile(tournament, "last_4.csv"),
                    predictionFile(tournament, "final.csv"),
                    matchupFile(tournament, "final.csv")
            ));
            deletePaths(simulationFilesFrom(tournament, "groups"));
            return;
        }
        cascadeDeleteAfterRoundEdit(tournament, step);
    }

    public List<Path> simulationFilesFrom(String tournament, String startRound) {
        List<String> rounds = List.of("groups", "last_32", "last_16", "last_8", "last_4", "final");
        int start = rounds.indexOf(startRound);
        if (start < 0) return List.of();
        List<Path> paths = new ArrayList<>();
        for (String round : rounds.subList(start, rounds.size())) {
            paths.add(simulationFile(tournament, "simulation_" + round + ".csv"));
            if ("groups".equals(round)) {
                paths.add(simulationFile(tournament, "simulation_group_routes.csv"));
                paths.add(simulationFile(tournament, "simulation_scorelines_groups.csv"));
            }
            if (!"groups".equals(round)) {
                paths.add(simulationFile(tournament, "simulation_paths_" + round + ".csv"));
                paths.add(simulationFile(tournament, "simulation_scorelines_" + round + ".csv"));
            }
        }
        return paths;
    }

    public List<Path> liveSimulationFilesFrom(String tournament, String startRound) {
        List<String> rounds = List.of("last_32", "last_16", "last_8", "last_4", "final");
        int start = rounds.indexOf(startRound);
        if (start < 0) return List.of();
        List<Path> paths = new ArrayList<>();
        for (String round : rounds.subList(start, rounds.size())) {
            paths.add(liveSimulationFile(tournament, "simulation_" + round + ".csv"));
            paths.add(liveSimulationFile(tournament, "simulation_paths_" + round + ".csv"));
            paths.add(liveSimulationFile(tournament, "simulation_scorelines_" + round + ".csv"));
        }
        return paths;
    }

    public void deletePaths(List<Path> paths) throws IOException {
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    public long countDataRows(Path path) throws IOException {
        List<String> lines = generatedDataStore.readLines(path);
        if (lines.isEmpty()) return 0;
        return lines.stream().filter(line -> !line.trim().isEmpty()).skip(1).count();
    }

    private GeneratedDataset generatedDataset(Path path) {
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

    private record GeneratedDataset(String tournament, String datasetType, String round) {}

    public boolean generatedDataExists(Path path) throws IOException {
        return generatedDataStore.exists(path);
    }

    public List<String> readGeneratedLines(Path path) throws IOException {
        return generatedDataStore.readLines(path);
    }

    public CsvData readCsv(Path path) throws IOException {
        if (generatedDataStore.exists(path)) {
            List<String> lines = generatedDataStore.readLines(path);
            if (!lines.isEmpty()) {
                try (Reader reader = new java.io.StringReader(String.join("\n", lines));
                     CSVParser parser = CSV.parse(reader)) {
                    List<String> headers = new ArrayList<>(parser.getHeaderNames());
                    List<Map<String, String>> rows = new ArrayList<>();
                    for (CSVRecord record : parser) {
                        Map<String, String> row = new LinkedHashMap<>();
                        for (String header : headers) {
                            row.put(header, record.isMapped(header) ? record.get(header) : "");
                        }
                        rows.add(row);
                    }
                    return new CsvData(headers, rows);
                }
            }
        }
        return csv.readCsv(path);
    }

    public List<Map<String, String>> readMatchupPredictionRows(String tournament, String round) throws IOException {
        return generatedDataStore.readMatchupPredictionRows(tournament, round);
    }

    public CsvData readCachedDataset(String tournament, String datasetType, String round, Path path) throws IOException {
        return generatedDataStore.exists(path) ? csv.readCsv(path) : new CsvData(List.of(), List.of());
    }

    public List<String> readCachedLines(String tournament, String datasetType, String round, Path path) throws IOException {
        return generatedDataStore.exists(path) ? generatedDataStore.readLines(path) : List.of();
    }

    public List<Map<String, String>> readCachedMatchupsForTeam(String tournament, String round, String team, String pathFilter, Path source) throws IOException {
        String safeTeam = WebText.trim(team);
        return filterMatchupRows(readCachedDataset(tournament, "matchup", round, source), pathFilter, safeTeam);
    }

    public MatchupPageLines readCachedMatchupPage(String tournament, String round, Path path, String pathFilter, String teamFilter,
                                                  int page, int pageSize) throws IOException {
        CsvData data = readCachedDataset(tournament, "matchup", round, path);
        List<Map<String, String>> filtered = filterMatchupRows(data, pathFilter, teamFilter);
        Map<String, String> routeContextsByMatchup = routeContextsByMatchup(filtered);
        List<String> headers = new ArrayList<>(data.headers());
        if (!headers.isEmpty() && !headers.contains("route_contexts")) {
            headers.add("route_contexts");
        }
        for (Map<String, String> row : filtered) {
            row.put("route_contexts", routeContextsByMatchup.getOrDefault(matchupContextKey(row), ""));
        }
        filtered.sort(matchupLikelihoodComparator());
        int safePageSize = Math.max(1, pageSize);
        int safePage = Math.max(1, page);
        int from = Math.min(filtered.size(), (safePage - 1) * safePageSize);
        int to = Math.min(filtered.size(), from + safePageSize);
        List<Map<String, String>> rows = filtered.subList(from, to);
        List<String> lines = new ArrayList<>();
        if (!headers.isEmpty()) {
            lines.add(String.join(",", headers));
            for (Map<String, String> row : rows) {
                lines.add(csvLine(headers, row));
            }
        }
        return new MatchupPageLines(lines, rows, filtered.size());
    }

    private Map<String, String> routeContextsByMatchup(List<Map<String, String>> rows) {
        Map<String, LinkedHashSet<String>> contexts = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String key = matchupContextKey(row);
            if (key.isBlank()) continue;
            String context = routeContextValue(row);
            if (context.isBlank()) continue;
            contexts.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(context);
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : contexts.entrySet()) {
            out.put(entry.getKey(), String.join("||", entry.getValue()));
        }
        return out;
    }

    private static String matchupContextKey(Map<String, String> row) {
        String matchId = WebText.trim(row.getOrDefault("match_id", ""));
        String team1 = WebText.trim(row.getOrDefault("team1", ""));
        String team2 = WebText.trim(row.getOrDefault("team2", ""));
        if (matchId.isBlank() || team1.isBlank() || team2.isBlank()) return "";
        return matchId + "|" + team1 + "|" + team2;
    }

    private static String routeContextValue(Map<String, String> row) {
        String path = WebText.trim(row.getOrDefault("path", ""));
        if ("predicted".equalsIgnoreCase(path) || "prediction".equalsIgnoreCase(path)) path = "primary";
        String team1Fatigue = WebText.trim(row.getOrDefault("team1_path_fatigue", ""));
        String team2Fatigue = WebText.trim(row.getOrDefault("team2_path_fatigue", ""));
        String team1Opponent = WebText.trim(row.getOrDefault("team1_path_opponent", ""));
        String team2Opponent = WebText.trim(row.getOrDefault("team2_path_opponent", ""));
        return safeRouteField(path) + "~"
                + safeRouteField(team1Opponent) + "~"
                + safeRouteField(team1Fatigue) + "~"
                + safeRouteField(team2Opponent) + "~"
                + safeRouteField(team2Fatigue);
    }

    private static String safeRouteField(String value) {
        return WebText.trim(value)
                .replace(",", " ")
                .replace("|", " ")
                .replace("~", " ")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    public List<String> readCachedMatchupTeamNames(String tournament, String round, Path path) throws IOException {
        CsvData data = readCachedDataset(tournament, "matchup", round, path);
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, String> row : data.rows()) {
            addIfPresent(names, row.get("team1_team"));
            addIfPresent(names, row.get("team2_team"));
            addIfPresent(names, row.get("team1"));
            addIfPresent(names, row.get("team2"));
        }
        return new ArrayList<>(names);
    }


    private java.util.Comparator<Map<String, String>> matchupLikelihoodComparator() {
        return java.util.Comparator
                .comparingDouble((Map<String, String> row) -> matchupLikelihoodValue(row)).reversed()
                .thenComparing(row -> WebText.trim(row.getOrDefault("match_id", "")))
                .thenComparing(row -> pathSortRank(WebText.trim(row.getOrDefault("path", ""))))
                .thenComparing(row -> WebText.trim(row.getOrDefault("team1", "")))
                .thenComparing(row -> WebText.trim(row.getOrDefault("team2", "")));
    }

    private static double matchupLikelihoodValue(Map<String, String> row) {
        String value = WebText.trim(row.getOrDefault("matchup_pct", ""));
        if (value.isBlank()) value = WebText.trim(row.getOrDefault("matchup_likelihood", ""));
        value = value.replace("%", "").trim();
        boolean lessThan = value.startsWith("<");
        if (lessThan) value = value.substring(1).trim();
        try {
            double parsed = Double.parseDouble(value);
            return lessThan ? Math.max(0.0, parsed / 2.0) : parsed;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static int pathSortRank(String path) {
        return switch (path.toLowerCase(java.util.Locale.ROOT)) {
            case "primary", "predicted", "prediction", "live" -> 0;
            case "results", "fixture", "actual", "result_upset" -> 1;
            case "alt", "upset" -> 2;
            default -> 3;
        };
    }


    public List<Map<String, String>> matchupAdvanceRows(String tournament, String round, Path path) throws IOException {
        CsvData data = readCachedDataset(tournament, "matchup", round, path);
        Map<String, Double> advancePct = new LinkedHashMap<>();
        int routeRows = 0;
        int runs = 0;
        EloCalculator elo = new EloCalculator();
        for (Map<String, String> row : data.rows()) {
            String matchId = WebText.trim(row.getOrDefault("match_id", ""));
            String team1 = WebText.trim(row.getOrDefault("team1", ""));
            String team2 = WebText.trim(row.getOrDefault("team2", ""));
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank()) continue;
            double matchupPct = matchupLikelihoodValue(row);
            if (matchupPct <= 0.0) continue;
            String prediction = WebText.trim(row.getOrDefault("prediction", ""));
            if (prediction.isBlank()) prediction = WebText.trim(row.getOrDefault("model_prediction", ""));
            String winner = elo.parseTeamFromPrediction(prediction);
            int winnerPct = elo.parsePctFromPrediction(prediction);
            if (winner.isBlank() || winnerPct <= 0) continue;
            double winnerContribution = matchupPct * winnerPct / 100.0;
            double otherContribution = matchupPct * (100.0 - winnerPct) / 100.0;
            if (winner.equalsIgnoreCase(team1)) {
                advancePct.merge(team1, winnerContribution, Double::sum);
                advancePct.merge(team2, otherContribution, Double::sum);
            } else if (winner.equalsIgnoreCase(team2)) {
                advancePct.merge(team2, winnerContribution, Double::sum);
                advancePct.merge(team1, otherContribution, Double::sum);
            } else {
                advancePct.merge(winner, winnerContribution, Double::sum);
            }
            routeRows++;
            runs = Math.max(runs, parseInt(WebText.trim(row.getOrDefault("matchup_runs", ""))));
        }
        String advanceColumn = SimulationViewDataService.advanceColumnForRound(round);
        if (advanceColumn.isBlank()) advanceColumn = "reach_next_round";
        final String sortColumn = advanceColumn;
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map.Entry<String, Double> entry : advancePct.entrySet()) {
            Map<String, String> out = new LinkedHashMap<>();
            out.put("team", entry.getKey());
            out.put(advanceColumn, formatPct(entry.getValue()));
            out.put("simulation_runs", String.valueOf(runs));
            out.put("simulation_origin", "matchup_paths");
            out.put("route_matchups", String.valueOf(routeRows));
            out.put("route_weighted", "true");
            rows.add(out);
        }
        rows.sort(java.util.Comparator.comparingDouble((Map<String, String> row) -> parseDouble(row.getOrDefault(sortColumn, "0"))).reversed()
                .thenComparing(row -> row.getOrDefault("team", "")));
        return rows;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String formatPct(double value) {
        double bounded = Math.max(0.0, Math.min(100.0, value));
        if (bounded > 0.0 && bounded < 0.1) return String.format(java.util.Locale.ROOT, "%.3f", bounded);
        return String.format(java.util.Locale.ROOT, "%.1f", bounded);
    }

    private List<Map<String, String>> filterMatchupRows(CsvData data, String pathFilter, String teamFilter) {
        String safePath = WebText.trim(pathFilter).toLowerCase(java.util.Locale.ROOT);
        String safeTeam = WebText.trim(teamFilter);
        List<Map<String, String>> filtered = new ArrayList<>();
        for (Map<String, String> row : data.rows()) {
            String rowPath = WebText.trim(row.get("path")).toLowerCase(java.util.Locale.ROOT);
            boolean pathMatches = safePath.isBlank() || "all".equals(safePath)
                    || ("prediction".equals(safePath) ? ("primary".equals(rowPath) || "predicted".equals(rowPath) || "live".equals(rowPath))
                    : ("results".equals(safePath) ? ("results".equals(rowPath) || "fixture".equals(rowPath) || "actual".equals(rowPath) || "result_upset".equals(rowPath))
                    : ("alt".equals(safePath) ? ("alt".equals(rowPath) || "upset".equals(rowPath)) : safePath.equals(rowPath))));
            boolean teamMatches = safeTeam.isBlank()
                    || safeTeam.equalsIgnoreCase(WebText.trim(row.get("team1_team")))
                    || safeTeam.equalsIgnoreCase(WebText.trim(row.get("team2_team")))
                    || safeTeam.equalsIgnoreCase(WebText.trim(row.get("team1")))
                    || safeTeam.equalsIgnoreCase(WebText.trim(row.get("team2")));
            if (pathMatches && teamMatches) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private static void addIfPresent(Set<String> names, String value) {
        String clean = WebText.trim(value);
        if (!clean.isBlank()) {
            names.add(clean);
        }
    }

    public void warmGeneratedData(String tournament) {
    }

    private String csvLine(List<String> headers, Map<String, String> row) throws IOException {
        java.io.StringWriter writer = new java.io.StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            printer.printRecord(headers.stream().map(header -> row.getOrDefault(header, "")).collect(Collectors.toList()));
        }
        return writer.toString().trim();
    }

    public record MatchupPageLines(List<String> lines, List<Map<String, String>> rows, int totalRows) {}

    public Map<String, String> routeWeightedMatchupLikelihoods(
            String tournament, String round, List<Map<String, String>> currentRows,
            List<Map<String, String>> groupRows) throws IOException {
        if ("last_32".equals(round)) return RouteLikelihoodService.matchupLikelihoodMap(currentRows, groupRows);
        List<Map<String, String>> last32Rows = readCsv(matchupFile(tournament, "last_32.csv")).rows();
        List<Map<String, String>> last16Rows = "last_16".equals(round)
                ? currentRows : readCsv(matchupFile(tournament, "last_16.csv")).rows();
        Map<String, String> last16Likelihoods = RouteLikelihoodService.routeWeightedNextRoundMatchupLikelihoodMap(
                last16Rows, last32Rows, groupRows);
        if ("last_16".equals(round)) return last16Likelihoods;

        List<Map<String, String>> last8Rows = "last_8".equals(round)
                ? currentRows : readCsv(matchupFile(tournament, "last_8.csv")).rows();
        Map<String, String> last8Likelihoods = RouteLikelihoodService.routeWeightedNextRoundMatchupLikelihoodMap(
                last8Rows, last16Rows, last16Likelihoods);
        if ("last_8".equals(round)) return last8Likelihoods;

        List<Map<String, String>> last4Rows = "last_4".equals(round)
                ? currentRows : readCsv(matchupFile(tournament, "last_4.csv")).rows();
        Map<String, String> last4Likelihoods = RouteLikelihoodService.routeWeightedNextRoundMatchupLikelihoodMap(
                last4Rows, last8Rows, last8Likelihoods);
        if ("last_4".equals(round)) return last4Likelihoods;
        return RouteLikelihoodService.routeWeightedNextRoundMatchupLikelihoodMap(currentRows, last4Rows, last4Likelihoods);
    }

    public void writeCsv(Path path, List<String> headers, List<Map<String, String>> rows) throws IOException {
        if (!generatedDataStore.writeRows(path, headers, rows)) {
            Files.createDirectories(path.getParent());
            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build();
            try (Writer writer = Files.newBufferedWriter(path); CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (Map<String, String> row : rows) {
                    List<String> values = headers.stream().map(header -> row.getOrDefault(header, "")).collect(Collectors.toList());
                    printer.printRecord(values);
                }
            }
        }
    }

    public Map<String, String> baseRow(List<Map<String, String>> existingRows, int index, List<String> headers) {
        Map<String, String> row = new LinkedHashMap<>();
        if (index < existingRows.size()) {
            row.putAll(existingRows.get(index));
        }
        for (String header : headers) {
            row.putIfAbsent(header, "");
        }
        return row;
    }

    public void ensureHeaders(List<String> headers, List<String> requiredHeaders) {
        Set<String> current = new LinkedHashSet<>(headers);
        for (String header : requiredHeaders) {
            if (!current.contains(header)) {
                headers.add(header);
            }
        }
    }

    public Path outputPathForMode(String tournament, String mode) {
        return switch (mode) {
            case "start" -> predictionFile(tournament, "groups.csv");
            case "groups" -> predictionFile(tournament, "last_32.csv");
            case "group-simulation" -> simulationFile(tournament, "simulation_groups.csv");
            case "last_32" -> matchupFile(tournament, "last_32.csv");
            case "last_16" -> matchupFile(tournament, "last_16.csv");
            case "last_8" -> matchupFile(tournament, "last_8.csv");
            case "last_4" -> matchupFile(tournament, "last_4.csv");
            case "final" -> matchupFile(tournament, "final.csv");
            case "simulate" -> simulationFile(tournament, "simulation_last_32.csv");
            default -> null;
        };
    }

    public Path roundFileForView(String tournament, String round) {
        return switch (round) {
            case "start" -> predictionFile(tournament, "start.csv");
            case "groups", "groups_match" -> predictionFile(tournament, "groups.csv");
            case "last_32" -> predictionFile(tournament, "last_32.csv");
            case "last_32_match" -> viewMatchFile(tournament, "last_32.csv");
            case "last_16" -> predictionFile(tournament, "last_16.csv");
            case "last_16_match" -> viewMatchFile(tournament, "last_16.csv");
            case "last_8" -> predictionFile(tournament, "last_8.csv");
            case "last_8_match" -> viewMatchFile(tournament, "last_8.csv");
            case "last_4" -> predictionFile(tournament, "last_4.csv");
            case "last_4_match" -> viewMatchFile(tournament, "last_4.csv");
            case "final" -> predictionFile(tournament, "final.csv");
            case "final_match" -> viewMatchFile(tournament, "final.csv");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid round");
        };
    }

    public Path viewMatchFile(String tournament, String fileName) {
        Path view = matchupViewFile(tournament, fileName);
        if (generatedDataExistsQuiet(view)) return view;
        Path matchup = matchupFile(tournament, fileName);
        return generatedDataExistsQuiet(matchup) ? matchup : predictionFile(tournament, fileName);
    }

    public static final List<String> VIEW_ROUND_SEQUENCE = List.of(
        "start", "groups", "last_32_match", "last_16_match", "last_8_match", "last_4_match", "final_match"
    );

    public String nextViewRound(String round) {
        return WebNavigationService.nextViewRound(round);
    }

    public String prevViewRound(String round) {
        return WebNavigationService.prevViewRound(round);
    }

    public String editPrevViewRound(String round) {
        return WebNavigationService.editPrevViewRound(round);
    }

    public String viewRoundForEdit(String round) {
        return WebNavigationService.viewRoundForEdit(round);
    }

    public String nextRunPrereqForView(String round) {
        return WebNavigationService.nextRunPrereqForView(round);
    }

    public String nextRunModeForView(String round) {
        return WebNavigationService.nextRunModeForView(round);
    }


    public boolean generatedDataExistsQuiet(Path path) {
        try {
            return generatedDataStore.exists(path);
        } catch (IOException e) {
            return Files.exists(path);
        }
    }

    public int completedStepCount(String tournament) {
        int count = 0;
        if (generatedDataExistsQuiet(predictionFile(tournament, "start.csv"))) count++;
        if (generatedDataExistsQuiet(predictionFile(tournament, "groups.csv"))) count++;
        if (generatedDataExistsQuiet(predictionFile(tournament, "last_32.csv"))) count++;
        if (generatedDataExistsQuiet(matchupFile(tournament, "final.csv"))) count++;
        return count;
    }

    public String describeCurrentStage(String tournament) {
        if (generatedDataExistsQuiet(matchupFile(tournament, "final.csv"))) return "Final complete";
        if (generatedDataExistsQuiet(matchupFile(tournament, "last_32.csv"))) return "Tournament run in progress";
        if (generatedDataExistsQuiet(predictionFile(tournament, "last_32.csv"))) return "Group Picks complete";
        if (generatedDataExistsQuiet(predictionFile(tournament, "groups.csv"))) return "Group Rankings complete";
        if (generatedDataExistsQuiet(predictionFile(tournament, "start.csv"))) return "Team Setup complete";
        return "Not started";
    }

    public String safeTournament(String tournament) {
        String value = WebText.trim(tournament);
        if (!value.matches("[A-Za-z0-9_-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tournament name");
        }
        return value;
    }

    public String safeMode(String mode) {
        String value = WebText.trim(mode);
        if (!RUN_MODES.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid mode");
        }
        return value;
    }

    public String safeRound(String round) {
        String value = WebText.trim(round);
        if (!ROUND_NAMES.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid round");
        }
        return value;
    }

    public String safeResetStep(String step) {
        String value = WebText.trim(step);
        if (!RESET_STEPS.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset step");
        }
        return value;
    }

    public List<GroupMatchResultRow> buildGroupResultsEditorRows(String tournament) throws IOException {
        CsvData groupsData = readCsv(predictionFile(tournament, "groups.csv"));
        List<Map<String, String>> groupRows = groupsData.rows();
        if (groupRows.isEmpty()) {
            return List.of();
        }
        Map<String, String> teamGroups = new LinkedHashMap<>();
        for (Map<String, String> row : groupRows) {
            String group = WebText.trim(row.getOrDefault("group", ""));
            String team = WebText.trim(row.getOrDefault("team", ""));
            if (!group.isBlank() && !team.isBlank()) {
                teamGroups.put(team, group);
            }
        }
        List<Map<String, String>> scorelineRows = readMatchupPredictionRows(tournament, "groups");
        if (scorelineRows.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, String>> existing = loadGroupMatchResultsByMatchId(tournament);
        Map<String, GroupMatchAccumulator> matches = new LinkedHashMap<>();
        for (Map<String, String> row : scorelineRows) {
            if (!"groups".equalsIgnoreCase(WebText.trim(row.getOrDefault("stage", "")))) continue;
            String matchId = WebText.trim(row.getOrDefault("match_id", ""));
            String team1 = WebText.trim(row.getOrDefault("team1", ""));
            String team2 = WebText.trim(row.getOrDefault("team2", ""));
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank()) continue;
            String key = matchId + "|" + team1 + "|" + team2;
            GroupMatchAccumulator match = matches.computeIfAbsent(key,
                    ignored -> new GroupMatchAccumulator(matchId, teamGroups.getOrDefault(team1, ""), team1, team2));
            int count = WebText.parseInt(row.getOrDefault("count", "0"), 0);
            match.total += count;
            match.outcomes.merge(WebText.trim(row.getOrDefault("winner", "Draw")), count, Integer::sum);
        }
        List<GroupMatchResultRow> rows = new ArrayList<>();
        int index = 0;
        for (GroupMatchAccumulator match : matches.values()) {
            Map<String, String> existingRow = existing.getOrDefault(match.matchId, Map.of());
            Map.Entry<String, Integer> likely = match.outcomes.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(Map.entry("Draw", 0));
            int pct = match.total == 0 ? 0 : (int) Math.round(likely.getValue() * 100.0 / match.total);
            String predicted = likely.getKey() + " (" + pct + "%)";
            rows.add(new GroupMatchResultRow(index++, match.group, match.matchId, match.team1, match.team2,
                    predicted,
                    existingRow.getOrDefault("winner", ""),
                    existingRow.getOrDefault("home_score", ""),
                    existingRow.getOrDefault("away_score", "")));
        }
        rows.sort(java.util.Comparator.comparing(GroupMatchResultRow::getGroup)
                .thenComparing(row -> WebText.parseInt(row.getMatchId().replaceAll("\\D+", ""), Integer.MAX_VALUE))
                .thenComparing(GroupMatchResultRow::getMatchId));
        return rows;
    }

    public Map<String, Map<String, String>> loadGroupMatchResultsByMatchId(String tournament) throws IOException {
        Path file = projectRoot.resolve("data").resolve("results").resolve(tournament).resolve("groups.csv");
        if (!generatedDataStore.exists(file)) {
            return Map.of();
        }
        Map<String, Map<String, String>> rowsByKey = new LinkedHashMap<>();
        for (Map<String, String> row : readCsv(file).rows()) {
            String matchId = WebText.trim(row.getOrDefault("match_id", ""));
            if (matchId.isBlank()) continue;
            rowsByKey.put(matchId, row);
        }
        return rowsByKey;
    }

    public boolean hasEarlierResultsForLivePrediction(String tournament, String round) {
        String safeRound = WebText.trim(round).toLowerCase(java.util.Locale.ROOT);
        if (safeRound.isBlank()) {
            return false;
        }
        List<String> rounds = new ArrayList<>();
        rounds.add("groups");
        if (bracketHasStage(tournament, "LAST_32")) {
            rounds.add("last_32");
        }
        rounds.addAll(List.of("last_16", "last_8", "last_4", "final"));
        int index = rounds.indexOf(safeRound);
        if (index <= 0) {
            return false;
        }
        for (int i = 0; i < index; i++) {
            if (generatedDataExistsQuiet(resultsFile(tournament, rounds.get(i)))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasResultsPrerequisite(String tournament, String round) {
        String safeRound = WebText.trim(round).toLowerCase(java.util.Locale.ROOT);
        if (safeRound.isBlank()) {
            return false;
        }
        if ("last_32".equals(safeRound) || "last_16".equals(safeRound) && !bracketHasStage(tournament, "LAST_32")) {
            return generatedDataExistsQuiet(resultsFile(tournament, "groups"));
        }
        return switch (safeRound) {
            case "last_16" -> generatedDataExistsQuiet(resultsFile(tournament, "last_32"));
            case "last_8" -> generatedDataExistsQuiet(resultsFile(tournament, "last_16"));
            case "last_4" -> generatedDataExistsQuiet(resultsFile(tournament, "last_8"));
            case "final" -> generatedDataExistsQuiet(resultsFile(tournament, "last_4"));
            default -> false;
        };
    }

    public List<ResultsRoundView> buildResultsEditorRounds(String tournament) throws IOException {
        return buildResultsEditorRounds(tournament, null);
    }

    public List<ResultsRoundView> buildResultsEditorRounds(String tournament, String selectedRound) throws IOException {
        List<ResultsRoundView> rounds = new ArrayList<>();
        for (String round : RESULTS_ROUNDS) {
            if (selectedRound != null && !selectedRound.isBlank() && !round.equalsIgnoreCase(selectedRound)) continue;
            if (!hasResultsPrerequisite(tournament, round)) continue;
            List<ResultEntryRow> roundRows = buildActualResultsEditorRows(tournament, round);
            if (roundRows.isEmpty()) {
                roundRows = buildPredictedResultsEditorRows(tournament, round);
            }
            if (!roundRows.isEmpty()) {
                rounds.add(new ResultsRoundView(round, displayViewMode(round), roundRows));
            }
        }
        return rounds;
    }

    public List<ResultEntryRow> buildActualResultsEditorRows(String tournament, String round) throws IOException {
        String safeRound = WebText.trim(round).toLowerCase(java.util.Locale.ROOT);
        List<CsvLoader.BracketEntry> brackets = new CsvLoader(projectRoot).loadBrackets(tournament);
        String stage = knockoutStageForRound(safeRound);
        if (stage == null) {
            return List.of();
        }
        Map<String, Map<String, String>> existingByKey = loadRoundResultsByKey(tournament, safeRound);
        List<ResultEntryRow> rows = new ArrayList<>();
        int index = 0;
        if (safeRound.equals(openingKnockoutRound(tournament))) {
            Map<String, String> slotMap = actualGroupSlotMap(tournament);
            if (slotMap.isEmpty()) {
                return List.of();
            }
            Map<String, String> thirdPlaceByMatch = actualThirdPlaceAssignments(tournament, brackets);
            for (CsvLoader.BracketEntry bracket : brackets) {
                if (!stage.equalsIgnoreCase(bracket.stage) || bracket.matchId == null || bracket.matchId.isBlank()) continue;
                String team1 = resolveActualOpeningToken(bracket.matchId, bracket.token1, slotMap, thirdPlaceByMatch);
                String team2 = resolveActualOpeningToken(bracket.matchId, bracket.token2, slotMap, thirdPlaceByMatch);
                if (team1.isBlank() || team2.isBlank()) continue;
                String key = KnockoutViewRows.matchKey(team1, team2);
                Map<String, String> existing = existingByKey.getOrDefault(key, Map.of());
                rows.add(new ResultEntryRow(safeRound, index++, bracket.matchId, team1, team2,
                        existing.getOrDefault("winner", ""),
                        existing.getOrDefault("home_score", ""),
                        existing.getOrDefault("away_score", ""),
                        "yes".equalsIgnoreCase(existing.getOrDefault("penalties", ""))));
            }
            return rows;
        }
        Map<String, String> winnerByMatch = priorRoundWinnersByMatchId(tournament, safeRound);
        if (winnerByMatch.isEmpty()) {
            return List.of();
        }
        for (CsvLoader.BracketEntry bracket : brackets) {
            if (!stage.equalsIgnoreCase(bracket.stage) || bracket.matchId == null || bracket.matchId.isBlank()) continue;
            String team1 = resolveWinnerToken(bracket.token1, winnerByMatch);
            String team2 = resolveWinnerToken(bracket.token2, winnerByMatch);
            if (team1.isBlank() || team2.isBlank()) continue;
            String key = KnockoutViewRows.matchKey(team1, team2);
            Map<String, String> existing = existingByKey.getOrDefault(key, Map.of());
            rows.add(new ResultEntryRow(safeRound, index++, bracket.matchId, team1, team2,
                    existing.getOrDefault("winner", ""),
                    existing.getOrDefault("home_score", ""),
                    existing.getOrDefault("away_score", ""),
                    "yes".equalsIgnoreCase(existing.getOrDefault("penalties", ""))));
        }
        return rows;
    }

    public List<ResultEntryRow> buildPredictedResultsEditorRows(String tournament, String round) throws IOException {
        Path matchupPath = matchupFile(tournament, round + ".csv");
        if (!generatedDataStore.exists(matchupPath)) {
            return List.of();
        }
        Map<String, Map<String, String>> existingByKey = loadRoundResultsByKey(tournament, round);
        LinkedHashMap<String, Map<String, String>> uniqueMatchups = new LinkedHashMap<>();
        for (Map<String, String> row : readCsv(matchupPath).rows()) {
            String team1 = WebText.trim(row.getOrDefault("team1", ""));
            String team2 = WebText.trim(row.getOrDefault("team2", ""));
            if (team1.isBlank() || team2.isBlank()) continue;
            String key = KnockoutViewRows.matchKey(team1, team2);
            String path = WebText.trim(row.getOrDefault("path", ""));
            if (!uniqueMatchups.containsKey(key) || "predicted".equalsIgnoreCase(path)) {
                uniqueMatchups.put(key, row);
            }
        }
        List<ResultEntryRow> roundRows = new ArrayList<>();
        int index = 0;
        for (Map<String, String> matchup : uniqueMatchups.values()) {
            String team1 = WebText.trim(matchup.getOrDefault("team1", ""));
            String team2 = WebText.trim(matchup.getOrDefault("team2", ""));
            if (team1.isBlank() || team2.isBlank()) continue;
            String key = KnockoutViewRows.matchKey(team1, team2);
            Map<String, String> existing = existingByKey.getOrDefault(key, Map.of());
            roundRows.add(new ResultEntryRow(
                    round,
                    index++,
                    WebText.trim(matchup.getOrDefault("match_id", "")),
                    team1,
                    team2,
                    existing.getOrDefault("winner", ""),
                    existing.getOrDefault("home_score", ""),
                    existing.getOrDefault("away_score", ""),
                    "yes".equalsIgnoreCase(existing.getOrDefault("penalties", ""))
            ));
        }
        return roundRows;
    }

    public String openingKnockoutRound(String tournament) {
        return bracketHasStage(tournament, "LAST_32") ? "last_32" : "last_16";
    }

    public String knockoutStageForRound(String round) {
        return switch (WebText.trim(round).toLowerCase(java.util.Locale.ROOT)) {
            case "last_32" -> "LAST_32";
            case "last_16" -> "LAST_16";
            case "last_8" -> "QUARTER";
            case "last_4" -> "SEMI";
            case "final" -> "FINAL";
            default -> null;
        };
    }

    public String previousResultsRound(String tournament, String round) {
        String safeRound = WebText.trim(round).toLowerCase(java.util.Locale.ROOT);
        if ("last_16".equals(safeRound)) {
            return bracketHasStage(tournament, "LAST_32") ? "last_32" : "groups";
        }
        return switch (safeRound) {
            case "last_8" -> "last_16";
            case "last_4" -> "last_8";
            case "final" -> "last_4";
            default -> null;
        };
    }

    public Map<String, String> priorRoundWinnersByMatchId(String tournament, String round) throws IOException {
        String previousRound = previousResultsRound(tournament, round);
        if (previousRound == null || "groups".equals(previousRound)) {
            return Map.of();
        }
        Path file = resultsFile(tournament, previousRound);
        if (!generatedDataStore.exists(file)) {
            return Map.of();
        }
        Map<String, String> winners = new LinkedHashMap<>();
        for (Map<String, String> row : readCsv(file).rows()) {
            String matchId = WebText.trim(row.getOrDefault("match_id", ""));
            String winner = WebText.trim(row.getOrDefault("winner", ""));
            if (!matchId.isBlank() && !winner.isBlank() && !"Draw".equalsIgnoreCase(winner)) {
                winners.put(matchId, winner);
            }
        }
        return winners;
    }

    public String resolveWinnerToken(String token, Map<String, String> winnerByMatch) {
        String safeToken = WebText.trim(token);
        if (safeToken.matches("^W\\d+$")) {
            return winnerByMatch.getOrDefault("M" + safeToken.substring(1), "");
        }
        return "";
    }

    public String resolveActualOpeningToken(String matchId, String token, Map<String, String> slotMap,
                                             Map<String, String> thirdPlaceByMatch) {
        String safeToken = WebText.trim(token);
        if (safeToken.matches("^[A-L][1-4]$")) {
            return slotMap.getOrDefault(safeToken, "");
        }
        if (safeToken.matches("^[A-L]+3$")) {
            return thirdPlaceByMatch.getOrDefault(matchId, "");
        }
        return "";
    }

    public Set<String> actualGroupAdvancers(String tournament) throws IOException {
        Map<String, List<GroupStandingRow>> standings = actualGroupStandings(tournament);
        Set<String> advancers = new LinkedHashSet<>();
        for (List<GroupStandingRow> table : standings.values()) {
            for (int i = 0; i < table.size() && i < 2; i++) {
                advancers.add(table.get(i).team());
            }
        }
        List<CsvLoader.BracketEntry> brackets = new CsvLoader(projectRoot).loadBrackets(tournament);
        if (brackets.stream().anyMatch(entry -> entry.token1 != null && entry.token1.matches("^[A-L]+3$")
                || entry.token2 != null && entry.token2.matches("^[A-L]+3$"))) {
            advancers.addAll(actualThirdPlaceAssignments(tournament, brackets).values());
        }
        return advancers;
    }

    public Map<String, String> actualGroupSlotMap(String tournament) throws IOException {
        Map<String, List<GroupStandingRow>> standings = actualGroupStandings(tournament);
        if (standings.isEmpty()) {
            return Map.of();
        }
        Map<String, String> slots = new LinkedHashMap<>();
        for (Map.Entry<String, List<GroupStandingRow>> entry : standings.entrySet()) {
            String group = entry.getKey();
            List<GroupStandingRow> table = entry.getValue();
            for (int i = 0; i < table.size(); i++) {
                slots.put(group + (i + 1), table.get(i).team());
            }
        }
        return slots;
    }

    public Map<String, List<GroupStandingRow>> actualGroupStandings(String tournament) throws IOException {
        Path file = resultsFile(tournament, "groups");
        if (!generatedDataStore.exists(file)) {
            return Map.of();
        }
        Map<String, Map<String, StandingAccumulator>> groups = new LinkedHashMap<>();
        for (Map<String, String> row : readCsv(file).rows()) {
            String group = WebText.trim(row.getOrDefault("group", ""));
            String team1 = WebText.trim(row.getOrDefault("team1", ""));
            String team2 = WebText.trim(row.getOrDefault("team2", ""));
            String hs = WebText.trim(row.getOrDefault("home_score", ""));
            String as = WebText.trim(row.getOrDefault("away_score", ""));
            if (group.isBlank() || team1.isBlank() || team2.isBlank() || hs.isBlank() || as.isBlank()) continue;
            int homeScore = WebText.parseInt(hs, Integer.MIN_VALUE);
            int awayScore = WebText.parseInt(as, Integer.MIN_VALUE);
            if (homeScore == Integer.MIN_VALUE || awayScore == Integer.MIN_VALUE) continue;
            Map<String, StandingAccumulator> table = groups.computeIfAbsent(group, ignored -> new LinkedHashMap<>());
            StandingAccumulator home = table.computeIfAbsent(team1, StandingAccumulator::new);
            StandingAccumulator away = table.computeIfAbsent(team2, StandingAccumulator::new);
            home.played += 1; away.played += 1;
            home.goalsFor += homeScore; home.goalsAgainst += awayScore;
            away.goalsFor += awayScore; away.goalsAgainst += homeScore;
            if (homeScore > awayScore) {
                home.points += 3; home.wins += 1; away.losses += 1;
            } else if (awayScore > homeScore) {
                away.points += 3; away.wins += 1; home.losses += 1;
            } else {
                home.points += 1; away.points += 1; home.draws += 1; away.draws += 1;
            }
        }
        Map<String, List<GroupStandingRow>> standings = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, StandingAccumulator>> entry : groups.entrySet()) {
            List<GroupStandingRow> table = new ArrayList<>();
            for (StandingAccumulator acc : entry.getValue().values()) {
                table.add(new GroupStandingRow(entry.getKey(), acc.team, acc.points, acc.goalDifference(), acc.goalsFor, acc.goalsAgainst));
            }
            table.sort(java.util.Comparator.comparingInt(GroupStandingRow::points).reversed()
                    .thenComparing(java.util.Comparator.comparingInt(GroupStandingRow::goalDifference).reversed())
                    .thenComparing(java.util.Comparator.comparingInt(GroupStandingRow::goalsFor).reversed())
                    .thenComparing(GroupStandingRow::team));
            standings.put(entry.getKey(), table);
        }
        return standings;
    }

    public Map<String, String> actualThirdPlaceAssignments(String tournament, List<CsvLoader.BracketEntry> brackets) throws IOException {
        if (!bracketHasStage(tournament, "LAST_32")) {
            return Map.of();
        }
        Map<String, List<GroupStandingRow>> standings = actualGroupStandings(tournament);
        List<GroupStandingRow> thirds = new ArrayList<>();
        for (List<GroupStandingRow> table : standings.values()) {
            if (table.size() >= 3) {
                thirds.add(table.get(2));
            }
        }
        if (thirds.isEmpty()) {
            return Map.of();
        }
        thirds.sort(java.util.Comparator.comparingInt(GroupStandingRow::points).reversed()
                .thenComparing(java.util.Comparator.comparingInt(GroupStandingRow::goalDifference).reversed())
                .thenComparing(java.util.Comparator.comparingInt(GroupStandingRow::goalsFor).reversed())
                .thenComparing(GroupStandingRow::group));
        List<GroupStandingRow> qualified = thirds.size() > 8 ? thirds.subList(0, 8) : thirds;
        List<String> groups = qualified.stream().map(GroupStandingRow::group).sorted().toList();
        String lookupKey = String.join("", groups);
        Map<String, String> columnToGroup = thirdPlaceLookup(lookupKey);
        if (columnToGroup.isEmpty()) {
            return Map.of();
        }
        Map<String, String> groupToTeam = new LinkedHashMap<>();
        for (GroupStandingRow row : qualified) {
            groupToTeam.put(row.group(), row.team());
        }
        Map<String, String> columnToMatch = new LinkedHashMap<>();
        for (CsvLoader.BracketEntry bracket : brackets) {
            if (!"LAST_32".equalsIgnoreCase(bracket.stage) || bracket.matchId == null || bracket.matchId.isBlank()) continue;
            String winnerToken = null;
            if (WebText.trim(bracket.token1).matches("^[A-L]+3$") && WebText.trim(bracket.token2).matches("^[A-L]1$")) {
                winnerToken = WebText.trim(bracket.token2);
            } else if (WebText.trim(bracket.token2).matches("^[A-L]+3$") && WebText.trim(bracket.token1).matches("^[A-L]1$")) {
                winnerToken = WebText.trim(bracket.token1);
            }
            if (winnerToken != null) {
                columnToMatch.put("1" + winnerToken.charAt(0), bracket.matchId);
            }
        }
        Map<String, String> byMatch = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : columnToGroup.entrySet()) {
            String matchId = columnToMatch.get(entry.getKey());
            String team = groupToTeam.get(entry.getValue());
            if (matchId != null && team != null) {
                byMatch.put(matchId, team);
            }
        }
        return byMatch;
    }

    public Map<String, String> thirdPlaceLookup(String lookupKey) throws IOException {
        Path lookupPath = projectRoot.resolve("data").resolve("bracket").resolve("third_place_lookup.csv");
        if (!Files.exists(lookupPath)) {
            return Map.of();
        }
        try (BufferedReader reader = Files.newBufferedReader(lookupPath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return Map.of();
            }
            String[] headers = headerLine.split(",", -1);
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length == 0 || !lookupKey.equals(parts[0])) continue;
                Map<String, String> mapping = new LinkedHashMap<>();
                for (int i = 1; i < parts.length && i < headers.length; i++) {
                    if (!parts[i].isBlank()) {
                        mapping.put(headers[i], parts[i]);
                    }
                }
                return mapping;
            }
        }
        return Map.of();
    }

    public Map<String, Map<String, String>> loadRoundResultsByKey(String tournament, String round) throws IOException {
        Path file = projectRoot.resolve("data").resolve("results").resolve(tournament).resolve(round + ".csv");
        if (!generatedDataStore.exists(file)) {
            return Map.of();
        }
        Map<String, Map<String, String>> rowsByKey = new LinkedHashMap<>();
        for (Map<String, String> row : readCsv(file).rows()) {
            String team1 = WebText.trim(row.getOrDefault("team1", row.getOrDefault("home_team", "")));
            String team2 = WebText.trim(row.getOrDefault("team2", row.getOrDefault("away_team", "")));
            if (team1.isBlank() || team2.isBlank()) continue;
            rowsByKey.put(KnockoutViewRows.matchKey(team1, team2), row);
        }
        return rowsByKey;
    }

    public String displayMode(String mode) {
        return WebNavigationService.displayMode(mode);
    }

    public static String displayTournament(String name) {
        return WebNavigationService.displayTournament(name);
    }

    public void ensureSimulationExists(String tournament, String simulationRound) throws IOException {
        if (simulationRound == null) return;
        Path output = simulationFile(tournament, "simulation_" + simulationRound + ".csv");
        Path input = predictionFile(tournament, simulationRound + ".csv");
        if (generatedDataStore.exists(output) || !generatedDataStore.exists(input)) return;
        MatchResolver.forWeb(new HtmlReporter().withConfig(predictionConfig), predictionConfig)
                .resolveAndWriteSimulation(tournament, simulationRound);
    }


    public void ensureLiveSimulationExists(String tournament, String simulationRound) throws IOException {
        if (simulationRound == null) return;
        Path output = liveSimulationFile(tournament, "simulation_" + simulationRound + ".csv");
        Path scorelines = liveSimulationFile(tournament, "simulation_scorelines_" + simulationRound + ".csv");
        if (generatedDataStore.exists(output) && generatedDataStore.exists(scorelines)) return;
        String completedRound = previousResultsRound(tournament, simulationRound);
        if (completedRound == null || !isResultsRoundComplete(tournament, completedRound)) return;
        List<String> liveStartRows = buildLiveSimulationStartRows(tournament, simulationRound);
        if (liveStartRows.size() <= 1) return;
        MatchResolver.forWeb(new HtmlReporter().withConfig(predictionConfig), predictionConfig)
                .resolveAndWriteLiveSimulation(tournament, simulationRound, liveStartRows);
    }

    public List<Map<String, String>> loadLiveSimulationRows(String tournament, String round) throws IOException {
        Path file = liveSimulationFile(tournament, "simulation_" + round + ".csv");
        if (!generatedDataStore.exists(file)) {
            return List.of();
        }
        return readCsv(file).rows();
    }

    public List<Map<String, String>> resolvedSnapshotLiveRows(List<Map<String, String>> snapshotRows,
                                                               List<Map<String, String>> liveRows,
                                                               String advanceColumn,
                                                               Set<String> actualAdvancingTeams) {
        Map<String, Map<String, String>> liveByTeam = new LinkedHashMap<>();
        for (Map<String, String> row : liveRows) {
            String team = WebText.trim(row.getOrDefault("team", ""));
            if (!team.isBlank()) {
                liveByTeam.putIfAbsent(team, row);
            }
        }
        List<Map<String, String>> resolved = new ArrayList<>();
        for (Map<String, String> row : snapshotRows) {
            String team = WebText.trim(row.getOrDefault("team", ""));
            if (team.isBlank()) {
                continue;
            }
            Map<String, String> source = liveByTeam.getOrDefault(team, row);
            Map<String, String> copy = new LinkedHashMap<>(source);
            copy.put("team", team);
            copy.put(advanceColumn, actualAdvancingTeams.contains(team) ? "100.0" : "0.0");
            resolved.add(copy);
        }
        return resolved;
    }

    public List<String> buildLiveRoundRows(String tournament, String round) throws IOException {
        List<String> liveStartRows = buildLiveSimulationStartRows(tournament, round);
        if (liveStartRows.size() <= 1) {
            return List.of();
        }
        Path scorelinesFile = liveSimulationFile(tournament, "simulation_scorelines_" + round + ".csv");
        if (!generatedDataStore.exists(scorelinesFile)) {
            return List.of();
        }
        List<Map<String, String>> scorelineRows = readCsv(scorelinesFile).rows();
        if (scorelineRows.isEmpty()) {
            return List.of();
        }
        Map<String, LiveMatchSummary> liveByMatch = new LinkedHashMap<>();
        for (Map<String, String> row : scorelineRows) {
            String matchId = WebText.trim(row.getOrDefault("match_id", ""));
            String team1 = WebText.trim(row.getOrDefault("team1", ""));
            String team2 = WebText.trim(row.getOrDefault("team2", ""));
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank()) {
                continue;
            }
            String key = matchId + "|" + team1 + "|" + team2;
            liveByMatch.computeIfAbsent(key, ignored -> new LiveMatchSummary(team1)).add(row);
        }
        List<String> out = new ArrayList<>();
        out.add("match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,model_prediction,selection_source,matchup_pct,matchup_runs");
        for (int i = 1; i < liveStartRows.size(); i++) {
            String line = liveStartRows.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] cols = line.split(",", -1);
            String matchId = valueAt(cols, 0);
            String team1 = valueAt(cols, 1);
            String team2 = valueAt(cols, 2);
            LiveMatchSummary summary = liveByMatch.get(matchId + "|" + team1 + "|" + team2);
            if (summary == null) {
                continue;
            }
            String prediction = summary.predictionText();
            out.add(String.join(",",
                    WebText.csvValue(matchId),
                    WebText.csvValue(team1),
                    WebText.csvValue(team2),
                    "live",
                    WebText.csvValue(prediction),
                    WebText.csvValue(valueAt(cols, 5)),
                    WebText.csvValue(valueAt(cols, 6)),
                    WebText.csvValue(valueAt(cols, 7)),
                    WebText.csvValue(valueAt(cols, 8)),
                    WebText.csvValue(prediction),
                    "live",
                    WebText.csvValue(summary.matchupPct()),
                    WebText.csvValue(summary.matchupRuns())));
        }
        return out;
    }

    public String oddsColumnForRound(String round) {
        return WebNavigationService.oddsColumnForRound(round);
    }

    public String displayViewMode(String round) {
        return WebNavigationService.displayViewMode(round);
    }

    public String redirectToTournament(String tournament) {
        return "redirect:/tournament/" + tournament;
    }

    public String redirectAfterSaveRun(String round, String tournament) {
        return switch (round) {
            case "start"   -> redirectToTournament(tournament);
            case "groups" -> redirectToTournament(tournament);
            case "group-simulation" -> redirectToTournament(tournament);
            case "last_32" -> "redirect:/view/last_32_match?tournament=" + tournament;
            case "last_16" -> "redirect:/view/last_16_match?tournament=" + tournament;
            case "last_8"  -> "redirect:/view/last_8_match?tournament=" + tournament;
            case "last_4"  -> "redirect:/view/last_4_match?tournament=" + tournament;
            case "final"   -> "redirect:/view/final_match?tournament=" + tournament;
            case "simulate" -> redirectToTournament(tournament);
            case "tournament" -> bracketHasStage(tournament, "LAST_32")
                    ? "redirect:/view/last_32_match?tournament=" + tournament
                    : "redirect:/view/last_16_match?tournament=" + tournament;
            default        -> redirectToTournament(tournament);
        };
    }


    public Path predictionFile(String tournament, String fileName) {
        return projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve(fileName);
    }

    public Path simulationFile(String tournament, String fileName) {
        return projectRoot.resolve("data").resolve("simulations").resolve(tournament).resolve(fileName);
    }

    public Path liveSimulationFile(String tournament, String fileName) {
        return projectRoot.resolve("data").resolve("live").resolve(tournament).resolve("simulations").resolve(fileName);
    }

    public Path matchupFile(String tournament, String fileName) {
        String round = fileName.endsWith(".csv")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        return projectRoot.resolve("data").resolve("simulations").resolve(tournament)
                .resolve("matchup_paths_" + round + ".csv");
    }

    public Path matchupViewFile(String tournament, String fileName) {
        String round = fileName.endsWith(".csv")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        return projectRoot.resolve("data").resolve("simulations").resolve(tournament)
                .resolve("matchup_paths_" + round + "_view.csv");
    }

    public Path resultsFile(String tournament, String round) {
        return projectRoot.resolve("data").resolve("results").resolve(tournament).resolve(round + ".csv");
    }

    public boolean hasResultsData(String tournament, String round) {
        String resultRound = round.endsWith("_match") ? round.substring(0, round.length() - 6) : round;
        Path roundResults = projectRoot.resolve("data").resolve("results").resolve(tournament).resolve(resultRound + ".csv");
        if (generatedDataExistsQuiet(roundResults)) {
            return true;
        }
        return "groups".equals(resultRound) && generatedDataExistsQuiet(projectRoot.resolve("data").resolve("results").resolve(tournament).resolve("groups.csv"));
    }

    public Path resultsSnapshotFile(String tournament) {
        return projectRoot.resolve("data").resolve("elo").resolve("snapshots").resolve(tournament).resolve("results.csv");
    }

    public boolean hasAnyRoundResults(String tournament) {
        if (generatedDataExistsQuiet(resultsFile(tournament, "groups"))) return true;
        for (String round : RESULTS_ROUNDS) {
            if (generatedDataExistsQuiet(resultsFile(tournament, round))) return true;
        }
        return false;
    }

    public List<Map<String, String>> loadActualSnapshotRows(String tournament) throws IOException {
        Path file = resultsSnapshotFile(tournament);
        if (!Files.exists(file)) {
            return List.of();
        }
        return readCsv(file).rows();
    }

    public Set<String> roundMatchKeys(List<String> lines) {
        if (lines == null || lines.size() <= 1) {
            return Set.of();
        }
        String[] headers = lines.get(0).split(",", -1);
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        if (team1Idx < 0 || team2Idx < 0) {
            return Set.of();
        }
        EloCalculator elo = new EloCalculator();
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String team1 = elo.extractTeamName(valueAt(cols, team1Idx));
            String team2 = elo.extractTeamName(valueAt(cols, team2Idx));
            if (team1.isBlank() || team2.isBlank()) continue;
            keys.add(KnockoutViewRows.matchKey(team1, team2));
        }
        return keys;
    }

    public boolean actualRowMatchesRound(Map<String, String> row, String round, Set<String> fallbackMatchKeys) {
        String rowRound = row.getOrDefault("round", "") == null ? "" : row.getOrDefault("round", "").trim();
        if (!rowRound.isBlank()) {
            return rowRound.equalsIgnoreCase(round);
        }
        if (fallbackMatchKeys == null || fallbackMatchKeys.isEmpty()) {
            return true;
        }
        String team1 = (row.getOrDefault("team1", row.getOrDefault("home_team", "")) == null ? "" : row.getOrDefault("team1", row.getOrDefault("home_team", "")).trim());
        String team2 = (row.getOrDefault("team2", row.getOrDefault("away_team", "")) == null ? "" : row.getOrDefault("team2", row.getOrDefault("away_team", "")).trim());
        if (team1.isBlank() || team2.isBlank()) {
            return false;
        }
        return fallbackMatchKeys.contains(KnockoutViewRows.matchKey(team1, team2));
    }

    public static int roundOrder(String round) {
        String normalized = round == null ? "" : round.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "group" -> 0;
            case "last_32" -> 1;
            case "last_16" -> 2;
            case "last_8" -> 3;
            case "last_4" -> 4;
            case "third_place" -> 5;
            case "final" -> 6;
            default -> -1;
        };
    }

    public static boolean isKnockoutRound(String round) {
        String normalized = round == null ? "" : round.trim().toLowerCase(java.util.Locale.ROOT);
        return !normalized.isEmpty() && !"group".equals(normalized);
    }

    public static LocalDate parseIsoDate(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeException e) {
            return null;
        }
    }

    public Map<String, String> actualResultLabelsFromRows(List<Map<String, String>> rows,
                                                           List<Map<String, String>> allRows,
                                                           String round) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, String> labels = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String team1 = (row.getOrDefault("team1", row.getOrDefault("home_team", "")) == null ? "" : row.getOrDefault("team1", row.getOrDefault("home_team", "")).trim());
            String team2 = (row.getOrDefault("team2", row.getOrDefault("away_team", "")) == null ? "" : row.getOrDefault("team2", row.getOrDefault("away_team", "")).trim());
            if (team1.isBlank() || team2.isBlank()) continue;
            String winner = WebText.trim(row.getOrDefault("winner", row.getOrDefault("team", "")));
            if (winner.isBlank() || "Draw".equalsIgnoreCase(winner)) {
                int homeScore = WebText.parseInt(row.getOrDefault("home_score", "0"), 0);
                int awayScore = WebText.parseInt(row.getOrDefault("away_score", "0"), 0);
                if (homeScore > awayScore) {
                    winner = team1;
                } else if (awayScore > homeScore) {
                    winner = team2;
                } else {
                    winner = inferKnockoutWinnerFromLaterMatch(row, allRows, round, team1, team2);
                }
            }
            labels.put(KnockoutViewRows.matchKey(team1, team2), winner);
        }
        return labels;
    }

    public String inferKnockoutWinnerFromLaterMatch(Map<String, String> currentRow,
                                                     List<Map<String, String>> allRows,
                                                     String round,
                                                     String team1,
                                                     String team2) {
        if (!isKnockoutRound(round) || allRows == null || allRows.isEmpty()) {
            return "Draw";
        }
        LocalDate currentDate = parseIsoDate(currentRow.getOrDefault("date", ""));
        if (currentDate == null) {
            return "Draw";
        }
        boolean team1Advances = appearsInLaterRound(allRows, team1, currentDate, round);
        boolean team2Advances = appearsInLaterRound(allRows, team2, currentDate, round);
        if (team1Advances && !team2Advances) return team1;
        if (team2Advances && !team1Advances) return team2;
        return "Draw";
    }

    public boolean appearsInLaterRound(List<Map<String, String>> allRows, String team, LocalDate currentDate, String round) {
        int currentOrder = roundOrder(round);
        if (team == null || team.isBlank() || currentOrder < 0) {
            return false;
        }
        for (Map<String, String> candidate : allRows) {
            String candidateTeam1 = (candidate.getOrDefault("team1", candidate.getOrDefault("home_team", "")) == null ? "" : candidate.getOrDefault("team1", candidate.getOrDefault("home_team", "")).trim());
            String candidateTeam2 = (candidate.getOrDefault("team2", candidate.getOrDefault("away_team", "")) == null ? "" : candidate.getOrDefault("team2", candidate.getOrDefault("away_team", "")).trim());
            if (!team.equalsIgnoreCase(candidateTeam1) && !team.equalsIgnoreCase(candidateTeam2)) {
                continue;
            }
            LocalDate candidateDate = parseIsoDate(candidate.getOrDefault("date", ""));
            if (candidateDate == null || !candidateDate.isAfter(currentDate)) {
                continue;
            }
            if (roundOrder(candidate.getOrDefault("round", "") == null ? "" : candidate.getOrDefault("round", "").trim()) > currentOrder) {
                return true;
            }
        }
        return false;
    }

    public Map<String, String> loadActualResultLabels(String tournament) throws IOException {
        Path file = resultsSnapshotFile(tournament);
        if (!generatedDataStore.exists(file)) {
            return Map.of();
        }
        Map<String, String> labels = new LinkedHashMap<>();
        for (Map<String, String> row : readCsv(file).rows()) {
            String home = WebText.trim(row.getOrDefault("home_team", ""));
            String away = WebText.trim(row.getOrDefault("away_team", ""));
            if (home.isEmpty() || away.isEmpty()) continue;
            int homeScore = WebText.parseInt(row.getOrDefault("home_score", "0"), 0);
            int awayScore = WebText.parseInt(row.getOrDefault("away_score", "0"), 0);
            String label = homeScore > awayScore ? home : awayScore > homeScore ? away : "Draw";
            labels.put(KnockoutViewRows.matchKey(home, away), label);
        }
        return labels;
    }

    public Map<String, String> actualScoreMap(List<Map<String, String>> resultRows) {
        if (resultRows == null || resultRows.isEmpty()) {
            return Map.of();
        }
        Map<String, String> scores = new LinkedHashMap<>();
        for (Map<String, String> row : resultRows) {
            String team1 = (row.getOrDefault("team1", row.getOrDefault("home_team", "")) == null ? "" : row.getOrDefault("team1", row.getOrDefault("home_team", "")).trim());
            String team2 = (row.getOrDefault("team2", row.getOrDefault("away_team", "")) == null ? "" : row.getOrDefault("team2", row.getOrDefault("away_team", "")).trim());
            if (team1.isBlank() || team2.isBlank()) continue;
            String homeScore = WebText.trim(row.getOrDefault("home_score", ""));
            String awayScore = WebText.trim(row.getOrDefault("away_score", ""));
            if (homeScore.isBlank() || awayScore.isBlank()) continue;
            scores.put(KnockoutViewRows.matchKey(team1, team2), homeScore + " - " + awayScore);
        }
        return scores;
    }

    public Map<String, String> loadActualRoundResultLabels(String tournament, String round) throws IOException {
        String resultRound = round.endsWith("_match") ? round.substring(0, round.length() - 6) : round;
        List<Map<String, String>> rows = loadActualRoundResultRows(tournament, resultRound);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<String, String> labels = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String team1 = (row.getOrDefault("team1", row.getOrDefault("home_team", "")) == null ? "" : row.getOrDefault("team1", row.getOrDefault("home_team", "")).trim());
            String team2 = (row.getOrDefault("team2", row.getOrDefault("away_team", "")) == null ? "" : row.getOrDefault("team2", row.getOrDefault("away_team", "")).trim());
            if (team1.isBlank() || team2.isBlank()) continue;
            String winner = WebText.trim(row.getOrDefault("winner", ""));
            if (winner.isBlank()) {
                int homeScore = WebText.parseInt(row.getOrDefault("home_score", "0"), 0);
                int awayScore = WebText.parseInt(row.getOrDefault("away_score", "0"), 0);
                winner = homeScore > awayScore ? team1 : awayScore > homeScore ? team2 : "Draw";
            }
            labels.put(KnockoutViewRows.matchKey(team1, team2), winner);
        }
        return labels;
    }

    public List<Map<String, String>> loadActualRoundResultRows(String tournament, String round) throws IOException {
        String resultRound = round.endsWith("_match") ? round.substring(0, round.length() - 6) : round;
        Path file = resultsFile(tournament, resultRound);
        if (!generatedDataStore.exists(file)) {
            return List.of();
        }
        return readCsv(file).rows();
    }

    public List<String> buildActualRoundRows(List<String> baseLines,
                                              List<Map<String, String>> resultRows,
                                              Map<String, String> predictedWinners) {
        if (baseLines == null || baseLines.isEmpty() || resultRows == null || resultRows.isEmpty()) {
            return List.of();
        }
        String[] headers = baseLines.get(0).split(",", -1);
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int pathIdx = indexOf(headers, "path");
        int predIdx = indexOf(headers, "prediction");
        int homeScoreIdx = indexOf(headers, "home_score");
        int awayScoreIdx = indexOf(headers, "away_score");
        if (team1Idx < 0 || team2Idx < 0 || pathIdx < 0 || predIdx < 0) {
            return baseLines;
        }
        Map<String, String> actualLabels = new LinkedHashMap<>();
        Map<String, String> actualScores = new LinkedHashMap<>();
        for (Map<String, String> row : resultRows) {
            String team1 = (row.getOrDefault("team1", row.getOrDefault("home_team", "")) == null ? "" : row.getOrDefault("team1", row.getOrDefault("home_team", "")).trim());
            String team2 = (row.getOrDefault("team2", row.getOrDefault("away_team", "")) == null ? "" : row.getOrDefault("team2", row.getOrDefault("away_team", "")).trim());
            if (team1.isBlank() || team2.isBlank()) continue;
            String key = KnockoutViewRows.matchKey(team1, team2);
            String winner = WebText.trim(row.getOrDefault("winner", row.getOrDefault("team", "")));
            if (winner.isBlank()) {
                String homeScore = WebText.trim(row.getOrDefault("home_score", ""));
                String awayScore = WebText.trim(row.getOrDefault("away_score", ""));
                int home = WebText.parseInt(homeScore, 0);
                int away = WebText.parseInt(awayScore, 0);
                if (home > away) {
                    winner = team1;
                } else if (away > home) {
                    winner = team2;
                } else {
                    winner = "Draw";
                }
            }
            actualLabels.put(key, winner);
            String homeScore = WebText.trim(row.getOrDefault("home_score", ""));
            String awayScore = WebText.trim(row.getOrDefault("away_score", ""));
            if (!homeScore.isBlank() && !awayScore.isBlank()) {
                actualScores.put(key, homeScore + " - " + awayScore);
            }
        }
        List<String> out = new ArrayList<>();
        out.add(baseLines.get(0));
        Set<String> emittedActualKeys = new LinkedHashSet<>();
        EloCalculator elo = new EloCalculator();
        for (int i = 1; i < baseLines.size(); i++) {
            String line = baseLines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String team1 = elo.extractTeamName(valueAt(cols, team1Idx));
            String team2 = elo.extractTeamName(valueAt(cols, team2Idx));
            if (team1.isBlank() || team2.isBlank()) continue;
            String key = KnockoutViewRows.matchKey(team1, team2);
            if (!emittedActualKeys.add(key)) {
                continue;
            }
            String actualWinner = actualLabels.get(key);
            if (actualWinner == null || actualWinner.isBlank()) continue;
            String[] actualCols = cols.clone();
            actualCols[pathIdx] = "results";
            actualCols[predIdx] = actualWinner;
            if (homeScoreIdx >= 0 && awayScoreIdx >= 0) {
                String score = actualScores.getOrDefault(key, "");
                if (!score.isBlank()) {
                    String[] scoreParts = score.split(" - ", 2);
                    if (scoreParts.length == 2) {
                        actualCols[homeScoreIdx] = scoreParts[0];
                        actualCols[awayScoreIdx] = scoreParts[1];
                    }
                }
            }
            out.add(String.join(",", actualCols));
        }
        return out;
    }

    public Set<String> loadActualAdvancingTeams(String tournament, String round) throws IOException {
        String resultRound = round.endsWith("_match") ? round.substring(0, round.length() - 6) : round;
        Path file = projectRoot.resolve("data").resolve("results").resolve(tournament).resolve(resultRound + ".csv");
        if (!generatedDataStore.exists(file)) {
            return Set.of();
        }
        List<Map<String, String>> rows = readCsv(file).rows();
        if (rows.isEmpty()) {
            return Set.of();
        }
        Set<String> teams = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            String team = WebText.trim(row.getOrDefault("team", ""));
            if (team.isBlank()) {
                team = WebText.trim(row.getOrDefault("winner", ""));
            }
            if (!team.isBlank() && !"Draw".equalsIgnoreCase(team)) {
                teams.add(team);
            }
        }
        return teams;
    }

    public Set<String> actualAdvanceTeams(List<String> lines) {
        return ResultLineService.actualAdvanceTeams(lines);
    }

    public List<String> buildActualOnlyRows(List<String> lines, Map<String, String> actualResultLabels, Map<String, String> predictedWinners) {
        return ResultLineService.buildActualOnlyRows(lines, actualResultLabels);
    }

    public List<String> mergeViewLines(List<String> baseLines, List<String> overlayLines) {
        return ResultLineService.mergeViewLines(baseLines, overlayLines);
    }

    public Map<String, String> predictedWinnersByMatch(List<String> lines) {
        return ResultLineService.predictedWinnersByMatch(lines);
    }


    public List<String> allStageTeamNames(List<String> lines) {
        return ViewLineService.allStageTeamNames(lines);
    }
    public List<String> filterViewLines(List<String> lines, String pathFilter, String teamFilter) {
        return ViewLineService.filterViewLines(lines, pathFilter, teamFilter);
    }

    public List<String> paginateLines(List<String> lines, int page, int pageSize) {
        return ViewLineService.paginateLines(lines, page, pageSize);
    }

    public String buildPageNavigationHtml(String tournament, String round, boolean actualMode, int currentPage, int pageCount) {
        return ViewLineService.buildPageNavigationHtml(tournament, round, actualMode, currentPage, pageCount);
    }

    public static int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    public static String valueAt(String[] cols, int idx) {
        return idx >= 0 && idx < cols.length ? cols[idx].trim() : "";
    }

    public String renderGroupMatches(String tournament, List<Map<String, String>> scorelineRows,
                                      Map<String, EloBreakdown> eloBreakdowns, boolean actualMode) throws IOException {
        if (scorelineRows.isEmpty()) return "";
        Map<String, String[]> matches = new java.util.TreeMap<>();
        Map<String, Map<String, Integer>> outcomes = new LinkedHashMap<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Map<String, String> row : scorelineRows) {
            if (!"groups".equals(row.getOrDefault("stage", ""))) continue;
            String matchId = row.getOrDefault("match_id", "");
            String team1 = row.getOrDefault("team1", "");
            String team2 = row.getOrDefault("team2", "");
            String key = matchId + "|" + team1 + "|" + team2;
            int count = WebText.parseInt(row.getOrDefault("count", "0"), 0);
            matches.putIfAbsent(key, new String[]{matchId, team1, team2});
            outcomes.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                    .merge(row.getOrDefault("winner", "Draw"), count, Integer::sum);
            totals.merge(key, count, Integer::sum);
        }
        Map<String, Map<String, String>> existingResults = loadGroupMatchResultsByMatchId(tournament);
        List<String> lines = new ArrayList<>();
        lines.add("match_id,team1,team2,prediction,path,home_score,away_score");
        for (Map.Entry<String, String[]> entry : matches.entrySet()) {
            String key = entry.getKey();
            String[] match = entry.getValue();
            Map.Entry<String, Integer> likely = outcomes.getOrDefault(key, Map.of()).entrySet().stream()
                    .max(Map.Entry.comparingByValue()).orElse(Map.entry("Draw", 0));
            int pct = totals.getOrDefault(key, 0) == 0 ? 0
                    : (int) Math.round(likely.getValue() * 100.0 / totals.get(key));
            lines.add(String.join(",", match[0], match[1], match[2], likely.getKey() + " (" + pct + "%)", "predicted", "", ""));
            if (actualMode) {
                Map<String, String> actual = existingResults.getOrDefault(match[0], Map.of());
                String winner = WebText.trim(actual.getOrDefault("winner", ""));
                if (!winner.isBlank()) {
                    lines.add(String.join(",",
                            match[0],
                            match[1],
                            match[2],
                            winner,
                            "results",
                            WebText.trim(actual.getOrDefault("home_score", "")),
                            WebText.trim(actual.getOrDefault("away_score", ""))));
                }
            }
        }
        List<Map<String, String>> actualResultRows = new ArrayList<>(existingResults.values());
        Map<String, String> actualLabels = new LinkedHashMap<>();
        for (Map<String, String> row : actualResultRows) {
            String team1 = WebText.trim(row.getOrDefault("team1", row.getOrDefault("home_team", "")));
            String team2 = WebText.trim(row.getOrDefault("team2", row.getOrDefault("away_team", "")));
            if (team1.isBlank() || team2.isBlank()) continue;
            String winner = WebText.trim(row.getOrDefault("winner", ""));
            if (winner.isBlank()) winner = "Draw";
            actualLabels.put(KnockoutViewRows.matchKey(team1, team2), winner);
        }
        HtmlReporter reporter = new HtmlReporter().withConfig(predictionConfig)
                .withActualMode(actualMode)
                .withActualResultScores(actualScoreMap(actualResultRows))
                .withActualResultLabels(actualLabels)
                .withMatchupSimulationRuns(SimulationViewDataService.simulationMatchupRunsMap(scorelineRows, "groups"));
        reporter.printMatchups("Group Stage Matches", lines, new EloCalculator(), null, Map.of(), eloBreakdowns);
        return reporter.getHtml();
    }

    public Map<String, Map<String, String>> groupSimulationByTeam(String tournament) throws IOException {
        Path simulation = simulationFile(tournament, "simulation_groups.csv");
        if (!generatedDataStore.exists(simulation)) return Map.of();
        Map<String, Map<String, String>> byTeam = new LinkedHashMap<>();
        for (Map<String, String> row : readCsv(simulation).rows()) {
            String team = row.getOrDefault("team", "").trim();
            if (!team.isBlank()) byTeam.put(team, row);
        }
        return byTeam;
    }

    public static List<String> validateGroupPicks(List<Map<String, String>> rows) {
        Map<String, List<Map<String, String>>> byGroup = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String group = row.getOrDefault("group", "?");
            byGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(row);
        }
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, String>>> entry : byGroup.entrySet()) {
            String group = entry.getKey();
            List<Map<String, String>> groupRows = entry.getValue();
            long winners = groupRows.stream().filter(r -> "yes".equals(r.get("group_winner"))).count();
            long runnersUp = groupRows.stream().filter(r -> "yes".equals(r.get("runner_up"))).count();
            boolean overlap = groupRows.stream().anyMatch(
                r -> "yes".equals(r.get("group_winner")) && "yes".equals(r.get("runner_up")));
            if (winners != 1) errors.add("Group " + group + ": must have exactly 1 Group Winner (yes) — found " + winners);
            if (runnersUp != 1) errors.add("Group " + group + ": must have exactly 1 Runner-up (yes) — found " + runnersUp);
            if (overlap) errors.add("Group " + group + ": the same team cannot be both Group Winner and Runner-up");
        }
        return errors;
    }

}

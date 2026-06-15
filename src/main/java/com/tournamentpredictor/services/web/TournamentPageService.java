package com.tournamentpredictor.services.web;

import com.tournamentpredictor.model.common.CsvData;
import com.tournamentpredictor.model.history.HistoricalComparison;
import com.tournamentpredictor.model.history.HistoricalMatchView;
import com.tournamentpredictor.model.history.HistoricalProfile;
import com.tournamentpredictor.model.tournament.StageStatus;
import com.tournamentpredictor.model.tournament.StageView;
import com.tournamentpredictor.model.tournament.TournamentSummary;
import com.tournamentpredictor.services.calculation.ExpectedGoalsCalculator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TournamentPageService {
    private final WebControllerService web;

    public TournamentPageService(WebControllerService web) {
        this.web = web;
    }

    public List<TournamentSummary> scanTournaments() throws IOException {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Path predictionsRoot = web.projectRoot.resolve("data").resolve("predictions");
        if (Files.exists(predictionsRoot)) {
            try (Stream<Path> stream = Files.list(predictionsRoot)) {
                stream.filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .forEach(names::add);
            }
        }
        if (names.isEmpty()) return Collections.emptyList();
        return names.stream()
                .sorted()
                .map(name -> new TournamentSummary(name, web.describeCurrentStage(name), web.completedStepCount(name),
                        WebControllerService.HISTORICAL_COMPARISONS.contains(name)))
                .collect(Collectors.toList());
    }

    public HistoricalComparison buildHistoricalComparison(String tournament) throws IOException {
        CsvData groupData = web.readCsv(web.predictionFile(tournament, "groups.csv"));
        CsvData actualData = web.readCsv(web.projectRoot.resolve("data/backtests").resolve(tournament).resolve("actual_results.csv"));
        Map<String, HistoricalProfile> profiles = new LinkedHashMap<>();
        for (Map<String, String> row : groupData.rows()) {
            String team = row.getOrDefault("team", "");
            profiles.put(team, new HistoricalProfile(WebText.parseInt(row.getOrDefault("elo_ranking", "0"), 0),
                    WebText.parseInt(row.getOrDefault("attack_quality", "0"), 0),
                    WebText.parseInt(row.getOrDefault("defence_quality", "0"), 0)));
        }
        ExpectedGoalsCalculator calculator = new ExpectedGoalsCalculator(web.predictionConfig.getEloScaleDivisor(),
                2.60, web.predictionConfig.getGoalDiffPer400Elo(), web.predictionConfig.getExpectedGoalsMultiplier());
        List<HistoricalMatchView> matches = new ArrayList<>();
        int correct = 0;
        for (Map<String, String> row : actualData.rows()) {
            String team1 = row.getOrDefault("team1", ""), team2 = row.getOrDefault("team2", "");
            HistoricalProfile p1 = profiles.get(team1), p2 = profiles.get(team2);
            if (p1 == null || p2 == null) continue;
            var projection = calculator.project(team1, team2, p1.elo(), p2.elo(),
                    p1.attack(), p1.defence(), p2.attack(), p2.defence());
            int goals1 = WebText.parseInt(row.getOrDefault("team1_goals", "0"), 0);
            int goals2 = WebText.parseInt(row.getOrDefault("team2_goals", "0"), 0);
            String predicted = WebText.outcome(projection.exactTeam1WinProbability(), projection.exactDrawProbability(),
                    projection.exactTeam2WinProbability());
            String actual = goals1 > goals2 ? "Home" : goals2 > goals1 ? "Away" : "Draw";
            boolean matchCorrect = predicted.equals(actual);
            if (matchCorrect) correct++;
            matches.add(new HistoricalMatchView(team1, team2, goals1 + "-" + goals2, predicted, actual,
                    WebText.percent(projection.exactTeam1WinProbability()), WebText.percent(projection.exactDrawProbability()),
                    WebText.percent(projection.exactTeam2WinProbability()), projection.expectedGoalsText(), matchCorrect));
        }
        return new HistoricalComparison(tournament, web.displayTournament(tournament), correct, matches.size(), matches);
    }

    public List<StageView> buildStages(String tournament) {
        boolean startExists = web.generatedDataExistsQuiet(web.predictionFile(tournament, "start.csv"));
        boolean snapshotExists = snapshotExists(tournament);
        boolean groupsExists = web.generatedDataExistsQuiet(web.predictionFile(tournament, "groups.csv"));
        boolean groupSimulationExists = web.generatedDataExistsQuiet(web.simulationFile(tournament, "simulation_groups.csv"))
                && web.generatedDataExistsQuiet(web.simulationFile(tournament, "simulation_group_routes.csv"));
        boolean last32MatchExists = web.generatedDataExistsQuiet(web.matchupFile(tournament, "last_32.csv"));
        boolean last16MatchExists = web.generatedDataExistsQuiet(web.matchupFile(tournament, "last_16.csv"));
        boolean last8MatchExists = web.generatedDataExistsQuiet(web.matchupFile(tournament, "last_8.csv"));
        boolean last4MatchExists = web.generatedDataExistsQuiet(web.matchupFile(tournament, "last_4.csv"));
        boolean finalMatchExists = web.generatedDataExistsQuiet(web.matchupFile(tournament, "final.csv"));
        boolean startsAtLast16 = !web.bracketHasStage(tournament, "LAST_32")
                && web.bracketHasStage(tournament, "LAST_16");
        boolean knockoutSimulationExists = web.generatedDataExistsQuiet(web.simulationFile(tournament,
                startsAtLast16 ? "simulation_last_16.csv" : "simulation_last_32.csv"));
        boolean knockoutComplete = startsAtLast16 ? knockoutSimulationExists : finalMatchExists;

        List<StageView> stages = new ArrayList<>();
        stages.add(new StageView("Pre-Tournament Snapshot", snapshotDescription(tournament, snapshotExists),
                snapshotExists ? new StageStatus("❄", "Frozen", "info") : web.status(false, startExists),
                startExists, "snapshot-refresh", "Refresh", "btn-outline-secondary",
                false, null, false, null, false, null, null, false, null, false, null));
        stages.add(new StageView("Team Setup", "Edit teams and tournament inputs, then save the setup and generate the group rankings.",
                web.status(groupsExists, startExists),
                startExists && !groupsExists, "start", "Save", "btn-primary",
                true, "/edit/start?tournament=" + tournament,
                startExists, "/view/start?tournament=" + tournament,
                false, null, null, false, null, groupsExists, "/reset/groups?tournament=" + tournament));
        stages.add(new StageView("Group Stage", "Simulate every group fixture and save every possible opening knockout route.",
                web.status(groupSimulationExists, groupsExists && !groupSimulationExists),
                groupsExists, "group-simulation", groupSimulationExists ? "Rerun" : "Run",
                groupSimulationExists ? "btn-outline-primary" : "btn-primary",
                groupSimulationExists, "/edit/group-results?tournament=" + tournament,
                groupSimulationExists, "/view/groups?tournament=" + tournament,
                false, null, null, false, null, groupSimulationExists, "/reset/group-simulation?tournament=" + tournament));
        boolean last32Ready = !startsAtLast16 && groupSimulationExists;
        boolean last16Ready = startsAtLast16 ? groupSimulationExists : last32MatchExists;
        boolean last8Ready = last16MatchExists;
        boolean last4Ready = last8MatchExists;
        boolean finalReady = last4MatchExists;
        if (!startsAtLast16) {
            stages.add(resultStage("Last 32", "Inspect predicted and alternate Last 32 routes.",
                    last32MatchExists, last32Ready, "/view/last_32_match?tournament=" + tournament,
                    last32Ready ? "/edit/results?tournament=" + tournament + "&round=last_32" : null,
                    last32Ready, "last_32", last32MatchExists, "/reset/last_32?tournament=" + tournament));
        }
        stages.add(resultStage("Last 16", "Inspect predicted and alternate Last 16 routes.",
                startsAtLast16 ? knockoutSimulationExists : last16MatchExists,
                last16Ready, "/view/last_16_match?tournament=" + tournament,
                last16Ready ? "/edit/results?tournament=" + tournament + "&round=last_16" : null,
                last16Ready, "last_16", (startsAtLast16 ? knockoutSimulationExists : last16MatchExists), "/reset/last_16?tournament=" + tournament));
        stages.add(resultStage("Quarter Finals", "Inspect predicted and alternate quarter-final routes.",
                last8MatchExists, last8Ready, "/view/last_8_match?tournament=" + tournament,
                last8Ready ? "/edit/results?tournament=" + tournament + "&round=last_8" : null,
                last8Ready, "last_8", last8MatchExists, "/reset/last_8?tournament=" + tournament));
        stages.add(resultStage("Semi Finals", "Inspect predicted and alternate semi-final routes.",
                last4MatchExists, last4Ready, "/view/last_4_match?tournament=" + tournament,
                last4Ready ? "/edit/results?tournament=" + tournament + "&round=last_4" : null,
                last4Ready, "last_4", last4MatchExists, "/reset/last_4?tournament=" + tournament));
        stages.add(resultStage("Final", "Inspect the final matchup and champion probabilities.",
                finalMatchExists, finalReady, "/view/final_match?tournament=" + tournament,
                finalReady ? "/edit/results?tournament=" + tournament + "&round=final" : null,
                finalReady, "final", finalMatchExists, "/reset/final?tournament=" + tournament));
        return stages;
    }

    public StageView resultStage(String label, String description, boolean complete, String viewUrl) {
        return resultStage(label, description, complete, false, viewUrl, null, false, null, false, null);
    }

    public StageView resultStage(String label, String description, boolean complete, String viewUrl, String editUrl) {
        return resultStage(label, description, complete, false, viewUrl, editUrl, false, null, false, null);
    }

    public StageView resultStage(String label, String description, boolean complete, boolean ready, String viewUrl, String editUrl, boolean canRun, String runMode, boolean canReset, String resetUrl) {
        String runLabel = canRun ? (complete ? "Rerun" : "Run") : null;
        String runButtonClass = complete ? "btn-outline-primary" : "btn-primary";
        boolean canEdit = complete && editUrl != null;
        boolean showReset = complete && canReset;
        return new StageView(label, description, web.status(complete, ready),
                canRun, runMode, runLabel, runButtonClass, canEdit, editUrl,
                complete, viewUrl, false, null, null,
                false, null, showReset, resetUrl);
    }

    public String predictionSnapshotNote(String tournament) {
        return snapshotNote(tournament, "Prediction data saved");
    }

    public String actualSnapshotNote(String tournament) {
        return snapshotNote(tournament, "Actual data saved");
    }

    public String snapshotNote(String tournament, String prefix) {
        try {
            Properties metadata = loadSnapshotMetadata(tournament);
            if (metadata.isEmpty()) return "";
            String createdAt = metadata.getProperty("created_at", "");
            String teamCount = metadata.getProperty("team_count", "");
            if (createdAt.isBlank()) {
                return "";
            }
            String saved = DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(createdAt));
            StringBuilder description = new StringBuilder(prefix).append(' ').append(saved);
            if (!teamCount.isBlank()) {
                description.append(" for ").append(teamCount).append(" teams");
            }
            description.append('.');
            return description.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public boolean tournamentHasStarted(String tournament) {
        try {
            Properties metadata = loadSnapshotMetadata(tournament);
            if (metadata.isEmpty()) return false;
            String startDate = metadata.getProperty("tournament_start_date", "");
            if (startDate.isBlank()) {
                return false;
            }
            LocalDate tournamentStart = LocalDate.parse(startDate);
            return !LocalDate.now(ZoneId.systemDefault()).isBefore(tournamentStart);
        } catch (Exception e) {
            return false;
        }
    }

    public String snapshotDescription(String tournament, boolean snapshotExists) {

        String base = "Save the current team ratings and recent results used for this tournament, so future data updates do not change these predictions.";
        if (!snapshotExists) {
            return base;
        }
        try {
            Properties metadata = loadSnapshotMetadata(tournament);
            if (metadata.isEmpty()) return base;
            String createdAt = metadata.getProperty("created_at", "");
            String teamCount = metadata.getProperty("team_count", "");
            String startDate = metadata.getProperty("tournament_start_date", "");
            if (createdAt.isBlank()) {
                return base;
            }
            String saved = DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(createdAt));
            StringBuilder description = new StringBuilder("Prediction data saved ").append(saved);
            if (!teamCount.isBlank()) {
                description.append(" for ").append(teamCount).append(" teams");
            }
            description.append(".\n");
            if (!startDate.isBlank()) {
                String cutoff = LocalDate.parse(startDate).format(DateTimeFormatter.ofPattern("d MMM yyyy"));
                description.append("Results from ").append(cutoff).append(" onward are excluded.");
            }
            description.append(" Refresh before the tournament to include newly played warm-up matches.");
            return description.toString();
        } catch (Exception e) {
            return base;
        }
    }

    private boolean snapshotExists(String tournament) {
        Path teams = web.projectRoot.resolve("data").resolve("elo").resolve("snapshots")
                .resolve(tournament).resolve("teams.csv");
        return Files.exists(teams);
    }

    private Properties loadSnapshotMetadata(String tournament) throws IOException {
        Path metadataPath = web.projectRoot.resolve("data").resolve("elo").resolve("snapshots")
                .resolve(tournament).resolve("metadata.properties");
        if (Files.exists(metadataPath)) {
            Properties metadata = new Properties();
            try (Reader reader = Files.newBufferedReader(metadataPath)) {
                metadata.load(reader);
            }
            return metadata;
        }
        return new Properties();
    }

}

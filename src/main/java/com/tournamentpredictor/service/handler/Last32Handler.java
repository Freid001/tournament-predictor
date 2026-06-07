package com.tournamentpredictor.service.handler;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.builder.Last16LineBuilder;
import com.tournamentpredictor.service.builder.Last32LineBuilder;
import com.tournamentpredictor.service.mapper.DisagreeMapMapper;
import com.tournamentpredictor.service.util.ConsoleReporter;
import com.tournamentpredictor.service.util.CsvHelper;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.PredictionScorer;
import com.tournamentpredictor.service.util.TeamEloSnapshot;
import com.tournamentpredictor.service.validator.PredictionsFileValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Last32Handler {
    private final CsvLoader loader;
    private final Path projectRoot;
    private final CsvHelper csvHelper;
    private final PredictionsFileValidator predictionsFileValidator;
    private final DisagreeMapMapper disagreeMapMapper;
    private final EloCalculator eloCalculator;
    private final PredictionScorer predictionScorer;
    private final Last32LineBuilder last32LineBuilder;
    private final Last16LineBuilder last16LineBuilder;
    private final ConsoleReporter consoleReporter;

    public Last32Handler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                         PredictionsFileValidator predictionsFileValidator, DisagreeMapMapper disagreeMapMapper,
                         EloCalculator eloCalculator, PredictionScorer predictionScorer,
                         Last32LineBuilder last32LineBuilder, Last16LineBuilder last16LineBuilder,
                         ConsoleReporter consoleReporter) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.csvHelper = csvHelper;
        this.predictionsFileValidator = predictionsFileValidator;
        this.disagreeMapMapper = disagreeMapMapper;
        this.eloCalculator = eloCalculator;
        this.predictionScorer = predictionScorer;
        this.last32LineBuilder = last32LineBuilder;
        this.last16LineBuilder = last16LineBuilder;
        this.consoleReporter = consoleReporter;
    }

    public void handle(String tournament) throws IOException {
        Path simulationDir = projectRoot.resolve("data").resolve("simulations").resolve(tournament);
        Path predictionDir = projectRoot.resolve("data").resolve("predictions").resolve(tournament);
        Path matchupFile = simulationDir.resolve("matchup_paths_last_32.csv");
        if (csvHelper.isLocked(matchupFile)) {
            System.out.println("  🔒 Output already exists: " + matchupFile + " — delete to re-run");
            Map<String, String> last32Odds = loader.loadOdds(tournament, "last_16");
            consoleReporter.printMatchups("Last 32 matchups", Files.readAllLines(matchupFile), eloCalculator, predictionDir.resolve("last_16.csv"), last32Odds);
            return;
        }

        Path overrideFile = predictionDir.resolve("last_32.csv");
        if (!Files.exists(overrideFile)) {
            throw new IOException("Predictions file not found: " + overrideFile + ". Run mode=groups first.");
        }
        predictionsFileValidator.validatePredictionsFile(overrideFile);
        Map<String, String> disagreeMap = disagreeMapMapper.loadDisagreeMap(overrideFile);

        Map<String, String> groups = loader.loadGroups(tournament);
        Map<String, String> groupWinner = loader.loadGroupWinner(tournament);
        Map<String, String> runnerUp = loader.loadRunnerUp(tournament);
        Map<String, String> thirdPlace = loader.loadThirdPlace(tournament);
        Map<String, Integer> eloRatings = loader.loadTournamentElo(tournament);
        Map<String, TeamEloSnapshot> snapshots = loader.loadTeamSnapshots(tournament);
        List<com.tournamentpredictor.loader.CsvLoader.BracketEntry> brackets = loader.loadBrackets(tournament);

        Files.createDirectories(simulationDir);
        List<String> allLines = last32LineBuilder.buildLast32Lines(groups, groupWinner, runnerUp, thirdPlace,
                eloRatings, brackets, snapshots);
        predictionScorer.setSnapshots(snapshots);
        List<String> detailed = predictionScorer.scoreLines(allLines, disagreeMap);
        List<String> sortedDetailed = csvHelper.sortGroupsPrimaryFirst(detailed);
        Files.write(simulationDir.resolve("matchup_paths_last_32.csv"), sortedDetailed);

        List<String> allLast16 = last16LineBuilder.buildLast16Lines(groups, groupWinner, runnerUp, thirdPlace,
                eloRatings, brackets, detailed, snapshots);
        // Preserve every plausible Last 32 winner in the staged UI. The model-selected
        // winner keeps its path; the predicted loser continues as an upset route.
        Files.write(predictionDir.resolve("last_16.csv"), allLast16);
        Map<String, String> last32Odds = loader.loadOdds(tournament, "last_16");
        consoleReporter.printMatchups("Last 32 matchups", sortedDetailed, eloCalculator, predictionDir.resolve("last_16.csv"), last32Odds);
    }
}

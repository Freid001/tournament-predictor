package com.tournamentpredictor.service.handler;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.builder.Last4LineBuilder;
import com.tournamentpredictor.service.builder.Last8LineBuilder;
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
import java.util.List;
import java.util.Map;

public class Last8Handler {
    private final CsvLoader loader;
    private final Path projectRoot;
    private final CsvHelper csvHelper;
    private final PredictionsFileValidator predictionsFileValidator;
    private final DisagreeMapMapper disagreeMapMapper;
    private final EloCalculator eloCalculator;
    private final PredictionScorer predictionScorer;
    private final Last8LineBuilder last8LineBuilder;
    private final Last4LineBuilder last4LineBuilder;
    private final ConsoleReporter consoleReporter;

    public Last8Handler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                        PredictionsFileValidator predictionsFileValidator, DisagreeMapMapper disagreeMapMapper,
                        EloCalculator eloCalculator, PredictionScorer predictionScorer,
                        Last8LineBuilder last8LineBuilder,
                        Last4LineBuilder last4LineBuilder, ConsoleReporter consoleReporter) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.csvHelper = csvHelper;
        this.predictionsFileValidator = predictionsFileValidator;
        this.disagreeMapMapper = disagreeMapMapper;
        this.eloCalculator = eloCalculator;
        this.predictionScorer = predictionScorer;
        this.last8LineBuilder = last8LineBuilder;
        this.last4LineBuilder = last4LineBuilder;
        this.consoleReporter = consoleReporter;
    }

    public void handle(String tournament) throws IOException {
        Path simulationDir = projectRoot.resolve("data").resolve("simulations").resolve(tournament);
        Path predictionDir = projectRoot.resolve("data").resolve("predictions").resolve(tournament);
        Path matchupFile = simulationDir.resolve("matchup_paths_last_8.csv");
        Map<String, String> qfOdds = loader.loadOdds(tournament, "last_4");
        if (csvHelper.isLocked(matchupFile)) {
            System.out.println("  🔒 Output already exists: " + matchupFile + " — delete to re-run");
            consoleReporter.printMatchups("Quarter-final matchups", Files.readAllLines(matchupFile), eloCalculator, predictionDir.resolve("last_4.csv"), qfOdds);
            return;
        }

        Path overrideFile = predictionDir.resolve("last_8.csv");
        if (!Files.exists(overrideFile)) {
            throw new IOException("Predictions file not found: " + overrideFile + ". Run mode=last_16 first.");
        }
        predictionsFileValidator.validatePredictionsFile(overrideFile);
        Map<String, String> disagreeMap = disagreeMapMapper.loadDisagreeMap(overrideFile);

        Path last16File = projectRoot.resolve("data").resolve("simulations").resolve(tournament).resolve("matchup_paths_last_16.csv");
        if (!Files.exists(last16File)) {
            throw new IOException("last_16 matchups not found: " + last16File + ". Run mode=last_16 first.");
        }
        List<String> last16Rows = Files.readAllLines(last16File);
        Map<String, Integer> eloRatings = loader.loadTournamentElo(tournament);
        Map<String, TeamEloSnapshot> snapshots = loader.loadTeamSnapshots(tournament);
        List<com.tournamentpredictor.loader.CsvLoader.BracketEntry> brackets = loader.loadBrackets(tournament);

        Files.createDirectories(simulationDir);
        List<String> allLines = last8LineBuilder.buildLast8Lines(eloRatings, brackets, last16Rows, snapshots);
        predictionScorer.setSnapshots(snapshots);
        List<String> output = predictionScorer.scoreLines(allLines, disagreeMap);
        List<String> sortedOutput = csvHelper.sortGroupsPrimaryFirst(output);
        Files.write(simulationDir.resolve("matchup_paths_last_8.csv"), sortedOutput);
        generateLastSfPredictions(tournament, eloRatings, snapshots, brackets, output);
        consoleReporter.printMatchups("Quarter-final matchups", sortedOutput, eloCalculator, predictionDir.resolve("last_4.csv"), qfOdds);
    }

    private void generateLastSfPredictions(String tournament, Map<String, Integer> eloRatings,
                                           Map<String, TeamEloSnapshot> snapshots,
                                           List<com.tournamentpredictor.loader.CsvLoader.BracketEntry> brackets,
                                           List<String> last8Output) throws IOException {
        List<String> allLines = last4LineBuilder.buildLast4Lines(eloRatings, brackets, last8Output, snapshots);
        List<String> output = allLines;
        Path predictionDir = projectRoot.resolve("data").resolve("predictions").resolve(tournament);
        Files.createDirectories(predictionDir);
        Files.write(predictionDir.resolve("last_4.csv"), output);
    }
}

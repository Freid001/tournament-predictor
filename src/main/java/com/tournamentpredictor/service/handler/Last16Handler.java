package com.tournamentpredictor.service.handler;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.builder.Last16LineBuilder;
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

public class Last16Handler {
    private final CsvLoader loader;
    private final Path projectRoot;
    private final CsvHelper csvHelper;
    private final PredictionsFileValidator predictionsFileValidator;
    private final DisagreeMapMapper disagreeMapMapper;
    private final EloCalculator eloCalculator;
    private final PredictionScorer predictionScorer;
    private final Last16LineBuilder last16LineBuilder;
    private final Last8LineBuilder last8LineBuilder;
    private final ConsoleReporter consoleReporter;

    public Last16Handler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                         PredictionsFileValidator predictionsFileValidator, DisagreeMapMapper disagreeMapMapper,
                         EloCalculator eloCalculator, PredictionScorer predictionScorer,
                         Last16LineBuilder last16LineBuilder, Last8LineBuilder last8LineBuilder,
                         ConsoleReporter consoleReporter) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.csvHelper = csvHelper;
        this.predictionsFileValidator = predictionsFileValidator;
        this.disagreeMapMapper = disagreeMapMapper;
        this.eloCalculator = eloCalculator;
        this.predictionScorer = predictionScorer;
        this.last16LineBuilder = last16LineBuilder;
        this.last8LineBuilder = last8LineBuilder;
        this.consoleReporter = consoleReporter;
    }

    public void handle(String tournament) throws IOException {
        Path matchupDir = projectRoot.resolve("data").resolve("matchups").resolve(tournament);
        Path predictionDir = projectRoot.resolve("data").resolve("predictions").resolve(tournament);
        Path matchupFile = matchupDir.resolve("last_16.csv");
        if (csvHelper.isLocked(matchupFile)) {
            System.out.println("  [locked] Output already exists: " + matchupFile + " — delete to re-run");
            Map<String, String> last16Odds = loader.loadOdds(tournament, "last_8");
            consoleReporter.printMatchups("Last 16 matchups", Files.readAllLines(matchupFile), eloCalculator, predictionDir.resolve("last_8.csv"), last16Odds);
            return;
        }

        Path overrideFile = predictionDir.resolve("last_16.csv");
        if (!Files.exists(overrideFile)) {
            throw new IOException("Predictions file not found: " + overrideFile + ". Run mode=last_32 first.");
        }
        predictionsFileValidator.validatePredictionsFile(overrideFile);
        Map<String, String> disagreeMap = disagreeMapMapper.loadDisagreeMap(overrideFile);

        Path last32File = projectRoot.resolve("data").resolve("matchups").resolve(tournament).resolve("last_32.csv");
        if (!Files.exists(last32File)) {
            throw new IOException("last_32 matchups not found: " + last32File + ". Run mode=last_32 first.");
        }
        List<String> last32Rows = Files.readAllLines(last32File);
        Map<String, String> groups = loader.loadGroups(tournament);
        Map<String, String> groupWinner = loader.loadGroupWinner(tournament);
        Map<String, String> runnerUp = loader.loadRunnerUp(tournament);
        Map<String, String> thirdPlace = loader.loadThirdPlace(tournament);
        Map<String, Integer> eloRatings = loader.loadTournamentElo(tournament);
        Map<String, TeamEloSnapshot> snapshots = loader.loadTeamSnapshots(tournament);
        List<com.tournamentpredictor.loader.CsvLoader.BracketEntry> brackets = loader.loadBrackets(tournament);

        Files.createDirectories(matchupDir);
        List<String> lines = last16LineBuilder.buildLast16Lines(groups, groupWinner, runnerUp, thirdPlace,
                eloRatings, brackets, last32Rows, snapshots);
        predictionScorer.setSnapshots(snapshots);
        List<String> output = predictionScorer.scoreLines(lines, disagreeMap);
        List<String> sortedOutput = csvHelper.sortGroupsPrimaryFirst(output);
        Files.write(matchupDir.resolve("last_16.csv"), sortedOutput);
        generateLast8Predictions(tournament, eloRatings, snapshots, brackets, output);
        Map<String, String> last16Odds = loader.loadOdds(tournament, "last_8");
        consoleReporter.printMatchups("Last 16 matchups", sortedOutput, eloCalculator, predictionDir.resolve("last_8.csv"), last16Odds);
    }

    private void generateLast8Predictions(String tournament, Map<String, Integer> eloRatings,
                                          Map<String, TeamEloSnapshot> snapshots,
                                          List<com.tournamentpredictor.loader.CsvLoader.BracketEntry> brackets,
                                          List<String> last16Output) throws IOException {
        List<String> allLines = last8LineBuilder.buildLast8Lines(eloRatings, brackets, last16Output, snapshots);
        List<String> output = csvHelper.filterPrimaryOnly(allLines);
        Path predictionDir = projectRoot.resolve("data").resolve("predictions").resolve(tournament);
        Files.createDirectories(predictionDir);
        Files.write(predictionDir.resolve("last_8.csv"), output);
    }
}

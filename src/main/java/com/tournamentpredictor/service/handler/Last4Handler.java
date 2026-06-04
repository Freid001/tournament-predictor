package com.tournamentpredictor.service.handler;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.builder.FinalLineBuilder;
import com.tournamentpredictor.service.builder.Last4LineBuilder;
import com.tournamentpredictor.service.mapper.DisagreeMapMapper;
import com.tournamentpredictor.service.util.ConsoleReporter;
import com.tournamentpredictor.service.util.CsvHelper;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.PredictionScorer;
import com.tournamentpredictor.service.util.QualificationFormCalculator;
import com.tournamentpredictor.service.validator.PredictionsFileValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Last4Handler {
    private final CsvLoader loader;
    private final Path projectRoot;
    private final CsvHelper csvHelper;
    private final PredictionsFileValidator predictionsFileValidator;
    private final DisagreeMapMapper disagreeMapMapper;
    private final EloCalculator eloCalculator;
    private final PredictionScorer predictionScorer;
    private final Last4LineBuilder last4LineBuilder;
    private final FinalLineBuilder finalLineBuilder;
    private final ConsoleReporter consoleReporter;

    public Last4Handler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                        PredictionsFileValidator predictionsFileValidator, DisagreeMapMapper disagreeMapMapper,
                        EloCalculator eloCalculator, PredictionScorer predictionScorer,
                        Last4LineBuilder last4LineBuilder,
                        FinalLineBuilder finalLineBuilder, ConsoleReporter consoleReporter) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.csvHelper = csvHelper;
        this.predictionsFileValidator = predictionsFileValidator;
        this.disagreeMapMapper = disagreeMapMapper;
        this.eloCalculator = eloCalculator;
        this.predictionScorer = predictionScorer;
        this.last4LineBuilder = last4LineBuilder;
        this.finalLineBuilder = finalLineBuilder;
        this.consoleReporter = consoleReporter;
    }

    public void handle(String tournament) throws IOException {
        Path matchupDir = projectRoot.resolve("data").resolve("matchups").resolve(tournament);
        Path predictionDir = projectRoot.resolve("data").resolve("predictions").resolve(tournament);
        Path matchupFile = matchupDir.resolve("last_4.csv");
        Map<String, String> sfOdds = loader.loadOdds(tournament, "last_4");
        if (csvHelper.isLocked(matchupFile)) {
            System.out.println("  🔒 Output already exists: " + matchupFile + " — delete to re-run");
            consoleReporter.printMatchups("Semi-final matchups", Files.readAllLines(matchupFile), eloCalculator, predictionDir.resolve("final.csv"), sfOdds);
            return;
        }

        Path overrideFile = predictionDir.resolve("last_4.csv");
        if (!Files.exists(overrideFile)) {
            throw new IOException("Predictions file not found: " + overrideFile + ". Run mode=last_8 first.");
        }
        predictionsFileValidator.validatePredictionsFile(overrideFile);
        Map<String, String> disagreeMap = disagreeMapMapper.loadDisagreeMap(overrideFile);

        Path last8File = projectRoot.resolve("data").resolve("matchups").resolve(tournament).resolve("last_8.csv");
        if (!Files.exists(last8File)) {
            throw new IOException("last_8 matchups not found: " + last8File + ". Run mode=last_8 first.");
        }
        QualificationFormCalculator qualCalc = new QualificationFormCalculator(
                projectRoot.resolve("data").resolve("elo").resolve("history"), 2023);
        List<String> last8Rows = Files.readAllLines(last8File);
        Map<String, Integer> eloRatings = loader.loadTournamentElo(tournament);
        List<com.tournamentpredictor.loader.CsvLoader.BracketEntry> brackets = loader.loadBrackets(tournament);

        Files.createDirectories(matchupDir);
        List<String> allLines = last4LineBuilder.buildLast4Lines(eloRatings, brackets, last8Rows);
        List<String> output = predictionScorer.scoreLines(allLines, disagreeMap, qualCalc);
        List<String> sortedOutput = csvHelper.sortGroupsPrimaryFirst(output);
        Files.write(matchupDir.resolve("last_4.csv"), sortedOutput);
        generateFinalPredictions(tournament, eloRatings, brackets, output);
        consoleReporter.printMatchups("Semi-final matchups", sortedOutput, eloCalculator, predictionDir.resolve("final.csv"), sfOdds);
    }

    private void generateFinalPredictions(String tournament, Map<String, Integer> eloRatings,
                                          List<com.tournamentpredictor.loader.CsvLoader.BracketEntry> brackets,
                                          List<String> last4Output) throws IOException {
        List<String> allLines = finalLineBuilder.buildFinalLines(eloRatings, brackets, last4Output);
        List<String> output = csvHelper.filterPrimaryOnly(allLines);
        Path predictionDir = projectRoot.resolve("data").resolve("predictions").resolve(tournament);
        Files.createDirectories(predictionDir);
        Files.write(predictionDir.resolve("final.csv"), output);
    }
}

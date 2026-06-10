package com.tournamentpredictor.services.prediction.handler;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.prediction.builder.Last16LineBuilder;
import com.tournamentpredictor.services.prediction.builder.Last8LineBuilder;
import com.tournamentpredictor.services.report.ConsoleReporter;
import com.tournamentpredictor.services.io.CsvHelper;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PredictionScorer;
import com.tournamentpredictor.services.prediction.validation.PredictionsFileValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Last16Handler {
    private final CsvLoader loader;
    private final EloCalculator eloCalculator;
    private final Last16LineBuilder last16LineBuilder;
    private final Last8LineBuilder last8LineBuilder;
    private final ConsoleReporter consoleReporter;
    private final KnockoutRoundFileService roundFiles;

    public Last16Handler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                         PredictionsFileValidator predictionsFileValidator,
                         EloCalculator eloCalculator, PredictionScorer predictionScorer,
                         Last16LineBuilder last16LineBuilder, Last8LineBuilder last8LineBuilder,
                         ConsoleReporter consoleReporter) {
        this.loader = loader;
        this.eloCalculator = eloCalculator;
        this.last16LineBuilder = last16LineBuilder;
        this.last8LineBuilder = last8LineBuilder;
        this.consoleReporter = consoleReporter;
        this.roundFiles = new KnockoutRoundFileService(loader, projectRoot, csvHelper, predictionsFileValidator, predictionScorer);
    }

    public void handle(String tournament) throws IOException {
        Path simulationDir = roundFiles.simulationDir(tournament);
        Path predictionDir = roundFiles.predictionDir(tournament);
        Path matchupFile = simulationDir.resolve("matchup_paths_last_16.csv");
        Map<String, String> last16Odds = loader.loadOdds(tournament, "last_8");
        if (roundFiles.reportLockedMatchups(matchupFile, "Last 16 matchups", predictionDir.resolve("last_8.csv"),
                last16Odds, consoleReporter, eloCalculator, "  [locked] Output already exists: ")) {
            return;
        }

        Path overrideFile = predictionDir.resolve("last_16.csv");
        KnockoutRoundFileService.GroupRoundContext context = roundFiles.loadGroupRoundContext(tournament);
        boolean startsAtLast16 = context.brackets().stream().noneMatch(entry -> "LAST_32".equalsIgnoreCase(entry.stage))
                && context.brackets().stream().anyMatch(entry -> "LAST_16".equalsIgnoreCase(entry.stage));
        if (Files.exists(overrideFile)) {
            roundFiles.validatePredictionFile(overrideFile, "last_32");
        } else if (!startsAtLast16) {
            throw new IOException("Predictions file not found: " + overrideFile + ". Run mode=last_32 first.");
        }
        Path last32File = simulationDir.resolve("matchup_paths_last_32.csv");
        List<String> last32Rows;
        if (Files.exists(last32File)) {
            last32Rows = Files.readAllLines(last32File);
        } else if (startsAtLast16) {
            last32Rows = List.of();
        } else {
            throw new IOException("last_32 matchups not found: " + last32File + ". Run mode=last_32 first.");
        }
        List<String> lines = last16LineBuilder.buildLast16Lines(context.groups(), context.groupWinner(),
                context.runnerUp(), context.thirdPlace(), context.eloRatings(), context.brackets(), last32Rows, context.snapshots());
        KnockoutRoundFileService.ScoredRows scoredRows = roundFiles.scoreSortAndWrite(tournament, "last_16", lines, context.snapshots());
        generateLast8Predictions(tournament, context, scoredRows.output());
        consoleReporter.printMatchups("Last 16 matchups", scoredRows.sortedOutput(), eloCalculator, predictionDir.resolve("last_8.csv"), last16Odds);
    }

    private void generateLast8Predictions(String tournament, KnockoutRoundFileService.GroupRoundContext context,
                                          List<String> last16Output) throws IOException {
        List<String> allLines = last8LineBuilder.buildLast8Lines(context.eloRatings(), context.brackets(),
                last16Output, context.snapshots());
        roundFiles.writePredictionRows(tournament, "last_8.csv", allLines);
    }
}

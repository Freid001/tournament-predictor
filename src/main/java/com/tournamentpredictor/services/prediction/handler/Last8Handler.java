package com.tournamentpredictor.services.prediction.handler;

import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PredictionScorer;
import com.tournamentpredictor.services.io.CsvHelper;
import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.prediction.builder.Last4LineBuilder;
import com.tournamentpredictor.services.prediction.builder.Last8LineBuilder;
import com.tournamentpredictor.services.prediction.validation.PredictionsFileValidator;
import com.tournamentpredictor.services.report.ConsoleReporter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Last8Handler {
    private final CsvLoader loader;
    private final EloCalculator eloCalculator;
    private final Last8LineBuilder last8LineBuilder;
    private final Last4LineBuilder last4LineBuilder;
    private final ConsoleReporter consoleReporter;
    private final KnockoutRoundFileService roundFiles;

    public Last8Handler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                        PredictionsFileValidator predictionsFileValidator,
                        EloCalculator eloCalculator, PredictionScorer predictionScorer,
                        Last8LineBuilder last8LineBuilder,
                        Last4LineBuilder last4LineBuilder, ConsoleReporter consoleReporter) {
        this.loader = loader;
        this.eloCalculator = eloCalculator;
        this.last8LineBuilder = last8LineBuilder;
        this.last4LineBuilder = last4LineBuilder;
        this.consoleReporter = consoleReporter;
        this.roundFiles = new KnockoutRoundFileService(loader, projectRoot, csvHelper, predictionsFileValidator, predictionScorer);
    }

    public void handle(String tournament) throws IOException {
        Path simulationDir = roundFiles.simulationDir(tournament);
        Path predictionDir = roundFiles.predictionDir(tournament);
        Path matchupFile = simulationDir.resolve("matchup_paths_last_8.csv");
        Map<String, String> qfOdds = loader.loadOdds(tournament, "last_4");
        if (roundFiles.reportLockedMatchups(matchupFile, "Quarter-final matchups", predictionDir.resolve("last_4.csv"),
                qfOdds, consoleReporter, eloCalculator, "  🔒 Output already exists: ")) {
            return;
        }

        roundFiles.validatePredictionFile(predictionDir.resolve("last_8.csv"), "last_16");
        List<String> last16Rows = roundFiles.readRequiredSimulationRows(tournament, "last_16", "last_16");
        KnockoutRoundFileService.RoundContext context = roundFiles.loadRoundContext(tournament);

        List<String> allLines = last8LineBuilder.buildLast8Lines(context.eloRatings(), context.brackets(), last16Rows, context.snapshots());
        KnockoutRoundFileService.ScoredRows scoredRows = roundFiles.scoreSortAndWrite(tournament, "last_8", allLines, context.snapshots());
        generateLastSfPredictions(tournament, context, scoredRows.output());
        consoleReporter.printMatchups("Quarter-final matchups", scoredRows.sortedOutput(), eloCalculator, predictionDir.resolve("last_4.csv"), qfOdds);
    }

    private void generateLastSfPredictions(String tournament, KnockoutRoundFileService.RoundContext context,
                                           List<String> last8Output) throws IOException {
        List<String> allLines = last4LineBuilder.buildLast4Lines(context.eloRatings(), context.brackets(), last8Output, context.snapshots());
        roundFiles.writePredictionRows(tournament, "last_4.csv", allLines);
    }
}

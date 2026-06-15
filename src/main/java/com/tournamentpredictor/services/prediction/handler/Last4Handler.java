package com.tournamentpredictor.services.prediction.handler;

import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PredictionScorer;
import com.tournamentpredictor.services.io.CsvHelper;
import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.prediction.builder.FinalLineBuilder;
import com.tournamentpredictor.services.prediction.builder.Last4LineBuilder;
import com.tournamentpredictor.services.prediction.validation.PredictionsFileValidator;
import com.tournamentpredictor.services.report.ConsoleReporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Last4Handler {
    private final CsvLoader loader;
    private final EloCalculator eloCalculator;
    private final Last4LineBuilder last4LineBuilder;
    private final FinalLineBuilder finalLineBuilder;
    private final ConsoleReporter consoleReporter;
    private final KnockoutRoundFileService roundFiles;

    public Last4Handler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                        PredictionsFileValidator predictionsFileValidator,
                        EloCalculator eloCalculator, PredictionScorer predictionScorer,
                        Last4LineBuilder last4LineBuilder,
                        FinalLineBuilder finalLineBuilder, ConsoleReporter consoleReporter) {
        this.loader = loader;
        this.eloCalculator = eloCalculator;
        this.last4LineBuilder = last4LineBuilder;
        this.finalLineBuilder = finalLineBuilder;
        this.consoleReporter = consoleReporter;
        this.roundFiles = new KnockoutRoundFileService(loader, projectRoot, csvHelper, predictionsFileValidator, predictionScorer);
    }

    public void handle(String tournament) throws IOException {
        Path simulationDir = roundFiles.simulationDir(tournament);
        Path predictionDir = roundFiles.predictionDir(tournament);
        Path matchupFile = simulationDir.resolve("matchup_paths_last_4.csv");
        Map<String, String> sfOdds = loader.loadOdds(tournament, "final");
        if (roundFiles.reportLockedMatchups(matchupFile, "Semi-final matchups", predictionDir.resolve("final.csv"),
                sfOdds, consoleReporter, eloCalculator, "  🔒 Output already exists: ")) {
            return;
        }

        ensurePredictionExists(predictionDir.resolve("last_4.csv"), "last_8");
        List<String> last8Rows = roundFiles.readRequiredSimulationRows(tournament, "last_8", "last_8");
        KnockoutRoundFileService.RoundContext context = roundFiles.loadRoundContext(tournament);

        List<String> allLines = last4LineBuilder.buildLast4Lines(context.eloRatings(), context.brackets(), last8Rows, context.snapshots());
        KnockoutRoundFileService.ScoredRows scoredRows = roundFiles.scoreSortAndWrite(tournament, "last_4", allLines, context.snapshots());
        generateFinalPredictions(tournament, context, scoredRows.output());
        consoleReporter.printMatchups("Semi-final matchups", scoredRows.sortedOutput(), eloCalculator, predictionDir.resolve("final.csv"), sfOdds);
    }

    private void generateFinalPredictions(String tournament, KnockoutRoundFileService.RoundContext context,
                                          List<String> last4Output) throws IOException {
        List<String> allLines = finalLineBuilder.buildFinalLines(context.eloRatings(), context.brackets(), last4Output, context.snapshots());
        roundFiles.writePredictionRows(tournament, "final.csv", allLines);
    }

    private void ensurePredictionExists(Path predictionFile, String previousMode) throws IOException {
        if (!roundFiles.generatedDataExists(predictionFile)) {
            throw new IOException("Predictions data not found: " + predictionFile + ". Run mode=" + previousMode + " first.");
        }
    }

}

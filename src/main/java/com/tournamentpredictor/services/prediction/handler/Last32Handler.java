package com.tournamentpredictor.services.prediction.handler;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.prediction.builder.Last16LineBuilder;
import com.tournamentpredictor.services.prediction.builder.Last32LineBuilder;
import com.tournamentpredictor.services.report.ConsoleReporter;
import com.tournamentpredictor.services.io.CsvHelper;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PredictionScorer;
import com.tournamentpredictor.services.prediction.validation.PredictionsFileValidator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Last32Handler {
    private final CsvLoader loader;
    private final EloCalculator eloCalculator;
    private final Last32LineBuilder last32LineBuilder;
    private final Last16LineBuilder last16LineBuilder;
    private final ConsoleReporter consoleReporter;
    private final KnockoutRoundFileService roundFiles;

    public Last32Handler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                         PredictionsFileValidator predictionsFileValidator,
                         EloCalculator eloCalculator, PredictionScorer predictionScorer,
                         Last32LineBuilder last32LineBuilder, Last16LineBuilder last16LineBuilder,
                         ConsoleReporter consoleReporter) {
        this.loader = loader;
        this.eloCalculator = eloCalculator;
        this.last32LineBuilder = last32LineBuilder;
        this.last16LineBuilder = last16LineBuilder;
        this.consoleReporter = consoleReporter;
        this.roundFiles = new KnockoutRoundFileService(loader, projectRoot, csvHelper, predictionsFileValidator, predictionScorer);
    }

    public void handle(String tournament) throws IOException {
        Path simulationDir = roundFiles.simulationDir(tournament);
        Path predictionDir = roundFiles.predictionDir(tournament);
        Path matchupFile = simulationDir.resolve("matchup_paths_last_32.csv");
        Map<String, String> last32Odds = loader.loadOdds(tournament, "last_16");
        if (roundFiles.reportLockedMatchups(matchupFile, "Last 32 matchups", predictionDir.resolve("last_16.csv"),
                last32Odds, consoleReporter, eloCalculator, "  🔒 Output already exists: ")) {
            return;
        }

        roundFiles.validatePredictionFile(predictionDir.resolve("last_32.csv"), "groups");

        KnockoutRoundFileService.GroupRoundContext context = roundFiles.loadGroupRoundContext(tournament);
        List<String> allLines = last32LineBuilder.buildLast32Lines(context.groups(), context.groupWinner(),
                context.runnerUp(), context.thirdPlace(), context.eloRatings(), context.brackets(), context.snapshots());
        KnockoutRoundFileService.ScoredRows scoredRows = roundFiles.scoreSortAndWrite(tournament, "last_32", allLines, context.snapshots());

        List<String> enrichedLast32Rows = roundFiles.readGeneratedLines(matchupFile);
        List<String> allLast16 = last16LineBuilder.buildLast16Lines(context.groups(), context.groupWinner(),
                context.runnerUp(), context.thirdPlace(), context.eloRatings(), context.brackets(), enrichedLast32Rows, context.snapshots());
        // Preserve the model-selected Last 32 winners in the staged UI.
        roundFiles.writePredictionRows(tournament, "last_16.csv", allLast16);
        consoleReporter.printMatchups("Last 32 matchups", scoredRows.sortedOutput(), eloCalculator, predictionDir.resolve("last_16.csv"), last32Odds);
    }
}

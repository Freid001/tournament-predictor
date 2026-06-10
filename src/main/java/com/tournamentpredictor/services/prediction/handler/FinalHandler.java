package com.tournamentpredictor.services.prediction.handler;

import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PredictionScorer;
import com.tournamentpredictor.services.io.CsvHelper;
import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.prediction.builder.FinalLineBuilder;
import com.tournamentpredictor.services.prediction.validation.PredictionsFileValidator;
import com.tournamentpredictor.services.report.ConsoleReporter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class FinalHandler {
    private final CsvLoader loader;
    private final EloCalculator eloCalculator;
    private final FinalLineBuilder finalLineBuilder;
    private final ConsoleReporter consoleReporter;
    private final KnockoutRoundFileService roundFiles;

    public FinalHandler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                        PredictionsFileValidator predictionsFileValidator,
                        EloCalculator eloCalculator, PredictionScorer predictionScorer,
                        FinalLineBuilder finalLineBuilder, ConsoleReporter consoleReporter) {
        this.loader = loader;
        this.eloCalculator = eloCalculator;
        this.finalLineBuilder = finalLineBuilder;
        this.consoleReporter = consoleReporter;
        this.roundFiles = new KnockoutRoundFileService(loader, projectRoot, csvHelper, predictionsFileValidator, predictionScorer);
    }

    public void handle(String tournament) throws IOException {
        Path simulationDir = roundFiles.simulationDir(tournament);
        Path matchupFile = simulationDir.resolve("matchup_paths_final.csv");
        Map<String, String> finalOdds = loader.loadOdds(tournament, "final");
        if (roundFiles.reportLockedMatchups(matchupFile, "Predicted World Cup champion", null,
                finalOdds, consoleReporter, eloCalculator, "  🔒 Output already exists: ")) {
            return;
        }

        Path predictionFile = roundFiles.predictionDir(tournament).resolve("final.csv");
        roundFiles.validatePredictionFile(predictionFile, "last_4");
        List<String> last4Rows = roundFiles.readRequiredSimulationRows(tournament, "last_4", "last_4");
        KnockoutRoundFileService.RoundContext context = roundFiles.loadRoundContext(tournament);

        List<String> allLines = finalLineBuilder.buildFinalLines(context.eloRatings(), context.brackets(), last4Rows, context.snapshots());
        KnockoutRoundFileService.ScoredRows scoredRows = roundFiles.scoreSortAndWrite(tournament, "final", allLines, context.snapshots());
        consoleReporter.printMatchups("Predicted World Cup champion", scoredRows.sortedOutput(), eloCalculator, null, finalOdds);
    }
}

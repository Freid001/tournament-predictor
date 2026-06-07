package com.tournamentpredictor.service.handler;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.builder.FinalLineBuilder;
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

public class FinalHandler {
    private final CsvLoader loader;
    private final Path projectRoot;
    private final CsvHelper csvHelper;
    private final PredictionsFileValidator predictionsFileValidator;
    private final DisagreeMapMapper disagreeMapMapper;
    private final EloCalculator eloCalculator;
    private final PredictionScorer predictionScorer;
    private final FinalLineBuilder finalLineBuilder;
    private final ConsoleReporter consoleReporter;

    public FinalHandler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                        PredictionsFileValidator predictionsFileValidator, DisagreeMapMapper disagreeMapMapper,
                        EloCalculator eloCalculator, PredictionScorer predictionScorer,
                        FinalLineBuilder finalLineBuilder, ConsoleReporter consoleReporter) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.csvHelper = csvHelper;
        this.predictionsFileValidator = predictionsFileValidator;
        this.disagreeMapMapper = disagreeMapMapper;
        this.eloCalculator = eloCalculator;
        this.predictionScorer = predictionScorer;
        this.finalLineBuilder = finalLineBuilder;
        this.consoleReporter = consoleReporter;
    }

    public void handle(String tournament) throws IOException {
        Path simulationDir = projectRoot.resolve("data").resolve("simulations").resolve(tournament);
        Path matchupFile = simulationDir.resolve("matchup_paths_final.csv");
        Map<String, String> finalOdds = loader.loadOdds(tournament, "final");
        if (csvHelper.isLocked(matchupFile)) {
            System.out.println("  🔒 Output already exists: " + matchupFile + " — delete to re-run");
            consoleReporter.printMatchups("Predicted World Cup champion", Files.readAllLines(matchupFile), eloCalculator, null, finalOdds);
            return;
        }

        Path predictionFile = projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve("final.csv");
        if (!Files.exists(predictionFile)) {
            throw new IOException("Predictions file not found: " + predictionFile + ". Run mode=last_4 first.");
        }
        predictionsFileValidator.validatePredictionsFile(predictionFile);
        Map<String, String> disagreeMap = disagreeMapMapper.loadDisagreeMap(predictionFile);

        Path last4File = projectRoot.resolve("data").resolve("simulations").resolve(tournament).resolve("matchup_paths_last_4.csv");
        if (!Files.exists(last4File)) {
            throw new IOException("last_4 matchups not found: " + last4File + ". Run mode=last_4 first.");
        }
        List<String> last4Rows = Files.readAllLines(last4File);
        Map<String, Integer> eloRatings = loader.loadTournamentElo(tournament);
        Map<String, TeamEloSnapshot> snapshots = loader.loadTeamSnapshots(tournament);
        List<com.tournamentpredictor.loader.CsvLoader.BracketEntry> brackets = loader.loadBrackets(tournament);

        Files.createDirectories(simulationDir);
        List<String> allLines = finalLineBuilder.buildFinalLines(eloRatings, brackets, last4Rows, snapshots);
        predictionScorer.setSnapshots(snapshots);
        List<String> output = predictionScorer.scoreLines(allLines, disagreeMap);
        List<String> sortedOutput = csvHelper.sortGroupsPrimaryFirst(output);
        Files.write(simulationDir.resolve("matchup_paths_final.csv"), sortedOutput);
        consoleReporter.printMatchups("Predicted World Cup champion", sortedOutput, eloCalculator, null, finalOdds);
    }
}

package com.tournamentpredictor.services.prediction.handler;

import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PredictionScorer;
import com.tournamentpredictor.services.calculation.TeamEloSnapshot;
import com.tournamentpredictor.services.io.CsvHelper;
import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.prediction.validation.PredictionsFileValidator;
import com.tournamentpredictor.services.report.ConsoleReporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class KnockoutRoundFileService {
    private final CsvLoader loader;
    private final Path projectRoot;
    private final CsvHelper csvHelper;
    private final PredictionsFileValidator predictionsFileValidator;
    private final PredictionScorer predictionScorer;

    KnockoutRoundFileService(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                             PredictionsFileValidator predictionsFileValidator,
                             PredictionScorer predictionScorer) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.csvHelper = csvHelper;
        this.predictionsFileValidator = predictionsFileValidator;
        this.predictionScorer = predictionScorer;
    }

    Path simulationDir(String tournament) {
        return projectRoot.resolve("data").resolve("simulations").resolve(tournament);
    }

    Path predictionDir(String tournament) {
        return projectRoot.resolve("data").resolve("predictions").resolve(tournament);
    }

    boolean reportLockedMatchups(Path matchupFile, String title, Path nextPredictionFile,
                                 Map<String, String> odds, ConsoleReporter consoleReporter,
                                 EloCalculator eloCalculator, String lockedMessage) throws IOException {
        if (!csvHelper.isLocked(matchupFile)) {
            return false;
        }
        System.out.println(lockedMessage + matchupFile + " — delete to re-run");
        consoleReporter.printMatchups(title, Files.readAllLines(matchupFile), eloCalculator, nextPredictionFile, odds);
        return true;
    }

    void validatePredictionFile(Path predictionFile, String previousMode) throws IOException {
        if (!Files.exists(predictionFile)) {
            throw new IOException("Predictions file not found: " + predictionFile + ". Run mode=" + previousMode + " first.");
        }
        predictionsFileValidator.validatePredictionsFile(predictionFile);
    }

    List<String> readRequiredSimulationRows(String tournament, String round, String previousMode) throws IOException {
        Path file = simulationDir(tournament).resolve("matchup_paths_" + round + ".csv");
        if (!Files.exists(file)) {
            throw new IOException(round + " matchups not found: " + file + ". Run mode=" + previousMode + " first.");
        }
        return Files.readAllLines(file);
    }

    RoundContext loadRoundContext(String tournament) throws IOException {
        return new RoundContext(
                loader.loadTournamentElo(tournament),
                loader.loadTeamSnapshots(tournament),
                loader.loadBrackets(tournament)
        );
    }

    GroupRoundContext loadGroupRoundContext(String tournament) throws IOException {
        return new GroupRoundContext(
                loader.loadGroups(tournament),
                loader.loadGroupWinner(tournament),
                loader.loadRunnerUp(tournament),
                loader.loadThirdPlace(tournament),
                loader.loadTournamentElo(tournament),
                loader.loadTeamSnapshots(tournament),
                loader.loadBrackets(tournament)
        );
    }

    ScoredRows scoreSortAndWrite(String tournament, String round, List<String> rawLines,
                                  Map<String, TeamEloSnapshot> snapshots) throws IOException {
        Path simulationDir = simulationDir(tournament);
        Files.createDirectories(simulationDir);
        predictionScorer.setSnapshots(snapshots);
        List<String> output = predictionScorer.scoreLines(rawLines);
        List<String> sortedOutput = csvHelper.sortGroupsPrimaryFirst(output);
        Files.write(simulationDir.resolve("matchup_paths_" + round + ".csv"), sortedOutput);
        return new ScoredRows(output, sortedOutput);
    }

    void writePredictionRows(String tournament, String fileName, List<String> rows) throws IOException {
        Path predictionDir = predictionDir(tournament);
        Files.createDirectories(predictionDir);
        Files.write(predictionDir.resolve(fileName), rows);
    }

    record RoundContext(Map<String, Integer> eloRatings,
                        Map<String, TeamEloSnapshot> snapshots,
                        List<CsvLoader.BracketEntry> brackets) {
    }

    record GroupRoundContext(Map<String, String> groups,
                             Map<String, String> groupWinner,
                             Map<String, String> runnerUp,
                             Map<String, String> thirdPlace,
                             Map<String, Integer> eloRatings,
                             Map<String, TeamEloSnapshot> snapshots,
                             List<CsvLoader.BracketEntry> brackets) {
    }

    record ScoredRows(List<String> output, List<String> sortedOutput) {
    }
}

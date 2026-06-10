package com.tournamentpredictor.services.prediction.handler;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.prediction.builder.Last32LineBuilder;
import com.tournamentpredictor.services.report.ConsoleReporter;
import com.tournamentpredictor.services.io.CsvHelper;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.TeamEloSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GroupsHandler {
    private final CsvLoader loader;
    private final Path projectRoot;
    private final CsvHelper csvHelper;
    private final Last32LineBuilder last32LineBuilder;
    private final EloCalculator eloCalculator;

    public GroupsHandler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                         Last32LineBuilder last32LineBuilder, EloCalculator eloCalculator) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.csvHelper = csvHelper;
        this.last32LineBuilder = last32LineBuilder;
        this.eloCalculator = eloCalculator;
    }

    public void handle(String tournament) throws IOException {
        Path predictionDir = projectRoot.resolve("data").resolve("predictions").resolve(tournament);
        Path last32Pred = predictionDir.resolve("last_32.csv");
        if (csvHelper.isLocked(last32Pred)) {
            System.out.println("  🔒 Output already exists: " + last32Pred + " — delete to re-run");
            List<String> existing = Files.readAllLines(last32Pred);
            ConsoleReporter.printGeneratedFile(last32Pred, existing.size() - 1, "last_32");
            return;
        }

        List<String> validationErrors = loader.validateGroups(tournament);
        if (!validationErrors.isEmpty()) {
            throw new IOException("groups.csv validation failed:\n" + String.join("\n", validationErrors));
        }

        Map<String, String> groups = loader.loadGroups(tournament);
        Map<String, String> groupWinner = loader.loadGroupWinner(tournament);
        Map<String, String> runnerUp = loader.loadRunnerUp(tournament);
        Map<String, String> thirdPlace = loader.loadThirdPlace(tournament);
        Map<String, Integer> eloRatings = loader.loadTournamentElo(tournament);
        Map<String, TeamEloSnapshot> snapshots = loader.loadTeamSnapshots(tournament);

        List<String> allLines = last32LineBuilder.buildLast32Lines(groups, groupWinner, runnerUp, thirdPlace,
                eloRatings, loader.loadBrackets(tournament), snapshots);
        List<String> output = new ArrayList<>();
        output.add("match_id,team1,team2,path,elo,prediction");
        boolean addedForMatch = false;
        for (int i = 1; i < allLines.size(); i++) {
            String row = allLines.get(i);
            if (row.trim().isEmpty()) {
                if (addedForMatch) {
                    output.add("");
                }
                addedForMatch = false;
                continue;
            }
            String[] cols = row.split(",", -1);
            if (cols.length >= 5 && "predicted".equals(cols[3].trim())) {
                String matchId = cols[0].trim();
                String team1Display = cols[1].trim();
                String team2Display = cols[2].trim();
                String path = cols[3].trim();
                String eloPrediction = cols[4].trim();
                output.add(String.join(",", matchId, team1Display, team2Display, path, eloPrediction,
                        eloPrediction));
                addedForMatch = true;
            }
        }

        Files.createDirectories(predictionDir);
        Files.write(predictionDir.resolve("last_32.csv"), output);
        ConsoleReporter.printGeneratedFile(predictionDir.resolve("last_32.csv"), output.size() - 1, "last_32");
    }
}

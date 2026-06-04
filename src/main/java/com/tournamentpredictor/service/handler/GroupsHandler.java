package com.tournamentpredictor.service.handler;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.builder.Last32LineBuilder;
import com.tournamentpredictor.service.util.ConsoleReporter;
import com.tournamentpredictor.service.util.CsvHelper;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.HeadToHeadCalculator;
import com.tournamentpredictor.service.util.QualificationFormCalculator;

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
    private final HeadToHeadCalculator headToHeadCalculator;

    public GroupsHandler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                         Last32LineBuilder last32LineBuilder, EloCalculator eloCalculator,
                         HeadToHeadCalculator headToHeadCalculator) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.csvHelper = csvHelper;
        this.last32LineBuilder = last32LineBuilder;
        this.eloCalculator = eloCalculator;
        this.headToHeadCalculator = headToHeadCalculator;
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

        QualificationFormCalculator qualCalc = new QualificationFormCalculator(
                projectRoot.resolve("data").resolve("elo").resolve("history"), 2023);
        Map<String, String> groups = loader.loadGroups(tournament);
        Map<String, String> groupWinner = loader.loadGroupWinner(tournament);
        Map<String, String> runnerUp = loader.loadRunnerUp(tournament);
        Map<String, String> thirdPlace = loader.loadThirdPlace(tournament);
        Map<String, Integer> eloRatings = loader.loadTournamentElo(tournament);

        List<String> allLines = last32LineBuilder.buildLast32Lines(groups, groupWinner, runnerUp, thirdPlace,
                eloRatings, loader.loadBrackets(tournament));
        List<String> output = new ArrayList<>();
        output.add("match_id,team1,team2,path,elo,prediction,do_you_disagree");
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
            if (cols.length >= 5 && "primary".equals(cols[3].trim())) {
                String matchId = cols[0].trim();
                String team1Display = cols[1].trim();
                String team2Display = cols[2].trim();
                String path = cols[3].trim();
                String eloPrediction = cols[4].trim();
                String team1Name = eloCalculator.extractTeamName(team1Display);
                String team2Name = eloCalculator.extractTeamName(team2Display);
                boolean hasQual1 = qualCalc.hasData(team1Name);
                boolean hasQual2 = qualCalc.hasData(team2Name);
                double qualScore1 = qualCalc.getFormScore(team1Name);
                double qualScore2 = qualCalc.getFormScore(team2Name);
                boolean hasQualData = hasQual1 || hasQual2;
                double qualRelativePct = (qualScore1 + qualScore2) > 0.0 ? qualScore1 / (qualScore1 + qualScore2) : 0.5;
                String prediction = eloCalculator.combinePredictionsWithQual(team1Name, team2Name,
                        eloPrediction, qualRelativePct, hasQualData);
                output.add(String.join(",", matchId, team1Display, team2Display, path, eloPrediction,
                        prediction, ""));
                addedForMatch = true;
            }
        }

        Files.createDirectories(predictionDir);
        Files.write(predictionDir.resolve("last_32.csv"), output);
        ConsoleReporter.printGeneratedFile(predictionDir.resolve("last_32.csv"), output.size() - 1, "last_32");
    }
}

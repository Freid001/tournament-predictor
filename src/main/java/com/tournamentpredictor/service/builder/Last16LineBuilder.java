package com.tournamentpredictor.service.builder;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.DisplayBuilder;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.PathCalculator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Last16LineBuilder {
    private final DisplayBuilder displayBuilder;
    private final PathCalculator pathCalculator;
    private final EloCalculator predictionHelper;

    public Last16LineBuilder(DisplayBuilder displayBuilder, PathCalculator pathCalculator,
                             EloCalculator predictionHelper) {
        this.displayBuilder = displayBuilder;
        this.pathCalculator = pathCalculator;
        this.predictionHelper = predictionHelper;
    }

    public List<String> buildLast16Lines(Map<String, String> groups, Map<String, String> groupWinner,
                                         Map<String, String> runnerUp, Map<String, String> thirdPlace,
                                         Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets,
                                         List<String> last32Rows) {
        Map<String, String> last32PredByTeam = new LinkedHashMap<>();
        for (String row : last32Rows) {
            if (row.trim().isEmpty() || row.startsWith("match_id")) {
                continue;
            }
            String[] cols = row.split(",", -1);
            if (cols.length < 5) {
                continue;
            }
            String matchId = cols[0].trim();
            String team1Name = predictionHelper.extractTeamName(cols[1].trim());
            String team2Name = predictionHelper.extractTeamName(cols[2].trim());
            String path = cols[3].trim();
            String eloWinner = predictionHelper.parseTeamFromPrediction(cols[4].trim());
            String eloLoser = eloWinner.equalsIgnoreCase(team1Name) ? team2Name : team1Name;
            if (!eloWinner.isEmpty()) {
                last32PredByTeam.merge(matchId + "|" + eloWinner, path, pathCalculator::bestPredicted);
            }
            if (!eloLoser.isEmpty()) {
                last32PredByTeam.merge(matchId + "|" + eloLoser, "alt", pathCalculator::bestPredicted);
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("match_id,team1,team2,path,elo,do_you_disagree");
        for (CsvLoader.BracketEntry bracket : brackets) {
            if (!"LAST_16".equalsIgnoreCase(bracket.stage)) {
                continue;
            }
            List<String> displays1 = displayBuilder.buildWinnerDisplays(bracket.token1, groups, groupWinner, runnerUp,
                    thirdPlace, brackets, last32Rows);
            List<String> displays2 = displayBuilder.buildWinnerDisplays(bracket.token2, groups, groupWinner, runnerUp,
                    thirdPlace, brackets, last32Rows);
            for (String display1 : displays1) {
                for (String display2 : displays2) {
                    String team1 = predictionHelper.extractTeamName(display1);
                    String team2 = predictionHelper.extractTeamName(display2);
                    if (team1.equalsIgnoreCase(team2)) {
                        continue;
                    }
                    String eloPrediction = predictionHelper.computeEloPrediction(team1, team2, eloRatings);
                    String path = pathCalculator.computeLast16PredictedMatch(display1, display2, last32PredByTeam);
                    lines.add(String.join(",", bracket.matchId, displayBuilder.safe(display1),
                            displayBuilder.safe(display2), path, eloPrediction, ""));
                }
            }
            lines.add("");
        }
        return lines;
    }
}

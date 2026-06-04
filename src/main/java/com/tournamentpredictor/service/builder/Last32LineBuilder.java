package com.tournamentpredictor.service.builder;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.DisplayBuilder;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.PathCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Last32LineBuilder {
    private final DisplayBuilder displayBuilder;
    private final PathCalculator pathCalculator;
    private final EloCalculator predictionHelper;

    public Last32LineBuilder(DisplayBuilder displayBuilder, PathCalculator pathCalculator,
                             EloCalculator predictionHelper) {
        this.displayBuilder = displayBuilder;
        this.pathCalculator = pathCalculator;
        this.predictionHelper = predictionHelper;
    }

    public List<String> buildLast32Lines(Map<String, String> groups, Map<String, String> groupWinner,
                                         Map<String, String> runnerUp, Map<String, String> thirdPlace,
                                         Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets) {
        Map<String, String> teamGW = new HashMap<>();
        Map<String, String> teamRU = new HashMap<>();
        Map<String, String> teamTP = new HashMap<>();
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            String team = entry.getValue();
            String position = entry.getKey();
            if (team != null && !team.isEmpty()) {
                teamGW.put(team, groupWinner.getOrDefault(position, ""));
                teamRU.put(team, runnerUp.getOrDefault(position, ""));
                teamTP.put(team, thirdPlace.getOrDefault(position, ""));
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("match_id,team1,team2,path,elo");
        for (CsvLoader.BracketEntry bracket : brackets) {
            if (!"LAST_32".equalsIgnoreCase(bracket.stage)) {
                continue;
            }
            List<String> displays1 = displayBuilder.buildDisplays(bracket.token1, groups, groupWinner, runnerUp, thirdPlace);
            List<String> displays2 = displayBuilder.buildDisplays(bracket.token2, groups, groupWinner, runnerUp, thirdPlace);
            for (String display1 : displays1) {
                for (String display2 : displays2) {
                    String team1Name = predictionHelper.extractTeamName(display1);
                    String team2Name = predictionHelper.extractTeamName(display2);
                    String path = pathCalculator.computePredictedMatch(bracket.token1, display1, bracket.token2, display2,
                            teamGW, teamRU, teamTP);
                    String eloPrediction = predictionHelper.computeEloPrediction(team1Name, team2Name, eloRatings);
                    lines.add(String.join(",", bracket.matchId, displayBuilder.safe(display1),
                            displayBuilder.safe(display2), path, eloPrediction));
                }
            }
            lines.add("");
        }
        return lines;
    }
}

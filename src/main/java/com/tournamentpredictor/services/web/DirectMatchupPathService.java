package com.tournamentpredictor.services.web;

import com.tournamentpredictor.model.round.DirectMatchupSummary;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.SlotStatusEvaluator;

import java.util.Map;

public final class DirectMatchupPathService {
    private DirectMatchupPathService() {
    }

    public static String classify(DirectMatchupSummary summary, String winner, Map<String, Integer> primaryRunsByMatch,
                                  Map<String, Integer> eloRatings) {
        if (isUpset(summary, winner, eloRatings)) {
            return "upset";
        }
        return summary.matchupRuns == primaryRunsByMatch.getOrDefault(summary.matchId, -1) ? "predicted" : "alt";
    }


    public static String classifyOpeningRoute(DirectMatchupSummary summary, String winner, String token1, String token2,
                                              Map<String, String> teamGW, Map<String, String> teamRU,
                                              Map<String, String> teamTP, Map<String, Integer> primaryRunsByMatch,
                                              Map<String, Integer> eloRatings) {
        if (summary == null || token1 == null || token2 == null || token1.isBlank() || token2.isBlank()) {
            return classify(summary, winner, primaryRunsByMatch, eloRatings);
        }
        SlotStatusEvaluator slots = new SlotStatusEvaluator(new EloCalculator());
        boolean team1Predicted = slots.isDisplayPredicted(token1, summary.team1, teamGW, teamRU, teamTP);
        boolean team2Predicted = slots.isDisplayPredicted(token2, summary.team2, teamGW, teamRU, teamTP);
        if (team1Predicted && team2Predicted) {
            return "predicted";
        }
        boolean team1Possible = team1Predicted || slots.isDisplayMaybe(token1, summary.team1, teamGW, teamRU, teamTP);
        boolean team2Possible = team2Predicted || slots.isDisplayMaybe(token2, summary.team2, teamGW, teamRU, teamTP);
        if (team1Possible && team2Possible) {
            return "alt";
        }
        return "upset";
    }

    private static boolean isUpset(DirectMatchupSummary summary, String winner, Map<String, Integer> eloRatings) {
        if (summary == null || winner == null || winner.isBlank() || eloRatings == null || eloRatings.isEmpty()) {
            return false;
        }
        String loser = winner.equalsIgnoreCase(summary.team1) ? summary.team2
                : winner.equalsIgnoreCase(summary.team2) ? summary.team1 : "";
        if (loser.isBlank()) {
            return false;
        }
        Integer winnerElo = eloRatings.get(winner);
        Integer loserElo = eloRatings.get(loser);
        return winnerElo != null && loserElo != null && winnerElo < loserElo;
    }
}

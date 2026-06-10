package com.tournamentpredictor.services.web;

import com.tournamentpredictor.model.round.DirectMatchupSummary;

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

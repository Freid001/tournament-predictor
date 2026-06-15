package com.tournamentpredictor.services.calculation;

import java.util.Map;

public class SlotStatusEvaluator {
    public SlotStatusEvaluator(EloCalculator eloCalculator) {
    }

    public boolean isTeamPredicted(String token, String team, Map<String, String> teamGW,
                                   Map<String, String> teamRU, Map<String, String> teamTP) {
        return hasStatus(token, team, "yes", teamGW, teamRU, teamTP);
    }

    public boolean isTeamMaybe(String token, String team, Map<String, String> teamGW,
                               Map<String, String> teamRU, Map<String, String> teamTP) {
        return hasStatus(token, team, "maybe", teamGW, teamRU, teamTP);
    }

    private boolean hasStatus(String token, String team, String status, Map<String, String> teamGW,
                              Map<String, String> teamRU, Map<String, String> teamTP) {
        if (token == null || token.isEmpty() || team == null || team.isBlank()) {
            return false;
        }
        token = token.trim();
        if (token.matches("^[A-L][1-4]$")) {
            if (token.endsWith("1")) {
                return status.equalsIgnoreCase(teamGW.getOrDefault(team, ""));
            }
            if (token.endsWith("2")) {
                return status.equalsIgnoreCase(teamRU.getOrDefault(team, ""));
            }
        }
        if (token.matches("^[A-L]+3$")) {
            return status.equalsIgnoreCase(teamTP.getOrDefault(team, ""));
        }
        return false;
    }
}

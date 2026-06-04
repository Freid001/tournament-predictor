package com.tournamentpredictor.service.util;

import java.util.Map;

public class SlotStatusEvaluator {
    private final EloCalculator eloCalculator;

    public SlotStatusEvaluator(EloCalculator eloCalculator) {
        this.eloCalculator = eloCalculator;
    }

    public boolean isDisplayPredicted(String token, String display, Map<String, String> teamGW,
                                      Map<String, String> teamRU, Map<String, String> teamTP) {
        if (token == null || token.isEmpty() || display == null) {
            return false;
        }
        String name = eloCalculator.extractTeamName(display);
        if (name.isEmpty()) {
            return false;
        }
        token = token.trim();
        if (token.matches("^[A-L][1-4]$")) {
            if (token.endsWith("1")) {
                return "yes".equalsIgnoreCase(teamGW.getOrDefault(name, ""));
            }
            if (token.endsWith("2")) {
                return "yes".equalsIgnoreCase(teamRU.getOrDefault(name, ""));
            }
        }
        if (token.matches("^[A-L]+3$")) {
            return "yes".equalsIgnoreCase(teamTP.getOrDefault(name, ""));
        }
        return false;
    }

    public boolean isDisplayMaybe(String token, String display, Map<String, String> teamGW,
                                  Map<String, String> teamRU, Map<String, String> teamTP) {
        if (token == null || token.isEmpty() || display == null) {
            return false;
        }
        String name = eloCalculator.extractTeamName(display);
        if (name.isEmpty()) {
            return false;
        }
        token = token.trim();
        if (token.matches("^[A-L][1-4]$")) {
            if (token.endsWith("1")) {
                return "maybe".equalsIgnoreCase(teamGW.getOrDefault(name, ""));
            }
            if (token.endsWith("2")) {
                return "maybe".equalsIgnoreCase(teamRU.getOrDefault(name, ""));
            }
        }
        if (token.matches("^[A-L]+3$")) {
            return "maybe".equalsIgnoreCase(teamTP.getOrDefault(name, ""));
        }
        return false;
    }
}

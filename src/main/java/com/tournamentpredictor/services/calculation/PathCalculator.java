package com.tournamentpredictor.services.calculation;

import java.util.Map;
public class PathCalculator {
    private final SlotStatusEvaluator slotStatusEvaluator;
    private final EloCalculator eloCalculator;

    public PathCalculator(SlotStatusEvaluator slotStatusEvaluator, EloCalculator eloCalculator) {
        this.slotStatusEvaluator = slotStatusEvaluator;
        this.eloCalculator = eloCalculator;
    }

    public String bestPredicted(String existing, String incoming) {
        if ("predicted".equals(existing) || "predicted".equals(incoming)) {
            return "predicted";
        }
        if ("alt".equals(existing) || "alt".equals(incoming)
                || "upset".equals(existing) || "upset".equals(incoming)) {
            return "alt";
        }
        return existing;
    }

    public String computePathFromSlots(String path1, String path2) {
        if ("predicted".equals(path1) && "predicted".equals(path2)) {
            return "predicted";
        }
        if ("alt".equals(path1) || "alt".equals(path2)
                || "upset".equals(path1) || "upset".equals(path2)) {
            return "alt";
        }
        return "";
    }

    /** Classify a complete displayed route from its actual knockout history. */
    public String classifyCompletedRoute(String candidatePath, String team1Chain, String team2Chain) {
        return "predicted".equalsIgnoreCase(candidatePath)
                && !containsUpset(team1Chain)
                && !containsUpset(team2Chain) ? "predicted" : "alt";
    }

    private static boolean containsUpset(String chain) {
        if (chain == null || chain.isBlank()) return false;
        return java.util.Arrays.stream(chain.split(" > "))
                .map(String::trim)
                .anyMatch(segment -> segment.toUpperCase(java.util.Locale.ROOT).startsWith("U@"));
    }

    public String computePredictedMatchForTeams(String token1, String team1, String token2, String team2,
                                                Map<String, String> teamGW, Map<String, String> teamRU,
                                                Map<String, String> teamTP) {
        if (slotStatusEvaluator.isTeamPredicted(token1, team1, teamGW, teamRU, teamTP)
                && slotStatusEvaluator.isTeamPredicted(token2, team2, teamGW, teamRU, teamTP)) {
            return "predicted";
        }
        if (slotStatusEvaluator.isTeamMaybe(token1, team1, teamGW, teamRU, teamTP)
                || slotStatusEvaluator.isTeamMaybe(token2, team2, teamGW, teamRU, teamTP)) {
            return "alt";
        }
        return "";
    }

}

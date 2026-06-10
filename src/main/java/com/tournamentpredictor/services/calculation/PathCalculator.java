package com.tournamentpredictor.services.calculation;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathCalculator {
    private static final Pattern WINNER_DISPLAY_PATTERN = Pattern.compile("^W(\\d+)\\(.+\\)$");

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
        if ("alt".equals(existing) || "alt".equals(incoming)) {
            return "alt";
        }
        if ("upset".equals(existing) || "upset".equals(incoming)) {
            return "upset";
        }
        return existing;
    }

    public String computePathFromSlots(String path1, String path2) {
        if ("predicted".equals(path1) && "predicted".equals(path2)) {
            return "predicted";
        }
        if ("upset".equals(path1) || "upset".equals(path2)) {
            return "upset";
        }
        if ("alt".equals(path1) || "alt".equals(path2)) {
            return "alt";
        }
        return "";
    }

    /** Classify a complete displayed route from its actual knockout history. */
    public String classifyCompletedRoute(String candidatePath, String team1Chain, String team2Chain) {
        if (containsUpset(team1Chain) || containsUpset(team2Chain)) return "upset";
        return "predicted".equalsIgnoreCase(candidatePath) ? "predicted" : "alt";
    }

    private static boolean containsUpset(String chain) {
        if (chain == null || chain.isBlank()) return false;
        return java.util.Arrays.stream(chain.split(" > "))
                .map(String::trim)
                .anyMatch(segment -> segment.toUpperCase(java.util.Locale.ROOT).startsWith("U@"));
    }

    public String computePredictedMatch(String token1, String display1, String token2, String display2,
                                        Map<String, String> teamGW, Map<String, String> teamRU,
                                        Map<String, String> teamTP) {
        if (slotStatusEvaluator.isDisplayPredicted(token1, display1, teamGW, teamRU, teamTP)
                && slotStatusEvaluator.isDisplayPredicted(token2, display2, teamGW, teamRU, teamTP)) {
            return "predicted";
        }
        if (slotStatusEvaluator.isDisplayMaybe(token1, display1, teamGW, teamRU, teamTP)
                || slotStatusEvaluator.isDisplayMaybe(token2, display2, teamGW, teamRU, teamTP)) {
            return "alt";
        }
        return "";
    }

    public String computeLast16PredictedMatch(String team1Display, String team2Display,
                                              Map<String, String> last32Predicted) {
        String path1 = lookupLast32Predicted(team1Display, last32Predicted);
        String path2 = lookupLast32Predicted(team2Display, last32Predicted);
        return computePathFromSlots(path1, path2);
    }

    public String lookupLast32Predicted(String teamDisplay, Map<String, String> last32Predicted) {
        if (teamDisplay == null) {
            return "";
        }
        Matcher matcher = WINNER_DISPLAY_PATTERN.matcher(teamDisplay.trim());
        if (!matcher.matches()) {
            return "";
        }
        String matchId = "M" + matcher.group(1);
        String teamName = eloCalculator.extractTeamName(teamDisplay);
        return last32Predicted.getOrDefault(matchId + "|" + teamName, "");
    }
}

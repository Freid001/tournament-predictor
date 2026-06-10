package com.tournamentpredictor.services.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimulationViewDataService {
    private SimulationViewDataService() {
    }

    public static String simulationRoundForMatchView(String round) {
        return switch (round) {
            case "last_32_match" -> "last_32";
            case "last_16_match" -> "last_16";
            case "last_8_match" -> "last_8";
            case "last_4_match" -> "last_4";
            case "final_match" -> "final";
            default -> null;
        };
    }

    public static String advanceColumnForRound(String round) {
        return switch (round) {
            case "last_32" -> "reach_last_16";
            case "last_16" -> "reach_last_8";
            case "last_8" -> "reach_last_4";
            case "last_4" -> "reach_final";
            case "final" -> "champion";
            default -> "";
        };
    }

    public static Map<String, String> simulationAdvanceMap(List<Map<String, String>> rows, String column) {
        Map<String, String> percentages = new LinkedHashMap<>();
        if (column == null || column.isBlank()) return percentages;
        for (Map<String, String> row : rows) {
            String team = row.getOrDefault("team", "").trim();
            String percentage = row.getOrDefault(column, "").trim();
            if (!team.isBlank() && !percentage.isBlank()) percentages.put(team, percentage);
        }
        return percentages;
    }

    public static Map<String, String> simulationMatchupLikelihoodMap(List<Map<String, String>> rows, String stage) {
        Map<String, String> percentages = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            if (!stage.equals(row.getOrDefault("stage", ""))) continue;
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1 = row.getOrDefault("team1", "").trim();
            String team2 = row.getOrDefault("team2", "").trim();
            String percentage = row.getOrDefault("matchup_pct", "").trim();
            if (!matchId.isBlank() && !team1.isBlank() && !team2.isBlank() && !percentage.isBlank()) {
                percentages.putIfAbsent(RouteLikelihoodService.matchupLikelihoodKey(matchId, team1, team2), percentage);
            }
        }
        return percentages;
    }

    public static Map<String, String> simulationMatchupRunsMap(List<Map<String, String>> rows, String stage) {
        Map<String, String> runs = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            if (!stage.equals(row.getOrDefault("stage", ""))) continue;
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1 = row.getOrDefault("team1", "").trim();
            String team2 = row.getOrDefault("team2", "").trim();
            String matchupRuns = row.getOrDefault("matchup_runs", "").trim();
            if (!matchId.isBlank() && !team1.isBlank() && !team2.isBlank() && !matchupRuns.isBlank()) {
                runs.putIfAbsent(RouteLikelihoodService.matchupLikelihoodKey(matchId, team1, team2), matchupRuns);
                runs.putIfAbsent(RouteLikelihoodService.matchupLikelihoodKey(matchId, team2, team1), matchupRuns);
            }
        }
        return runs;
    }
}

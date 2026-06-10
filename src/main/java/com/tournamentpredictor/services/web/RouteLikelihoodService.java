package com.tournamentpredictor.services.web;

import com.tournamentpredictor.services.calculation.EloCalculator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RouteLikelihoodService {
    private RouteLikelihoodService() {
    }

    public static String preferredWinner(Map<String, String> row) {
        for (String key : List.of("predicted_winner", "prediction", "elo")) {
            String raw = row.getOrDefault(key, "");
            String value = raw == null ? "" : raw.trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    public static List<Map<String, String>> routeAverageLast16Rows(List<Map<String, String>> matchupRows) {
        return routeAverageLast16Rows(matchupRows, List.of());
    }

    public static List<Map<String, String>> routeAverageLast16Rows(List<Map<String, String>> matchupRows,
                                                                   List<Map<String, String>> groupRows) {
        Map<String, Map<String, Double>> slotProbabilities = groupSlotProbabilities(groupRows);
        boolean weighted = !slotProbabilities.isEmpty();
        EloCalculator eloCalculator = new EloCalculator();
        Map<String, double[]> totals = new LinkedHashMap<>();
        int routeRows = 0;
        for (Map<String, String> row : matchupRows) {
            String team1Display = row.getOrDefault("team1", "");
            String team2Display = row.getOrDefault("team2", "");
            String team1 = eloCalculator.extractTeamName(team1Display);
            String team2 = eloCalculator.extractTeamName(team2Display);
            String prediction = preferredWinner(row);
            String winner = eloCalculator.parseTeamFromPrediction(prediction);
            int winnerPct = eloCalculator.parsePctFromPrediction(prediction);
            if (team1.isBlank() || team2.isBlank() || winner.isBlank() || winnerPct <= 0) {
                continue;
            }
            double routeWeight = weighted
                    ? slotProbability(team1, slotToken(team1Display), slotProbabilities)
                    * slotProbability(team2, slotToken(team2Display), slotProbabilities)
                    : 1.0;
            if (routeWeight <= 0) {
                continue;
            }
            routeRows++;
            addRoutePct(totals, team1, winner.equalsIgnoreCase(team1) ? winnerPct : 100 - winnerPct, routeWeight);
            addRoutePct(totals, team2, winner.equalsIgnoreCase(team2) ? winnerPct : 100 - winnerPct, routeWeight);
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : totals.entrySet()) {
            double[] total = entry.getValue();
            if (total[1] == 0) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("team", entry.getKey());
            row.put("reach_last_16", String.format(java.util.Locale.ROOT, "%.1f", total[0] / total[1]));
            row.put("route_matchups", String.valueOf(routeRows));
            if (weighted) {
                row.put("route_weighted", "true");
            }
            rows.add(row);
        }
        return rows;
    }

    public static Map<String, String> routeWeightedNextRoundMatchupLikelihoodMap(
            List<Map<String, String>> nextRoundRows, List<Map<String, String>> feederRows,
            List<Map<String, String>> groupRows) {
        Map<String, Map<String, Double>> slotProbabilities = groupSlotProbabilities(groupRows);
        EloCalculator eloCalculator = new EloCalculator();
        Map<String, Double> routeTotals = new LinkedHashMap<>();
        Map<String, Double> teamWinTotals = new LinkedHashMap<>();

        for (Map<String, String> row : feederRows) {
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1Display = row.getOrDefault("team1", "");
            String team2Display = row.getOrDefault("team2", "");
            String team1 = eloCalculator.extractTeamName(team1Display);
            String team2 = eloCalculator.extractTeamName(team2Display);
            String prediction = preferredWinner(row);
            String winner = eloCalculator.parseTeamFromPrediction(prediction);
            int winnerPct = eloCalculator.parsePctFromPrediction(prediction);
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank() || winner.isBlank() || winnerPct <= 0) continue;

            double routeWeight = slotProbabilities.isEmpty() ? 1.0
                    : slotProbability(team1, slotToken(team1Display), slotProbabilities)
                    * slotProbability(team2, slotToken(team2Display), slotProbabilities);
            if (routeWeight <= 0) continue;
            routeTotals.merge(matchId, routeWeight, Double::sum);
            double team1Win = winner.equalsIgnoreCase(team1) ? winnerPct / 100.0 : (100 - winnerPct) / 100.0;
            teamWinTotals.merge(matchId + "|" + team1, routeWeight * team1Win, Double::sum);
            teamWinTotals.merge(matchId + "|" + team2, routeWeight * (1.0 - team1Win), Double::sum);
        }

        Map<String, String> likelihoods = new LinkedHashMap<>();
        for (Map<String, String> row : nextRoundRows) {
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1Display = row.getOrDefault("team1", "");
            String team2Display = row.getOrDefault("team2", "");
            String team1 = eloCalculator.extractTeamName(team1Display);
            String team2 = eloCalculator.extractTeamName(team2Display);
            String feeder1 = winnerTokenMatchId(team1Display);
            String feeder2 = winnerTokenMatchId(team2Display);
            double total1 = routeTotals.getOrDefault(feeder1, 0.0);
            double total2 = routeTotals.getOrDefault(feeder2, 0.0);
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank() || total1 <= 0 || total2 <= 0) continue;
            double likelihood = teamWinTotals.getOrDefault(feeder1 + "|" + team1, 0.0) / total1
                    * teamWinTotals.getOrDefault(feeder2 + "|" + team2, 0.0) / total2;
            if (likelihood > 0) {
                likelihoods.put(matchupLikelihoodKey(matchId, team1, team2),
                        formatLikelihoodPct(likelihood));
            }
        }
        return likelihoods;
    }

    public static Map<String, String> routeWeightedNextRoundMatchupLikelihoodMap(
            List<Map<String, String>> nextRoundRows, List<Map<String, String>> feederRows,
            Map<String, String> feederMatchupLikelihoods) {
        EloCalculator eloCalculator = new EloCalculator();
        Map<String, Double> routeTotals = new LinkedHashMap<>();
        Map<String, Double> teamWinTotals = new LinkedHashMap<>();
        for (Map<String, String> row : feederRows) {
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1Display = row.getOrDefault("team1", "");
            String team2Display = row.getOrDefault("team2", "");
            String team1 = eloCalculator.extractTeamName(team1Display);
            String team2 = eloCalculator.extractTeamName(team2Display);
            String prediction = preferredWinner(row);
            String winner = eloCalculator.parseTeamFromPrediction(prediction);
            int winnerPct = eloCalculator.parsePctFromPrediction(prediction);
            double routeWeight = parseDoubleOrZero(feederMatchupLikelihoods.getOrDefault(
                    matchupLikelihoodKey(matchId, team1, team2), "")) / 100.0;
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank() || winner.isBlank()
                    || winnerPct <= 0 || routeWeight <= 0) continue;
            routeTotals.merge(matchId, routeWeight, Double::sum);
            double team1Win = winner.equalsIgnoreCase(team1) ? winnerPct / 100.0 : (100 - winnerPct) / 100.0;
            teamWinTotals.merge(matchId + "|" + team1, routeWeight * team1Win, Double::sum);
            teamWinTotals.merge(matchId + "|" + team2, routeWeight * (1.0 - team1Win), Double::sum);
        }
        return nextRoundLikelihoods(nextRoundRows, routeTotals, teamWinTotals, eloCalculator);
    }

    public static Map<String, String> nextRoundLikelihoods(
            List<Map<String, String>> nextRoundRows, Map<String, Double> routeTotals,
            Map<String, Double> teamWinTotals, EloCalculator eloCalculator) {
        Map<String, String> likelihoods = new LinkedHashMap<>();
        for (Map<String, String> row : nextRoundRows) {
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1Display = row.getOrDefault("team1", "");
            String team2Display = row.getOrDefault("team2", "");
            String team1 = eloCalculator.extractTeamName(team1Display);
            String team2 = eloCalculator.extractTeamName(team2Display);
            String feeder1 = winnerTokenMatchId(team1Display);
            String feeder2 = winnerTokenMatchId(team2Display);
            double total1 = routeTotals.getOrDefault(feeder1, 0.0);
            double total2 = routeTotals.getOrDefault(feeder2, 0.0);
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank() || total1 <= 0 || total2 <= 0) continue;
            double likelihood = teamWinTotals.getOrDefault(feeder1 + "|" + team1, 0.0) / total1
                    * teamWinTotals.getOrDefault(feeder2 + "|" + team2, 0.0) / total2;
            if (likelihood > 0) likelihoods.put(matchupLikelihoodKey(matchId, team1, team2),
                    formatLikelihoodPct(likelihood));
        }
        return likelihoods;
    }

    public static String formatLikelihoodPct(double probability) {
        double pct = probability * 100.0;
        if (pct >= 0.05) return String.format(java.util.Locale.ROOT, "%.1f", pct + 1e-9);
        return java.math.BigDecimal.valueOf(pct)
                .setScale(12, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    public static double parseDoubleOrZero(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    public static String winnerTokenMatchId(String display) {
        String token = slotToken(display);
        return token.matches("W\\d+") ? "M" + token.substring(1) : "";
    }

    public static Map<String, String> matchupLikelihoodMap(List<Map<String, String>> matchupRows,
                                                           List<Map<String, String>> groupRows) {
        Map<String, Map<String, Double>> slotProbabilities = groupSlotProbabilities(groupRows);
        Map<String, String> likelihoods = new LinkedHashMap<>();
        if (slotProbabilities.isEmpty()) {
            return likelihoods;
        }
        EloCalculator eloCalculator = new EloCalculator();
        for (Map<String, String> row : matchupRows) {
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1Display = row.getOrDefault("team1", "");
            String team2Display = row.getOrDefault("team2", "");
            String team1 = eloCalculator.extractTeamName(team1Display);
            String team2 = eloCalculator.extractTeamName(team2Display);
            double likelihood = slotProbability(team1, slotToken(team1Display), slotProbabilities)
                    * slotProbability(team2, slotToken(team2Display), slotProbabilities);
            if (!matchId.isEmpty() && !team1.isEmpty() && !team2.isEmpty() && likelihood > 0) {
                likelihoods.put(matchupLikelihoodKey(matchId, team1, team2),
                        formatLikelihoodPct(likelihood));
            }
        }
        return likelihoods;
    }

    public static String matchupLikelihoodKey(String matchId, String team1, String team2) {
        return matchId + "|" + team1 + "|" + team2;
    }

    public static void addRoutePct(Map<String, double[]> totals, String team, double pct, double weight) {
        double[] total = totals.computeIfAbsent(team, ignored -> new double[2]);
        total[0] += pct * weight;
        total[1] += weight;
    }

    public static Map<String, Map<String, Double>> groupSlotProbabilities(List<Map<String, String>> groupRows) {
        Map<String, Map<String, Double>> probabilities = new LinkedHashMap<>();
        for (Map<String, String> row : groupRows) {
            String team = row.getOrDefault("team", "").trim();
            if (team.isEmpty()) {
                continue;
            }
            Map<String, Double> slots = new LinkedHashMap<>();
            slots.put("1", flagProbability(row.getOrDefault("group_winner", "")));
            slots.put("2", flagProbability(row.getOrDefault("runner_up", "")));
            slots.put("3", flagProbability(row.getOrDefault("3rd_place", "")));

            String predictedPosition = row.getOrDefault("predicted_position", "").trim();
            String position = predictedPosition.isEmpty() ? "" : predictedPosition.substring(0, 1);
            double pct = probabilityFromText(predictedPosition);
            if (("1".equals(position) || "2".equals(position) || "3".equals(position)) && pct > 0) {
                slots.put(position, Math.max(slots.getOrDefault(position, 0.0), pct));
            }
            probabilities.put(team, slots);
        }
        return probabilities;
    }

    public static double slotProbability(String team, String slotToken, Map<String, Map<String, Double>> probabilities) {
        if (probabilities.isEmpty()) {
            return 1.0;
        }
        String slot = slotType(slotToken);
        if (slot.isEmpty()) {
            return 0.0;
        }
        return probabilities.getOrDefault(team, Map.of()).getOrDefault(slot, 0.0);
    }

    public static String slotToken(String display) {
        if (display == null) {
            return "";
        }
        int paren = display.indexOf("(");
        return (paren >= 0 ? display.substring(0, paren) : display).trim();
    }

    public static String slotType(String slotToken) {
        if (slotToken == null || slotToken.isBlank()) {
            return "";
        }
        String last = slotToken.substring(slotToken.length() - 1);
        return "1".equals(last) || "2".equals(last) || "3".equals(last) ? last : "";
    }

    public static double probabilityFromText(String value) {
        int open = value.indexOf("(");
        int pct = value.indexOf("%", open + 1);
        if (open < 0 || pct <= open) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.substring(open + 1, pct).trim()) / 100.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static double flagProbability(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "yes" -> 0.75;
            case "maybe" -> 0.35;
            default -> 0.0;
        };
    }
}

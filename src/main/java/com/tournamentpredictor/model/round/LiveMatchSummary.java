package com.tournamentpredictor.model.round;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class LiveMatchSummary {
        private final String team1;
        private final Map<String, Integer> winnerCounts = new LinkedHashMap<>();
        private int matchupRuns;
        private String matchupPct = "";

        public LiveMatchSummary(String team1) {
            this.team1 = team1 == null ? "" : team1.trim();
        }

        public void add(Map<String, String> row) {
            String winner = safeTrim(row.getOrDefault("winner", ""));
            int count = parseIntSafe(row.getOrDefault("count", "0"));
            if (!winner.isBlank()) {
                winnerCounts.merge(winner, count, Integer::sum);
            }
            if (matchupRuns <= 0) {
                matchupRuns = parseIntSafe(row.getOrDefault("matchup_runs", "0"));
            }
            if (matchupPct.isBlank()) {
                matchupPct = safeTrim(row.getOrDefault("matchup_pct", ""));
            }
        }

        public String predictionText() {
            String winner = winnerCounts.entrySet().stream()
                    .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry::getKey))
                    .map(Map.Entry::getKey)
                    .orElse(team1);
            int winnerCount = winnerCounts.getOrDefault(winner, 0);
            int pct = matchupRuns > 0 ? (int) Math.round((winnerCount * 100.0) / matchupRuns) : 0;
            return winner + " (" + pct + "%)";
        }

        public String matchupPct() {
            return matchupPct;
        }

        public String matchupRuns() {
            return matchupRuns > 0 ? String.valueOf(matchupRuns) : "";
        }

        private static String safeTrim(String value) {
            return value == null ? "" : value.trim();
        }

        private static int parseIntSafe(String value) {
            try {
                return Integer.parseInt(safeTrim(value));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

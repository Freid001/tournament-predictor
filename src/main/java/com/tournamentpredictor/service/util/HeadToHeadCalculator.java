package com.tournamentpredictor.service.util;

import com.tournamentpredictor.config.PredictionConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

public class HeadToHeadCalculator {
    private final double decayRate;

    private final Path projectRoot;
    private final Map<String, List<String[]>> historyCache = new HashMap<>();

    public HeadToHeadCalculator(Path projectRoot) {
        this(projectRoot, 5.0);
    }

    public HeadToHeadCalculator(Path projectRoot, PredictionConfig config) {
        this(projectRoot, config.getHalfLifeYears());
    }

    private HeadToHeadCalculator(Path projectRoot, double halfLifeYears) {
        this.projectRoot = projectRoot;
        this.decayRate = Math.log(2) / halfLifeYears;
    }

    public String computeCombinedPrediction(String team1Name, String team2Name) {
        return computePrediction(team1Name, team2Name, false, false);
    }

    public OptionalDouble computeRawCombinedWinRate(String team1Name, String team2Name) {
        return computeRawWinRate(team1Name, team2Name, false, false);
    }

    public String computeFriendlyPrediction(String team1Name, String team2Name) {
        return computePrediction(team1Name, team2Name, false, true);
    }

    public String computeCompetitionPrediction(String team1Name, String team2Name) {
        return computePrediction(team1Name, team2Name, true, false);
    }

    /** Returns team1's raw win rate (0.0–1.0) against team2 for competition matches only, or empty if no data. */
    public OptionalDouble computeRawCompWinRate(String team1Name, String team2Name) {
        return computeRawWinRate(team1Name, team2Name, true, false);
    }

    /** Returns team1's raw win rate (0.0–1.0) against team2 for friendly matches only, or empty if no data. */
    public OptionalDouble computeRawFriendlyWinRate(String team1Name, String team2Name) {
        return computeRawWinRate(team1Name, team2Name, false, true);
    }

    private String computePrediction(String team1Name, String team2Name, boolean competitionOnly, boolean friendlyOnly) {
        OptionalDouble opt = computeRawWinRate(team1Name, team2Name, competitionOnly, friendlyOnly);
        double winRate = opt.orElse(0.5);
        int roundedPct = (int) Math.round(winRate * 100.0);
        if (roundedPct == 50) {
            return team1Name + " (50%)";
        }
        if (roundedPct > 50) {
            return team1Name + " (" + roundedPct + "%)";
        }
        return team2Name + " (" + (100 - roundedPct) + "%)";
    }

    private OptionalDouble computeRawWinRate(String team1Name, String team2Name, boolean competitionOnly, boolean friendlyOnly) {
        List<String[]> rows = loadHistory(team1Name);
        if (rows.isEmpty()) {
            rows = loadHistory(team2Name);
        }
        if (rows.isEmpty()) {
            return OptionalDouble.empty();
        }

        int currentYear = LocalDate.now().getYear();
        double weightedOutcomes = 0.0;
        double totalWeight = 0.0;

        for (String[] row : rows) {
            if (row.length < 8) {
                continue;
            }
            boolean directOrder = team1Name.equals(row[3].trim()) && team2Name.equals(row[4].trim());
            boolean reverseOrder = team2Name.equals(row[3].trim()) && team1Name.equals(row[4].trim());
            if (!directOrder && !reverseOrder) {
                continue;
            }
            if (competitionOnly && "F".equals(row[7].trim())) {
                continue;
            }
            if (friendlyOnly && !"F".equals(row[7].trim())) {
                continue;
            }

            try {
                int year = Integer.parseInt(row[0].trim());
                int score1 = Integer.parseInt((directOrder ? row[5] : row[6]).trim());
                int score2 = Integer.parseInt((directOrder ? row[6] : row[5]).trim());
                double outcome = score1 > score2 ? 1.0 : score1 < score2 ? 0.0 : 0.5;
                double weight = Math.exp(-decayRate * (currentYear - year));
                weightedOutcomes += weight * outcome;
                totalWeight += weight;
            } catch (NumberFormatException ignored) {
            }
        }

        return totalWeight == 0.0 ? OptionalDouble.empty() : OptionalDouble.of(weightedOutcomes / totalWeight);
    }

    private List<String[]> loadHistory(String teamName) {
        return historyCache.computeIfAbsent(teamName, this::readHistory);
    }

    private List<String[]> readHistory(String teamName) {
        try {
            Path historyFile = projectRoot.resolve("data").resolve("elo").resolve("history").resolve(teamName + ".tsv");
            if (!Files.exists(historyFile)) {
                return List.of();
            }
            return Files.readAllLines(historyFile).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("year"))
                    .map(line -> line.split("\\t", -1))
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
            return List.of();
        }
    }
}

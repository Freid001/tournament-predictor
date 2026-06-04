package com.tournamentpredictor.service.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes qualification form scores from ELO history TSV files, using WC qualifier
 * match types (WQ, WQS, FQ) on or after sinceYear.
 * Host nations with no qualifying matches will have no data (hasData() returns false).
 * Score formula: 70% PPG + 30% normalised GD per game.
 */
public class QualificationFormCalculator {

    private static final java.util.Set<String> QUALIFIER_TYPES = java.util.Set.of("WQ", "WQS", "FQ");

    private final Map<String, Double> formScores = new HashMap<>();

    public QualificationFormCalculator(Path historyDir, int sinceYear) {
        if (!Files.exists(historyDir)) return;
        try (var stream = Files.list(historyDir)) {
            stream.filter(p -> p.toString().endsWith(".tsv")).forEach(p -> {
                try {
                    String teamName = p.getFileName().toString().replace(".tsv", "");
                    int played = 0, won = 0, drawn = 0, gf = 0, ga = 0;
                    List<String> lines = Files.readAllLines(p);
                    for (int i = 1; i < lines.size(); i++) {
                        String[] cols = lines.get(i).split("\t", -1);
                        if (cols.length < 8) continue;
                        int year, homeScore, awayScore;
                        try {
                            year      = Integer.parseInt(cols[0].trim());
                            homeScore = Integer.parseInt(cols[5].trim());
                            awayScore = Integer.parseInt(cols[6].trim());
                        } catch (Exception e) { continue; }
                        if (year < sinceYear) continue;
                        if (!QUALIFIER_TYPES.contains(cols[7].trim())) continue;
                        boolean isHome = teamName.equals(cols[3].trim());
                        int teamScore = isHome ? homeScore : awayScore;
                        int oppScore  = isHome ? awayScore : homeScore;
                        played++;
                        gf += teamScore; ga += oppScore;
                        if (teamScore > oppScore) won++;
                        else if (teamScore == oppScore) drawn++;
                    }
                    if (played > 0) {
                        double ppg    = (3.0 * won + drawn) / (3.0 * played);
                        double gdNorm = Math.min(1.0, Math.max(0.0, ((double) (gf - ga) / played + 3.0) / 6.0));
                        formScores.put(teamName, 0.7 * ppg + 0.3 * gdNorm);
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    /** Returns team's qual form score 0.0-1.0. Returns 0.5 (neutral) if no data. */
    public double getFormScore(String teamName) {
        return formScores.getOrDefault(teamName, 0.5);
    }

    /** True if real qualification data exists for this team (false for hosts, teams with no WC qualifier games). */
    public boolean hasData(String teamName) {
        return formScores.containsKey(teamName);
    }
}

package com.tournamentpredictor.service.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes current form scores from ELO history TSV files, using selected
 * match types between sinceYear and untilYear (inclusive), optionally capped at a maximum date.
 * If maxGames > 0, the year filter is applied first, then the most recent maxGames matches are used.
 * Host nations with no qualifying matches will have no data (hasData() returns false).
 * Score formula: 60% PPG + 20% goals scored per game + 20% inverted goals conceded per game.
 */
public class QualificationFormCalculator {

    private static final Set<String> DEFAULT_MATCH_TYPES = Set.of("WQ", "WQS", "FQ");

    private final Map<String, Double> formScores = new HashMap<>();
    private final int eloMax;

    public QualificationFormCalculator(Path historyDir, int sinceYear, int untilYear, int eloMax) {
        this(historyDir, sinceYear, untilYear, eloMax, DEFAULT_MATCH_TYPES, 0, null);
    }

    public QualificationFormCalculator(Path historyDir, int sinceYear, int untilYear, int eloMax, Set<String> matchTypes) {
        this(historyDir, sinceYear, untilYear, eloMax, matchTypes, 0, null);
    }

    public QualificationFormCalculator(Path historyDir, int sinceYear, int untilYear, int eloMax, Set<String> matchTypes, int maxGames) {
        this(historyDir, sinceYear, untilYear, eloMax, matchTypes, maxGames, null);
    }

    public QualificationFormCalculator(Path historyDir, int sinceYear, int untilYear, int eloMax,
                                       Set<String> matchTypes, int maxGames, LocalDate maxDate) {
        this.eloMax = eloMax;
        if (!Files.exists(historyDir)) return;
        try (var stream = Files.list(historyDir)) {
            stream.filter(p -> p.toString().endsWith(".tsv")).forEach(p -> {
                try {
                    String teamName = p.getFileName().toString().replace(".tsv", "");
                    List<String> lines = Files.readAllLines(p);
                    // Collect all matching rows first (file is chronological, oldest first)
                    List<String[]> matched = new java.util.ArrayList<>();
                    for (int i = 1; i < lines.size(); i++) {
                        String[] cols = lines.get(i).split("\t", -1);
                        if (cols.length < 8) continue;
                        int year;
                        try { year = Integer.parseInt(cols[0].trim()); } catch (Exception e) { continue; }
                        if (year < sinceYear || year > untilYear) continue;
                        if (maxDate != null) {
                            try {
                                LocalDate matchDate = LocalDate.of(year,
                                        Integer.parseInt(cols[1].trim()), Integer.parseInt(cols[2].trim()));
                                if (!matchDate.isBefore(maxDate)) continue;
                            } catch (DateTimeException | NumberFormatException e) {
                                continue;
                            }
                        }
                        if (!matchTypes.contains(cols[7].trim())) continue;
                        matched.add(cols);
                    }
                    // If maxGames set, take the last N
                    if (maxGames > 0 && matched.size() > maxGames) {
                        matched = matched.subList(matched.size() - maxGames, matched.size());
                    }
                    int played = 0, won = 0, drawn = 0, gf = 0, ga = 0;
                    for (String[] cols : matched) {
                        int homeScore, awayScore;
                        try {
                            homeScore = Integer.parseInt(cols[5].trim());
                            awayScore = Integer.parseInt(cols[6].trim());
                        } catch (Exception e) { continue; }
                        boolean isHome = teamName.equals(cols[3].trim());
                        int teamScore = isHome ? homeScore : awayScore;
                        int oppScore  = isHome ? awayScore : homeScore;
                        played++;
                        gf += teamScore;
                        ga += oppScore;
                        if (teamScore > oppScore) won++;
                        else if (teamScore == oppScore) drawn++;
                    }
                    if (played > 0) {
                        double ppg = (3.0 * won + drawn) / (3.0 * played);
                        double gfNorm = Math.min(1.0, Math.max(0.0, ((double) gf / played) / 3.0));
                        double gaNormInv = Math.min(1.0, Math.max(0.0, 1.0 - ((double) ga / played) / 3.0));
                        formScores.put(teamName, 0.6 * ppg + 0.2 * gfNorm + 0.2 * gaNormInv);
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    /** Returns team's qual form score 0.0-1.0. Returns 0.5 (neutral) if no data. */
    public double getFormScore(String teamName) {
        return formScores.getOrDefault(teamName, 0.5);
    }

    /** Returns ELO delta from qual form: range approx -eloMax to +eloMax. Returns 0 if no data (hosts, etc.). */
    public int getQualBonus(String teamName) {
        if (!formScores.containsKey(teamName)) return 0;
        return (int) Math.round((formScores.get(teamName) - 0.5) * eloMax * 2);
    }

    /** True if real qualification data exists for this team (false for hosts, teams with no WC qualifier games). */
    public boolean hasData(String teamName) {
        return formScores.containsKey(teamName);
    }
}

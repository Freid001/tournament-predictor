package com.tournamentpredictor.services.calculation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes recent friendly form scores from ELO history TSV files, using the
 * last N matches of type F (friendly) or FT (friendly tournament).
 * Score formula: 70% PPG + 30% normalised GD per game. No decay — last N games
 * are equally weighted.
 */
public class FriendliesFormCalculator {

    private static final java.util.Set<String> FRIENDLY_TYPES = java.util.Set.of("F", "FT");

    private final Map<String, Double> formScores = new HashMap<>();

    public FriendliesFormCalculator(Path historyDir, int lastN) {
        if (!Files.exists(historyDir)) return;
        try (var stream = Files.list(historyDir)) {
            stream.filter(p -> p.toString().endsWith(".tsv")).forEach(p -> {
                try {
                    String teamName = p.getFileName().toString().replace(".tsv", "");
                    // buffer: [year, month, day, teamScore, oppScore]
                    List<int[]> buffer = new ArrayList<>();
                    List<String> lines = Files.readAllLines(p);
                    for (int i = 1; i < lines.size(); i++) {
                        String[] cols = lines.get(i).split("\t", -1);
                        if (cols.length < 8) continue;
                        if (!FRIENDLY_TYPES.contains(cols[7].trim())) continue;
                        int year, month, day, homeScore, awayScore;
                        try {
                            year      = Integer.parseInt(cols[0].trim());
                            month     = Integer.parseInt(cols[1].trim());
                            day       = Integer.parseInt(cols[2].trim());
                            homeScore = Integer.parseInt(cols[5].trim());
                            awayScore = Integer.parseInt(cols[6].trim());
                        } catch (Exception e) { continue; }
                        boolean isHome = teamName.equals(cols[3].trim());
                        int teamScore = isHome ? homeScore : awayScore;
                        int oppScore  = isHome ? awayScore : homeScore;
                        // sort by integer date fields to avoid LocalDate parsing issues with bad data
                        buffer.add(new int[]{year, month, day, teamScore, oppScore});
                    }
                    if (buffer.isEmpty()) return;
                    buffer.sort((a, b) -> {
                        if (a[0] != b[0]) return Integer.compare(a[0], b[0]);
                        if (a[1] != b[1]) return Integer.compare(a[1], b[1]);
                        return Integer.compare(a[2], b[2]);
                    });
                    int take = Math.min(lastN, buffer.size());
                    List<int[]> recent = buffer.subList(buffer.size() - take, buffer.size());
                    int played = 0, won = 0, drawn = 0, gf = 0, ga = 0;
                    for (int[] g : recent) {
                        played++;
                        gf += g[3]; ga += g[4];
                        if (g[3] > g[4]) won++;
                        else if (g[3] == g[4]) drawn++;
                    }
                    double ppg    = (3.0 * won + drawn) / (3.0 * played);
                    double gdNorm = Math.min(1.0, Math.max(0.0, ((double) (gf - ga) / played + 3.0) / 6.0));
                    formScores.put(teamName, 0.7 * ppg + 0.3 * gdNorm);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    /** Returns team's friendly form score 0.0-1.0. Returns 0.5 (neutral) if no data. */
    public double getFormScore(String teamName) {
        return formScores.getOrDefault(teamName, 0.5);
    }

    /** True if friendly form data exists for this team. */
    public boolean hasData(String teamName) {
        return formScores.containsKey(teamName);
    }
}

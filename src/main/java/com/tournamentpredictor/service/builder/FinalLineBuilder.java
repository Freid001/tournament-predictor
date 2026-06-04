package com.tournamentpredictor.service.builder;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.PathCalculator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class FinalLineBuilder {
    private final PathCalculator pathCalculator;
    private final EloCalculator predictionHelper;

    public FinalLineBuilder(PathCalculator pathCalculator, EloCalculator predictionHelper) {
        this.pathCalculator = pathCalculator;
        this.predictionHelper = predictionHelper;
    }

    public List<String> buildFinalLines(Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets,
                                        List<String> last4Rows) {
        Map<String, LinkedHashSet<String>> winnersByMatch = new LinkedHashMap<>();
        Map<String, String> pathByWinner = new LinkedHashMap<>();
        for (String line : last4Rows) {
            if (line.trim().isEmpty() || line.startsWith("match_id")) {
                continue;
            }
            String[] cols = line.split(",", -1);
            if (cols.length < 5) {
                continue;
            }
            String matchId = cols[0].trim();
            String path = cols[3].trim();
            String winnerDisplay = cols[4].trim();
            String winner = predictionHelper.parseTeamFromPrediction(winnerDisplay);
            String team1Name = predictionHelper.extractTeamName(cols[1].trim());
            String team2Name = predictionHelper.extractTeamName(cols[2].trim());
            String loser = winner.equalsIgnoreCase(team1Name) ? team2Name : team1Name;
            if (!winner.isEmpty()) {
                winnersByMatch.computeIfAbsent(matchId, ignored -> new LinkedHashSet<>()).add(winner);
                pathByWinner.merge(matchId + "|" + winner, path, pathCalculator::bestPredicted);
            }
            if (!loser.isEmpty()) {
                pathByWinner.merge(matchId + "|" + loser, "alt", pathCalculator::bestPredicted);
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("match_id,team1,team2,path,elo,do_you_disagree");
        for (CsvLoader.BracketEntry bracket : brackets) {
            if (!"FINAL".equalsIgnoreCase(bracket.stage) || bracket.matchId.isEmpty()
                    || bracket.token1.isEmpty() || bracket.token2.isEmpty()) {
                continue;
            }
            String match1 = "M" + bracket.token1.substring(1);
            String match2 = "M" + bracket.token2.substring(1);
            List<String> winners1 = new ArrayList<>(winnersByMatch.getOrDefault(match1, new LinkedHashSet<>()));
            List<String> winners2 = new ArrayList<>(winnersByMatch.getOrDefault(match2, new LinkedHashSet<>()));
            for (String winner1 : winners1) {
                for (String winner2 : winners2) {
                    if (winner1.equalsIgnoreCase(winner2)) {
                        continue;
                    }
                    String display1 = bracket.token1 + "(" + winner1 + ")";
                    String display2 = bracket.token2 + "(" + winner2 + ")";
                    String path1 = pathByWinner.getOrDefault(match1 + "|" + winner1, "alt");
                    String path2 = pathByWinner.getOrDefault(match2 + "|" + winner2, "alt");
                    String path = pathCalculator.computePathFromSlots(path1, path2);
                    String eloPrediction = predictionHelper.computeEloPrediction(winner1, winner2, eloRatings);
                    lines.add(String.join(",", bracket.matchId, display1, display2, path, eloPrediction, ""));
                }
            }
            lines.add("");
        }
        return lines;
    }
}

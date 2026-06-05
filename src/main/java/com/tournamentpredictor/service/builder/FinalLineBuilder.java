package com.tournamentpredictor.service.builder;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.PathCalculator;
import com.tournamentpredictor.service.util.PathFatigueCalculator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class FinalLineBuilder {

    private final PathCalculator pathCalculator;
    private final EloCalculator predictionHelper;
    private final PathFatigueCalculator pathFatigueCalc;

    public FinalLineBuilder(PathCalculator pathCalculator, EloCalculator predictionHelper,
                            PathFatigueCalculator pathFatigueCalc) {
        this.pathCalculator = pathCalculator;
        this.predictionHelper = predictionHelper;
        this.pathFatigueCalc = pathFatigueCalc;
    }

    public List<String> buildFinalLines(Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets,
                                        List<String> last4Rows) {
        Map<String, LinkedHashSet<String>> winnersByMatch = new LinkedHashMap<>();
        Map<String, String> pathByWinner = new LinkedHashMap<>();
        Map<String, String> tokenByWinner = new LinkedHashMap<>();
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
            String team1Token = cols[1].trim();
            String team2Token = cols[2].trim();
            String team1Name = predictionHelper.extractTeamName(team1Token);
            String team2Name = predictionHelper.extractTeamName(team2Token);
            String winnerToken = winner.equalsIgnoreCase(team1Name) ? team1Token : team2Token;
            String loser = winner.equalsIgnoreCase(team1Name) ? team2Name : team1Name;
            if (!winner.isEmpty()) {
                winnersByMatch.computeIfAbsent(matchId, ignored -> new LinkedHashSet<>()).add(winner);
                pathByWinner.merge(matchId + "|" + winner, path, pathCalculator::bestPredicted);
                tokenByWinner.putIfAbsent(matchId + "|" + winner, winnerToken);
            }
            if (!loser.isEmpty()) {
                pathByWinner.merge(matchId + "|" + loser, "alt", pathCalculator::bestPredicted);
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("match_id,team1,team2,path,elo,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,do_you_disagree");
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
                    String t1 = tokenByWinner.getOrDefault(match1 + "|" + winner1, winner1);
                    String t2 = tokenByWinner.getOrDefault(match2 + "|" + winner2, winner2);
                    String display1 = bracket.token1 + "(" + t1 + ")";
                    String display2 = bracket.token2 + "(" + t2 + ")";
                    String path1 = pathByWinner.getOrDefault(match1 + "|" + winner1, "alt");
                    String path2 = pathByWinner.getOrDefault(match2 + "|" + winner2, "alt");
                    String path = pathCalculator.computePathFromSlots(path1, path2);
                    String[] opp1 = findPriorOpponentWithPath(winner1, last4Rows, eloRatings);
                    String[] opp2 = findPriorOpponentWithPath(winner2, last4Rows, eloRatings);
                    int raw1 = pathFatigueCalc.rawScore(Integer.parseInt(opp1[1]));
                    int raw2 = pathFatigueCalc.rawScore(Integer.parseInt(opp2[1]));
                    int t1ContribW = (int) Math.round(raw1 * pathFatigueCalc.stageMultiplierForRound("last_4"));
                    int t2ContribW = (int) Math.round(raw2 * pathFatigueCalc.stageMultiplierForRound("last_4"));
                    int t1NewTotal = Integer.parseInt(opp1[2]) + t1ContribW;
                    int t2NewTotal = Integer.parseInt(opp2[2]) + t2ContribW;
                    int t1ContribElo = pathFatigueCalc.eloAdjustmentFromWeighted(t1ContribW);
                    int t2ContribElo = pathFatigueCalc.eloAdjustmentFromWeighted(t2ContribW);
                    String t1Seg = opp1[0].isEmpty() ? "" : opp1[0] + ":" + t1ContribElo;
                    String t2Seg = opp2[0].isEmpty() ? "" : opp2[0] + ":" + t2ContribElo;
                    String t1Chain = opp1[3].isEmpty() ? t1Seg : opp1[3] + " > " + t1Seg;
                    String t2Chain = opp2[3].isEmpty() ? t2Seg : opp2[3] + " > " + t2Seg;
                    int t1AdjElo = eloRatings.getOrDefault(winner1, 0) + pathFatigueCalc.eloAdjustmentFromWeighted(t1NewTotal);
                    int t2AdjElo = eloRatings.getOrDefault(winner2, 0) + pathFatigueCalc.eloAdjustmentFromWeighted(t2NewTotal);
                    String adjEloPrediction = predictionHelper.computeEloPredictionFromElos(winner1, winner2, t1AdjElo, t2AdjElo);
                    lines.add(String.join(",", bracket.matchId, display1, display2, path, adjEloPrediction,
                            String.valueOf(t1NewTotal), String.valueOf(t2NewTotal), t1Chain, t2Chain, ""));
                }
            }
            lines.add("");
        }
        return lines;
    }

    private String[] findPriorOpponentWithPath(String teamName, List<String> priorRows, Map<String, Integer> eloRatings) {
        String[] fallback = null;
        for (String row : priorRows) {
            if (row.trim().isEmpty() || row.startsWith("match_id")) continue;
            String[] cols = row.split(",", -1);
            if (cols.length < 5) continue;
            String t1 = predictionHelper.extractTeamName(cols[1].trim());
            String t2 = predictionHelper.extractTeamName(cols[2].trim());
            String winner = predictionHelper.parseTeamFromPrediction(cols[4].trim());
            boolean isPredicted = "predicted".equalsIgnoreCase(cols[3].trim());
            if (winner.equalsIgnoreCase(teamName)) {
                boolean winnerIsT1 = winner.equalsIgnoreCase(t1);
                String loser = winnerIsT1 ? t2 : t1;
                String existingTotal = cols.length > 10 ? (winnerIsT1 ? cols[9].trim() : cols[10].trim()) : "0";
                String existingChain = cols.length > 12 ? (winnerIsT1 ? cols[11].trim() : cols[12].trim()) : "";
                String[] result = new String[]{loser, String.valueOf(eloRatings.getOrDefault(loser, pathFatigueCalc.getTournamentAvgElo())),
                        existingTotal.isEmpty() ? "0" : existingTotal, existingChain};
                if (isPredicted) return result;
                if (fallback == null) fallback = result;
            } else if (fallback == null) {
                boolean isT1 = t1.equalsIgnoreCase(teamName);
                boolean isT2 = t2.equalsIgnoreCase(teamName);
                if (isT1 || isT2) {
                    String opponent = isT1 ? t2 : t1;
                    String existingTotal = cols.length > 10 ? (isT1 ? cols[9].trim() : cols[10].trim()) : "0";
                    String existingChain = cols.length > 12 ? (isT1 ? cols[11].trim() : cols[12].trim()) : "";
                    fallback = new String[]{opponent, String.valueOf(eloRatings.getOrDefault(opponent, pathFatigueCalc.getTournamentAvgElo())),
                            existingTotal.isEmpty() ? "0" : existingTotal, existingChain};
                }
            }
        }
        if (fallback != null) return fallback;
        return new String[]{"", String.valueOf(pathFatigueCalc.getTournamentAvgElo()), "0", ""};
    }
}

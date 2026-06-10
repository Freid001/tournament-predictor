package com.tournamentpredictor.services.prediction.builder;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PathCalculator;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.calculation.TeamEloSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

class LaterRoundLineBuilder {
    private static final String HEADER = "match_id,team1,team2,path,elo,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent";

    private final PathCalculator pathCalculator;
    private final EloCalculator predictionHelper;
    private final PathFatigueCalculator pathFatigueCalc;

    LaterRoundLineBuilder(PathCalculator pathCalculator, EloCalculator predictionHelper,
                          PathFatigueCalculator pathFatigueCalc) {
        this.pathCalculator = pathCalculator;
        this.predictionHelper = predictionHelper;
        this.pathFatigueCalc = pathFatigueCalc;
    }

    List<String> buildLines(Map<String, Integer> eloRatings,
                            List<CsvLoader.BracketEntry> brackets,
                            List<String> priorRows,
                            Map<String, TeamEloSnapshot> snapshots,
                            String bracketStage,
                            String fatigueStage) {
        PriorRound priorRound = parsePriorRound(priorRows);
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);

        for (CsvLoader.BracketEntry bracket : brackets) {
            if (!bracketStage.equalsIgnoreCase(bracket.stage) || bracket.matchId.isEmpty()
                    || bracket.token1.isEmpty() || bracket.token2.isEmpty()) {
                continue;
            }
            String match1 = "M" + bracket.token1.substring(1);
            String match2 = "M" + bracket.token2.substring(1);
            List<String> winners1 = new ArrayList<>(priorRound.winnersByMatch.getOrDefault(match1, new LinkedHashSet<>()));
            List<String> winners2 = new ArrayList<>(priorRound.winnersByMatch.getOrDefault(match2, new LinkedHashSet<>()));

            for (String winner1 : winners1) {
                for (String winner2 : winners2) {
                    if (winner1.equalsIgnoreCase(winner2)) {
                        continue;
                    }
                    lines.add(buildLine(bracket, match1, match2, winner1, winner2,
                            priorRound, priorRows, eloRatings, snapshots, fatigueStage));
                }
            }
            lines.add("");
        }
        return lines;
    }

    private String buildLine(CsvLoader.BracketEntry bracket, String match1, String match2,
                             String winner1, String winner2, PriorRound priorRound,
                             List<String> priorRows, Map<String, Integer> eloRatings,
                             Map<String, TeamEloSnapshot> snapshots, String fatigueStage) {
        String t1 = priorRound.tokenByWinner.getOrDefault(match1 + "|" + winner1, winner1);
        String t2 = priorRound.tokenByWinner.getOrDefault(match2 + "|" + winner2, winner2);
        String display1 = bracket.token1 + "(" + t1 + ")";
        String display2 = bracket.token2 + "(" + t2 + ")";
        String path1 = priorRound.pathByWinner.getOrDefault(match1 + "|" + winner1, "alt");
        String path2 = priorRound.pathByWinner.getOrDefault(match2 + "|" + winner2, "alt");
        String path = pathCalculator.computePathFromSlots(path1, path2);

        PriorOpponent opp1 = findPriorOpponentWithPath(winner1, match1, priorRows, eloRatings);
        PriorOpponent opp2 = findPriorOpponentWithPath(winner2, match2, priorRows, eloRatings);
        FatigueResult fatigue1 = fatigueFor(winner1, opp1, fatigueStage, snapshots);
        FatigueResult fatigue2 = fatigueFor(winner2, opp2, fatigueStage, snapshots);

        path = pathCalculator.classifyCompletedRoute(path, fatigue1.chain, fatigue2.chain);

        int t1AdjElo = eloRatings.getOrDefault(winner1, 0) + fatigue1.adjustedElo;
        int t2AdjElo = eloRatings.getOrDefault(winner2, 0) + fatigue2.adjustedElo;
        String adjEloPrediction = predictionHelper.computeEloPredictionFromElos(winner1, winner2, t1AdjElo, t2AdjElo);

        return String.join(",", bracket.matchId, display1, display2, path, adjEloPrediction,
                String.valueOf(fatigue1.weightedTotal), String.valueOf(fatigue2.weightedTotal),
                fatigue1.chain, fatigue2.chain);
    }

    private PriorRound parsePriorRound(List<String> priorRows) {
        Map<String, LinkedHashSet<String>> winnersByMatch = new LinkedHashMap<>();
        Map<String, String> pathByWinner = new LinkedHashMap<>();
        Map<String, String> tokenByWinner = new LinkedHashMap<>();

        for (String line : priorRows) {
            if (line.trim().isEmpty() || line.startsWith("match_id")) continue;
            String[] cols = line.split(",", -1);
            if (cols.length < 5) continue;
            String matchId = cols[0].trim();
            String path = cols[3].trim();
            String winner = predictionHelper.parseTeamFromPrediction(cols[4].trim());
            String team1Token = cols[1].trim();
            String team2Token = cols[2].trim();
            String team1Name = predictionHelper.extractTeamName(team1Token);
            String team2Name = predictionHelper.extractTeamName(team2Token);
            String winnerToken = winner.equalsIgnoreCase(team1Name) ? team1Token : team2Token;
            String loser = winner.equalsIgnoreCase(team1Name) ? team2Name : team1Name;

            // Matchup tables expose both possible winners. The selected winner retains its
            // path classification; the predicted loser advancing is explicitly an upset.
            if (!winner.isEmpty()) {
                winnersByMatch.computeIfAbsent(matchId, ignored -> new LinkedHashSet<>()).add(winner);
                pathByWinner.merge(matchId + "|" + winner, path, pathCalculator::bestPredicted);
                tokenByWinner.putIfAbsent(matchId + "|" + winner, winnerToken);
            }
            if (!loser.isEmpty()) {
                String loserToken = loser.equalsIgnoreCase(team1Name) ? team1Token : team2Token;
                winnersByMatch.computeIfAbsent(matchId, ignored -> new LinkedHashSet<>()).add(loser);
                pathByWinner.merge(matchId + "|" + loser, "upset", pathCalculator::bestPredicted);
                tokenByWinner.putIfAbsent(matchId + "|" + loser, loserToken);
            }
        }
        return new PriorRound(winnersByMatch, pathByWinner, tokenByWinner);
    }

    private FatigueResult fatigueFor(String team, PriorOpponent opponent, String fatigueStage,
                                     Map<String, TeamEloSnapshot> snapshots) {
        int weightedContribution = pathFatigueCalc.knockoutWeightedContribution(opponent.elo, fatigueStage, opponent.upsetWin);
        int weightedTotal = opponent.existingWeightedTotal + weightedContribution;
        int contributionElo = pathFatigueCalc.eloAdjustmentFromWeighted(weightedContribution);
        String segment = opponent.name.isEmpty() ? "" : (opponent.upsetWin ? "U@" : "K@") + opponent.matchId + "|" + opponent.name + ":" + contributionElo;
        String chain = opponent.existingChain.isEmpty() ? segment : opponent.existingChain + " > " + segment;
        int fatigue = pathFatigueCalc.eloAdjustmentFromWeighted(weightedTotal);
        TeamEloSnapshot snapshot = snapshots != null ? snapshots.get(team) : null;
        int depthLevel = snapshot != null ? snapshot.squadDepthLevel() : 0;
        int adjustedElo = pathFatigueCalc.applyDepthMultiplier(fatigue, depthLevel);
        return new FatigueResult(weightedTotal, adjustedElo, chain);
    }

    private PriorOpponent findPriorOpponentWithPath(String teamName, String sourceMatchId, List<String> priorRows, Map<String, Integer> eloRatings) {
        PriorOpponent winningFallback = null;
        PriorOpponent losingFallback = null;
        for (String row : priorRows) {
            if (row.trim().isEmpty() || row.startsWith("match_id")) continue;
            String[] cols = row.split(",", -1);
            if (cols.length < 5) continue;
            if (!sourceMatchId.equalsIgnoreCase(cols[0].trim())) continue;
            String t1 = predictionHelper.extractTeamName(cols[1].trim());
            String t2 = predictionHelper.extractTeamName(cols[2].trim());
            String winner = predictionHelper.parseTeamFromPrediction(cols[4].trim());
            boolean isPredicted = "predicted".equalsIgnoreCase(cols[3].trim());
            if (winner.equalsIgnoreCase(teamName)) {
                boolean winnerIsT1 = winner.equalsIgnoreCase(t1);
                String loser = winnerIsT1 ? t2 : t1;
                String existingTotal = cols.length > 10 ? (winnerIsT1 ? cols[9].trim() : cols[10].trim()) : "0";
                String existingChain = cols.length > 12 ? (winnerIsT1 ? cols[11].trim() : cols[12].trim()) : "";
                PriorOpponent result = new PriorOpponent(loser,
                        eloRatings.getOrDefault(loser, pathFatigueCalc.getTournamentAvgElo()),
                        parseInt(existingTotal), existingChain, false, cols[0].trim());
                if (isPredicted) return result;
                if (winningFallback == null) winningFallback = result;
            } else if (losingFallback == null) {
                boolean isT1 = t1.equalsIgnoreCase(teamName);
                boolean isT2 = t2.equalsIgnoreCase(teamName);
                if (isT1 || isT2) {
                    String opponent = isT1 ? t2 : t1;
                    String existingTotal = cols.length > 10 ? (isT1 ? cols[9].trim() : cols[10].trim()) : "0";
                    String existingChain = cols.length > 12 ? (isT1 ? cols[11].trim() : cols[12].trim()) : "";
                    losingFallback = new PriorOpponent(opponent,
                            eloRatings.getOrDefault(opponent, pathFatigueCalc.getTournamentAvgElo()),
                            parseInt(existingTotal), existingChain, true, cols[0].trim());
                }
            }
        }
        if (winningFallback != null) return winningFallback;
        if (losingFallback != null) return losingFallback;
        return new PriorOpponent("", pathFatigueCalc.getTournamentAvgElo(), 0, "", false, "");
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) return 0;
        return Integer.parseInt(value.trim());
    }

    private record PriorRound(Map<String, LinkedHashSet<String>> winnersByMatch,
                              Map<String, String> pathByWinner,
                              Map<String, String> tokenByWinner) {}

    private record PriorOpponent(String name, int elo, int existingWeightedTotal, String existingChain, boolean upsetWin, String matchId) {}

    private record FatigueResult(int weightedTotal, int adjustedElo, String chain) {}
}

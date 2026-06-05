package com.tournamentpredictor.service.builder;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.DisplayBuilder;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.PathCalculator;
import com.tournamentpredictor.service.util.PathFatigueCalculator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Last16LineBuilder {

    private final DisplayBuilder displayBuilder;
    private final PathCalculator pathCalculator;
    private final EloCalculator predictionHelper;
    private final PathFatigueCalculator pathFatigueCalc;

    public Last16LineBuilder(DisplayBuilder displayBuilder, PathCalculator pathCalculator,
                             EloCalculator predictionHelper, PathFatigueCalculator pathFatigueCalc) {
        this.displayBuilder = displayBuilder;
        this.pathCalculator = pathCalculator;
        this.predictionHelper = predictionHelper;
        this.pathFatigueCalc = pathFatigueCalc;
    }

    public List<String> buildLast16Lines(Map<String, String> groups, Map<String, String> groupWinner,
                                         Map<String, String> runnerUp, Map<String, String> thirdPlace,
                                         Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets,
                                         List<String> last32Rows) {
        Map<String, String> last32PredByTeam = new LinkedHashMap<>();
        for (String row : last32Rows) {
            if (row.trim().isEmpty() || row.startsWith("match_id")) {
                continue;
            }
            String[] cols = row.split(",", -1);
            if (cols.length < 5) {
                continue;
            }
            String matchId = cols[0].trim();
            String team1Name = predictionHelper.extractTeamName(cols[1].trim());
            String team2Name = predictionHelper.extractTeamName(cols[2].trim());
            String path = cols[3].trim();
            String eloWinner = predictionHelper.parseTeamFromPrediction(cols[4].trim());
            String eloLoser = eloWinner.equalsIgnoreCase(team1Name) ? team2Name : team1Name;
            if (!eloWinner.isEmpty()) {
                last32PredByTeam.merge(matchId + "|" + eloWinner, path, pathCalculator::bestPredicted);
            }
            if (!eloLoser.isEmpty()) {
                last32PredByTeam.merge(matchId + "|" + eloLoser, "alt", pathCalculator::bestPredicted);
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("match_id,team1,team2,path,elo,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,do_you_disagree");
        for (CsvLoader.BracketEntry bracket : brackets) {
            if (!"LAST_16".equalsIgnoreCase(bracket.stage)) {
                continue;
            }
            List<String> displays1 = displayBuilder.buildWinnerDisplays(bracket.token1, groups, groupWinner, runnerUp,
                    thirdPlace, brackets, last32Rows);
            List<String> displays2 = displayBuilder.buildWinnerDisplays(bracket.token2, groups, groupWinner, runnerUp,
                    thirdPlace, brackets, last32Rows);
            for (String display1 : displays1) {
                for (String display2 : displays2) {
                    String team1 = predictionHelper.extractTeamName(display1);
                    String team2 = predictionHelper.extractTeamName(display2);
                    if (team1.equalsIgnoreCase(team2)) {
                        continue;
                    }
                    String path = pathCalculator.computeLast16PredictedMatch(display1, display2, last32PredByTeam);
                    String[] opp1 = findPriorOpponent(team1, last32Rows, eloRatings);
                    String[] opp2 = findPriorOpponent(team2, last32Rows, eloRatings);
                    int raw1 = pathFatigueCalc.rawScore(Integer.parseInt(opp1[1]));
                    int raw2 = pathFatigueCalc.rawScore(Integer.parseInt(opp2[1]));
                    int t1ContribW = (int) Math.round(raw1 * pathFatigueCalc.stageMultiplierForRound("last_32"));
                    int t2ContribW = (int) Math.round(raw2 * pathFatigueCalc.stageMultiplierForRound("last_32"));
                    int t1NewTotal = t1ContribW;
                    int t2NewTotal = t2ContribW;
                    int t1ContribElo = pathFatigueCalc.eloAdjustmentFromWeighted(t1ContribW);
                    int t2ContribElo = pathFatigueCalc.eloAdjustmentFromWeighted(t2ContribW);
                    String t1Chain = opp1[0].isEmpty() ? "" : opp1[0] + ":" + t1ContribElo;
                    String t2Chain = opp2[0].isEmpty() ? "" : opp2[0] + ":" + t2ContribElo;
                    int t1AdjElo = eloRatings.getOrDefault(team1, 0) + pathFatigueCalc.eloAdjustmentFromWeighted(t1NewTotal);
                    int t2AdjElo = eloRatings.getOrDefault(team2, 0) + pathFatigueCalc.eloAdjustmentFromWeighted(t2NewTotal);
                    String adjEloPrediction = predictionHelper.computeEloPredictionFromElos(team1, team2, t1AdjElo, t2AdjElo);
                    lines.add(String.join(",", bracket.matchId, displayBuilder.safe(display1),
                            displayBuilder.safe(display2), path, adjEloPrediction,
                            String.valueOf(t1NewTotal), String.valueOf(t2NewTotal), t1Chain, t2Chain, ""));
                }
            }
            lines.add("");
        }
        return lines;
    }

    /** Returns [loserName, loserElo, "0", ""] — Last16 is the first knockout stage, no prior chain. */
    private String[] findPriorOpponent(String teamName, List<String> priorRows, Map<String, Integer> eloRatings) {
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
                String loser = winner.equalsIgnoreCase(t1) ? t2 : t1;
                String[] result = new String[]{loser, String.valueOf(eloRatings.getOrDefault(loser, pathFatigueCalc.getTournamentAvgElo())), "0", ""};
                if (isPredicted) return result;
                if (fallback == null) fallback = result;
            } else if (fallback == null) {
                if (t1.equalsIgnoreCase(teamName)) fallback = new String[]{t2, String.valueOf(eloRatings.getOrDefault(t2, pathFatigueCalc.getTournamentAvgElo())), "0", ""};
                else if (t2.equalsIgnoreCase(teamName)) fallback = new String[]{t1, String.valueOf(eloRatings.getOrDefault(t1, pathFatigueCalc.getTournamentAvgElo())), "0", ""};
            }
        }
        if (fallback != null) return fallback;
        return new String[]{"", String.valueOf(pathFatigueCalc.getTournamentAvgElo()), "0", ""};
    }
}

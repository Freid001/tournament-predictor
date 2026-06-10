package com.tournamentpredictor.services.prediction.builder;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.bracket.DisplayBuilder;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PathCalculator;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.calculation.TeamEloSnapshot;

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
                                         List<String> last32Rows, Map<String, TeamEloSnapshot> snapshots) {
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
                last32PredByTeam.merge(matchId + "|" + eloLoser, "upset", pathCalculator::bestPredicted);
            }
        }

        Map<String, String> teamGW = new LinkedHashMap<>();
        Map<String, String> teamRU = new LinkedHashMap<>();
        Map<String, String> teamTP = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            String team = entry.getValue();
            String position = entry.getKey();
            if (team != null && !team.isEmpty()) {
                teamGW.put(team, groupWinner.getOrDefault(position, ""));
                teamRU.put(team, runnerUp.getOrDefault(position, ""));
                teamTP.put(team, thirdPlace.getOrDefault(position, ""));
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("match_id,team1,team2,path,elo,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent");
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
                    boolean openingRoundFromGroups = last32Rows == null || last32Rows.isEmpty();
                    String path = openingRoundFromGroups
                            ? pathCalculator.computePredictedMatch(bracket.token1, display1, bracket.token2, display2,
                                    teamGW, teamRU, teamTP)
                            : pathCalculator.computeLast16PredictedMatch(display1, display2, last32PredByTeam);
                    int t1NewTotal;
                    int t2NewTotal;
                    String t1Chain;
                    String t2Chain;
                    if (openingRoundFromGroups) {
                        GroupLoadResult groupLoad1 = groupLoadFor(team1, groups, eloRatings, snapshots);
                        GroupLoadResult groupLoad2 = groupLoadFor(team2, groups, eloRatings, snapshots);
                        t1NewTotal = groupLoad1.weightedTotal();
                        t2NewTotal = groupLoad2.weightedTotal();
                        t1Chain = groupLoad1.chain();
                        t2Chain = groupLoad2.chain();
                    } else {
                        String[] opp1 = findPriorOpponent(team1, sourceMatchId(display1), last32Rows, eloRatings);
                        String[] opp2 = findPriorOpponent(team2, sourceMatchId(display2), last32Rows, eloRatings);
                        int t1ContribW = pathFatigueCalc.knockoutWeightedContribution(Integer.parseInt(opp1[1]), "last_32", "true".equals(opp1[4]));
                        int t2ContribW = pathFatigueCalc.knockoutWeightedContribution(Integer.parseInt(opp2[1]), "last_32", "true".equals(opp2[4]));
                        int t1ExistingTotal = parseInt(opp1[2]);
                        int t2ExistingTotal = parseInt(opp2[2]);
                        t1NewTotal = t1ExistingTotal + t1ContribW;
                        t2NewTotal = t2ExistingTotal + t2ContribW;
                        int t1ContribElo = pathFatigueCalc.eloAdjustmentFromWeighted(t1ContribW);
                        int t2ContribElo = pathFatigueCalc.eloAdjustmentFromWeighted(t2ContribW);
                        String t1Segment = opp1[0].isEmpty() ? "" : ("true".equals(opp1[4]) ? "U@" : "K@") + opp1[5] + "|" + opp1[0] + ":" + t1ContribElo;
                        String t2Segment = opp2[0].isEmpty() ? "" : ("true".equals(opp2[4]) ? "U@" : "K@") + opp2[5] + "|" + opp2[0] + ":" + t2ContribElo;
                        t1Chain = appendChain(opp1[3], t1Segment);
                        t2Chain = appendChain(opp2[3], t2Segment);
                    }
                    path = pathCalculator.classifyCompletedRoute(path, t1Chain, t2Chain);
                    int t1AdjElo = eloRatings.getOrDefault(team1, 0) + fatigueAdjustedElo(team1, t1NewTotal, snapshots);
                    int t2AdjElo = eloRatings.getOrDefault(team2, 0) + fatigueAdjustedElo(team2, t2NewTotal, snapshots);
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

    private int fatigueAdjustedElo(String team, int weightedTotal, Map<String, TeamEloSnapshot> snapshots) {
        int fatigue = pathFatigueCalc.eloAdjustmentFromWeighted(weightedTotal);
        TeamEloSnapshot snapshot = snapshots != null ? snapshots.get(team) : null;
        int depthLevel = snapshot != null ? snapshot.squadDepthLevel() : 0;
        return pathFatigueCalc.applyDepthMultiplier(fatigue, depthLevel);
    }

    private GroupLoadResult groupLoadFor(String teamName, Map<String, String> groups, Map<String, Integer> eloRatings,
                                         Map<String, TeamEloSnapshot> snapshots) {
        if (teamName == null || teamName.isBlank()) {
            return new GroupLoadResult(0, 0, "");
        }
        String group = groupForTeam(teamName, groups);
        if (group.isEmpty()) {
            return new GroupLoadResult(0, 0, "");
        }

        int weightedTotal = 0;
        List<String> segments = new ArrayList<>();
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            String slot = entry.getKey();
            String opponent = entry.getValue();
            if (slot == null || opponent == null || opponent.isBlank() || opponent.equalsIgnoreCase(teamName)) {
                continue;
            }
            if (!slot.toUpperCase().startsWith(group)) {
                continue;
            }
            int weighted = pathFatigueCalc.groupStageWeightedContribution(
                    eloRatings.getOrDefault(opponent, pathFatigueCalc.getTournamentAvgElo()));
            if (weighted <= 0) {
                continue;
            }
            weightedTotal += weighted;
            int contributionElo = pathFatigueCalc.eloAdjustmentFromWeighted(weighted);
            segments.add("G|" + opponent + ":" + contributionElo);
        }

        int fatigue = pathFatigueCalc.eloAdjustmentFromWeighted(weightedTotal);
        TeamEloSnapshot snapshot = snapshots != null ? snapshots.get(teamName) : null;
        int depthLevel = snapshot != null ? snapshot.squadDepthLevel() : 0;
        int adjustedElo = pathFatigueCalc.applyDepthMultiplier(fatigue, depthLevel);
        return new GroupLoadResult(weightedTotal, adjustedElo, String.join(" > ", segments));
    }

    private static String groupForTeam(String teamName, Map<String, String> groups) {
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            String team = entry.getValue();
            if (team != null && team.equalsIgnoreCase(teamName)) {
                String slot = entry.getKey();
                return slot == null || slot.isBlank() ? "" : slot.substring(0, 1).toUpperCase();
            }
        }
        return "";
    }

    /** Returns [opponentName, opponentElo, existingWeightedTotal, existingChain, upsetWin, matchId]. */
    private String[] findPriorOpponent(String teamName, String sourceMatchId, List<String> priorRows, Map<String, Integer> eloRatings) {
        String[] winningFallback = null;
        String[] losingFallback = null;
        for (String row : priorRows) {
            if (row.trim().isEmpty() || row.startsWith("match_id")) continue;
            String[] cols = row.split(",", -1);
            if (cols.length < 5) continue;
            if (!sourceMatchId.isBlank() && !sourceMatchId.equalsIgnoreCase(cols[0].trim())) continue;
            String t1 = predictionHelper.extractTeamName(cols[1].trim());
            String t2 = predictionHelper.extractTeamName(cols[2].trim());
            String winner = predictionHelper.parseTeamFromPrediction(cols[4].trim());
            boolean isPredicted = "predicted".equalsIgnoreCase(cols[3].trim());
            if (winner.equalsIgnoreCase(teamName)) {
                boolean winnerIsT1 = winner.equalsIgnoreCase(t1);
                String loser = winnerIsT1 ? t2 : t1;
                String[] result = new String[]{loser, String.valueOf(eloRatings.getOrDefault(loser, pathFatigueCalc.getTournamentAvgElo())),
                        existingPathFatigue(cols, winnerIsT1), existingPathOpponent(cols, winnerIsT1), "false", cols[0].trim()};
                if (isPredicted) return result;
                if (winningFallback == null) winningFallback = result;
            } else if (losingFallback == null) {
                if (t1.equalsIgnoreCase(teamName)) losingFallback = new String[]{t2, String.valueOf(eloRatings.getOrDefault(t2, pathFatigueCalc.getTournamentAvgElo())),
                        existingPathFatigue(cols, true), existingPathOpponent(cols, true), "true", cols[0].trim()};
                else if (t2.equalsIgnoreCase(teamName)) losingFallback = new String[]{t1, String.valueOf(eloRatings.getOrDefault(t1, pathFatigueCalc.getTournamentAvgElo())),
                        existingPathFatigue(cols, false), existingPathOpponent(cols, false), "true", cols[0].trim()};
            }
        }
        if (winningFallback != null) return winningFallback;
        if (losingFallback != null) return losingFallback;
        return new String[]{"", String.valueOf(pathFatigueCalc.getTournamentAvgElo()), "0", "", "false", ""};
    }

    private static String sourceMatchId(String display) {
        if (display == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^W(\\d+)\\(").matcher(display.trim());
        return matcher.find() ? "M" + matcher.group(1) : "";
    }

    private static String appendChain(String existingChain, String segment) {
        if (segment == null || segment.isBlank()) return existingChain == null ? "" : existingChain;
        if (existingChain == null || existingChain.isBlank()) return segment;
        return existingChain + " > " + segment;
    }

    private static String existingPathFatigue(String[] cols, boolean team1) {
        if (cols.length >= 13) return cols[team1 ? 9 : 10].trim();
        if (cols.length >= 9) return cols[team1 ? 5 : 6].trim();
        return "0";
    }

    private static String existingPathOpponent(String[] cols, boolean team1) {
        if (cols.length >= 13) return cols[team1 ? 11 : 12].trim();
        if (cols.length >= 9) return cols[team1 ? 7 : 8].trim();
        return "";
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) return 0;
        return Integer.parseInt(value.trim());
    }

    private record GroupLoadResult(int weightedTotal, int adjustedElo, String chain) {}

}

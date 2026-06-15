package com.tournamentpredictor.services.prediction.builder;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.bracket.DisplayBuilder;
import com.tournamentpredictor.services.bracket.DisplayBuilder.RouteOption;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PathCalculator;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.calculation.TeamEloSnapshot;
import com.tournamentpredictor.services.route.RoutePathValidator;

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
                last32PredByTeam.merge(matchId + "|" + eloLoser, "alt", pathCalculator::bestPredicted);
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
        lines.add("match_id,team1,team2,path,elo,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,"
                + "team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,"
                + "team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot,"
                + "matchup_pct,matchup_runs,upset_path");
        Map<String, Integer> priorHeader = header(last32Rows);
        for (CsvLoader.BracketEntry bracket : brackets) {
            if (!"LAST_16".equalsIgnoreCase(bracket.stage)) {
                continue;
            }
            boolean openingRoundFromGroups = last32Rows == null || last32Rows.isEmpty();
            List<RouteCandidate> candidates1 = openingRoundFromGroups
                    ? groupCandidates(bracket.token1, displayBuilder.buildWinnerOptions(bracket.token1, groups, groupWinner, runnerUp,
                            thirdPlace, brackets, last32Rows))
                    : priorRoundCandidates(bracket.token1, last32Rows, priorHeader, eloRatings);
            List<RouteCandidate> candidates2 = openingRoundFromGroups
                    ? groupCandidates(bracket.token2, displayBuilder.buildWinnerOptions(bracket.token2, groups, groupWinner, runnerUp,
                            thirdPlace, brackets, last32Rows))
                    : priorRoundCandidates(bracket.token2, last32Rows, priorHeader, eloRatings);
            for (RouteCandidate candidate1 : candidates1) {
                for (RouteCandidate candidate2 : candidates2) {
                    String team1 = candidate1.team();
                    String team2 = candidate2.team();
                    if (team1.equalsIgnoreCase(team2)) {
                        continue;
                    }
                    String path = openingRoundFromGroups
                            ? pathCalculator.computePredictedMatchForTeams(bracket.token1, candidate1.team(), bracket.token2, candidate2.team(),
                                    teamGW, teamRU, teamTP)
                            : pathCalculator.computePathFromSlots(candidate1.path(), candidate2.path());
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
                        int t1ContribW = pathFatigueCalc.knockoutWeightedContribution(candidate1.opponentElo(), "last_32", false);
                        int t2ContribW = pathFatigueCalc.knockoutWeightedContribution(candidate2.opponentElo(), "last_32", false);
                        t1NewTotal = candidate1.existingWeightedTotal() + t1ContribW;
                        t2NewTotal = candidate2.existingWeightedTotal() + t2ContribW;
                        int t1ContribElo = pathFatigueCalc.eloAdjustmentFromWeighted(t1ContribW);
                        int t2ContribElo = pathFatigueCalc.eloAdjustmentFromWeighted(t2ContribW);
                        String t1Segment = candidate1.opponent().isEmpty() ? "" : "K@" + candidate1.sourceMatchId() + "|" + candidate1.opponent() + ":" + t1ContribElo;
                        String t2Segment = candidate2.opponent().isEmpty() ? "" : "K@" + candidate2.sourceMatchId() + "|" + candidate2.opponent() + ":" + t2ContribElo;
                        t1Chain = appendChain(candidate1.existingChain(), t1Segment);
                        t2Chain = appendChain(candidate2.existingChain(), t2Segment);
                    }
                    if (RoutePathValidator.invalidMatchupRoute(team1, team2, t1Chain, t2Chain)) {
                        continue;
                    }
                    path = defaultAlternativePath(pathCalculator.classifyCompletedRoute(path, t1Chain, t2Chain));
                    if (!openingRoundFromGroups && (candidate1.upsetPath() || candidate2.upsetPath())) {
                        path = "alt";
                    }
                    int t1AdjElo = eloRatings.getOrDefault(team1, 0) + fatigueAdjustedElo(team1, t1NewTotal, snapshots);
                    int t2AdjElo = eloRatings.getOrDefault(team2, 0) + fatigueAdjustedElo(team2, t2NewTotal, snapshots);
                    String adjEloPrediction = predictionHelper.computeEloPredictionFromElos(team1, team2, t1AdjElo, t2AdjElo);
                    String t1Source = candidate1.sourceMatchId();
                    String t2Source = candidate2.sourceMatchId();
                    String t1GroupFinish = openingRoundFromGroups ? groupFinishForTeam(team1, groups) : candidate1.groupFinish();
                    String t2GroupFinish = openingRoundFromGroups ? groupFinishForTeam(team2, groups) : candidate2.groupFinish();
                    String t1BracketSlot = openingRoundFromGroups ? bracket.token1 : candidate1.bracketSlot();
                    String t2BracketSlot = openingRoundFromGroups ? bracket.token2 : candidate2.bracketSlot();
                    String matchupPct = openingRoundFromGroups ? "" : formatPct(candidate1.likelihoodPct() * candidate2.likelihoodPct() / 100.0);
                    String matchupRuns = openingRoundFromGroups ? "" : String.valueOf(Math.max(candidate1.matchupRuns(), candidate2.matchupRuns()));
                    String upsetPath = (!openingRoundFromGroups && (candidate1.upsetPath() || candidate2.upsetPath())) ? "1" : "0";
                    lines.add(String.join(",", bracket.matchId, team1,
                            team2, path, adjEloPrediction,
                            String.valueOf(t1NewTotal), String.valueOf(t2NewTotal), t1Chain, t2Chain,
                            bracket.token1, team1, t1Source, t1GroupFinish, t1BracketSlot,
                            bracket.token2, team2, t2Source, t2GroupFinish, t2BracketSlot,
                            matchupPct, matchupRuns, upsetPath));
                }
            }
            lines.add("");
        }
        return lines;
    }

    private List<RouteCandidate> groupCandidates(String token, List<RouteOption> options) {
        List<RouteCandidate> candidates = new ArrayList<>();
        for (RouteOption option : options) {
            String team = option.team();
            addRouteCandidate(candidates, new RouteCandidate("", team, option.sourceMatchId(), "predicted", "",
                    pathFatigueCalc.getTournamentAvgElo(), 0, "", false, option.groupFinish(), option.bracketSlot(), 100.0, 0, false));
        }
        return candidates;
    }

    private List<RouteCandidate> priorRoundCandidates(String token, List<String> priorRows, Map<String, Integer> header,
                                                       Map<String, Integer> eloRatings) {
        List<RouteCandidate> candidates = new ArrayList<>();
        String matchId = sourceMatchId(token);
        if (matchId.isBlank()) {
            return candidates;
        }
        for (String row : priorRows) {
            if (row.trim().isEmpty() || row.startsWith("match_id")) continue;
            String[] cols = row.split(",", -1);
            if (cols.length < 5 || !matchId.equalsIgnoreCase(cols[0].trim())) continue;
            String path = cols[3].trim();
            String team1 = valueAt(cols, header, "team1_team", predictionHelper.extractTeamName(cols[1].trim()));
            String team2 = valueAt(cols, header, "team2_team", predictionHelper.extractTeamName(cols[2].trim()));
            String prediction = valueAt(cols, header, "prediction", cols[4].trim());
            String winner = predictionHelper.parseTeamFromPrediction(prediction);
            if (winner.isBlank()) {
                continue;
            }
            boolean winnerIsT1 = winner.equalsIgnoreCase(team1);
            if (winnerIsT1 || winner.equalsIgnoreCase(team2)) {
                int winnerPct = predictionHelper.parsePctFromPrediction(prediction);
                double matchupPct = parseDouble(valueAt(cols, header, "matchup_pct", "100"), 100.0);
                int matchupRuns = parseInt(valueAt(cols, header, "matchup_runs", "0"));
                boolean priorUpset = isTruthy(valueAt(cols, header, "upset_path", "0"));
                addRouteCandidate(candidates, candidateFromSide(cols, header, token, matchId, true, team2, path, !winnerIsT1,
                        candidateLikelihood(matchupPct, winnerIsT1 ? winnerPct : 100 - winnerPct), matchupRuns, priorUpset || !winnerIsT1, eloRatings));
                addRouteCandidate(candidates, candidateFromSide(cols, header, token, matchId, false, team1, path, winnerIsT1,
                        candidateLikelihood(matchupPct, winnerIsT1 ? 100 - winnerPct : winnerPct), matchupRuns, priorUpset || winnerIsT1, eloRatings));
            }
        }
        return candidates;
    }

    private void addRouteCandidate(List<RouteCandidate> candidates, RouteCandidate candidate) {
        String key = routeCandidateKey(candidate);
        boolean exists = candidates.stream().anyMatch(existing -> routeCandidateKey(existing).equals(key));
        if (!exists) {
            candidates.add(candidate);
        }
    }

    private String routeCandidateKey(RouteCandidate candidate) {
        return String.join("|", candidate.team(), candidate.sourceMatchId(), candidate.path(), candidate.opponent(),
                candidate.existingChain(), String.valueOf(candidate.upsetWin()), candidate.groupFinish(), candidate.bracketSlot());
    }

    private RouteCandidate candidateFromSide(String[] cols, Map<String, Integer> header, String token, String matchId,
                                             boolean team1Side, String opponent, String path, boolean upsetWin,
                                             double likelihoodPct, int matchupRuns, boolean upsetPath,
                                             Map<String, Integer> eloRatings) {
        String prefix = team1Side ? "team1_" : "team2_";
        String fallbackTeam = predictionHelper.extractTeamName(cols[team1Side ? 1 : 2].trim());
        String team = valueAt(cols, header, prefix + "team", fallbackTeam);
        String groupFinish = valueAt(cols, header, prefix + "group_finish", seedFromDisplay(cols[team1Side ? 1 : 2].trim()));
        String bracketSlot = valueAt(cols, header, prefix + "bracket_slot", groupFinish);
        String display = bracketSlot + "(" + team + ")";
        return new RouteCandidate(token + "(" + display + ")", team, matchId, path, opponent,
                eloRatings.getOrDefault(opponent, pathFatigueCalc.getTournamentAvgElo()),
                parseInt(existingPathFatigue(cols, header, team1Side)), existingPathOpponent(cols, header, team1Side),
                upsetWin, groupFinish, bracketSlot, likelihoodPct, matchupRuns, upsetPath);
    }

    private String defaultAlternativePath(String path) {
        return path == null || path.isBlank() ? "alt" : path;
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
        String slot = groupFinishForTeam(teamName, groups);
        return slot.isBlank() ? "" : slot.substring(0, 1).toUpperCase();
    }

    private static String groupFinishForTeam(String teamName, Map<String, String> groups) {
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            String team = entry.getValue();
            if (team != null && team.equalsIgnoreCase(teamName)) {
                String slot = entry.getKey();
                return slot == null ? "" : slot.trim().toUpperCase();
            }
        }
        return "";
    }

    private String priorMetadata(List<String> priorRows, Map<String, Integer> header, String matchId, String team,
                                 String suffix, String fallback) {
        if (priorRows == null || priorRows.isEmpty() || matchId.isBlank() || team.isBlank()) return fallback;
        for (String row : priorRows) {
            if (row.trim().isEmpty() || row.startsWith("match_id")) continue;
            String[] cols = row.split(",", -1);
            if (cols.length < 5 || !matchId.equalsIgnoreCase(cols[0].trim())) continue;
            String team1 = valueAt(cols, header, "team1_team", predictionHelper.extractTeamName(cols[1].trim()));
            String team2 = valueAt(cols, header, "team2_team", predictionHelper.extractTeamName(cols[2].trim()));
            if (team.equalsIgnoreCase(team1)) return valueAt(cols, header, "team1_" + suffix, fallback);
            if (team.equalsIgnoreCase(team2)) return valueAt(cols, header, "team2_" + suffix, fallback);
        }
        return fallback;
    }

    private static Map<String, Integer> header(List<String> rows) {
        Map<String, Integer> header = new LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) return header;
        String[] cols = rows.get(0).split(",", -1);
        for (int i = 0; i < cols.length; i++) {
            header.put(cols[i].trim(), i);
        }
        return header;
    }

    private static String valueAt(String[] cols, Map<String, Integer> header, String column, String fallback) {
        Integer index = header.get(column);
        if (index == null || index < 0 || index >= cols.length) return fallback;
        String value = cols[index].trim();
        return value.isBlank() ? fallback : value;
    }

    private static String seedFromDisplay(String display) {
        String safe = display == null ? "" : display.trim();
        int first = safe.indexOf('(');
        if (first <= 0) return safe;
        String inner = safe.substring(first + 1);
        int nested = inner.indexOf('(');
        if (nested > 0) return inner.substring(0, nested).trim();
        return safe.substring(0, first).trim();
    }

    /** Returns [opponentName, opponentElo, existingWeightedTotal, existingChain, upsetWin, matchId]. */
    private String[] findPriorOpponent(String teamName, String sourceMatchId, List<String> priorRows,
                                        Map<String, Integer> header, Map<String, Integer> eloRatings) {
        String[] winningFallback = null;
        String[] losingFallback = null;
        for (String row : priorRows) {
            if (row.trim().isEmpty() || row.startsWith("match_id")) continue;
            String[] cols = row.split(",", -1);
            if (cols.length < 5) continue;
            if (!sourceMatchId.isBlank() && !sourceMatchId.equalsIgnoreCase(cols[0].trim())) continue;
            String t1 = valueAt(cols, header, "team1_team", predictionHelper.extractTeamName(cols[1].trim()));
            String t2 = valueAt(cols, header, "team2_team", predictionHelper.extractTeamName(cols[2].trim()));
            String winner = predictionHelper.parseTeamFromPrediction(valueAt(cols, header, "prediction", cols[4].trim()));
            boolean isPredicted = "predicted".equalsIgnoreCase(cols[3].trim());
            if (winner.equalsIgnoreCase(teamName)) {
                boolean winnerIsT1 = winner.equalsIgnoreCase(t1);
                String loser = winnerIsT1 ? t2 : t1;
                String[] result = new String[]{loser, String.valueOf(eloRatings.getOrDefault(loser, pathFatigueCalc.getTournamentAvgElo())),
                        existingPathFatigue(cols, header, winnerIsT1), existingPathOpponent(cols, header, winnerIsT1), "false", cols[0].trim()};
                if (isPredicted) return result;
                if (winningFallback == null) winningFallback = result;
            } else if (losingFallback == null) {
                if (t1.equalsIgnoreCase(teamName)) losingFallback = new String[]{t2, String.valueOf(eloRatings.getOrDefault(t2, pathFatigueCalc.getTournamentAvgElo())),
                        existingPathFatigue(cols, header, true), existingPathOpponent(cols, header, true), "true", cols[0].trim()};
                else if (t2.equalsIgnoreCase(teamName)) losingFallback = new String[]{t1, String.valueOf(eloRatings.getOrDefault(t1, pathFatigueCalc.getTournamentAvgElo())),
                        existingPathFatigue(cols, header, false), existingPathOpponent(cols, header, false), "true", cols[0].trim()};
            }
        }
        if (winningFallback != null) return winningFallback;
        if (losingFallback != null) return losingFallback;
        return new String[]{"", String.valueOf(pathFatigueCalc.getTournamentAvgElo()), "0", "", "false", ""};
    }

    private static String sourceMatchId(String token) {
        if (token == null || !token.trim().matches("^W\\d+$")) return "";
        return "M" + token.trim().substring(1);
    }

    private static String appendChain(String existingChain, String segment) {
        if (segment == null || segment.isBlank()) return existingChain == null ? "" : existingChain;
        if (existingChain == null || existingChain.isBlank()) return segment;
        return existingChain + " > " + segment;
    }

    private static String existingPathFatigue(String[] cols, Map<String, Integer> header, boolean team1) {
        return valueAt(cols, header, team1 ? "team1_path_fatigue" : "team2_path_fatigue", "0");
    }

    private static String existingPathOpponent(String[] cols, Map<String, Integer> header, boolean team1) {
        return valueAt(cols, header, team1 ? "team1_path_opponent" : "team2_path_opponent", "");
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double candidateLikelihood(double matchupPct, int advancePct) {
        return matchupPct * Math.max(0, Math.min(100, advancePct)) / 100.0;
    }

    private static String formatPct(double value) {
        if (value <= 0) return "0.0";
        if (value < 0.1) return String.format(java.util.Locale.ROOT, "%.3f", value);
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static boolean isTruthy(String value) {
        return "1".equals(value == null ? "" : value.trim()) || "true".equalsIgnoreCase(value == null ? "" : value.trim());
    }

    private record GroupLoadResult(int weightedTotal, int adjustedElo, String chain) {}

    private record RouteCandidate(String display, String team, String sourceMatchId, String path, String opponent,
                                  int opponentElo, int existingWeightedTotal, String existingChain, boolean upsetWin,
                                  String groupFinish, String bracketSlot, double likelihoodPct, int matchupRuns, boolean upsetPath) {}

}

package com.tournamentpredictor.services.prediction.builder;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PathCalculator;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.calculation.TeamEloSnapshot;
import com.tournamentpredictor.services.route.RoutePathValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class LaterRoundLineBuilder {
    private static final int MAX_ROUTE_VARIANTS_PER_TEAM_MATCH = 4;
    private static final String HEADER = "match_id,team1,team2,path,elo,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,"
            + "team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,"
            + "team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot,"
            + "matchup_pct,matchup_runs,upset_path";

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
        Map<String, List<RouteCandidate>> candidatesByMatch = parsePriorCandidates(priorRows, eloRatings);
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);

        for (CsvLoader.BracketEntry bracket : brackets) {
            if (!bracketStage.equalsIgnoreCase(bracket.stage) || bracket.matchId.isEmpty()
                    || bracket.token1.isEmpty() || bracket.token2.isEmpty()) {
                continue;
            }
            String match1 = "M" + bracket.token1.substring(1);
            String match2 = "M" + bracket.token2.substring(1);
            List<RouteCandidate> candidates1 = candidatesByMatch.getOrDefault(match1, List.of());
            List<RouteCandidate> candidates2 = candidatesByMatch.getOrDefault(match2, List.of());

            for (RouteCandidate candidate1 : candidates1) {
                for (RouteCandidate candidate2 : candidates2) {
                    if (candidate1.team().equalsIgnoreCase(candidate2.team())) {
                        continue;
                    }
                    String line = buildLine(bracket, candidate1, candidate2, eloRatings, snapshots, fatigueStage);
                    if (line != null) {
                        lines.add(line);
                    }
                }
            }
            lines.add("");
        }
        return lines;
    }

    private String buildLine(CsvLoader.BracketEntry bracket, RouteCandidate candidate1, RouteCandidate candidate2,
                             Map<String, Integer> eloRatings, Map<String, TeamEloSnapshot> snapshots, String fatigueStage) {
        String path = pathCalculator.computePathFromSlots(candidate1.path(), candidate2.path());

        FatigueResult fatigue1 = fatigueFor(candidate1, fatigueStage, snapshots);
        FatigueResult fatigue2 = fatigueFor(candidate2, fatigueStage, snapshots);
        if (RoutePathValidator.invalidMatchupRoute(candidate1.team(), candidate2.team(), fatigue1.chain, fatigue2.chain)) {
            return null;
        }

        path = defaultAlternativePath(pathCalculator.classifyCompletedRoute(path, fatigue1.chain, fatigue2.chain));
        if (candidate1.upsetPath() || candidate2.upsetPath()) {
            path = "alt";
        }

        int t1AdjElo = eloRatings.getOrDefault(candidate1.team(), 0) + fatigue1.adjustedElo;
        int t2AdjElo = eloRatings.getOrDefault(candidate2.team(), 0) + fatigue2.adjustedElo;
        String adjEloPrediction = predictionHelper.computeEloPredictionFromElos(candidate1.team(), candidate2.team(), t1AdjElo, t2AdjElo);

        String matchupPct = formatPct(candidate1.likelihoodPct() * candidate2.likelihoodPct() / 100.0);
        String matchupRuns = String.valueOf(Math.max(candidate1.matchupRuns(), candidate2.matchupRuns()));
        String upsetPath = candidate1.upsetPath() || candidate2.upsetPath() ? "1" : "0";
        return String.join(",", bracket.matchId, candidate1.team(), candidate2.team(), path, adjEloPrediction,
                String.valueOf(fatigue1.weightedTotal), String.valueOf(fatigue2.weightedTotal),
                fatigue1.chain, fatigue2.chain,
                bracket.token1, candidate1.team(), candidate1.sourceMatchId(), candidate1.groupFinish(), candidate1.bracketSlot(),
                bracket.token2, candidate2.team(), candidate2.sourceMatchId(), candidate2.groupFinish(), candidate2.bracketSlot(),
                matchupPct, matchupRuns, upsetPath);
    }

    private String defaultAlternativePath(String path) {
        return path == null || path.isBlank() ? "alt" : path;
    }

    private Map<String, List<RouteCandidate>> parsePriorCandidates(List<String> priorRows, Map<String, Integer> eloRatings) {
        Map<String, List<RouteCandidate>> candidatesByMatch = new LinkedHashMap<>();
        Map<String, Integer> header = header(priorRows);
        for (String line : priorRows) {
            if (line.trim().isEmpty() || line.startsWith("match_id")) continue;
            String[] cols = line.split(",", -1);
            if (cols.length < 5) continue;
            String matchId = cols[0].trim();
            String path = cols[3].trim();
            String prediction = valueAt(cols, header, "prediction", cols[4].trim());
            String winner = predictionHelper.parseTeamFromPrediction(prediction);
            String team1Name = valueAt(cols, header, "team1_team", predictionHelper.extractTeamName(cols[1].trim()));
            String team2Name = valueAt(cols, header, "team2_team", predictionHelper.extractTeamName(cols[2].trim()));
            if (winner.isBlank()) {
                continue;
            }
            boolean winnerIsT1 = winner.equalsIgnoreCase(team1Name);
            if (winnerIsT1 || winner.equalsIgnoreCase(team2Name)) {
                int winnerPct = predictionHelper.parsePctFromPrediction(prediction);
                double matchupPct = parseDouble(valueAt(cols, header, "matchup_pct", "100"), 100.0);
                int matchupRuns = parseInt(valueAt(cols, header, "matchup_runs", "0"));
                boolean priorUpset = isTruthy(valueAt(cols, header, "upset_path", "0"));
                addCandidate(candidatesByMatch, matchId, candidateFromSide(cols, header, true, team2Name, path, !winnerIsT1,
                        candidateLikelihood(matchupPct, winnerIsT1 ? winnerPct : 100 - winnerPct), matchupRuns, priorUpset || !winnerIsT1, eloRatings));
                addCandidate(candidatesByMatch, matchId, candidateFromSide(cols, header, false, team1Name, path, winnerIsT1,
                        candidateLikelihood(matchupPct, winnerIsT1 ? 100 - winnerPct : winnerPct), matchupRuns, priorUpset || winnerIsT1, eloRatings));
            }
        }
        return candidatesByMatch;
    }

    private void addCandidate(Map<String, List<RouteCandidate>> candidatesByMatch, String matchId, RouteCandidate candidate) {
        if (candidate.team().isBlank() || candidate.opponent().isBlank()) {
            return;
        }
        List<RouteCandidate> candidates = candidatesByMatch.computeIfAbsent(matchId, ignored -> new ArrayList<>());
        String key = candidateKey(candidate);
        boolean exists = candidates.stream().anyMatch(existing -> candidateKey(existing).equals(key));
        if (exists) {
            return;
        }
        long teamVariantCount = candidates.stream()
                .filter(existing -> existing.team().equalsIgnoreCase(candidate.team()))
                .count();
        if (teamVariantCount < MAX_ROUTE_VARIANTS_PER_TEAM_MATCH) {
            candidates.add(candidate);
        }
    }

    private String candidateKey(RouteCandidate candidate) {
        return String.join("|", candidate.team(), candidate.sourceMatchId(), candidate.path(), candidate.opponent(),
                candidate.existingChain(), String.valueOf(candidate.upsetWin()), candidate.groupFinish(), candidate.bracketSlot());
    }

    private RouteCandidate candidateFromSide(String[] cols, Map<String, Integer> header, boolean team1Side,
                                             String opponent, String path, boolean upsetWin, double likelihoodPct,
                                             int matchupRuns, boolean upsetPath, Map<String, Integer> eloRatings) {
        String prefix = team1Side ? "team1_" : "team2_";
        String fallbackTeam = predictionHelper.extractTeamName(cols[team1Side ? 1 : 2].trim());
        String team = valueAt(cols, header, prefix + "team", fallbackTeam);
        String sourceMatch = cols[0].trim();
        String groupFinish = valueAt(cols, header, prefix + "group_finish", seedFromDisplay(cols[team1Side ? 1 : 2].trim()));
        String bracketSlot = valueAt(cols, header, prefix + "bracket_slot", groupFinish);
        return new RouteCandidate(team, sourceMatch, path, opponent,
                eloRatings.getOrDefault(opponent, pathFatigueCalc.getTournamentAvgElo()),
                parseInt(existingPathFatigue(cols, header, team1Side)), existingPathOpponent(cols, header, team1Side),
                upsetWin, groupFinish, bracketSlot, likelihoodPct, matchupRuns, upsetPath);
    }

    private FatigueResult fatigueFor(RouteCandidate candidate, String fatigueStage, Map<String, TeamEloSnapshot> snapshots) {
        int weightedContribution = pathFatigueCalc.knockoutWeightedContribution(candidate.opponentElo(), fatigueStage, false);
        int weightedTotal = candidate.existingWeightedTotal() + weightedContribution;
        int contributionElo = pathFatigueCalc.eloAdjustmentFromWeighted(weightedContribution);
        String segment = candidate.opponent().isEmpty() ? "" : "K@"
                + candidate.sourceMatchId() + "|" + candidate.opponent() + ":" + contributionElo;
        String chain = candidate.existingChain().isEmpty() ? segment : candidate.existingChain() + " > " + segment;
        int fatigue = pathFatigueCalc.eloAdjustmentFromWeighted(weightedTotal);
        TeamEloSnapshot snapshot = snapshots != null ? snapshots.get(candidate.team()) : null;
        int depthLevel = snapshot != null ? snapshot.squadDepthLevel() : 0;
        int adjustedElo = pathFatigueCalc.applyDepthMultiplier(fatigue, depthLevel);
        return new FatigueResult(weightedTotal, adjustedElo, chain);
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

    private static String existingPathFatigue(String[] cols, Map<String, Integer> header, boolean team1) {
        return valueAt(cols, header, team1 ? "team1_path_fatigue" : "team2_path_fatigue", "0");
    }

    private static String existingPathOpponent(String[] cols, Map<String, Integer> header, boolean team1) {
        return valueAt(cols, header, team1 ? "team1_path_opponent" : "team2_path_opponent", "");
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

    private record RouteCandidate(String team, String sourceMatchId, String path, String opponent, int opponentElo,
                                  int existingWeightedTotal, String existingChain, boolean upsetWin,
                                  String groupFinish, String bracketSlot, double likelihoodPct, int matchupRuns, boolean upsetPath) {}

    private record FatigueResult(int weightedTotal, int adjustedElo, String chain) {}
}

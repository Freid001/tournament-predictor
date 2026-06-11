package com.tournamentpredictor.view;

import com.tournamentpredictor.model.results.ResultEntryRow;
import com.tournamentpredictor.services.calculation.EloCalculator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class KnockoutViewRows {
    private KnockoutViewRows() {
    }

    public static String matchKey(String team1, String team2) {
        String a = safeTrim(team1);
        String b = safeTrim(team2);
        return a.compareToIgnoreCase(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    public static List<String> buildResultRows(List<String> baseLines,
                                               List<Map<String, String>> resultRows,
                                               Map<String, String> predictedWinners) {
        if (baseLines == null || baseLines.isEmpty() || resultRows == null || resultRows.isEmpty()) {
            return List.of();
        }
        String[] headers = baseLines.get(0).split(",", -1);
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int pathIdx = indexOf(headers, "path");
        int predIdx = indexOf(headers, "prediction");
        int homeScoreIdx = indexOf(headers, "home_score");
        int awayScoreIdx = indexOf(headers, "away_score");
        if (team1Idx < 0 || team2Idx < 0 || pathIdx < 0 || predIdx < 0) {
            return baseLines;
        }

        ResultMaps results = resultMaps(resultRows);
        List<String> out = new ArrayList<>();
        out.add(baseLines.get(0));
        Set<String> emittedKeys = new LinkedHashSet<>();
        EloCalculator elo = new EloCalculator();
        for (int i = 1; i < baseLines.size(); i++) {
            String line = baseLines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String team1 = elo.extractTeamName(valueAt(cols, team1Idx));
            String team2 = elo.extractTeamName(valueAt(cols, team2Idx));
            if (team1.isBlank() || team2.isBlank()) continue;
            String key = matchKey(team1, team2);
            if (!emittedKeys.add(key)) continue;
            String winner = results.labels().get(key);
            if (winner == null || winner.isBlank()) continue;
            String[] resultCols = cols.clone();
            String predictedWinner = predictedWinners == null ? "" : predictedWinners.getOrDefault(key, "");
            boolean upset = !predictedWinner.isBlank() && !"Draw".equalsIgnoreCase(winner)
                    && !winner.equalsIgnoreCase(predictedWinner);
            resultCols[pathIdx] = upset ? "result_upset" : "results";
            resultCols[predIdx] = winner;
            if (homeScoreIdx >= 0 && awayScoreIdx >= 0) {
                String score = results.scores().getOrDefault(key, "");
                String[] scoreParts = score.split(" - ", 2);
                if (scoreParts.length == 2) {
                    resultCols[homeScoreIdx] = scoreParts[0];
                    resultCols[awayScoreIdx] = scoreParts[1];
                }
            }
            out.add(String.join(",", resultCols));
        }
        return out;
    }


    public static List<String> buildFixtureRows(List<String> baseLines,
                                                List<ResultEntryRow> fixtures,
                                                Map<String, String> resultLabels) {
        return buildFixtureRows(baseLines, fixtures, resultLabels, baseLines);
    }

    public static List<String> buildFixtureRows(List<String> baseLines,
                                                List<ResultEntryRow> fixtures,
                                                Map<String, String> resultLabels,
                                                List<String> contextLines) {
        return buildFixtureRows(baseLines, fixtures, resultLabels, contextLines, Map.of());
    }

    public static List<String> buildFixtureRows(List<String> baseLines,
                                                List<ResultEntryRow> fixtures,
                                                Map<String, String> resultLabels,
                                                List<String> contextLines,
                                                Map<String, String[]> slotsByMatch) {
        if (baseLines == null || baseLines.isEmpty() || fixtures == null || fixtures.isEmpty()) {
            return List.of();
        }
        String[] headers = baseLines.get(0).split(",", -1);
        int matchIdIdx = indexOf(headers, "match_id");
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int pathIdx = indexOf(headers, "path");
        int predIdx = indexOf(headers, "prediction");
        int homeScoreIdx = indexOf(headers, "home_score");
        int awayScoreIdx = indexOf(headers, "away_score");
        int team1PathDiffIdx = indexOf(headers, "team1_path_fatigue");
        int team2PathDiffIdx = indexOf(headers, "team2_path_fatigue");
        int team1PathOppIdx = indexOf(headers, "team1_path_opponent");
        int team2PathOppIdx = indexOf(headers, "team2_path_opponent");
        if (team1Idx < 0 || team2Idx < 0 || pathIdx < 0 || predIdx < 0) {
            return List.of();
        }
        Map<String, String[]> templateByKey = new LinkedHashMap<>();
        Map<String, String[]> predictedTemplateByMatch = new LinkedHashMap<>();
        EloCalculator elo = new EloCalculator();
        for (int i = 1; i < baseLines.size(); i++) {
            String line = baseLines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String team1 = elo.extractTeamName(valueAt(cols, team1Idx));
            String team2 = elo.extractTeamName(valueAt(cols, team2Idx));
            if (team1.isBlank() || team2.isBlank()) continue;
            String rowPath = valueAt(cols, pathIdx);
            String key = matchKey(team1, team2);
            if (!templateByKey.containsKey(key) || "predicted".equalsIgnoreCase(rowPath)) {
                templateByKey.put(key, cols);
            }
            if (matchIdIdx >= 0 && "predicted".equalsIgnoreCase(rowPath)) {
                predictedTemplateByMatch.putIfAbsent(valueAt(cols, matchIdIdx), cols);
            }
        }
        Map<String, TeamPathContext> teamPathContext = teamPathContext(contextLines);
        List<String> out = new ArrayList<>();
        out.add(baseLines.get(0));
        Set<String> emittedKeys = new LinkedHashSet<>();
        Map<String, String> labels = resultLabels == null ? Map.of() : resultLabels;
        for (ResultEntryRow fixture : fixtures) {
            String team1 = safeTrim(fixture.getTeam1());
            String team2 = safeTrim(fixture.getTeam2());
            if (team1.isBlank() || team2.isBlank()) continue;
            String key = matchKey(team1, team2);
            if (!emittedKeys.add(key)) continue;
            String[] source = templateByKey.get(key);
            String[] fallback = matchIdIdx >= 0 ? predictedTemplateByMatch.get(safeTrim(fixture.getMatchId())) : null;
            String[] cols = source == null
                    ? blankColumns(headers.length)
                    : source.clone();
            if (source == null && fallback != null) {
                copySharedContext(cols, fallback, matchIdIdx, pathIdx, predIdx, team1Idx, team2Idx);
            }
            if (matchIdIdx >= 0 && !safeTrim(fixture.getMatchId()).isBlank()) {
                cols[matchIdIdx] = safeTrim(fixture.getMatchId());
            }
            String[] matchSlots = slotsByMatch == null ? null : slotsByMatch.get(safeTrim(fixture.getMatchId()));
            cols[team1Idx] = slotWrappedTeam(team1, matchSlots == null || matchSlots.length < 1 ? "" : matchSlots[0]);
            cols[team2Idx] = slotWrappedTeam(team2, matchSlots == null || matchSlots.length < 2 ? "" : matchSlots[1]);
            applyTeamPathContext(cols, team1, teamPathContext, team1PathDiffIdx, team1PathOppIdx);
            applyTeamPathContext(cols, team2, teamPathContext, team2PathDiffIdx, team2PathOppIdx);
            String resultWinner = labels.getOrDefault(key, "");
            String predictedWinner = source == null ? "" : elo.parseTeamFromPrediction(valueAt(cols, predIdx));
            boolean actualUpset = !resultWinner.isBlank()
                    && !"Draw".equalsIgnoreCase(resultWinner)
                    && !predictedWinner.isBlank()
                    && !resultWinner.equalsIgnoreCase(predictedWinner);
            cols[pathIdx] = actualUpset ? "result_upset" : "fixture";
            cols[predIdx] = resultWinner.isBlank() ? valueAt(cols, predIdx) : resultWinner;
            if (homeScoreIdx >= 0) cols[homeScoreIdx] = safeTrim(fixture.getHomeScore());
            if (awayScoreIdx >= 0) cols[awayScoreIdx] = safeTrim(fixture.getAwayScore());
            out.add(String.join(",", cols));
        }
        return out;
    }

    private static String slotWrappedTeam(String team, String slot) {
        String cleanTeam = safeTrim(team);
        String cleanSlot = safeTrim(slot);
        return cleanSlot.isBlank() ? cleanTeam : cleanSlot + "(" + cleanTeam + ")";
    }

    private static Map<String, TeamPathContext> teamPathContext(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Map.of();
        }
        String[] headers = lines.get(0).split(",", -1);
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int team1PathDiffIdx = indexOf(headers, "team1_path_fatigue");
        int team2PathDiffIdx = indexOf(headers, "team2_path_fatigue");
        int team1PathOppIdx = indexOf(headers, "team1_path_opponent");
        int team2PathOppIdx = indexOf(headers, "team2_path_opponent");
        if (team1Idx < 0 || team2Idx < 0 || team1PathDiffIdx < 0 || team2PathDiffIdx < 0
                || team1PathOppIdx < 0 || team2PathOppIdx < 0) {
            return Map.of();
        }
        Map<String, TeamPathContext> contexts = new LinkedHashMap<>();
        EloCalculator elo = new EloCalculator();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            putTeamPathContext(contexts, elo.extractTeamName(valueAt(cols, team1Idx)),
                    valueAt(cols, team1PathDiffIdx), valueAt(cols, team1PathOppIdx));
            putTeamPathContext(contexts, elo.extractTeamName(valueAt(cols, team2Idx)),
                    valueAt(cols, team2PathDiffIdx), valueAt(cols, team2PathOppIdx));
        }
        return contexts;
    }

    private static void putTeamPathContext(Map<String, TeamPathContext> contexts, String team, String fatigue, String opponent) {
        if (safeTrim(team).isBlank()) return;
        TeamPathContext context = new TeamPathContext(safeTrim(fatigue), safeTrim(opponent));
        if (!context.hasSpecificPath()) return;
        contexts.putIfAbsent(safeTrim(team), context);
    }

    private static void applyTeamPathContext(String[] cols, String team, Map<String, TeamPathContext> contexts,
                                             int fatigueIdx, int opponentIdx) {
        TeamPathContext context = contexts.get(safeTrim(team));
        if (context == null || !context.hasSpecificPath()) return;
        if (fatigueIdx >= 0 && fatigueIdx < cols.length) cols[fatigueIdx] = context.fatigue();
        if (opponentIdx >= 0 && opponentIdx < cols.length) cols[opponentIdx] = context.opponent();
    }

    private record TeamPathContext(String fatigue, String opponent) {
        boolean hasSpecificPath() {
            return !fatigue.isBlank() || (!opponent.isBlank() && !"Group stage".equalsIgnoreCase(opponent));
        }
    }

    private static String[] blankColumns(int length) {
        String[] cols = new String[length];
        java.util.Arrays.fill(cols, "");
        return cols;
    }

    private static void copySharedContext(String[] target, String[] source, int... excludedIndexes) {
        Set<Integer> excluded = java.util.Arrays.stream(excludedIndexes).boxed().collect(Collectors.toSet());
        for (int i = 0; i < target.length && i < source.length; i++) {
            if (!excluded.contains(i)) {
                target[i] = source[i];
            }
        }
    }

    public static List<String> enrichPathContext(List<String> baseLines, List<String> contextLines) {
        if (baseLines == null || baseLines.isEmpty() || contextLines == null || contextLines.isEmpty()) {
            return baseLines == null ? List.of() : baseLines;
        }
        String[] headers = baseLines.get(0).split(",", -1);
        int matchIdIdx = indexOf(headers, "match_id");
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int pathIdx = indexOf(headers, "path");
        int team1PathDiffIdx = indexOf(headers, "team1_path_fatigue");
        int team2PathDiffIdx = indexOf(headers, "team2_path_fatigue");
        int team1PathOppIdx = indexOf(headers, "team1_path_opponent");
        int team2PathOppIdx = indexOf(headers, "team2_path_opponent");
        if (matchIdIdx < 0 || team1Idx < 0 || team2Idx < 0 || pathIdx < 0
                || team1PathDiffIdx < 0 || team2PathDiffIdx < 0 || team1PathOppIdx < 0 || team2PathOppIdx < 0) {
            return baseLines;
        }
        Map<String, PathContextRow> contextByPath = pathContextRows(contextLines);
        if (contextByPath.isEmpty()) {
            return baseLines;
        }
        EloCalculator elo = new EloCalculator();
        List<String> out = new ArrayList<>();
        out.add(baseLines.get(0));
        for (int i = 1; i < baseLines.size(); i++) {
            String line = baseLines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String team1 = elo.extractTeamName(valueAt(cols, team1Idx));
            String team2 = elo.extractTeamName(valueAt(cols, team2Idx));
            String key = pathContextKey(valueAt(cols, matchIdIdx), team1, team2, valueAt(cols, pathIdx));
            PathContextRow context = contextByPath.get(key);
            if (context == null) {
                context = contextByPath.get(pathContextKey(valueAt(cols, matchIdIdx), team1, team2, ""));
            }
            if (context != null) {
                cols[team1Idx] = context.team1();
                cols[team2Idx] = context.team2();
                cols[team1PathDiffIdx] = context.team1Fatigue();
                cols[team2PathDiffIdx] = context.team2Fatigue();
                cols[team1PathOppIdx] = context.team1Opponent();
                cols[team2PathOppIdx] = context.team2Opponent();
            }
            out.add(String.join(",", cols));
        }
        return out;
    }

    private static Map<String, PathContextRow> pathContextRows(List<String> contextLines) {
        String[] headers = contextLines.get(0).split(",", -1);
        int matchIdIdx = indexOf(headers, "match_id");
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int pathIdx = indexOf(headers, "path");
        int team1PathDiffIdx = indexOf(headers, "team1_path_fatigue");
        int team2PathDiffIdx = indexOf(headers, "team2_path_fatigue");
        int team1PathOppIdx = indexOf(headers, "team1_path_opponent");
        int team2PathOppIdx = indexOf(headers, "team2_path_opponent");
        if (matchIdIdx < 0 || team1Idx < 0 || team2Idx < 0 || pathIdx < 0
                || team1PathDiffIdx < 0 || team2PathDiffIdx < 0 || team1PathOppIdx < 0 || team2PathOppIdx < 0) {
            return Map.of();
        }
        Map<String, PathContextRow> rows = new LinkedHashMap<>();
        EloCalculator elo = new EloCalculator();
        for (int i = 1; i < contextLines.size(); i++) {
            String line = contextLines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String team1 = elo.extractTeamName(valueAt(cols, team1Idx));
            String team2 = elo.extractTeamName(valueAt(cols, team2Idx));
            PathContextRow context = new PathContextRow(
                    valueAt(cols, team1Idx), valueAt(cols, team2Idx),
                    valueAt(cols, team1PathDiffIdx), valueAt(cols, team2PathDiffIdx),
                    valueAt(cols, team1PathOppIdx), valueAt(cols, team2PathOppIdx));
            rows.putIfAbsent(pathContextKey(valueAt(cols, matchIdIdx), team1, team2, valueAt(cols, pathIdx)), context);
            rows.putIfAbsent(pathContextKey(valueAt(cols, matchIdIdx), team1, team2, ""), context);
        }
        return rows;
    }

    private static String pathContextKey(String matchId, String team1, String team2, String path) {
        return safeTrim(matchId) + "|" + matchKey(team1, team2) + "|" + safeTrim(path).toLowerCase();
    }

    private record PathContextRow(String team1, String team2, String team1Fatigue, String team2Fatigue,
                                  String team1Opponent, String team2Opponent) {
    }

    public static List<String> buildResultRows(List<String> baseLines, List<Map<String, String>> resultRows) {
        return buildResultRows(baseLines, resultRows, Map.of());
    }

    public static List<String> buildResultOnlyRows(List<String> lines, Map<String, String> resultLabels) {
        if (lines == null || lines.isEmpty() || resultLabels == null || resultLabels.isEmpty()) {
            return lines;
        }
        String[] headers = lines.get(0).split(",", -1);
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int pathIdx = indexOf(headers, "path");
        int predIdx = indexOf(headers, "prediction");
        if (team1Idx < 0 || team2Idx < 0 || pathIdx < 0 || predIdx < 0) {
            return lines;
        }
        EloCalculator elo = new EloCalculator();
        Set<String> emittedKeys = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        out.add(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String rowPath = valueAt(cols, pathIdx);
            if (!"predicted".equalsIgnoreCase(rowPath)) continue;
            String team1 = elo.extractTeamName(valueAt(cols, team1Idx));
            String team2 = elo.extractTeamName(valueAt(cols, team2Idx));
            String key = matchKey(team1, team2);
            if (!emittedKeys.add(key)) continue;
            String label = resultLabels.get(key);
            if (label == null || label.isBlank()) continue;
            String[] resultCols = cols.clone();
            resultCols[pathIdx] = "results";
            resultCols[predIdx] = label;
            out.add(String.join(",", resultCols));
        }
        return out;
    }

    public static List<String> merge(List<String> baseLines, List<String> overlayLines) {
        if (baseLines == null || baseLines.isEmpty()) {
            return overlayLines == null ? List.of() : overlayLines;
        }
        if (overlayLines == null || overlayLines.size() <= 1) {
            return baseLines;
        }
        String[] baseHeaders = baseLines.get(0).split(",", -1);
        String[] overlayHeaders = overlayLines.get(0).split(",", -1);
        int baseTeam1Idx = indexOf(baseHeaders, "team1");
        int baseTeam2Idx = indexOf(baseHeaders, "team2");
        int overlayTeam1Idx = indexOf(overlayHeaders, "team1");
        int overlayTeam2Idx = indexOf(overlayHeaders, "team2");
        if (baseTeam1Idx < 0 || baseTeam2Idx < 0 || overlayTeam1Idx < 0 || overlayTeam2Idx < 0) {
            return baseLines;
        }

        EloCalculator elo = new EloCalculator();
        Map<String, List<String>> overlayByKey = new LinkedHashMap<>();
        for (int i = 1; i < overlayLines.size(); i++) {
            String line = overlayLines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String team1 = elo.extractTeamName(valueAt(cols, overlayTeam1Idx));
            String team2 = elo.extractTeamName(valueAt(cols, overlayTeam2Idx));
            if (team1.isBlank() || team2.isBlank()) continue;
            overlayByKey.computeIfAbsent(matchKey(team1, team2), ignored -> new ArrayList<>()).add(line);
        }

        List<String> merged = new ArrayList<>();
        merged.add(baseLines.get(0));
        for (int i = 1; i < baseLines.size(); i++) {
            String line = baseLines.get(i);
            if (line == null || line.isBlank()) continue;
            merged.add(line);
            String[] cols = line.split(",", -1);
            String team1 = elo.extractTeamName(valueAt(cols, baseTeam1Idx));
            String team2 = elo.extractTeamName(valueAt(cols, baseTeam2Idx));
            List<String> extras = overlayByKey.remove(matchKey(team1, team2));
            if (extras != null) {
                merged.addAll(extras);
            }
        }
        for (List<String> extras : overlayByKey.values()) {
            merged.addAll(extras);
        }
        return merged;
    }

    public static Map<String, String> predictedWinnersByMatch(List<String> lines) {
        if (lines == null || lines.size() <= 1) {
            return Map.of();
        }
        String[] headers = lines.get(0).split(",", -1);
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int predIdx = indexOf(headers, "prediction");
        if (team1Idx < 0 || team2Idx < 0 || predIdx < 0) {
            return Map.of();
        }
        EloCalculator elo = new EloCalculator();
        Map<String, String> predicted = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String team1 = elo.extractTeamName(valueAt(cols, team1Idx));
            String team2 = elo.extractTeamName(valueAt(cols, team2Idx));
            String winner = elo.parseTeamFromPrediction(valueAt(cols, predIdx));
            if (team1.isBlank() || team2.isBlank() || winner.isBlank()) continue;
            predicted.put(matchKey(team1, team2), winner);
        }
        return predicted;
    }

    public static List<String> relabelPredictedRowsAsLive(List<String> lines) {
        if (lines == null || lines.size() <= 1) {
            return lines;
        }
        String[] headers = lines.get(0).split(",", -1);
        int pathIdx = indexOf(headers, "path");
        if (pathIdx < 0) {
            return lines;
        }
        List<String> out = new ArrayList<>();
        out.add(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            if ("predicted".equalsIgnoreCase(valueAt(cols, pathIdx)) || "prediction".equalsIgnoreCase(valueAt(cols, pathIdx))) {
                cols[pathIdx] = "live";
                out.add(String.join(",", cols));
            } else {
                out.add(line);
            }
        }
        return out;
    }

    public static List<String> filter(List<String> lines, String pathFilter, String teamFilter) {
        if (lines == null || lines.size() <= 1) {
            return lines;
        }
        String[] headers = lines.get(0).split(",", -1);
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int pathIdx = indexOf(headers, "path");
        if (pathIdx < 0) {
            return lines;
        }
        String normalizedPath = pathFilter == null || pathFilter.isBlank()
                ? "all"
                : pathFilter.trim().toLowerCase();
        if ("both".equals(normalizedPath)) normalizedPath = "all";
        String normalizedTeam = teamFilter == null ? "" : teamFilter.trim().toLowerCase();
        EloCalculator elo = new EloCalculator();
        List<String> out = new ArrayList<>();
        out.add(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String rowPath = valueAt(cols, pathIdx).trim().toLowerCase();
            if (rowPath.isBlank() && cols.length > 7) rowPath = valueAt(cols, 7).trim().toLowerCase();
            boolean pathMatches = switch (normalizedPath) {
                case "all" -> true;
                case "results" -> "results".equals(rowPath) || "fixture".equals(rowPath) || "result_upset".equals(rowPath);
                case "prediction" -> "predicted".equals(rowPath) || "live".equals(rowPath);
                case "upset" -> "upset".equals(rowPath) || "result_upset".equals(rowPath);
                default -> normalizedPath.equals(rowPath);
            };
            if (!pathMatches) continue;
            if (!normalizedTeam.isBlank()) {
                String team1 = elo.extractTeamName(valueAt(cols, team1Idx)).toLowerCase();
                String team2 = elo.extractTeamName(valueAt(cols, team2Idx)).toLowerCase();
                if (!team1.contains(normalizedTeam) && !team2.contains(normalizedTeam)) continue;
            }
            out.add(line);
        }
        return out;
    }

    public static List<String> allTeamNames(List<String> lines) {
        if (lines == null || lines.size() <= 1) {
            return List.of();
        }
        String[] headers = lines.get(0).split(",", -1);
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        if (team1Idx < 0 || team2Idx < 0) {
            return List.of();
        }
        EloCalculator elo = new EloCalculator();
        return lines.stream()
                .skip(1)
                .filter(line -> line != null && !line.isBlank())
                .flatMap(line -> {
                    String[] cols = line.split(",", -1);
                    return java.util.stream.Stream.of(
                            elo.extractTeamName(valueAt(cols, team1Idx)),
                            elo.extractTeamName(valueAt(cols, team2Idx)));
                })
                .filter(team -> team != null && !team.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public static Map<String, String> resultLabels(List<Map<String, String>> rows) {
        return resultMaps(rows).labels();
    }

    public static Map<String, String> resultScores(List<Map<String, String>> rows) {
        return resultMaps(rows).scores();
    }

    private static ResultMaps resultMaps(List<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ResultMaps(Map.of(), Map.of());
        }
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, String> scores = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String team1 = first(row, "team1", "home_team");
            String team2 = first(row, "team2", "away_team");
            if (team1.isBlank() || team2.isBlank()) continue;
            String winner = safeTrim(row.getOrDefault("winner", row.getOrDefault("team", "")));
            String homeScore = safeTrim(row.getOrDefault("home_score", ""));
            String awayScore = safeTrim(row.getOrDefault("away_score", ""));
            if (winner.isBlank()) {
                int home = parseInt(homeScore, 0);
                int away = parseInt(awayScore, 0);
                winner = home > away ? team1 : away > home ? team2 : "Draw";
            }
            String key = matchKey(team1, team2);
            labels.put(key, winner);
            if (!homeScore.isBlank() && !awayScore.isBlank()) {
                scores.put(key, homeScore + " - " + awayScore);
            }
        }
        return new ResultMaps(labels, scores);
    }

    private static String first(Map<String, String> row, String firstKey, String secondKey) {
        String first = row.getOrDefault(firstKey, "");
        if (first != null && !first.trim().isBlank()) return first.trim();
        String second = row.getOrDefault(secondKey, "");
        return second == null ? "" : second.trim();
    }

    private static int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (name.equalsIgnoreCase(headers[i].trim())) return i;
        }
        return -1;
    }

    private static String valueAt(String[] values, int index) {
        return index >= 0 && index < values.length ? values[index].trim() : "";
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private record ResultMaps(Map<String, String> labels, Map<String, String> scores) {
    }
}

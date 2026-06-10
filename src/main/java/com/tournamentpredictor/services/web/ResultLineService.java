package com.tournamentpredictor.services.web;

import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.view.KnockoutViewRows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ResultLineService {
    private ResultLineService() {
    }

    public static Set<String> actualAdvanceTeams(List<String> lines) {
        if (lines == null || lines.size() < 2) {
            return Set.of();
        }
        String[] headers = lines.get(0).split(",", -1);
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int predIdx = indexOf(headers, "prediction");
        if (team1Idx < 0 || team2Idx < 0 || predIdx < 0) {
            return Set.of();
        }
        EloCalculator elo = new EloCalculator();
        Set<String> advancing = new LinkedHashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String team1 = elo.extractTeamName(valueAt(cols, team1Idx));
            String team2 = elo.extractTeamName(valueAt(cols, team2Idx));
            String winner = elo.parseTeamFromPrediction(valueAt(cols, predIdx));
            if (winner.isBlank() || "Draw".equalsIgnoreCase(winner)) continue;
            if (winner.equalsIgnoreCase(team1)) advancing.add(team1);
            else if (winner.equalsIgnoreCase(team2)) advancing.add(team2);
        }
        return advancing;
    }

    public static List<String> buildActualOnlyRows(List<String> lines, Map<String, String> actualResultLabels) {
        if (lines == null || lines.isEmpty() || actualResultLabels == null || actualResultLabels.isEmpty()) {
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
        Set<String> emittedActualKeys = new LinkedHashSet<>();
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
            String key = KnockoutViewRows.matchKey(team1, team2);
            if (!emittedActualKeys.add(key)) {
                continue;
            }
            String actualLabel = actualResultLabels.get(key);
            if (actualLabel == null || actualLabel.isBlank()) continue;
            String[] actualCols = cols.clone();
            actualCols[pathIdx] = "results";
            actualCols[predIdx] = actualLabel;
            out.add(String.join(",", actualCols));
        }
        return out;
    }

    public static List<String> mergeViewLines(List<String> baseLines, List<String> overlayLines) {
        if (baseLines == null || baseLines.isEmpty()) {
            return overlayLines == null ? List.of() : overlayLines;
        }
        if (overlayLines == null || overlayLines.size() <= 1) {
            return baseLines;
        }
        String[] baseHeaders = baseLines.get(0).split(",", -1);
        int baseTeam1Idx = indexOf(baseHeaders, "team1");
        int baseTeam2Idx = indexOf(baseHeaders, "team2");
        if (baseTeam1Idx < 0 || baseTeam2Idx < 0) {
            return baseLines;
        }
        String[] overlayHeaders = overlayLines.get(0).split(",", -1);
        int overlayTeam1Idx = indexOf(overlayHeaders, "team1");
        int overlayTeam2Idx = indexOf(overlayHeaders, "team2");
        if (overlayTeam1Idx < 0 || overlayTeam2Idx < 0) {
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
            overlayByKey.computeIfAbsent(KnockoutViewRows.matchKey(team1, team2), ignored -> new ArrayList<>()).add(line);
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
            List<String> extras = overlayByKey.remove(KnockoutViewRows.matchKey(team1, team2));
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
            predicted.put(KnockoutViewRows.matchKey(team1, team2), winner);
        }
        return predicted;
    }

    private static int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static String valueAt(String[] cols, int idx) {
        return idx >= 0 && idx < cols.length ? cols[idx].trim() : "";
    }
}

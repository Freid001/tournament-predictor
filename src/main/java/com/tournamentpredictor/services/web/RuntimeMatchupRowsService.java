package com.tournamentpredictor.services.web;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RuntimeMatchupRowsService {
    private final RouteInstanceService routeInstanceService;

    public RuntimeMatchupRowsService() {
        this(new RouteInstanceService());
    }

    RuntimeMatchupRowsService(RouteInstanceService routeInstanceService) {
        this.routeInstanceService = routeInstanceService;
    }

    public List<String> matchupLinesForRound(List<PathVisualizationService.RoundRows> roundRows, String safeRound,
                                             String selectedTeam, List<String> baseLines) throws IOException {
        if (baseLines == null || baseLines.isEmpty() || WebText.trim(selectedTeam).isBlank()) return List.of();
        List<Map<String, String>> routeRows = csvLinesToRows(routeInstanceService.buildLines(roundRows, selectedTeam));
        if (routeRows.isEmpty()) return List.of();
        String[] headers = baseLines.get(0).split(",", -1);
        List<String> out = new ArrayList<>();
        out.add(baseLines.get(0));
        Set<String> emitted = new LinkedHashSet<>();
        for (Map<String, String> route : routeRows) {
            if (!safeRound.equalsIgnoreCase(WebText.trim(route.getOrDefault("round", "")))) continue;
            String team = WebText.trim(route.getOrDefault("team", ""));
            String opponent = WebText.trim(route.getOrDefault("opponent", ""));
            String matchId = WebText.trim(route.getOrDefault("match_id", ""));
            String path = normalizeRuntimePath(route.getOrDefault("path", ""));
            if (team.isBlank() || opponent.isBlank() || matchId.isBlank()) continue;
            String key = matchId + "|" + team + "|" + opponent + "|" + path + "|" + route.getOrDefault("path_history", "");
            if (!emitted.add(key)) continue;
            out.add(lineFromHeaders(headers, rowFor(route, team, opponent, matchId, path)));
        }
        return out.size() <= 1 ? List.of() : out;
    }

    private Map<String, String> rowFor(Map<String, String> route, String team, String opponent, String matchId, String path) {
        Map<String, String> row = new LinkedHashMap<>();
        String winner = WebText.trim(route.getOrDefault("winner", ""));
        String seed = WebText.trim(route.getOrDefault("seed", ""));
        String opponentSeed = WebText.trim(route.getOrDefault("opponent_seed", ""));
        row.put("match_id", matchId);
        row.put("team1", team);
        row.put("team2", opponent);
        row.put("path", path);
        row.put("prediction", winner);
        row.put("elo", winner);
        row.put("model_prediction", winner);
        row.put("selection_source", "runtime_route");
        row.put("matchup_pct", WebText.trim(route.getOrDefault("matchup_pct", "")));
        row.put("team1_path_fatigue", "0");
        row.put("team2_path_fatigue", "0");
        row.put("team1_path_opponent", WebText.trim(route.getOrDefault("path_history", "")));
        row.put("team2_path_opponent", WebText.trim(route.getOrDefault("opponent_path", "")));
        row.put("team1_slot", seed);
        row.put("team1_team", team);
        row.put("team1_source_match", WebText.trim(route.getOrDefault("source_match_id", "")));
        row.put("team1_group_finish", seed);
        row.put("team1_bracket_slot", seed);
        row.put("team2_slot", opponentSeed);
        row.put("team2_team", opponent);
        row.put("team2_source_match", "");
        row.put("team2_group_finish", opponentSeed);
        row.put("team2_bracket_slot", opponentSeed);
        return row;
    }

    private List<Map<String, String>> csvLinesToRows(List<String> lines) throws IOException {
        if (lines == null || lines.isEmpty()) return List.of();
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .build()
                .parse(new StringReader(String.join("\n", lines)))) {
            return parser.stream().map(record -> {
                Map<String, String> row = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> header : parser.getHeaderMap().entrySet()) {
                    row.put(header.getKey(), record.get(header.getKey()));
                }
                return row;
            }).toList();
        }
    }

    private String lineFromHeaders(String[] headers, Map<String, String> row) {
        List<String> values = new ArrayList<>();
        for (String header : headers) {
            values.add(csvEscape(row.getOrDefault(header, "")));
        }
        return String.join(",", values);
    }

    private String csvEscape(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private String normalizeRuntimePath(String value) {
        String safe = WebText.trim(value).toLowerCase(java.util.Locale.ROOT);
        if (safe.equals("results") || safe.equals("result") || safe.equals("fixture") || safe.equals("actual") || safe.equals("result_upset")) return "results";
        if (safe.equals("predicted") || safe.equals("live")) return "predicted";
        return "alt";
    }
}

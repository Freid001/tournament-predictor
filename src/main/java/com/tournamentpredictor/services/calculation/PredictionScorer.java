package com.tournamentpredictor.services.calculation;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PredictionScorer {

    private static final String ROUTE_METADATA_HEADER =
            "team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,"
                    + "team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot";

    private static final String PATH_METADATA_HEADER = "matchup_pct,matchup_runs,upset_path";

    static final String OUTPUT_HEADER =
            "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,model_prediction,selection_source,"
                    + ROUTE_METADATA_HEADER + "," + PATH_METADATA_HEADER;

    private static final CSVFormat LINE_FORMAT = CSVFormat.DEFAULT;

    private final EloCalculator eloCalculator;
    private Map<String, TeamEloSnapshot> snapshots = new HashMap<>();

    public PredictionScorer(EloCalculator eloCalculator) {
        this.eloCalculator = eloCalculator;
    }

    public void setSnapshots(Map<String, TeamEloSnapshot> snapshots) {
        this.snapshots = snapshots != null ? new HashMap<>(snapshots) : new HashMap<>();
    }

    public List<String> scoreLines(List<String> rawLines) {
        List<String> output = new ArrayList<>();
        output.add(OUTPUT_HEADER);
        Map<String, Integer> header = header(rawLines);
        for (int i = 1; i < rawLines.size(); i++) {
            String line = rawLines.get(i);
            if (line.trim().isEmpty()) {
                output.add("");
                continue;
            }
            CSVRecord record = parseLine(line);
            if (record == null || record.size() < 5) {
                continue;
            }

            String matchId = record.get(0).trim();
            String team1Display = record.get(1).trim();
            String team2Display = record.get(2).trim();
            String path = record.get(3).trim();
            String eloPrediction = record.get(4).trim();
            String team1PathDiff = record.size() > 5 ? record.get(5).trim() : "0";
            String team2PathDiff = record.size() > 6 ? record.get(6).trim() : "0";
            String team1PathOpponent = record.size() > 7 ? record.get(7).trim() : "";
            String team2PathOpponent = record.size() > 8 ? record.get(8).trim() : "";
            String prediction = eloPrediction;
            String team1Name = routeValue(record, header, "team1_team", eloCalculator.extractTeamName(team1Display));
            String team2Name = routeValue(record, header, "team2_team", eloCalculator.extractTeamName(team2Display));
            TeamEloSnapshot team1Snapshot = snapshots.get(team1Name);
            TeamEloSnapshot team2Snapshot = snapshots.get(team2Name);
            RouteMetadata team1Route = routeMetadata(record, header, "team1", team1Display, team1Name);
            RouteMetadata team2Route = routeMetadata(record, header, "team2", team2Display, team2Name);

            output.add(String.join(",",
                    matchId,
                    team1Name,
                    team2Name,
                    path,
                    prediction,
                    String.valueOf(team1Snapshot != null ? team1Snapshot.baseElo() : 0),
                    String.valueOf(team1Snapshot != null ? team1Snapshot.qualBonus() : 0),
                    String.valueOf(team2Snapshot != null ? team2Snapshot.baseElo() : 0),
                    String.valueOf(team2Snapshot != null ? team2Snapshot.qualBonus() : 0),
                    team1PathDiff,
                    team2PathDiff,
                    team1PathOpponent,
                    team2PathOpponent,
                    eloPrediction,
                    "model",
                    team1Route.slot(),
                    team1Route.team(),
                    team1Route.sourceMatch(),
                    team1Route.groupFinish(),
                    team1Route.bracketSlot(),
                    team2Route.slot(),
                    team2Route.team(),
                    team2Route.sourceMatch(),
                    team2Route.groupFinish(),
                    team2Route.bracketSlot(),
                    routeValue(record, header, "matchup_pct", ""),
                    routeValue(record, header, "matchup_runs", ""),
                    routeValue(record, header, "upset_path", "0")));
        }
        return output;
    }

    private static Map<String, Integer> header(List<String> rawLines) {
        Map<String, Integer> header = new HashMap<>();
        if (rawLines.isEmpty()) return header;
        String[] cols = rawLines.get(0).split(",", -1);
        for (int i = 0; i < cols.length; i++) {
            header.put(cols[i].trim(), i);
        }
        return header;
    }

    private RouteMetadata routeMetadata(CSVRecord record, Map<String, Integer> header, String prefix,
                                        String display, String team) {
        String slot = routeValue(record, header, prefix + "_slot", "");
        String sourceMatch = routeValue(record, header, prefix + "_source_match", "");
        String groupFinish = routeValue(record, header, prefix + "_group_finish", "");
        String bracketSlot = routeValue(record, header, prefix + "_bracket_slot", slot);
        return new RouteMetadata(slot, team, sourceMatch, groupFinish, bracketSlot);
    }

    private static String routeValue(CSVRecord record, Map<String, Integer> header, String column, String fallback) {
        Integer index = header.get(column);
        if (index == null || index < 0 || index >= record.size()) {
            return fallback;
        }
        String value = record.get(index).trim();
        return value.isBlank() ? fallback : value;
    }

    private record RouteMetadata(String slot, String team, String sourceMatch, String groupFinish, String bracketSlot) {
    }

    private static CSVRecord parseLine(String line) {
        try {
            List<CSVRecord> records = LINE_FORMAT.parse(new java.io.StringReader(line)).getRecords();
            return records.isEmpty() ? null : records.get(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

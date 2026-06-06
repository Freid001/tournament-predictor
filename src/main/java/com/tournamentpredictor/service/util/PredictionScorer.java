package com.tournamentpredictor.service.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PredictionScorer {

    static final String OUTPUT_HEADER =
            "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,model_prediction,selection_source";

    private static final CSVFormat LINE_FORMAT = CSVFormat.DEFAULT;

    private final EloCalculator eloCalculator;
    private Map<String, TeamEloSnapshot> snapshots = new HashMap<>();

    public PredictionScorer(EloCalculator eloCalculator) {
        this.eloCalculator = eloCalculator;
    }

    public void setSnapshots(Map<String, TeamEloSnapshot> snapshots) {
        this.snapshots = snapshots != null ? new HashMap<>(snapshots) : new HashMap<>();
    }

    public List<String> scoreLines(List<String> rawLines, Map<String, String> disagreeMap) {
        List<String> output = new ArrayList<>();
        output.add(OUTPUT_HEADER);
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
            String team1Name = eloCalculator.extractTeamName(team1Display);
            String team2Name = eloCalculator.extractTeamName(team2Display);
            TeamEloSnapshot team1Snapshot = snapshots.get(team1Name);
            TeamEloSnapshot team2Snapshot = snapshots.get(team2Name);

            output.add(String.join(",",
                    matchId,
                    team1Display,
                    team2Display,
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
                    "model"));
        }
        return output;
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

package com.tournamentpredictor.service.validator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PredictionsFileValidator {
    public void validatePredictionsFile(Path file) throws IOException {
        List<String> errors = new ArrayList<>();
        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty()) {
            return;
        }

        String[] headers = lines.get(0).split(",", -1);
        int matchIdIndex = indexOf(headers, "match_id");
        int team1Index = indexOf(headers, "team1");
        int team2Index = indexOf(headers, "team2");
        int pathIndex = indexOf(headers, "path");
        int disagreeIndex = indexOf(headers, "do_you_disagree");

        if (matchIdIndex < 0 || team1Index < 0 || team2Index < 0 || pathIndex < 0) {
            errors.add("Header must include match_id, team1, team2, and path columns");
        }

        Map<String, Integer> primaryCount = new LinkedHashMap<>();
        Map<String, Boolean> matchSeen = new LinkedHashMap<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] cols = line.split(",", -1);
            if (cols.length < 4) {
                errors.add("Row " + (i + 1) + ": expected at least 4 columns, got " + cols.length);
                continue;
            }
            if (matchIdIndex < 0 || team1Index < 0 || team2Index < 0 || pathIndex < 0) {
                continue;
            }

            String matchId = valueAt(cols, matchIdIndex);
            String path = valueAt(cols, pathIndex);
            String doYouDisagree = disagreeIndex >= 0 ? valueAt(cols, disagreeIndex) : "";

            matchSeen.put(matchId, Boolean.TRUE);
            if (!doYouDisagree.isEmpty() && !doYouDisagree.equalsIgnoreCase("yes") && !doYouDisagree.equalsIgnoreCase("no")) {
                errors.add("Row " + (i + 1) + " (" + matchId + "): do_you_disagree must be 'yes', 'no', or blank, got: '" + doYouDisagree + "'");
            }
            if ("predicted".equalsIgnoreCase(path)) {
                primaryCount.merge(matchId, 1, Integer::sum);
            }
        }

        for (String matchId : matchSeen.keySet()) {
            if (primaryCount.getOrDefault(matchId, 0) < 1) {
                errors.add("Match " + matchId + ": expected at least 1 primary row, got 0");
            }
        }

        if (!errors.isEmpty()) {
            throw new IOException("Validation failed for " + file.getFileName() + ":\n" + String.join("\n", errors));
        }
    }

    private int indexOf(String[] headers, String headerName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(headerName)) {
                return i;
            }
        }
        return -1;
    }

    private String valueAt(String[] cols, int index) {
        return index >= 0 && index < cols.length ? cols[index].trim() : "";
    }
}

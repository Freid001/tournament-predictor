package com.tournamentpredictor.services.web;

import com.tournamentpredictor.model.start.StartRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StartRowViewService {
    public List<StartRow> build(List<Map<String, String>> csvRows, boolean includeEmptyTemplate) {
        if (csvRows.isEmpty() && includeEmptyTemplate) {
            return emptyTemplate();
        }
        List<StartRow> rows = new ArrayList<>();
        for (Map<String, String> row : csvRows) {
            rows.add(fromCsv(row));
        }
        return rows;
    }

    private List<StartRow> emptyTemplate() {
        List<StartRow> rows = new ArrayList<>();
        for (char group = 'A'; group <= 'L'; group++) {
            for (int i = 0; i < 4; i++) {
                rows.add(new StartRow(String.valueOf(group), "", false,
                        0, 0, 0, 0, 0, 0, 0, 0,
                        "", "", "", "", "", ""));
            }
        }
        return rows;
    }

    private StartRow fromCsv(Map<String, String> row) {
        return new StartRow(
                row.getOrDefault("group", ""),
                row.getOrDefault("team", ""),
                "yes".equalsIgnoreCase(row.getOrDefault("host", "")),
                WebText.parseInt(row.getOrDefault("injury_impact", "0"), 0),
                WebText.parseInt(row.getOrDefault("heat_impact", "0"), 0),
                WebText.parseInt(row.getOrDefault("squad_dropouts", "0"), 0),
                WebText.parseInt(row.getOrDefault("squad_age_profile", "0"), 0),
                WebText.parseInt(row.getOrDefault("squad_cohesion", "0"), 0),
                WebText.parseInt(row.getOrDefault("squad_depth", "0"), 0),
                WebText.parseInt(row.getOrDefault("attack_quality", row.getOrDefault("squad_quality", "0")), 0),
                WebText.parseInt(row.getOrDefault("defence_quality", row.getOrDefault("squad_quality", "0")), 0),
                row.getOrDefault("dropout_notes", ""),
                row.getOrDefault("injury_notes", ""),
                row.getOrDefault("age_notes", ""),
                row.getOrDefault("cohesion_notes", ""),
                row.getOrDefault("depth_notes", ""),
                row.getOrDefault("quality_notes", "")
        );
    }
}

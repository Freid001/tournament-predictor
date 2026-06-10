package com.tournamentpredictor.services.web;

import com.tournamentpredictor.model.group.GroupPickRow;
import com.tournamentpredictor.model.group.GroupPickView;
import com.tournamentpredictor.services.calculation.EloBreakdown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GroupPickViewService {
    public GroupPickView build(List<Map<String, String>> rawRows, Map<String, EloBreakdown> eloBreakdowns) {
        Map<String, EloBreakdown> safeBreakdowns = eloBreakdowns != null ? eloBreakdowns : Map.of();
        List<GroupPickRow> rows = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            Map<String, String> row = rawRows.get(i);
            String team = row.getOrDefault("team", "");
            rows.add(new GroupPickRow(i,
                    row.getOrDefault("group", ""),
                    team,
                    WebText.formatQualBonus(row.getOrDefault("qual_bonus", "")),
                    row.getOrDefault("predicted_position", ""),
                    row.getOrDefault("group_winner", ""),
                    row.getOrDefault("runner_up", ""),
                    row.getOrDefault("3rd_place", ""),
                    WebText.parseInt(row.getOrDefault("squad_age_profile", "0"), 0),
                    WebText.parseInt(row.getOrDefault("squad_cohesion", "0"), 0),
                    safeBreakdowns.get(team)
            ));
        }

        LinkedHashMap<String, List<GroupPickRow>> groupedRows = new LinkedHashMap<>();
        for (GroupPickRow row : rows) {
            groupedRows.computeIfAbsent(row.getGroup(), ignored -> new ArrayList<>()).add(row);
        }
        return new GroupPickView(rows, groupedRows);
    }
}

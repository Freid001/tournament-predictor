package com.tournamentpredictor.services.web;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMatchupRowsServiceTest {
    private final RuntimeMatchupRowsService service = new RuntimeMatchupRowsService();

    @Test
    void selectedTeamAlternativeWinProducesMergeableTableRowWithoutStoredUpsetRow() throws Exception {
        List<PathVisualizationService.RoundRows> rounds = List.of(
                new PathVisualizationService.RoundRows("last_32_match", "Last 32", List.of(
                        row("M75", "Japan", "F1", "", "F1", "F1", "Morocco", "C2", "", "C2", "C2",
                                "predicted", "Japan (60%)"))),
                new PathVisualizationService.RoundRows("last_16_match", "Last 16", List.of(
                        row("M90", "Morocco", "W75", "M75", "C2", "C2", "Canada", "W73", "M73", "B2", "B2",
                                "alt", "Canada (55%)"))));
        List<String> baseLines = List.of("match_id,team1,team2,path,prediction,team1_path_opponent,team2_path_opponent,selection_source,team1_team,team2_team,team1_bracket_slot,team2_bracket_slot");

        List<String> rows = service.matchupLinesForRound(rounds, "last_16_match", "Morocco", baseLines);

        assertEquals(baseLines.get(0), rows.get(0));
        assertTrue(rows.stream().anyMatch(row -> row.startsWith("M90,Morocco,Canada,alt,Canada")
                        && row.contains("runtime_route")
                        && row.contains("Morocco,Canada,C2,B2")),
                "Runtime selected-team route should be converted into a table row: " + rows);
        assertTrue(rows.stream().anyMatch(row -> row.contains("C2:M75|Japan|alt|W > M90|Canada|alt|L")),
                "The table row should keep the runtime path history for the dropdown: " + rows);
    }

    @Test
    void blankSelectedTeamReturnsNoRuntimeRows() throws Exception {
        List<String> rows = service.matchupLinesForRound(List.of(), "last_16_match", "", List.of("match_id,team1,team2"));

        assertTrue(rows.isEmpty());
    }

    private Map<String, String> row(String matchId, String team1, String team1Slot, String team1Source,
                                    String team1GroupFinish, String team1BracketSlot,
                                    String team2, String team2Slot, String team2Source,
                                    String team2GroupFinish, String team2BracketSlot,
                                    String path, String prediction) {
        Map<String, String> row = new java.util.LinkedHashMap<>();
        row.put("match_id", matchId);
        row.put("team1", team1);
        row.put("team2", team2);
        row.put("path", path);
        row.put("prediction", prediction);
        row.put("team1_slot", team1Slot);
        row.put("team1_team", team1);
        row.put("team1_source_match", team1Source);
        row.put("team1_group_finish", team1GroupFinish);
        row.put("team1_bracket_slot", team1BracketSlot);
        row.put("team2_slot", team2Slot);
        row.put("team2_team", team2);
        row.put("team2_source_match", team2Source);
        row.put("team2_group_finish", team2GroupFinish);
        row.put("team2_bracket_slot", team2BracketSlot);
        return row;
    }
}

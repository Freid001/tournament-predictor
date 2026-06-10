package com.tournamentpredictor.services.web;

import com.tournamentpredictor.model.group.GroupPickView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupPickViewServiceTest {
    private final GroupPickViewService service = new GroupPickViewService();

    @Test
    void buildPreservesRowIndexesAndGroupsInInputOrder() {
        GroupPickView view = service.build(List.of(
                Map.of("group", "A", "team", "Spain", "qual_bonus", "12", "predicted_position", "1", "group_winner", "yes"),
                Map.of("group", "A", "team", "Germany", "runner_up", "yes"),
                Map.of("group", "B", "team", "France", "3rd_place", "maybe")
        ), Map.of());

        assertEquals(3, view.rows().size());
        assertEquals(0, view.rows().get(0).getRowIndex());
        assertEquals(2, view.rows().get(2).getRowIndex());
        assertEquals(List.of("A", "B"), List.copyOf(view.groupedRows().keySet()));
        assertEquals(2, view.groupedRows().get("A").size());
        assertEquals("France", view.groupedRows().get("B").get(0).getTeam());
    }

    @Test
    void buildDefaultsMissingNumericValuesToZero() {
        GroupPickView view = service.build(List.of(Map.of("group", "A", "team", "Spain")), null);

        assertEquals(0, view.rows().get(0).getSquadAgeProfile());
        assertEquals(0, view.rows().get(0).getSquadCohesion());
    }
}

package com.tournamentpredictor.services.web;

import com.tournamentpredictor.model.start.StartRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartRowViewServiceTest {
    private final StartRowViewService service = new StartRowViewService();

    @Test
    void buildReturnsEmptyTemplateForEditWhenNoRowsExist() {
        List<StartRow> rows = service.build(List.of(), true);

        assertEquals(48, rows.size());
        assertEquals("A", rows.get(0).getGroup());
        assertEquals("L", rows.get(47).getGroup());
    }

    @Test
    void buildMapsCsvRowsAndFallsBackFromLegacySquadQuality() {
        List<StartRow> rows = service.build(List.of(Map.of(
                "group", "A",
                "team", "Spain",
                "host", "yes",
                "squad_quality", "4",
                "squad_depth", "3"
        )), false);

        assertEquals(1, rows.size());
        assertEquals("Spain", rows.get(0).getTeam());
        assertTrue(rows.get(0).isHost());
        assertEquals(4, rows.get(0).getAttackQuality());
        assertEquals(4, rows.get(0).getDefenceQuality());
        assertEquals(3, rows.get(0).getSquadDepth());
    }

    @Test
    void buildWithoutTemplateKeepsEmptyRowsEmptyForView() {
        assertTrue(service.build(List.of(), false).isEmpty());
    }
}

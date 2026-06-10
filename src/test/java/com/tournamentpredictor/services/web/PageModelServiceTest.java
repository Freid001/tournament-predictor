package com.tournamentpredictor.services.web;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class PageModelServiceTest {
    private final PageModelService service = new PageModelService(new WebControllerService(null));

    @Test
    void addTournamentPageAddsCommonLabels() {
        Model model = new ConcurrentModel();

        service.addTournamentPage(model, "world_cup_2026", "Results");

        assertEquals("world_cup_2026", model.asMap().get("tournament"));
        assertEquals("World Cup 2026", model.asMap().get("tournamentLabel"));
        assertEquals("Results", model.asMap().get("pageTitle"));
    }

    @Test
    void addDisabledNavigationSetsCommonDefaults() {
        Model model = new ConcurrentModel();

        service.addDisabledNavigation(model);

        assertEquals("#", model.asMap().get("prevViewUrl"));
        assertEquals("#", model.asMap().get("nextViewUrl"));
        assertFalse((Boolean) model.asMap().get("prevViewEnabled"));
        assertFalse((Boolean) model.asMap().get("nextViewEnabled"));
        assertNull(model.asMap().get("editUrl"));
    }

    @Test
    void groupByKeepsKeyOrder() {
        var grouped = service.groupBy(List.of("A1", "A2", "B1"), value -> value.substring(0, 1));

        assertEquals(List.of("A", "B"), List.copyOf(grouped.keySet()));
        assertEquals(List.of("A1", "A2"), grouped.get("A"));
    }
}

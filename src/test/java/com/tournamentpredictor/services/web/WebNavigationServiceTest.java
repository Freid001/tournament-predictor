package com.tournamentpredictor.services.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WebNavigationServiceTest {

    @Test
    void viewRoundsMoveThroughBracketSequence() {
        assertEquals("groups", WebNavigationService.nextViewRound("start"));
        assertEquals("last_32_match", WebNavigationService.nextViewRound("groups"));
        assertEquals("last_4_match", WebNavigationService.prevViewRound("final_match"));
        assertNull(WebNavigationService.nextViewRound("final_match"));
    }

    @Test
    void displayNamesUseUserFacingRoundLabels() {
        assertEquals("Quarter Finals", WebNavigationService.displayMode("last_8_match"));
        assertEquals("Group Rankings", WebNavigationService.displayViewMode("groups"));
        assertEquals("World Cup 2026", WebNavigationService.displayTournament("world_cup_2026"));
    }

    @Test
    void editRoundsMapBackToPriorViewRound() {
        assertEquals("last_16_match", WebNavigationService.viewRoundForEdit("last_16"));
        assertEquals("last_8_match", WebNavigationService.editPrevViewRound("last_4"));
        assertEquals("last_8", WebNavigationService.oddsColumnForRound("last_16_match"));
    }
}

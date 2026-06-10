package com.tournamentpredictor.services.web;

import com.tournamentpredictor.model.round.DirectMatchupSummary;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectMatchupPathServiceTest {

    @Test
    void primaryNonUpsetMatchupIsPredicted() {
        DirectMatchupSummary summary = new DirectMatchupSummary("M1", "France", "Spain");
        summary.matchupRuns = 100;

        assertEquals("predicted", DirectMatchupPathService.classify(summary, "France",
                Map.of("M1", 100), Map.of("France", 1900, "Spain", 1850)));
    }

    @Test
    void nonPrimaryNonUpsetMatchupIsAlternative() {
        DirectMatchupSummary summary = new DirectMatchupSummary("M1", "France", "Spain");
        summary.matchupRuns = 40;

        assertEquals("alt", DirectMatchupPathService.classify(summary, "France",
                Map.of("M1", 100), Map.of("France", 1900, "Spain", 1850)));
    }

    @Test
    void lowerEloWinnerIsUpsetEvenWhenMatchupIsPrimary() {
        DirectMatchupSummary summary = new DirectMatchupSummary("M1", "Morocco", "Spain");
        summary.matchupRuns = 100;

        assertEquals("upset", DirectMatchupPathService.classify(summary, "Morocco",
                Map.of("M1", 100), Map.of("Morocco", 1750, "Spain", 1900)));
    }
}

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
    void openingRouteWithPossibleButNonPrimarySlotsIsAlternative() {
        DirectMatchupSummary summary = new DirectMatchupSummary("M49", "Brazil", "Spain");
        summary.matchupRuns = 40;

        assertEquals("alt", DirectMatchupPathService.classifyOpeningRoute(summary, "Brazil", "A1", "B2",
                Map.of("Brazil", "yes", "Spain", "maybe"),
                Map.of("Brazil", "no", "Spain", "maybe"),
                Map.of(), Map.of("M49", 100), Map.of("Brazil", 2100, "Spain", 2050)));
    }

    @Test
    void openingRouteWithImpossibleSlotIsAlternative() {
        DirectMatchupSummary summary = new DirectMatchupSummary("M49", "Cameroon", "Australia");
        summary.matchupRuns = 10;

        assertEquals("alt", DirectMatchupPathService.classifyOpeningRoute(summary, "Cameroon", "A1", "B2",
                Map.of("Cameroon", "no", "Australia", "no"),
                Map.of("Cameroon", "no", "Australia", "no"),
                Map.of(), Map.of("M49", 100), Map.of("Cameroon", 1600, "Australia", 1700)));
    }

    @Test
    void lowerEloWinnerIsAlternativeEvenWhenMatchupIsPrimary() {
        DirectMatchupSummary summary = new DirectMatchupSummary("M1", "Morocco", "Spain");
        summary.matchupRuns = 100;

        assertEquals("alt", DirectMatchupPathService.classify(summary, "Morocco",
                Map.of("M1", 100), Map.of("Morocco", 1750, "Spain", 1900)));
    }
}

package com.tournamentpredictor.service.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EloCalculatorTest {
    private EloCalculator eloCalculator;

    @BeforeEach
    void setUp() {
        eloCalculator = new EloCalculator();
    }

    @Test
    void extractTeamName_fromGroupSlotDisplay() {
        assertEquals("SouthKorea", eloCalculator.extractTeamName("A2(SouthKorea)"));
    }

    @Test
    void extractTeamName_fromCompositeSlotDisplay() {
        assertEquals("SaudiArabia", eloCalculator.extractTeamName("CEFHI3(SaudiArabia)"));
    }

    @Test
    void extractTeamName_fromWinnerDisplay() {
        assertEquals("Germany", eloCalculator.extractTeamName("W74(Germany)"));
    }

    @Test
    void extractTeamName_noParens_returnsAsIs() {
        assertEquals("Germany", eloCalculator.extractTeamName("Germany"));
    }
}

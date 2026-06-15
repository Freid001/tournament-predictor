package com.tournamentpredictor.services.calculation;

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
    void extractTeamName_returnsRawTeamNameOnly() {
        assertEquals("Germany", eloCalculator.extractTeamName("Germany"));
    }

    @Test
    void extractTeamName_doesNotParseLegacySlotWrappedValues() {
        assertEquals("Raw Team Value", eloCalculator.extractTeamName("Raw Team Value"));
        assertEquals("Source Match Token", eloCalculator.extractTeamName("Source Match Token"));
    }
}

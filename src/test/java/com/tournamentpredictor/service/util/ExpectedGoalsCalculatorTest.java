package com.tournamentpredictor.service.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpectedGoalsCalculatorTest {
    private ExpectedGoalsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ExpectedGoalsCalculator();
    }

    @Test
    void equalTeams_haveBalancedExpectedGoalsAndAdvanceProbability() {
        ExpectedGoalsCalculator.Projection projection = calculator.project("A", "B", 1800, 1800);

        assertEquals(1.30, projection.team1ExpectedGoals());
        assertEquals(1.30, projection.team2ExpectedGoals());
        assertEquals(50, projection.team1AdvancePct());
        assertEquals(50, projection.team2AdvancePct());
        assertEquals("A", projection.pick());
    }

    @Test
    void favorite_getsMoreExpectedGoalsAndHigherAdvanceProbability() {
        ExpectedGoalsCalculator.Projection projection = calculator.project("Spain", "Uruguay", 2248, 1842);

        assertTrue(projection.team1ExpectedGoals() > projection.team2ExpectedGoals());
        assertTrue(projection.team1WinPct() > projection.team2WinPct());
        assertTrue(projection.team1AdvancePct() > projection.team2AdvancePct());
        assertEquals("Spain", projection.pick());
    }

    @Test
    void underdog_getsLowerExpectedGoalsAndLowerAdvanceProbability() {
        ExpectedGoalsCalculator.Projection projection = calculator.project("Uruguay", "Spain", 1842, 2248);

        assertTrue(projection.team1ExpectedGoals() < projection.team2ExpectedGoals());
        assertTrue(projection.team1WinPct() < projection.team2WinPct());
        assertTrue(projection.team1AdvancePct() < projection.team2AdvancePct());
        assertEquals("Spain", projection.pick());
    }

    @Test
    void ninetyMinuteProbabilities_sumToOneHundredAfterRounding() {
        ExpectedGoalsCalculator.Projection projection = calculator.project("A", "B", 1900, 1750);

        assertEquals(100, projection.team1WinPct() + projection.drawPct() + projection.team2WinPct());
    }

    @Test
    void knockoutAdvance_resolvesDrawsUsingEloProbability() {
        ExpectedGoalsCalculator.Projection projection = calculator.project("A", "B", 1900, 1750);

        assertTrue(projection.team1AdvancePct() > projection.team1WinPct());
        assertTrue(projection.team2AdvancePct() > projection.team2WinPct());
        assertEquals(100, projection.team1AdvancePct() + projection.team2AdvancePct());
    }

    @Test
    void expectedGoals_areClampedForHugeMismatches() {
        ExpectedGoalsCalculator.Projection projection = calculator.project("A", "B", 3000, 500);

        assertEquals(4.43, projection.team1ExpectedGoals());
        assertEquals(0.20, projection.team2ExpectedGoals());
    }

    @Test
    void textHelpers_formatExpectedGoalsAndScore() {
        ExpectedGoalsCalculator.Projection projection = calculator.project("A", "B", 1800, 1800);

        assertEquals("1.30 - 1.30", projection.expectedGoalsText());
        assertTrue(projection.mostLikelyScoreText().matches("\\d+-\\d+"));
    }


    
    void sampleScoreline_returnsGoalsAndKnockoutAdvanceResult() {
        ExpectedGoalsCalculator.Projection projection = calculator.project("A", "B", 1800, 1800);

        ExpectedGoalsCalculator.SampledScoreline scoreline = projection.sampleScoreline(new Random(7L));

        assertTrue(scoreline.team1Goals() >= 0);
        assertTrue(scoreline.team2Goals() >= 0);
        assertTrue(scoreline.scoreText().matches("\\d+-\\d+"));
    }
}

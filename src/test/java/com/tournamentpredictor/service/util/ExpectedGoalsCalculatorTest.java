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
        assertTrue(projection.mostLikelyScorePct() > 0);
        assertTrue(projection.mostLikelyScorePct() < 25);
    }


    
    @Test
    void attackAndDefenceQualityShapeGoalsWithoutChangingEloInputs() {
        ExpectedGoalsCalculator.Projection baseline = calculator.project("A", "B", 1800, 1800);
        ExpectedGoalsCalculator.Projection shaped = calculator.project("A", "B", 1800, 1800,
                2, 2, -1, -2);

        assertEquals(1.90, shaped.team1ExpectedGoals());
        assertEquals(0.85, shaped.team2ExpectedGoals());
        assertTrue(shaped.team1ExpectedGoals() > baseline.team1ExpectedGoals());
        assertTrue(shaped.team2ExpectedGoals() < baseline.team2ExpectedGoals());
    }

    @Test
    void strongAttacksCanRaiseTotalExpectedGoals() {
        ExpectedGoalsCalculator.Projection projection = calculator.project("A", "B", 1800, 1800,
                2, 0, 2, 0);

        assertEquals(1.60, projection.team1ExpectedGoals());
        assertEquals(1.60, projection.team2ExpectedGoals());
    }

    @Test
    void strongerEloGoalSeparationRaisesFavouriteWinProbability() {
        ExpectedGoalsCalculator baseline = new ExpectedGoalsCalculator(400.0, 2.60, 1.00, 0.95);
        ExpectedGoalsCalculator calibrated = new ExpectedGoalsCalculator(400.0, 2.60, 1.10, 0.95);

        ExpectedGoalsCalculator.Projection baselineProjection = baseline.project("A", "B", 2000, 1800);
        ExpectedGoalsCalculator.Projection calibratedProjection = calibrated.project("A", "B", 2000, 1800);

        assertTrue(calibratedProjection.team1WinPct() > baselineProjection.team1WinPct());
        assertEquals(baselineProjection.drawPct(), calibratedProjection.drawPct());
        assertEquals(50, calibrated.project("A", "B", 1800, 1800).team1AdvancePct());
    }

    @Test
    void totalMultiplierScalesBothTeamsAfterQualityAdjustments() {
        ExpectedGoalsCalculator scaled = new ExpectedGoalsCalculator(400.0, 2.60, 1.00, 0.97);

        ExpectedGoalsCalculator.Projection projection = scaled.project("A", "B", 1800, 1800,
                2, 0, 2, 0);

        assertEquals(1.55, projection.team1ExpectedGoals());
        assertEquals(1.55, projection.team2ExpectedGoals());
    }

    @Test
    void sampleScoreline_returnsGoalsAndKnockoutAdvanceResult() {
        ExpectedGoalsCalculator.Projection projection = calculator.project("A", "B", 1800, 1800);

        ExpectedGoalsCalculator.SampledScoreline scoreline = projection.sampleScoreline(new Random(7L));

        assertTrue(scoreline.team1Goals() >= 0);
        assertTrue(scoreline.team2Goals() >= 0);
        assertTrue(scoreline.scoreText().matches("\\d+-\\d+"));
    }
}

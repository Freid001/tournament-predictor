package com.tournamentpredictor.services.calculation;

import com.tournamentpredictor.config.PredictionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class PathFatigueCalculatorTest {

    private PathFatigueCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PathFatigueCalculator();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Build a PredictionConfig with specific path fatigue values via reflection (no Spring context needed). */
    private PredictionConfig fatigueConfig(int avgElo, int factor,
                                           double m32, double m16, double m8, double m4,
                                           double depthLimited, double depthThin) throws Exception {
        PredictionConfig config = new PredictionConfig();
        setField(config, "pathFatigueTournamentAvgElo",    avgElo);
        setField(config, "pathFatigueEloFactor",           factor);
        setField(config, "pathFatigueStageMultGroup",      0.25);
        setField(config, "pathFatigueStageMultLast32",     m32);
        setField(config, "pathFatigueStageMultLast16",     m16);
        setField(config, "pathFatigueStageMultLast8",      m8);
        setField(config, "pathFatigueStageMultLast4",      m4);
        setField(config, "pathFatigueUpsetMultiplier",       1.25);
        setField(config, "pathFatigueDepthLimitedMultiplier", depthLimited);
        setField(config, "pathFatigueDepthThinMultiplier",    depthThin);
        return config;
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    // ─── Default values ───────────────────────────────────────────────────────

    @Test
    void getTournamentAvgElo_defaultIs1850() {
        assertEquals(1850, calculator.getTournamentAvgElo());
    }

    @Test
    void rawScore_usesTournamentAverage() {
        assertEquals(100,  calculator.rawScore(1950));
        assertEquals(-100, calculator.rawScore(1750));
        assertEquals(0,    calculator.rawScore(1850));
    }

    @Test
    void eloAdjustmentFromWeighted_scalesCorrectly() {
        assertEquals(-12, calculator.eloAdjustmentFromWeighted(100));
        assertEquals(-24, calculator.eloAdjustmentFromWeighted(200));
        assertEquals(-72, calculator.eloAdjustmentFromWeighted(600));
    }

    @Test
    void stageMultiplierForRound_defaultValues() {
        assertEquals(0.25, calculator.stageMultiplierForRound("group"));
        assertEquals(0.5, calculator.stageMultiplierForRound("last_32"));
        assertEquals(1.0, calculator.stageMultiplierForRound("last_16"));
        assertEquals(1.2, calculator.stageMultiplierForRound("last_8"));
        assertEquals(1.5, calculator.stageMultiplierForRound("last_4"));
    }

    @Test
    void stageMultiplierForRound_isCaseInsensitive() {
        assertEquals(0.25, calculator.stageMultiplierForRound("GROUP"));
        assertEquals(1.0, calculator.stageMultiplierForRound("LAST_16"));
        assertEquals(1.2, calculator.stageMultiplierForRound("Last_8"));
    }

    @Test
    void stageMultiplierForRound_throwsOnUnknown() {
        assertThrows(IllegalArgumentException.class, () -> calculator.stageMultiplierForRound("final"));
    }

    @Test
    void groupStageWeightedContribution_onlyCountsAboveAverageOpponents() {
        assertEquals(25, calculator.groupStageWeightedContribution(1950));
        assertEquals(0, calculator.groupStageWeightedContribution(1850));
        assertEquals(0, calculator.groupStageWeightedContribution(1750));
    }

    @Test
    void knockoutWeightedContribution_onlyCountsAboveAverageOpponents() {
        assertEquals(50, calculator.knockoutWeightedContribution(1950, "last_32"));
        assertEquals(0, calculator.knockoutWeightedContribution(1850, "last_32"));
        assertEquals(0, calculator.knockoutWeightedContribution(1750, "last_32"));
    }

    @Test
    void upsetWinAmplifiesKnockoutFatigueContribution() {
        assertEquals(63, calculator.knockoutWeightedContribution(1950, "last_32", true));
        assertEquals(50, calculator.knockoutWeightedContribution(1950, "last_32", false));
        assertEquals(0, calculator.knockoutWeightedContribution(1750, "last_32", true));
    }

    // ─── Positive cap ─────────────────────────────────────────────────────────

    @Nested
    class PositiveCap {
        @Test
        void easyPath_cappedAtZero() {
            // An easy path (negative weighted total) produces 0, not a rest bonus
            assertEquals(0, calculator.eloAdjustmentFromWeighted(-100));
            assertEquals(0, calculator.eloAdjustmentFromWeighted(-500));
        }

        @Test
        void neutralPath_returnsZero() {
            assertEquals(0, calculator.eloAdjustmentFromWeighted(0));
        }

        @Test
        void hardPath_returnsNegative() {
            assertTrue(calculator.eloAdjustmentFromWeighted(100) < 0);
            assertTrue(calculator.eloAdjustmentFromWeighted(600) < 0);
        }
    }

    // ─── Depth multiplier ─────────────────────────────────────────────────────

    @Nested
    class BenchDepthMultiplier {
        @Test
        void goodDepth_reducesFatigue() {
            assertEquals(-61, calculator.applyDepthMultiplier(-72, 0));
            assertEquals(-10, calculator.applyDepthMultiplier(-12, 0));
        }

        @Test
        void limitedDepth_amplifiesFatigue() {
            // -72 × 1.15 = -82.8 → -83
            assertEquals(-83, calculator.applyDepthMultiplier(-72, 1));
        }

        @Test
        void thinDepth_amplifiesFatigueMore() {
            // -72 × 1.30 = -93.6 → -94
            assertEquals(-94, calculator.applyDepthMultiplier(-72, 2));
        }

        @Test
        void noEffectOnZeroFatigue() {
            // Zero fatigue = easy path, no depth penalty to amplify
            assertEquals(0, calculator.applyDepthMultiplier(0, 1));
            assertEquals(0, calculator.applyDepthMultiplier(0, 2));
        }

        @Test
        void noEffectOnPositive() {
            // Positive values should not be possible post-cap, but guard anyway
            assertEquals(5, calculator.applyDepthMultiplier(5, 2));
        }

        @Test
        void thinDepth_smallFatigue() {
            // -12 × 1.30 = -15.6 → -16
            assertEquals(-16, calculator.applyDepthMultiplier(-12, 2));
        }
    }

    // ─── withConfig ───────────────────────────────────────────────────────────

    @Nested
    class WithConfig {
        @Test
        void overrides_tournamentAvgElo() throws Exception {
            PredictionConfig config = fatigueConfig(2000, 12, 0.5, 1.0, 1.2, 1.5, 1.15, 1.30);
            PathFatigueCalculator calc = new PathFatigueCalculator().withConfig(config);
            assertEquals(2000, calc.getTournamentAvgElo());
            assertEquals(100, calc.rawScore(2100));  // 2100 - 2000 = 100
            assertEquals(0,   calc.rawScore(2000));
        }

        @Test
        void overrides_eloFactor() throws Exception {
            PredictionConfig config = fatigueConfig(1850, 20, 0.5, 1.0, 1.2, 1.5, 1.15, 1.30);
            PathFatigueCalculator calc = new PathFatigueCalculator().withConfig(config);
            // (100 / 100) × -20 = -20
            assertEquals(-20, calc.eloAdjustmentFromWeighted(100));
        }

        @Test
        void overrides_stageMultipliers() throws Exception {
            PredictionConfig config = fatigueConfig(1850, 12, 1.0, 2.0, 3.0, 4.0, 1.15, 1.30);
            PathFatigueCalculator calc = new PathFatigueCalculator().withConfig(config);
            assertEquals(1.0, calc.stageMultiplierForRound("last_32"));
            assertEquals(2.0, calc.stageMultiplierForRound("last_16"));
            assertEquals(3.0, calc.stageMultiplierForRound("last_8"));
            assertEquals(4.0, calc.stageMultiplierForRound("last_4"));
        }

        @Test
        void overrides_depthMultipliers() throws Exception {
            PredictionConfig config = fatigueConfig(1850, 12, 0.5, 1.0, 1.2, 1.5, 1.25, 1.50);
            PathFatigueCalculator calc = new PathFatigueCalculator().withConfig(config);
            // -100 × 1.25 = -125
            assertEquals(-125, calc.applyDepthMultiplier(-100, 1));
            // -100 × 1.50 = -150
            assertEquals(-150, calc.applyDepthMultiplier(-100, 2));
        }

        @Test
        void isChainable_returnsItself() throws Exception {
            PredictionConfig config = fatigueConfig(1850, 12, 0.5, 1.0, 1.2, 1.5, 1.15, 1.30);
            PathFatigueCalculator calc = new PathFatigueCalculator();
            assertSame(calc, calc.withConfig(config));
        }
    }

    // ─── Labels ───────────────────────────────────────────────────────────────

    @Test
    void label_coversAllBuckets() {
        assertEquals("Very Easy", calculator.label(-201));
        assertEquals("Easy",      calculator.label(-61));
        assertEquals("Medium",    calculator.label(0));
        assertEquals("Hard",      calculator.label(61));
        assertEquals("Very Hard", calculator.label(201));
    }

    @Test
    void label_boundaries() {
        assertEquals("Very Easy", calculator.label(-201));
        assertEquals("Easy",      calculator.label(-200)); // boundary: -200 is not < -200
        assertEquals("Easy",      calculator.label(-61));
        assertEquals("Medium",    calculator.label(-60));  // -60 is in the <= 60 bucket
        assertEquals("Medium",    calculator.label(60));
        assertEquals("Hard",      calculator.label(61));
        assertEquals("Hard",      calculator.label(200));
        assertEquals("Very Hard", calculator.label(201));
    }

    // ─── End-to-end pipeline ──────────────────────────────────────────────────

    @Nested
    class EndToEnd {
        @Test
        void hardPath_goodDepthSquad() {
            // Beat 1950 (last_32) → 2050 (last_16) → 2100 (last_8)
            int w32  = (int) Math.round(calculator.rawScore(1950) * calculator.stageMultiplierForRound("last_32")); //  50
            int w16  = (int) Math.round(calculator.rawScore(2050) * calculator.stageMultiplierForRound("last_16")); // 200
            int w8   = (int) Math.round(calculator.rawScore(2100) * calculator.stageMultiplierForRound("last_8"));  // 300
            int total = w32 + w16 + w8; // 550
            assertEquals(550, total);
            assertEquals("Very Hard", calculator.label(total));
            int fatigue = calculator.eloAdjustmentFromWeighted(total); // (550/100) × -12 = -66
            assertEquals(-66, fatigue);
            // Good/deep bench: fatigue reduced by 15%
            assertEquals(-56, calculator.applyDepthMultiplier(fatigue, 0));
        }

        @Test
        void hardPath_thinSquad_worseOutcome() {
            int w32  = (int) Math.round(calculator.rawScore(1950) * calculator.stageMultiplierForRound("last_32"));
            int w16  = (int) Math.round(calculator.rawScore(2050) * calculator.stageMultiplierForRound("last_16"));
            int w8   = (int) Math.round(calculator.rawScore(2100) * calculator.stageMultiplierForRound("last_8"));
            int fatigue = calculator.eloAdjustmentFromWeighted(w32 + w16 + w8);
            int goodFatigue = calculator.applyDepthMultiplier(fatigue, 0);
            int thinFatigue   = calculator.applyDepthMultiplier(fatigue, 2);
            // Thin squad is penalised more than normal on same path
            assertTrue(thinFatigue < goodFatigue, "Thin squad should have worse fatigue penalty than good/deep squad");
        }

        @Test
        void excellentDepth_reducesHardPathFatigueByThirtyPercent() {
            assertEquals(-50, calculator.applyDepthMultiplier(-72, -1));
        }

        @Test
        void easyPath_noBonus_forAnyDepth() {
            // Beat 1700 (last_32) — below average, easy path
            int weighted = (int) Math.round(calculator.rawScore(1700) * calculator.stageMultiplierForRound("last_32")); // -75
            int fatigue = calculator.eloAdjustmentFromWeighted(weighted); // capped at 0
            assertEquals(0, fatigue);
            // Depth multiplier has no effect on zero
            assertEquals(0, calculator.applyDepthMultiplier(fatigue, 1));
            assertEquals(0, calculator.applyDepthMultiplier(fatigue, 2));
        }

        @Test
        void cumulativeAccumulation_acrossRounds() {
            // Last32 opp ELO=1950 (raw+100), last_16 opp ELO=2000 (raw+150)
            int total = (int) Math.round(100 * calculator.stageMultiplierForRound("last_32"))
                    + (int) Math.round(150 * calculator.stageMultiplierForRound("last_16"));
            // 50 + 150 = 200
            assertEquals(200, total);
            assertEquals("Hard", calculator.label(total));
            assertEquals(-24, calculator.eloAdjustmentFromWeighted(total));
        }
    }
}

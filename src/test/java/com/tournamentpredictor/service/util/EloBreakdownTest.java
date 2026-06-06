package com.tournamentpredictor.service.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EloBreakdownTest {

    // ─── totalElo formula ────────────────────────────────────────────────────

    @Test
    void totalElo_allSignalsZero_equalsBaseElo() {
        EloBreakdown b = fullBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0);
        assertEquals(1900, b.totalElo);
    }

    @Test
    void totalElo_homeBonusAdded() {
        EloBreakdown b = fullBreakdown(1900, true, 50,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0);
        assertEquals(1950, b.totalElo);
    }

    @Test
    void totalElo_injuryPenaltySubtracted() {
        EloBreakdown b = fullBreakdown(1900, false, 0,
                1, 20, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0);
        assertEquals(1880, b.totalElo);
    }

    @Test
    void totalElo_heatBonusAdded() {
        EloBreakdown b = fullBreakdown(1900, false, 0,
                0, 0, 1, 15, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0);
        assertEquals(1915, b.totalElo);
    }

    @Test
    void totalElo_dropoutPenaltySubtracted() {
        EloBreakdown b = fullBreakdown(1900, false, 0,
                0, 0, 0, 0, 1, 10, 0, 0,
                0, 0, 0, 0, 0, 0);
        assertEquals(1890, b.totalElo);
    }

    @Test
    void totalElo_qualBonusAdded() {
        EloBreakdown b = fullBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 30, 0,
                0, 0, 0, 0, 0, 0);
        assertEquals(1930, b.totalElo);
    }

    @Test
    void totalElo_preTournamentBonusAdded() {
        EloBreakdown b = fullBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 20,
                0, 0, 0, 0, 0, 0);
        assertEquals(1920, b.totalElo);
    }

    @Test
    void totalElo_squadAgePenaltySubtracted() {
        EloBreakdown b = fullBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                1, 10, 0, 0, 0, 0);
        assertEquals(1890, b.totalElo);
    }

    @Test
    void totalElo_squadCohesionPenaltySubtracted() {
        EloBreakdown b = fullBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 1, 10, 0, 0);
        assertEquals(1890, b.totalElo);
    }

    @Test
    void totalElo_squadDepthPenaltySubtracted() {
        EloBreakdown b = fullBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 1, 10);
        assertEquals(1890, b.totalElo);
    }

    @Test
    void totalElo_attackAndDefenceQualityDoNotChangeElo() {
        EloBreakdown b = fullBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0);
        // Use the full constructor with quality bonus directly
        EloBreakdown bq = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 1, 20,
                "", "", "", "", "", "",
                List.of(), List.of(),
                0, "");
        assertEquals(1900, bq.totalElo);
    }

    @Test
    void totalElo_depthStillAppliesWithGoalQuality() {
        // Attack and defence quality are xG-only; the depth penalty still reduces ELO.
        EloBreakdown b = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                1, 10, 1, 10,
                "", "", "", "", "", "",
                List.of(), List.of(),
                0, "");
        assertEquals(1890, b.totalElo);
    }

    @Test
    void totalElo_allSignalsActive_correctSum() {
        // base=2000, home=+50, injury=-20, heat=+15, dropout=-10,
        // qual=+30, preTournament=+25, age=-10, cohesion=-10, depth=-10; goal quality is excluded
        // expected = 2000 + 50 - 20 + 15 - 10 + 30 + 25 - 10 - 10 - 10 = 2060
        EloBreakdown b = new EloBreakdown(2000, true, 50,
                1, 20, 1, 15, 1, 10, 30, 25,
                1, 10, 1, 10,
                1, 10, 1, 20,
                "dn", "in", "an", "cn", "depn", "qn",
                List.of(), List.of(),
                0, "");
        assertEquals(2060, b.totalElo);
    }

    @Test
    void withPathFatigue_returnsCopyWithAdjustmentAndLabel() {
        EloBreakdown original = fullBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0);
        EloBreakdown updated = original.withPathFatigue(-12, "Very Hard");

        assertEquals(1900, original.totalElo);
        assertEquals(0, original.pathFatigueAdjustment);
        assertEquals("", original.pathFatigueLabel);
        assertEquals(1888, updated.totalElo);
        assertEquals(-12, updated.pathFatigueAdjustment);
        assertEquals("Very Hard", updated.pathFatigueLabel);
    }

    @Test
    void totalElo_includesPathDifficultyAdjustment() {
        EloBreakdown b = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                "", "", "", "", "", "",
                List.of(), List.of(),
                12, "Easy");
        assertEquals(1912, b.totalElo);
        assertEquals(12, b.pathFatigueAdjustment);
        assertEquals("Easy", b.pathFatigueLabel);
    }

    // ─── Legacy constructors ─────────────────────────────────────────────────

    @Test
    void legacyConstructor_11param_defaultsDepthQualityToZero() {
        EloBreakdown b = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0,
                List.of());
        assertEquals(0, b.squadDepthLevel);
        assertEquals(0, b.squadDepthPenalty);
        assertEquals(0, b.attackQuality);
        assertEquals(0, b.defenceQuality);
        assertEquals(0, b.preTournamentBonus);
        assertEquals(1900, b.totalElo);
    }

    @Test
    void legacyConstructor_22param_defaultsDepthQualityToZero() {
        EloBreakdown b = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                "dn", "in", "an", "cn",
                List.of(), List.of());
        assertEquals(0, b.squadDepthLevel);
        assertEquals(0, b.squadDepthPenalty);
        assertEquals(0, b.attackQuality);
        assertEquals(0, b.defenceQuality);
        assertEquals(1900, b.totalElo);
    }

    // ─── Notes null-safety ───────────────────────────────────────────────────

    @Test
    void nullNotes_storedAsEmptyStrings() {
        EloBreakdown b = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                null, null, null, null, null, null,
                List.of(), List.of(),
                0, "");
        assertEquals("", b.dropoutNotes);
        assertEquals("", b.injuryNotes);
        assertEquals("", b.ageNotes);
        assertEquals("", b.cohesionNotes);
        assertEquals("", b.depthNotes);
        assertEquals("", b.goalQualityNotes);
    }

    // ─── Immutable result lists ───────────────────────────────────────────────

    @Test
    void qualResults_storedAsImmutableCopy() {
        var results = new java.util.ArrayList<String[]>();
        results.add(new String[]{"W", "vs A · 2–0"});
        EloBreakdown b = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0,
                results);
        results.clear();  // mutate original
        assertEquals(1, b.qualResults.size(), "qualResults should be an immutable copy");
    }

    @Test
    void nullQualResults_storedAsEmptyList() {
        EloBreakdown b = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0,
                null);
        assertNotNull(b.qualResults);
        assertTrue(b.qualResults.isEmpty());
    }

    // ─── Field storage ────────────────────────────────────────────────────────

    @Test
    void fullConstructor_storesAllFields() {
        List<String[]> qr = java.util.Collections.singletonList(new String[]{"W", "vs A"});
        List<String[]> fr = java.util.Collections.singletonList(new String[]{"D", "vs B"});
        EloBreakdown b = new EloBreakdown(2000, true, 50,
                2, 20, 1, 15, 1, 10, 30, 25,
                1, 10, 1, 8,
                2, 20, 1, 15,
                "dropout", "injury", "age", "cohesion", "depth", "quality",
                qr, fr,
                0, "");
        assertEquals(2000, b.baseElo);
        assertTrue(b.isHost);
        assertEquals(50, b.homeBonus);
        assertEquals(2, b.injuryLevel);
        assertEquals(20, b.injuryPenalty);
        assertEquals(1, b.heatLevel);
        assertEquals(15, b.heatBonus);
        assertEquals(1, b.dropoutLevel);
        assertEquals(10, b.dropoutPenalty);
        assertEquals(30, b.qualBonus);
        assertEquals(25, b.preTournamentBonus);
        assertEquals(1, b.squadAgeLevel);
        assertEquals(10, b.squadAgePenalty);
        assertEquals(1, b.squadCohesionLevel);
        assertEquals(8, b.squadCohesionPenalty);
        assertEquals(2, b.squadDepthLevel);
        assertEquals(20, b.squadDepthPenalty);
        assertEquals(1, b.attackQuality);
        assertEquals(15, b.defenceQuality);
        assertEquals("dropout", b.dropoutNotes);
        assertEquals("injury", b.injuryNotes);
        assertEquals("age", b.ageNotes);
        assertEquals("cohesion", b.cohesionNotes);
        assertEquals("depth", b.depthNotes);
        assertEquals("quality", b.goalQualityNotes);
        assertEquals(1, b.qualResults.size());
        assertEquals(1, b.friendlyResults.size());
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    /** Build a full-constructor EloBreakdown with depth and quality (quality as penalty here = 0). */
    private static EloBreakdown fullBreakdown(int baseElo, boolean isHost, int homeBonus,
                                               int injLevel, int injPenalty,
                                               int heatLevel, int heatBonus,
                                               int dropLevel, int dropPenalty,
                                               int qualBonus, int preTournamentBonus,
                                               int ageLevel, int agePenalty,
                                               int cohesionLevel, int cohesionPenalty,
                                               int depthLevel, int depthPenalty) {
        return new EloBreakdown(baseElo, isHost, homeBonus,
                injLevel, injPenalty,
                heatLevel, heatBonus,
                dropLevel, dropPenalty,
                qualBonus, preTournamentBonus,
                ageLevel, agePenalty,
                cohesionLevel, cohesionPenalty,
                depthLevel, depthPenalty, 0, 0,
                "", "", "", "", "", "",
                List.of(), List.of(),
                0, "");
    }
}

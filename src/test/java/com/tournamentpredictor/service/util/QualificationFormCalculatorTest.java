package com.tournamentpredictor.service.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class QualificationFormCalculatorTest {

    private static final String HEADER = "year\tmonth\tday\thome_team\taway_team\thome_score\taway_score\tmatch_type";
    private static final int ELO_MAX = 50;

    @TempDir
    Path historyDir;

    // ─── Helper ──────────────────────────────────────────────────────────────

    /** Write a TSV file for teamName with the given data rows (no header needed separately). */
    private void writeTsv(String teamName, String... dataRows) throws IOException {
        StringBuilder sb = new StringBuilder(HEADER);
        for (String row : dataRows) {
            sb.append('\n').append(row);
        }
        Files.writeString(historyDir.resolve(teamName + ".tsv"), sb.toString());
    }

    /** Build a TSV data row. home/away score, match type, year. */
    private static String row(int year, String home, int homeScore, String away, int awayScore, String type) {
        return rowDate(year, 1, 1, home, homeScore, away, awayScore, type);
    }

    private static String rowDate(int year, int month, int day, String home, int homeScore,
                                  String away, int awayScore, String type) {
        return year + "\t" + month + "\t" + day + "\t" + home + "\t" + away + "\t"
                + homeScore + "\t" + awayScore + "\t" + type;
    }

    private QualificationFormCalculator calc(int sinceYear, int untilYear) {
        return new QualificationFormCalculator(historyDir, sinceYear, untilYear, ELO_MAX);
    }

    // ─── hasData / getFormScore defaults ─────────────────────────────────────

    @Test
    void hasData_noTsvFile_returnsFalse() {
        QualificationFormCalculator c = calc(2023, 2026);
        assertFalse(c.hasData("England"));
    }

    @Test
    void hasData_tsvExistsWithQualData_returnsTrue() throws IOException {
        writeTsv("England",
                row(2024, "England", "Germany", 2, 0, "WQ"));
        assertTrue(calc(2023, 2026).hasData("England"));
    }

    @Test
    void hasData_tsvExistsButOnlyFriendlies_returnsFalse() throws IOException {
        writeTsv("England",
                row(2024, "England", "Germany", 2, 0, "F"));
        assertFalse(calc(2023, 2026).hasData("England"));
    }

    @Test
    void getFormScore_noData_returnsNeutral() {
        assertEquals(0.5, calc(2023, 2026).getFormScore("England"), 0.001);
    }

    @Test
    void getQualBonus_noData_returnsZero() {
        assertEquals(0, calc(2023, 2026).getQualBonus("England"));
    }

    // ─── Year bounds ─────────────────────────────────────────────────────────

    @Test
    void gamesBeforeSinceYear_notCounted() throws IOException {
        // Game in 2022 — before sinceYear=2023 — should be excluded
        writeTsv("England",
                row(2022, "England", "Germany", 3, 0, "WQ"),
                row(2024, "England", "France", 1, 0, "WQ"));
        QualificationFormCalculator c = calc(2023, 2026);
        // Only the 2024 game counts — 1 win, 1-0 in goals
        assertEquals(1, c.getQualBonus("England") > 0 ? 1 : 0, // should be positive
                "Expected positive bonus when only 2024 win counted");
    }

    @Test
    void gamesAfterUntilYear_notCounted() throws IOException {
        // Game in 2027 — after untilYear=2026 — should be excluded
        writeTsv("England",
                row(2027, "England", "Germany", 3, 0, "WQ"),
                row(2024, "England", "France", 1, 0, "WQ"));
        QualificationFormCalculator c = calc(2023, 2026);
        // Only the 2024 game counts
        assertTrue(c.hasData("England"));
    }

    @Test
    void gamesOutsideBothBounds_noDataRemaining() throws IOException {
        writeTsv("England",
                row(2021, "England", "Germany", 3, 0, "WQ"),
                row(2028, "England", "France", 3, 0, "WQ"));
        QualificationFormCalculator c = calc(2023, 2026);
        assertFalse(c.hasData("England"));
        assertEquals(0, c.getQualBonus("England"));
    }

    @Test
    void gamesAtBoundaryYears_included() throws IOException {
        // Exactly sinceYear=2023 and untilYear=2026 should both be included
        writeTsv("England",
                row(2023, "England", "Germany", 1, 0, "WQ"),
                row(2026, "England", "France", 1, 0, "WQ"));
        assertTrue(calc(2023, 2026).hasData("England"));
    }


    void maximumDateExcludesMatchesAfterTournamentStarts() throws IOException {
        writeTsv("England",
                rowDate(2026, 6, 10, "England", 1, "Before", 0, "WQ"),
                rowDate(2026, 6, 12, "After", 5, "England", 0, "WQ"));
        QualificationFormCalculator c = new QualificationFormCalculator(historyDir, 2023, 2026, ELO_MAX,
                Set.of("WQ"), 0, LocalDate.of(2026, 6, 11));
        assertTrue(c.getQualBonus("England") > 0, "Post-start loss must not affect form");
    }

    // ─── Match type filtering ─────────────────────────────────────────────────

    @Test
    void friendlyMatch_notCounted() throws IOException {
        writeTsv("England",
                row(2024, "England", "Germany", 3, 0, "F"));
        assertFalse(calc(2023, 2026).hasData("England"));
    }

    @Test
    void wqMatch_counted() throws IOException {
        writeTsv("England",
                row(2024, "England", "Germany", 1, 0, "WQ"));
        assertTrue(calc(2023, 2026).hasData("England"));
    }

    @Test
    void wqsMatch_counted() throws IOException {
        writeTsv("England",
                row(2024, "England", "Germany", 1, 0, "WQS"));
        assertTrue(calc(2023, 2026).hasData("England"));
    }

    @Test
    void fqMatch_counted() throws IOException {
        writeTsv("England",
                row(2024, "England", "Germany", 1, 0, "FQ"));
        assertTrue(calc(2023, 2026).hasData("England"));
    }

    // ─── Score formula ────────────────────────────────────────────────────────

    @Test
    void winAllGames_convincingly_returnsMaxBonus() throws IOException {
        // Win all 3 games 3-0 → ppg=1.0, gdNorm=1.0, score=1.0, bonus=+50
        writeTsv("England",
                row(2024, "England", "A", 3, 0, "WQ"),
                row(2024, "England", "B", 3, 0, "WQ"),
                row(2024, "England", "C", 3, 0, "WQ"));
        assertEquals(50, calc(2023, 2026).getQualBonus("England"));
    }

    @Test
    void loseAllGames_returnsNegativeBonus() throws IOException {
        // Lose all 3 games 0-3 → ppg=0, gdNorm=0, score=0, bonus=-50
        writeTsv("England",
                row(2024, "A", "England", 3, 0, "WQ"),
                row(2024, "B", "England", 3, 0, "WQ"),
                row(2024, "C", "England", 3, 0, "WQ"));
        assertEquals(-50, calc(2023, 2026).getQualBonus("England"));
    }

    @Test
    void drawAllGames_returnsNegativeBonus() throws IOException {
        // All 0-0 draws → ppg=0.333, gfNorm=0.0, gaNormInv=1.0, score=0.4 → bonus=-10
        writeTsv("England",
                row(2024, "England", "A", 0, 0, "WQ"),
                row(2024, "England", "B", 0, 0, "WQ"),
                row(2024, "England", "C", 0, 0, "WQ"));
        int bonus = calc(2023, 2026).getQualBonus("England");
        assertEquals(-10, bonus);
    }

    @Test
    void winAllGames_narrowly_returnsHighButNotMaxBonus() throws IOException {
        // Win 3 games 1-0 → ppg=1.0, gfNorm=0.333, gaNormInv=1.0, score=0.867, bonus=+37
        writeTsv("England",
                row(2024, "England", "A", 1, 0, "WQ"),
                row(2024, "England", "B", 1, 0, "WQ"),
                row(2024, "England", "C", 1, 0, "WQ"));
        assertEquals(37, calc(2023, 2026).getQualBonus("England"));
    }

    @Test
    void awayWin_teamInAwayColumn_correctlyRecognised() throws IOException {
        // England plays as AWAY team, wins 0-2
        writeTsv("England",
                row(2024, "Germany", "England", 0, 2, "WQ"),
                row(2024, "France", "England", 0, 2, "WQ"),
                row(2024, "Spain", "England", 0, 2, "WQ"));
        // 3 wins 2-0 as away → same as home win 2-0: ppg=1.0, gd=+2/game
        int bonus = calc(2023, 2026).getQualBonus("England");
        assertTrue(bonus > 0, "Away wins should produce positive bonus");
    }

    @Test
    void mixedResults_bonusIsBetweenExtremes() throws IOException {
        writeTsv("England",
                row(2024, "England", "A", 2, 0, "WQ"),  // win
                row(2024, "England", "B", 1, 1, "WQ"),  // draw
                row(2024, "C", "England", 1, 0, "WQ")); // loss (England is away, scores 0)
        int bonus = calc(2023, 2026).getQualBonus("England");
        assertTrue(bonus > -50 && bonus < 50, "Mixed results should produce a mid-range bonus");
    }

    @Test
    void eloMax_scalesBonus() throws IOException {
        // Same results, different eloMax — bonus should scale proportionally
        writeTsv("England",
                row(2024, "England", "A", 3, 0, "WQ"));
        int bonus50 = new QualificationFormCalculator(historyDir, 2023, 2026, 50).getQualBonus("England");
        int bonus100 = new QualificationFormCalculator(historyDir, 2023, 2026, 100).getQualBonus("England");
        assertEquals(bonus50 * 2, bonus100, "Bonus should scale linearly with eloMax");
    }

    @Test
    void nonExistentHistoryDir_noData() {
        QualificationFormCalculator c = new QualificationFormCalculator(
                Path.of("/nonexistent/path"), 2023, 2026, ELO_MAX);
        assertFalse(c.hasData("England"));
        assertEquals(0, c.getQualBonus("England"));
        assertEquals(0.5, c.getFormScore("England"), 0.001);
    }

    // ─── maxGames (last N games) ──────────────────────────────────────────────

    private QualificationFormCalculator calcWithMaxGames(int maxGames, Set<String> types) {
        return new QualificationFormCalculator(historyDir, 2023, 2026, ELO_MAX, types, maxGames);
    }

    @Test
    void maxGames_limitsToMostRecentGames() throws IOException {
        // Write 8 friendlies: first 3 are losses, last 5 are wins
        // With maxGames=5 only the 5 wins are used → positive bonus
        // Without limit all 8 (3 losses + 5 wins) → lower bonus
        writeTsv("England",
                row(2023, "A", "England", 3, 0, "F"),
                row(2023, "B", "England", 3, 0, "F"),
                row(2023, "C", "England", 3, 0, "F"),
                row(2024, "England", "D", 3, 0, "F"),
                row(2024, "England", "E", 3, 0, "F"),
                row(2024, "England", "F", 3, 0, "F"),
                row(2025, "England", "G", 3, 0, "F"),
                row(2025, "England", "H", 3, 0, "F"));
        QualificationFormCalculator limited   = calcWithMaxGames(5, Set.of("F"));
        QualificationFormCalculator unlimited = calcWithMaxGames(0, Set.of("F"));
        // limited: 5 wins 3-0 → max bonus
        assertEquals(50, limited.getQualBonus("England"));
        // unlimited: 5 wins + 3 losses → lower
        assertTrue(unlimited.getQualBonus("England") < limited.getQualBonus("England"));
    }

    @Test
    void maxGames_fewerGamesThanLimit_allUsed() throws IOException {
        // Only 3 friendlies but maxGames=5 — all 3 used
        writeTsv("England",
                row(2024, "England", "A", 3, 0, "F"),
                row(2024, "England", "B", 3, 0, "F"),
                row(2024, "England", "C", 3, 0, "F"));
        QualificationFormCalculator c = calcWithMaxGames(5, Set.of("F"));
        assertEquals(50, c.getQualBonus("England"));
    }

    @Test
    void maxGames_zeroMeansYearFilterApplies() throws IOException {
        // maxGames=0 → fall back to year-bounded behaviour
        writeTsv("England",
                row(2020, "England", "A", 3, 0, "F"),  // before sinceYear=2023
                row(2024, "England", "B", 0, 3, "F")); // loss in range
        QualificationFormCalculator c = calcWithMaxGames(0, Set.of("F"));
        // Only 2024 loss counts → negative bonus
        assertTrue(c.getQualBonus("England") < 0);
    }

    @Test
    void maxGames_appliesYearFilterBeforeLimit() throws IOException {
        // All 5 games are from 2020 (before sinceYear=2023) — they are excluded before maxGames is applied
        writeTsv("England",
                row(2020, "England", "A", 3, 0, "F"),
                row(2020, "England", "B", 3, 0, "F"),
                row(2020, "England", "C", 3, 0, "F"),
                row(2020, "England", "D", 3, 0, "F"),
                row(2020, "England", "E", 3, 0, "F"));
        QualificationFormCalculator withLimit  = calcWithMaxGames(5, Set.of("F"));
        QualificationFormCalculator withoutLimit = calcWithMaxGames(0, Set.of("F"));
        assertFalse(withoutLimit.hasData("England"));
        assertFalse(withLimit.hasData("England"));
    }

    @Test
    void maxGames_friendlyType_computesScore() throws IOException {
        // Validate the friendly set.of("F") + maxGames combination produces a score
        writeTsv("England",
                row(2024, "England", "A", 2, 1, "F"),
                row(2024, "England", "B", 1, 1, "F"),
                row(2024, "England", "C", 3, 0, "F"));
        QualificationFormCalculator c = calcWithMaxGames(5, Set.of("F"));
        assertTrue(c.hasData("England"));
        assertTrue(c.getQualBonus("England") > 0, "2 wins + 1 draw should be positive");
    }

    // ─── Helper overload (string-arg convenience) ────────────────────────────

    private static String row(int year, String home, String away, int homeScore, int awayScore, String type) {
        return row(year, home, homeScore, away, awayScore, type);
    }
}

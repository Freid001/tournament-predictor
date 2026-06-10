package com.tournamentpredictor.services.calculation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FriendliesFormCalculatorTest {

    private static final String HEADER = "year\tmonth\tday\thome_team\taway_team\thome_score\taway_score\tmatch_type";

    @TempDir
    Path historyDir;

    private void writeTsv(String teamName, String... dataRows) throws IOException {
        StringBuilder sb = new StringBuilder(HEADER);
        for (String row : dataRows) sb.append('\n').append(row);
        Files.writeString(historyDir.resolve(teamName + ".tsv"), sb.toString());
    }

    private static String row(int year, int month, int day, String home, int homeScore, String away, int awayScore, String type) {
        return year + "\t" + month + "\t" + day + "\t" + home + "\t" + away + "\t" + homeScore + "\t" + awayScore + "\t" + type;
    }

    // ─── hasData ─────────────────────────────────────────────────────────────

    @Test
    void noHistoryDir_noData() {
        FriendliesFormCalculator c = new FriendliesFormCalculator(Path.of("/nonexistent/path"), 5);
        assertFalse(c.hasData("England"));
        assertEquals(0.5, c.getFormScore("England"), 0.001);
    }

    @Test
    void noFriendlyGames_onlyQualifiers_noData() throws IOException {
        writeTsv("England",
                row(2024, 1, 1, "England", 2, "Germany", 0, "WQ"));
        FriendliesFormCalculator c = new FriendliesFormCalculator(historyDir, 5);
        assertFalse(c.hasData("England"));
    }

    @Test
    void hasFriendlyGames_hasData() throws IOException {
        writeTsv("England",
                row(2024, 1, 1, "England", 2, "Germany", 0, "F"));
        FriendliesFormCalculator c = new FriendliesFormCalculator(historyDir, 5);
        assertTrue(c.hasData("England"));
    }

    @Test
    void ftType_counted() throws IOException {
        writeTsv("England",
                row(2024, 1, 1, "England", 2, "Germany", 0, "FT"));
        FriendliesFormCalculator c = new FriendliesFormCalculator(historyDir, 5);
        assertTrue(c.hasData("England"), "FT (friendly tournament) should count");
    }

    @Test
    void nonFriendlyType_notCounted() throws IOException {
        writeTsv("England",
                row(2024, 1, 1, "England", 2, "Germany", 0, "WC"));
        FriendliesFormCalculator c = new FriendliesFormCalculator(historyDir, 5);
        assertFalse(c.hasData("England"));
    }

    // ─── Score formula ────────────────────────────────────────────────────────

    @Test
    void allWins_convincingly_highScore() throws IOException {
        // Win all 5 games 3-0 → ppg=1.0, gdNorm=1.0 → score = 0.7*1 + 0.3*1 = 1.0
        writeTsv("England",
                row(2024, 1, 1, "England", 3, "A", 0, "F"),
                row(2024, 2, 1, "England", 3, "B", 0, "F"),
                row(2024, 3, 1, "England", 3, "C", 0, "F"),
                row(2024, 4, 1, "England", 3, "D", 0, "F"),
                row(2024, 5, 1, "England", 3, "E", 0, "F"));
        FriendliesFormCalculator c = new FriendliesFormCalculator(historyDir, 5);
        assertEquals(1.0, c.getFormScore("England"), 0.001);
    }

    @Test
    void allLosses_convincingly_lowScore() throws IOException {
        // Lose all 5 games 0-3 → ppg=0, gdNorm=((-3)+3)/6=0 → score = 0.0
        writeTsv("England",
                row(2024, 1, 1, "A", 3, "England", 0, "F"),
                row(2024, 2, 1, "B", 3, "England", 0, "F"),
                row(2024, 3, 1, "C", 3, "England", 0, "F"),
                row(2024, 4, 1, "D", 3, "England", 0, "F"),
                row(2024, 5, 1, "E", 3, "England", 0, "F"));
        FriendliesFormCalculator c = new FriendliesFormCalculator(historyDir, 5);
        assertEquals(0.0, c.getFormScore("England"), 0.001);
    }

    @Test
    void awayWin_teamInAwayColumn_recognisedCorrectly() throws IOException {
        writeTsv("England",
                row(2024, 1, 1, "Germany", 0, "England", 3, "F"));
        FriendliesFormCalculator c = new FriendliesFormCalculator(historyDir, 5);
        assertTrue(c.getFormScore("England") > 0.5, "Away win should produce above-neutral score");
    }

    // ─── lastN limiting ───────────────────────────────────────────────────────

    @Test
    void limitsToLastN_olderGamesExcluded() throws IOException {
        // 8 games: first 3 losses, last 5 wins — with lastN=5, only the wins count → score=1.0
        writeTsv("England",
                row(2022, 1, 1, "A", 3, "England", 0, "F"),
                row(2022, 2, 1, "B", 3, "England", 0, "F"),
                row(2022, 3, 1, "C", 3, "England", 0, "F"),
                row(2023, 1, 1, "England", 3, "D", 0, "F"),
                row(2023, 2, 1, "England", 3, "E", 0, "F"),
                row(2023, 3, 1, "England", 3, "F", 0, "F"),
                row(2024, 1, 1, "England", 3, "G", 0, "F"),
                row(2024, 2, 1, "England", 3, "H", 0, "F"));
        FriendliesFormCalculator c = new FriendliesFormCalculator(historyDir, 5);
        assertEquals(1.0, c.getFormScore("England"), 0.001);
    }

    @Test
    void fewerGamesThanLastN_allUsed() throws IOException {
        // 3 games, lastN=5 → all 3 used
        writeTsv("England",
                row(2024, 1, 1, "England", 3, "A", 0, "F"),
                row(2024, 2, 1, "England", 3, "B", 0, "F"),
                row(2024, 3, 1, "England", 3, "C", 0, "F"));
        FriendliesFormCalculator c = new FriendliesFormCalculator(historyDir, 5);
        assertEquals(1.0, c.getFormScore("England"), 0.001);
    }

    @Test
    void chronologicalSort_gamesSortedByDate() throws IOException {
        // Games written out-of-order; lastN=2 should take the 2 most recent by date
        // Recent (2024): 2 wins; Older (2022): 2 losses — out-of-order in file
        writeTsv("England",
                row(2022, 6, 1, "A", 3, "England", 0, "F"),   // old loss
                row(2024, 3, 1, "England", 3, "C", 0, "F"),   // new win
                row(2022, 7, 1, "B", 3, "England", 0, "F"),   // old loss
                row(2024, 4, 1, "England", 3, "D", 0, "F"));  // new win
        FriendliesFormCalculator c = new FriendliesFormCalculator(historyDir, 2);
        assertEquals(1.0, c.getFormScore("England"), 0.001,
                "lastN=2 should pick the 2 most recent (by date) games");
    }

    @Test
    void historicalAliasUsesCorrectHomeAwayOrientation() throws IOException {
        writeTsv("Czechia",
                row(2024, 1, 1, "Czech Republic", 3, "Germany", 0, "F"));
        QualificationFormCalculator c = new QualificationFormCalculator(
                historyDir, 2024, 2024, 50, java.util.Set.of("F"), 3, null);
        assertTrue(c.getQualBonus("Czechia") > 0);
    }
}

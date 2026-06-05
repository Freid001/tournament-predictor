package com.tournamentpredictor.service.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadToHeadCalculatorTest {
    @TempDir
    Path tempDir;

    @Test
    void computeFriendlyPrediction_appliesTimeDecay() throws IOException {
        int currentYear = LocalDate.now().getYear();
        writeHistory("Team One", List.of(
                currentYear + "\t1\t1\tTeam One\tTeam Two\t2\t0\tF",
                (currentYear - 10) + "\t1\t1\tTeam One\tTeam Two\t0\t1\tF"
        ));

        HeadToHeadCalculator calculator = new HeadToHeadCalculator(tempDir);

        assertEquals("Team One (80%)",
                calculator.computeFriendlyPrediction("Team One", "Team Two"));
    }

    @Test
    void computeCompetitionPrediction_ignoresFriendlies() throws IOException {
        int currentYear = LocalDate.now().getYear();
        writeHistory("Team One", List.of(
                currentYear + "\t1\t1\tTeam One\tTeam Two\t1\t0\tF",
                currentYear + "\t2\t1\tTeam One\tTeam Two\t0\t1\tWC"
        ));

        HeadToHeadCalculator calculator = new HeadToHeadCalculator(tempDir);

        assertEquals("Team One (100%)",
                calculator.computeFriendlyPrediction("Team One", "Team Two"));
        assertEquals("Team Two (100%)",
                calculator.computeCompetitionPrediction("Team One", "Team Two"));
    }

    @Test
    void computeFriendlyPrediction_fallsBackToSecondTeamHistory() throws IOException {
        int currentYear = LocalDate.now().getYear();
        writeHistory("Team Two", List.of(
                currentYear + "\t1\t1\tTeam Two\tTeam One\t0\t2\tF"
        ));

        HeadToHeadCalculator calculator = new HeadToHeadCalculator(tempDir);

        assertEquals("Team One (100%)",
                calculator.computeFriendlyPrediction("Team One", "Team Two"));
    }

    @Test
    void computeFriendlyPrediction_noHistory_returnsTeamOneAt50Percent() {
        // No TSV files at all → falls back to 0.5 win rate
        HeadToHeadCalculator calculator = new HeadToHeadCalculator(tempDir);
        assertEquals("Team One (50%)", calculator.computeFriendlyPrediction("Team One", "Team Two"));
    }

    @Test
    void computeCompetitionPrediction_noHistory_returnsTeamOneAt50Percent() {
        HeadToHeadCalculator calculator = new HeadToHeadCalculator(tempDir);
        assertEquals("Team One (50%)", calculator.computeCompetitionPrediction("Team One", "Team Two"));
    }

    @Test
    void computeRawCompWinRate_noHistory_isEmpty() {
        HeadToHeadCalculator calculator = new HeadToHeadCalculator(tempDir);
        assertTrue(calculator.computeRawCompWinRate("Team One", "Team Two").isEmpty());
    }

    @Test
    void computeRawFriendlyWinRate_noHistory_isEmpty() {
        HeadToHeadCalculator calculator = new HeadToHeadCalculator(tempDir);
        assertTrue(calculator.computeRawFriendlyWinRate("Team One", "Team Two").isEmpty());
    }

    @Test
    void computeFriendlyPrediction_singleDraw_returns50Percent() throws IOException {
        int currentYear = LocalDate.now().getYear();
        writeHistory("Team One", List.of(
                currentYear + "\t1\t1\tTeam One\tTeam Two\t1\t1\tF"
        ));
        HeadToHeadCalculator calculator = new HeadToHeadCalculator(tempDir);
        assertEquals("Team One (50%)", calculator.computeFriendlyPrediction("Team One", "Team Two"));
    }

    @Test
    void computeCompetitionPrediction_onlyFriendliesExist_returns50Percent() throws IOException {
        // Competition mode ignores friendlies → no comp data → 50%
        int currentYear = LocalDate.now().getYear();
        writeHistory("Team One", List.of(
                currentYear + "\t1\t1\tTeam One\tTeam Two\t3\t0\tF"
        ));
        HeadToHeadCalculator calculator = new HeadToHeadCalculator(tempDir);
        assertEquals("Team One (50%)", calculator.computeCompetitionPrediction("Team One", "Team Two"));
    }

    private void writeHistory(String teamName, List<String> lines) throws IOException {
        Path dir = tempDir.resolve("data").resolve("elo").resolve("history");
        Files.createDirectories(dir);
        Files.write(dir.resolve(teamName + ".tsv"), lines);
    }
}

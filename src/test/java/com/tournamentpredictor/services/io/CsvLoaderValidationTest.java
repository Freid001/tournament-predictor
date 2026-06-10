package com.tournamentpredictor.services.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CsvLoader.validateGroups().
 *
 * Validation rules (9-col new format):
 *   - Header: group,team,base_elo,qual_bonus,elo_ranking,predicted_position,group_winner,runner_up,3rd_place
 *   - Position: [A-L] (new) or [A-L][1-4] (legacy)
 *   - Team name is non-empty, no duplicates
 *   - base_elo / qual_bonus / elo_ranking: integers
 *   - predicted_position: integer 1-4
 *   - group_winner / runner_up / 3rd_place values are yes/maybe/no
 *   - Each group has exactly 4 teams
 *   - Each group has exactly 1 group_winner=yes
 *   - Each group has exactly 1 runner_up=yes
 *   - 3rd_place can have multiple yes/maybe per group (no uniqueness rule)
 *
 * Backward-compat: 6-col and legacy h2h formats are also accepted.
 */
class CsvLoaderValidationTest {

    @TempDir
    Path tempDir;

    private static final String TOURNAMENT = "test";

    private static List<String> validGroupsCsv() {
        List<String> lines = new ArrayList<>();
        lines.add("group,team,base_elo,qual_bonus,elo_ranking,predicted_position,group_winner,runner_up,3rd_place");
        String[] groups = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"};
        int n = 1;
        for (String g : groups) {
            lines.add(g + ",Team" + n++ + ",1900,12,1912,1,yes,maybe,no");
            lines.add(g + ",Team" + n++ + ",1800,6,1806,2,maybe,yes,maybe");
            lines.add(g + ",Team" + n++ + ",1700,0,1700,3,no,maybe,yes");
            lines.add(g + ",Team" + n++ + ",1600,-8,1592,4,no,no,no");
            lines.add("");
        }
        return lines;
    }

    private void writeGroups(List<String> lines) throws IOException {
        Path dir = tempDir.resolve("data").resolve("predictions").resolve(TOURNAMENT);
        Files.createDirectories(dir);
        Files.write(dir.resolve("groups.csv"), lines);
    }

    private List<String> validate() throws IOException {
        return new CsvLoader(tempDir).validateGroups(TOURNAMENT);
    }

    @Test
    void validFile_noErrors() throws IOException {
        writeGroups(validGroupsCsv());
        assertTrue(validate().isEmpty());
    }

    @Test
    void missingColumn_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(1, "A,TeamX,1900,12,1912,1,yes,maybe");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("Row") && e.contains("9 columns")));
    }

    @Test
    void invalidPosition_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(1, "Z,TeamX,1900,12,1912,1,yes,maybe,no");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("invalid group") && e.contains("Z")));
    }

    @Test
    void positionDigitOutOfRange_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(1, "A5,TeamX,1900,12,1912,1,yes,maybe,no");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("invalid group") && e.contains("A5")));
    }

    @Test
    void emptyTeamName_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(1, "A,,1900,12,1912,1,yes,maybe,no");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("team name is empty")));
    }

    @Test
    void duplicateTeamName_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(6, "B,Team1,1900,12,1912,1,yes,maybe,no");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("duplicate team") && e.contains("Team1")));
    }

    @Test
    void extraTeamInGroup_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.add(5, "A,TeamExtra,1500,0,1500,3,no,no,no");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("Group A") && e.contains("4 teams")));
    }

    @Test
    void missingTeamInGroup_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.remove(4);
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("Group A") && e.contains("4 teams")));
    }

    @Test
    void invalidGroupWinnerValue_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(1, "A,Team1,1900,12,1912,1,invalid,maybe,no");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("group_winner") && e.contains("invalid")));
    }

    @Test
    void invalidRunnerUpValue_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(2, "A,Team2,1800,6,1806,2,maybe,bad,maybe");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("runner_up") && e.contains("bad")));
    }

    @Test
    void invalidThirdPlaceValue_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(3, "A,Team3,1700,0,1700,3,no,maybe,oops");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("3rd_place") && e.contains("oops")));
    }

    @Test
    void noGroupWinnerYes_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(1, "A,Team1,1900,12,1912,1,maybe,maybe,no");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("Group A") && e.contains("group_winner=yes") && e.contains("0")));
    }

    @Test
    void twoGroupWinnerYes_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(2, "A,Team2,1800,6,1806,2,yes,yes,maybe");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("Group A") && e.contains("group_winner=yes") && e.contains("2")));
    }

    @Test
    void noRunnerUpYes_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(2, "A,Team2,1800,6,1806,2,maybe,maybe,maybe");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("Group A") && e.contains("runner_up=yes") && e.contains("0")));
    }

    @Test
    void twoRunnerUpYes_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(3, "A,Team3,1700,0,1700,3,no,yes,yes");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("Group A") && e.contains("runner_up=yes") && e.contains("2")));
    }

    @Test
    void multipleThirdPlaceYes_noError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(4, "A,Team4,1600,-8,1592,4,no,no,yes");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().noneMatch(e -> e.contains("3rd_place") && e.contains("Group A")));
    }

    @Test
    void sixColumnOldFormat_noErrors() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("position,team,elo_ranking,group_winner,runner_up,3rd_place");
        String[] groups = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"};
        int n = 1;
        for (String g : groups) {
            lines.add(g + "1,Team" + n++ + ",1900,yes,maybe,no");
            lines.add(g + "2,Team" + n++ + ",1800,maybe,yes,maybe");
            lines.add(g + "3,Team" + n++ + ",1700,no,maybe,yes");
            lines.add(g + "4,Team" + n++ + ",1600,no,no,no");
            lines.add("");
        }
        writeGroups(lines);

        assertTrue(validate().isEmpty());
    }

    @Test
    void eightColumnFormat_noErrors() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("position,team,elo_ranking,group_h2h_comp,group_h2h_friendly,group_winner,runner_up,3rd_place");
        String[] groups = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"};
        int n = 1;
        for (String g : groups) {
            lines.add(g + "1,Team" + n++ + ",1900,67%,55%,yes,maybe,no");
            lines.add(g + "2,Team" + n++ + ",1800,50%,50%,maybe,yes,maybe");
            lines.add(g + "3,Team" + n++ + ",1700,40%,45%,no,maybe,yes");
            lines.add(g + "4,Team" + n++ + ",1600,30%,35%,no,no,no");
            lines.add("");
        }
        writeGroups(lines);

        assertTrue(validate().isEmpty());
    }

    @Test
    void invalidBaseElo_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(1, "A,Team1,abc,12,1912,1,yes,maybe,no");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("base_elo") && e.contains("integer")));
    }

    @Test
    void invalidQualBonus_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(2, "A,Team2,1800,abc,1806,2,maybe,yes,maybe");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("qual_bonus") && e.contains("integer")));
    }

    @Test
    void invalidPredictedPosition_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(1, "A,Team1,1900,12,1912,5,yes,maybe,no");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("predicted_position") && e.contains("1-4")));
    }

    @Test
    void nonIntegerPredictedPosition_reportsError() throws IOException {
        List<String> lines = validGroupsCsv();
        lines.set(1, "A,Team1,1900,12,1912,x,yes,maybe,no");
        writeGroups(lines);

        List<String> errors = validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("predicted_position") && e.contains("integer")));
    }
}

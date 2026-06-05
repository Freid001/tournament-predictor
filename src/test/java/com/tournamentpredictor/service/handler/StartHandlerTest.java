package com.tournamentpredictor.service.handler;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.CsvHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StartHandlerTest {

    @TempDir
    Path root;

    private CsvLoader loader;
    private CsvHelper csvHelper;

    @BeforeEach
    void setUp() throws IOException {
        loader = new CsvLoader(root);
        csvHelper = new CsvHelper();
        // Minimal world.csv: rank,team_code,team_name,rating
        Path eloDir = root.resolve("data/elo/snapshots/test");
        Files.createDirectories(eloDir);
        Files.writeString(eloDir.resolve("teams.csv"),
                "rank,team_code,team_name,rating\n" +
                "1,EN,England,2000\n" +
                "2,US,USA,1800\n");
        // Empty snapshot history dir so qual/pre-tournament bonuses are always 0
        Files.createDirectories(eloDir.resolve("history"));
        Files.writeString(eloDir.resolve("metadata.properties"),
                "qual_form_since=2023\n" +
                "qual_form_until=2026\n" +
                "pre_tournament_form_since=2026\n" +
                "pre_tournament_form_until=2026\n");
    }

    // ─── Constructor defaults ─────────────────────────────────────────────────

    @Nested
    class ConstructorDefaults {

        @Test
        void defaultHeatAdvantages_areCorrect() {
            StartHandler handler = new StartHandler(loader, root, csvHelper);
            assertArrayEquals(new int[]{0, 8, 15, 25}, handler.HEAT_ADVANTAGES,
                    "Default HEAT_ADVANTAGES should be {0, 8, 15, 25}");
        }

        @Test
        void defaultSquadDropoutPenalties_areCorrect() {
            StartHandler handler = new StartHandler(loader, root, csvHelper);
            assertArrayEquals(new int[]{0, 20, 40, 75}, handler.SQUAD_DROPOUT_PENALTIES,
                    "Default SQUAD_DROPOUT_PENALTIES should be {0, 20, 40, 75}");
        }
    }

    // ─── heat_impact column (optional) ───────────────────────────────────────

    @Nested
    class HeatImpact {

        /** heat_impact=2 → HEAT_ADVANTAGES[2]=15 added to adjusted ELO. */
        @Test
        void heatImpactPresent_addsHeatBonusToElo() throws IOException {
            writeStartCsv("group,team,host,injury_impact,heat_impact\n" +
                          "A,England,no,0,2\n");

            new StartHandler(loader, root, csvHelper).handle("test");

            int adjustedElo = readAdjustedElo("test", "England");
            assertEquals(2000 + 15, adjustedElo,
                    "heat_impact=2 should add HEAT_ADVANTAGES[2]=15 to base ELO 2000");
        }

        /** heat_impact column absent → no heat adjustment (defaults to 0). */
        @Test
        void heatImpactAbsent_noAdjustment() throws IOException {
            writeStartCsv("group,team,host,injury_impact\n" +
                          "A,England,no,0\n");

            new StartHandler(loader, root, csvHelper).handle("test");

            int adjustedElo = readAdjustedElo("test", "England");
            assertEquals(2000, adjustedElo,
                    "Missing heat_impact column should leave ELO unadjusted");
        }

        /** heat_impact header present but cell blank → defaults to 0, no adjustment. */
        @Test
        void heatImpactBlankCell_noAdjustment() throws IOException {
            writeStartCsv("group,team,host,injury_impact,heat_impact\n" +
                          "A,England,no,0,\n");

            new StartHandler(loader, root, csvHelper).handle("test");

            int adjustedElo = readAdjustedElo("test", "England");
            assertEquals(2000, adjustedElo,
                    "Blank heat_impact cell should default to 0 (no adjustment)");
        }
    }

    // ─── squad_dropouts column (optional) ────────────────────────────────────

    @Nested
    class SquadDropouts {

        /** squad_dropouts=1 → SQUAD_DROPOUT_PENALTIES[1]=20 subtracted from adjusted ELO. */
        @Test
        void squadDropoutsPresent_subtractsPenaltyFromElo() throws IOException {
            writeStartCsv("group,team,host,injury_impact,squad_dropouts\n" +
                          "A,England,no,0,1\n");

            new StartHandler(loader, root, csvHelper).handle("test");

            int adjustedElo = readAdjustedElo("test", "England");
            assertEquals(2000 - 20, adjustedElo,
                    "squad_dropouts=1 should subtract SQUAD_DROPOUT_PENALTIES[1]=20 from base ELO 2000");
        }

        /** squad_dropouts column absent → no dropout penalty (defaults to 0). */
        @Test
        void squadDropoutsAbsent_noPenalty() throws IOException {
            writeStartCsv("group,team,host,injury_impact\n" +
                          "A,England,no,0\n");

            new StartHandler(loader, root, csvHelper).handle("test");

            int adjustedElo = readAdjustedElo("test", "England");
            assertEquals(2000, adjustedElo,
                    "Missing squad_dropouts column should leave ELO unadjusted");
        }

        /** squad_dropouts header present but cell blank → defaults to 0, no penalty. */
        @Test
        void squadDropoutsBlankCell_noPenalty() throws IOException {
            writeStartCsv("group,team,host,injury_impact,squad_dropouts\n" +
                          "A,England,no,0,\n");

            new StartHandler(loader, root, csvHelper).handle("test");

            int adjustedElo = readAdjustedElo("test", "England");
            assertEquals(2000, adjustedElo,
                    "Blank squad_dropouts cell should default to 0 (no penalty)");
        }
    }

    // ─── Combined signals ─────────────────────────────────────────────────────

    @Nested
    class CombinedSignals {

        /**
         * heat_impact=2 (+15) and squad_dropouts=1 (-20): net = 2000 + 15 - 20 = 1995.
         * Verifies the signals are combined correctly with the right signs.
         */
        @Test
        void heatAndDropout_combinedCorrectly() throws IOException {
            writeStartCsv("group,team,host,injury_impact,heat_impact,squad_dropouts\n" +
                          "A,England,no,0,2,1\n");

            new StartHandler(loader, root, csvHelper).handle("test");

            int adjustedElo = readAdjustedElo("test", "England");
            assertEquals(2000 + 15 - 20, adjustedElo,
                    "heat=+15 and dropout=-20 should produce net 2000+15-20=1995");
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void writeStartCsv(String content) throws IOException {
        Path predDir = root.resolve("data/predictions/test");
        Files.createDirectories(predDir);
        Files.writeString(predDir.resolve("start.csv"), content);
    }

    /**
     * Reads the adjusted ELO (elo_ranking column) for the given team from groups.csv.
     * Parses the header to locate the column by name rather than relying on a fixed index.
     */
    private int readAdjustedElo(String tournament, String team) throws IOException {
        Path groupsFile = root.resolve("data/predictions/" + tournament + "/groups.csv");
        List<String> lines = Files.readAllLines(groupsFile);
        assertTrue(lines.size() >= 2, "groups.csv should have a header and at least one data row");

        String[] headers = lines.get(0).split(",", -1);
        int eloIdx = -1;
        for (int i = 0; i < headers.length; i++) {
            if ("elo_ranking".equals(headers[i].trim())) {
                eloIdx = i;
                break;
            }
        }
        assertNotEquals(-1, eloIdx, "groups.csv should contain an elo_ranking column");

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split(",", -1);
            if (cols.length > 1 && team.equals(cols[1].trim())) {
                return Integer.parseInt(cols[eloIdx].trim());
            }
        }
        throw new AssertionError("Team '" + team + "' not found in groups.csv");
    }
}

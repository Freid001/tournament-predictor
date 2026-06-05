package com.tournamentpredictor.service.builder;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.PathCalculator;
import com.tournamentpredictor.service.util.PathFatigueCalculator;
import com.tournamentpredictor.service.util.SlotStatusEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Last8LineBuilderTest {

    private Last8LineBuilder builder;

    // Scored R16 format (same 13-col structure as last_8 scored output)
    private static final String HEADER = "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent";

    @BeforeEach
    void setUp() {
        EloCalculator elo = new EloCalculator();
        PathCalculator path = new PathCalculator(new SlotStatusEvaluator(elo), elo);
        builder = new Last8LineBuilder(path, elo, new PathFatigueCalculator());
    }

    private List<CsvLoader.BracketEntry> quarterBracket() {
        // M99 QUARTER: winner of M91 vs winner of M92
        return List.of(new CsvLoader.BracketEntry("M99", "W91", "W92", "QUARTER"));
    }

    /** Builds a scored last_16 row in the 13-column format Last8LineBuilder reads. */
    private String scoredRow(String matchId, String team1Display, String team2Display, String path,
                              String prediction, int t1Path, int t2Path, String t1Opp, String t2Opp) {
        return matchId + "," + team1Display + "," + team2Display + "," + path + "," + prediction
                + ",1900,40,2000,48," + t1Path + "," + t2Path + "," + t1Opp + "," + t2Opp;
    }

    // ─── Prefer predicted over alt ────────────────────────────────────────────

    @Nested
    class PrefersPredicteOverAlt {

        /**
         * Team has an alt win in M90 (scanned first) and a predicted win in M92 (correct R16 match).
         * Expected: QF output uses the R16 opponent from the predicted match.
         */
        @Test
        void usesPredictedR16OpponentNotAltWinInEarlierMatch() {
            Map<String, Integer> elos = Map.of(
                    "England", 2020, "Senegal", 1867, "Mexico", 1867, "Norway", 1917);

            // M90 alt: England wins Mexico (earlier match ID, wrong bracket slot)
            String m90Alt = scoredRow("M90", "W83(K2(England))", "W79(A1(Mexico))",
                    "alt", "England (70%)", -77, -16, "Uzbekistan:0", "Canada:0");
            // M92 predicted: England beats Senegal (correct R16)
            String m92Pred = scoredRow("M92", "W80(L1(England))", "W79(CEFHI3(Senegal))",
                    "predicted", "England (61%)", -77, 7, "Uzbekistan:0", "Mexico:-1");
            // M91 for the other quarter slot
            String m91Pred = scoredRow("M91", "W77(I1(Norway))", "W78(I2(France))",
                    "predicted", "Norway (56%)", 0, 0, "", "");

            List<String> rows = Arrays.asList(HEADER, m90Alt, m91Pred, m92Pred);
            List<String> output = builder.buildLast8Lines(elos, quarterBracket(), rows);

            String m99 = output.stream()
                    .filter(l -> l.startsWith("M99,") && l.contains(",predicted,"))
                    .findFirst().orElseThrow(() -> new AssertionError("No M99 predicted row found"));

            // England is team2 in M99 (W92 bracket), chain in col[8]
            String[] cols = m99.split(",", -1);
            String t2Chain = cols[8];

            assertTrue(t2Chain.contains("Senegal"),
                    "Expected Senegal (predicted R16 opponent) but got: " + t2Chain);
            assertFalse(t2Chain.contains("Mexico"),
                    "Mexico (alt) should not appear in England's chain: " + t2Chain);
        }
    }

    // ─── Path chain starts fresh ──────────────────────────────────────────────

    @Nested
    class PathChainStartsAtR32 {

        @Test
        void r32OpponentBecomesFirstSegmentInChain() {
            Map<String, Integer> elos = Map.of(
                    "England", 2020, "Senegal", 1867, "Norway", 1917, "France", 2081);

            // R32 opponent for England was Uzbekistan — stored as prior chain in scored R16 rows
            String m91Pred = scoredRow("M91", "W77(I1(Norway))", "W78(I2(France))",
                    "predicted", "Norway (56%)", 0, 0, "", "");
            String m92Pred = scoredRow("M92", "W80(L1(England))", "W79(CEFHI3(Senegal))",
                    "predicted", "England (61%)", -77, 7, "Uzbekistan:0", "Mexico:-1");

            List<String> output = builder.buildLast8Lines(elos, quarterBracket(), Arrays.asList(HEADER, m91Pred, m92Pred));

            String m99 = output.stream()
                    .filter(l -> l.startsWith("M99,") && l.contains(",predicted,"))
                    .findFirst().orElseThrow();
            String[] cols = m99.split(",", -1);
            String t2Chain = cols[8]; // England is team2

            // Uzbekistan (R32 from prior chain) then Senegal (R16 just added)
            assertTrue(t2Chain.startsWith("Uzbekistan:0 > Senegal:"),
                    "Chain should start with R32 opponent then R16 opponent: " + t2Chain);
        }
    }

    // ─── Output structure ─────────────────────────────────────────────────────

    @Nested
    class OutputStructure {

        @Test
        void outputIncludesHeaderRow() {
            Map<String, Integer> elos = Map.of("England", 2020, "Senegal", 1867, "Norway", 1917);
            String m91 = scoredRow("M91", "W77(I1(Norway))", "W78(I2(France))", "predicted", "Norway (56%)", 0, 0, "", "");
            String m92 = scoredRow("M92", "W80(L1(England))", "W79(CEFHI3(Senegal))", "predicted", "England (61%)", -77, 7, "Uzbekistan:0", "");

            List<String> output = builder.buildLast8Lines(elos, quarterBracket(), Arrays.asList(HEADER, m91, m92));
            assertTrue(output.get(0).startsWith("match_id"), "First line should be header");
        }
    }
}

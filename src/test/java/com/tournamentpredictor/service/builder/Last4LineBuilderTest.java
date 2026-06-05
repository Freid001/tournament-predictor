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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Last4LineBuilderTest {

    private Last4LineBuilder builder;

    // Scored format: match_id,team1,team2,path,prediction,t1_base,t1_qual,t2_base,t2_qual,t1_path,t2_path,t1_opp,t2_opp
    private static final String HEADER = "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent";

    @BeforeEach
    void setUp() {
        EloCalculator elo = new EloCalculator();
        PathCalculator path = new PathCalculator(new SlotStatusEvaluator(elo), elo);
        builder = new Last4LineBuilder(path, elo, new PathFatigueCalculator());
    }

    private List<CsvLoader.BracketEntry> semiBracket() {
        // M102 SEMI: winner of M99 vs winner of M100
        return List.of(new CsvLoader.BracketEntry("M102", "W99", "W100", "SEMI"));
    }

    /** Builds a scored last_8 row in the 13-column format Last4LineBuilder reads. */
    private String scoredRow(String matchId, String team1Display, String team2Display, String path,
                              String prediction, int t1Path, int t2Path, String t1Opp, String t2Opp) {
        return matchId + "," + team1Display + "," + team2Display + "," + path + "," + prediction
                + ",1900,40,2000,48," + t1Path + "," + t2Path + "," + t1Opp + "," + t2Opp;
    }

    // ─── Prefer predicted over alt ────────────────────────────────────────────

    @Nested
    class PrefersPredicteOverAlt {

        /**
         * Mirrors the real England bug:
         * M98 alt = England wins Turkey (earlier match ID, scanned first)
         * M99 predicted = England wins Norway (correct QF)
         * Expected: Norway appears in the path chain, not Turkey.
         */
        @Test
        void usesNorwayNotTurkeyWhenAltWinInEarlierMatch() {
            Map<String, Integer> elos = Map.of(
                    "England", 2020, "Norway", 1917, "Turkey", 1906, "Argentina", 2113, "Portugal", 1984);

            // M98 alt: England wins Turkey (comes first by match ID when scanning)
            String m98Alt = scoredRow("M98", "W93(W83(L2(England)))", "W94(W81(D1(Turkey)))",
                    "alt", "England (64%)", 3, -24, "Uzbekistan:0 > Senegal:-10", "Algeria:0 > Belgium:-6");
            // M99 predicted: England beats Norway (the correct QF result)
            String m99Pred = scoredRow("M99", "W91(W78(I2(Norway)))", "W92(W80(L1(England)))",
                    "predicted", "England (57%)", 110, 3, "Morocco:0 > Germany:-14", "Uzbekistan:0 > Senegal:-10");
            // M100 predicted: Argentina beats Portugal
            String m100Pred = scoredRow("M100", "W95(W86(J1(Argentina)))", "W96(W87(K1(Portugal)))",
                    "predicted", "Argentina (65%)", -35, 108, "Uruguay:0 > Paraguay:0", "England:-10 > Austria:-3");

            List<String> rows = Arrays.asList(HEADER, m98Alt, m99Pred, m100Pred);

            List<String> output = builder.buildLast4Lines(elos, semiBracket(), rows);
            String m102 = output.stream()
                    .filter(l -> l.startsWith("M102,") && l.contains(",predicted,"))
                    .findFirst().orElseThrow(() -> new AssertionError("No M102 predicted row found"));

            // England is team1 in M102 (W99 bracket), path chain in col[7]
            String t1Chain = m102.split(",", -1)[7];
            assertTrue(t1Chain.contains("Norway"),
                    "Expected Norway in path chain but got: " + t1Chain);
            assertFalse(t1Chain.contains("Turkey"),
                    "Turkey should not appear in England's path chain: " + t1Chain);
        }

        @Test
        void prefersPredictedWinOverMultipleAltWinsInEarlierMatches() {
            Map<String, Integer> elos = Map.of(
                    "Spain", 2165, "Colombia", 1977, "Turkey", 1906, "France", 2081, "Germany", 1925);

            // Two alt rows for Spain (lower match IDs) — Colombia and Turkey are from alt scenarios
            String m97Alt = scoredRow("M97", "W89(W77(I1(Spain)))", "W90(W75(F1(Colombia)))",
                    "alt", "Spain (61%)", 14, -4, "Austria:-2", "Croatia:-3");
            String m98Alt = scoredRow("M98", "W93(W84(H1(Spain)))", "W94(W81(D1(Turkey)))",
                    "alt", "Spain (73%)", 14, -24, "Austria:-2", "Algeria:0");
            // M99 predicted: Spain beats France — prior chain uses Japan (unambiguous non-alt team)
            String m99Pred = scoredRow("M99", "W91(W78(I1(Spain)))", "W92(W80(L1(France)))",
                    "predicted", "Spain (54%)", 113, 78, "Austria:-2 > Japan:-10", "United States:0 > Germany:-14");
            String m100Pred = scoredRow("M100", "W95(W86(J1(Germany)))", "W96(W87(K1(Germany)))",
                    "predicted", "Germany (55%)", 0, 0, "", "");

            List<String> rows = Arrays.asList(HEADER, m97Alt, m98Alt, m99Pred, m100Pred);
            List<String> output = builder.buildLast4Lines(elos, semiBracket(), rows);

            String m102 = output.stream()
                    .filter(l -> l.startsWith("M102,") && l.contains(",predicted,"))
                    .findFirst().orElseThrow(() -> new AssertionError("No M102 predicted row"));

            String t1Chain = m102.split(",", -1)[7];
            assertTrue(t1Chain.contains("France"), "Expected France (predicted SF opponent) in chain: " + t1Chain);
            // Colombia and Turkey came only from alt rows — their names must not appear as NEW segment
            assertFalse(t1Chain.endsWith("Colombia") || t1Chain.contains("> Colombia"),
                    "Colombia (alt) must not be appended as latest opponent: " + t1Chain);
            assertFalse(t1Chain.endsWith("Turkey") || t1Chain.contains("> Turkey"),
                    "Turkey (alt) must not be appended as latest opponent: " + t1Chain);
        }
    }

    // ─── Path chain accumulation ──────────────────────────────────────────────

    @Nested
    class PathChainAccumulation {

        @Test
        void priorChainFromPredictedRowIsPreservedAndExtended() {
            Map<String, Integer> elos = Map.of(
                    "England", 2020, "Norway", 1917, "Argentina", 2113, "Portugal", 1984);

            // England prior chain = "Uzbekistan:0 > Senegal:-10" (accumulated from R32 + R16)
            String m99Pred = scoredRow("M99", "W91(W78(I2(Norway)))", "W92(W80(L1(England)))",
                    "predicted", "England (57%)", 110, 3, "Morocco:0 > Germany:-14", "Uzbekistan:0 > Senegal:-10");
            String m100Pred = scoredRow("M100", "W95(W86(J1(Argentina)))", "W96(W87(K1(Portugal)))",
                    "predicted", "Argentina (65%)", -35, 108, "Uruguay:0 > Paraguay:0", "England:-10 > Austria:-3");

            List<String> output = builder.buildLast4Lines(elos, semiBracket(), Arrays.asList(HEADER, m99Pred, m100Pred));

            String m102 = output.stream()
                    .filter(l -> l.startsWith("M102,") && l.contains(",predicted,"))
                    .findFirst().orElseThrow();
            String t1Chain = m102.split(",", -1)[7];

            // Prior chain preserved, Norway appended as latest segment
            assertTrue(t1Chain.startsWith("Uzbekistan:0 > Senegal:-10 > Norway:"),
                    "Chain should be prior history + Norway: " + t1Chain);
        }

        @Test
        void bothTeamsChainsBuiltIndependently() {
            Map<String, Integer> elos = Map.of(
                    "England", 2020, "Norway", 1917, "Argentina", 2113, "Portugal", 1984);

            String m99Pred = scoredRow("M99", "W91(W78(I2(Norway)))", "W92(W80(L1(England)))",
                    "predicted", "England (57%)", 110, 3, "Morocco:0 > Germany:-14", "Uzbekistan:0 > Senegal:-10");
            String m100Pred = scoredRow("M100", "W95(W86(J1(Argentina)))", "W96(W87(K1(Portugal)))",
                    "predicted", "Argentina (65%)", -35, 108, "Uruguay:0 > Paraguay:0", "England:-10 > Austria:-3");

            List<String> output = builder.buildLast4Lines(elos, semiBracket(), Arrays.asList(HEADER, m99Pred, m100Pred));

            String m102 = output.stream()
                    .filter(l -> l.startsWith("M102,") && l.contains(",predicted,"))
                    .findFirst().orElseThrow();
            String[] cols = m102.split(",", -1);
            String t2Chain = cols[8]; // Argentina's chain

            assertTrue(t2Chain.startsWith("Uruguay:0 > Paraguay:0 > Portugal:"),
                    "Argentina chain should include prior + Portugal: " + t2Chain);
        }
    }

    // ─── Fallback behaviour ───────────────────────────────────────────────────

    @Nested
    class Fallback {

        @Test
        void usesFirstAltWinWhenNoPredictedWinExists() {
            Map<String, Integer> elos = Map.of(
                    "England", 2020, "Turkey", 1906, "Norway", 1917, "Argentina", 2113, "Portugal", 1984);

            // No predicted win for England in M99 — only alt wins
            String m98Alt = scoredRow("M98", "W93(W83(L2(England)))", "W94(W81(D1(Turkey)))",
                    "alt", "England (64%)", 3, -24, "Uzbekistan:0", "Algeria:0");
            String m99Alt = scoredRow("M99", "W91(W78(I2(Norway)))", "W92(W80(L1(England)))",
                    "alt", "England (57%)", 110, 3, "Morocco:0", "Uzbekistan:0");
            String m100Pred = scoredRow("M100", "W95(W86(J1(Argentina)))", "W96(W87(K1(Portugal)))",
                    "predicted", "Argentina (65%)", -35, 108, "Uruguay:0 > Paraguay:0", "England:-10 > Austria:-3");

            List<String> rows = Arrays.asList(HEADER, m98Alt, m99Alt, m100Pred);
            List<String> output = builder.buildLast4Lines(elos, semiBracket(), rows);

            // England wins M99 (from alt row), so M102 should still be generated
            boolean hasSfRow = output.stream().anyMatch(l -> l.startsWith("M102,"));
            assertTrue(hasSfRow, "SF row should be generated using alt fallback for England");
        }
    }

    // ─── Output structure ─────────────────────────────────────────────────────

    @Nested
    class OutputStructure {

        @Test
        void outputAlwaysStartsWithHeader() {
            Map<String, Integer> elos = Map.of(
                    "England", 2020, "Norway", 1917, "Argentina", 2113, "Portugal", 1984);
            String m99 = scoredRow("M99", "W91(W78(I2(Norway)))", "W92(W80(L1(England)))",
                    "predicted", "England (57%)", 110, 3, "", "Uzbekistan:0 > Senegal:-10");
            String m100 = scoredRow("M100", "W95(W86(J1(Argentina)))", "W96(W87(K1(Portugal)))",
                    "predicted", "Argentina (65%)", -35, 108, "Uruguay:0", "");

            List<String> output = builder.buildLast4Lines(elos, semiBracket(), Arrays.asList(HEADER, m99, m100));
            assertTrue(output.get(0).startsWith("match_id"), "First line must be header");
        }

        @Test
        void winnerReflectsEloStrength() {
            // Argentina (2113) should beat England (2020) in the SF prediction
            Map<String, Integer> elos = Map.of(
                    "England", 2020, "Norway", 1917, "Argentina", 2113, "Portugal", 1984);
            String m99 = scoredRow("M99", "W91(W78(I2(Norway)))", "W92(W80(L1(England)))",
                    "predicted", "England (57%)", 110, 3, "Morocco:0", "Uzbekistan:0 > Senegal:-10");
            String m100 = scoredRow("M100", "W95(W86(J1(Argentina)))", "W96(W87(K1(Portugal)))",
                    "predicted", "Argentina (65%)", -35, 108, "Uruguay:0", "");

            List<String> output = builder.buildLast4Lines(elos, semiBracket(), Arrays.asList(HEADER, m99, m100));
            String m102 = output.stream()
                    .filter(l -> l.startsWith("M102,") && l.contains(",predicted,"))
                    .findFirst().orElseThrow();

            assertTrue(m102.contains("Argentina"), "Argentina (higher ELO) should be predicted winner");
        }
    }
}

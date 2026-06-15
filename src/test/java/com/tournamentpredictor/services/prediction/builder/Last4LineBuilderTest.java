package com.tournamentpredictor.services.prediction.builder;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PathCalculator;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.calculation.SlotStatusEvaluator;
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

    // Scored prior-round format with structured route metadata.
    private static final String HEADER = "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,"
            + "team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,"
            + "team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot";

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
    private String scoredRow(String matchId, RouteMeta team1, RouteMeta team2, String path,
                              String prediction, int t1Path, int t2Path, String t1Opp, String t2Opp) {
        t1Opp = withoutCurrentOpponent(t1Opp, team2.team);
        t2Opp = withoutCurrentOpponent(t2Opp, team1.team);
        return matchId + "," + team1.team + "," + team2.team + "," + path + "," + prediction
                + ",1900,40,2000,48," + t1Path + "," + t2Path + "," + t1Opp + "," + t2Opp
                + "," + team1.slot + "," + team1.team + "," + team1.sourceMatch + "," + team1.groupFinish + "," + team1.bracketSlot
                + "," + team2.slot + "," + team2.team + "," + team2.sourceMatch + "," + team2.groupFinish + "," + team2.bracketSlot;
    }


    private String withoutCurrentOpponent(String chain, String currentOpponent) {
        if (chain == null || chain.isBlank() || currentOpponent == null || currentOpponent.isBlank()) {
            return chain == null ? "" : chain;
        }
        return java.util.Arrays.stream(chain.split(" > "))
                .map(String::trim)
                .filter(segment -> !segment.equalsIgnoreCase(currentOpponent + ":0")
                        && !segment.startsWith(currentOpponent + ":")
                        && !segment.contains("|" + currentOpponent + ":"))
                .collect(java.util.stream.Collectors.joining(" > "));
    }

    private RouteMeta route(String slot, String team, String sourceMatch, String groupFinish, String bracketSlot) {
        return new RouteMeta(slot, team, sourceMatch, groupFinish, bracketSlot);
    }

    private record RouteMeta(String slot, String team, String sourceMatch, String groupFinish, String bracketSlot) {}


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
            String m98Alt = scoredRow("M98", route("W93", "England", "M93", "L2", "L2"), route("W94", "Turkey", "M94", "D1", "D1"),
                    "alt", "England (64%)", 3, -24, "Uzbekistan:0 > Senegal:-10", "Algeria:0 > Belgium:-6");
            // M99 predicted: England beats Norway (the correct QF result)
            String m99Pred = scoredRow("M99", route("W91", "Norway", "M91", "I2", "I2"), route("W92", "England", "M92", "L1", "L1"),
                    "predicted", "England (57%)", 110, 3, "Morocco:0 > Germany:-14", "Uzbekistan:0 > Senegal:-10");
            // M100 predicted: Argentina beats Portugal
            String m100Pred = scoredRow("M100", route("W95", "Argentina", "M95", "J1", "J1"), route("W96", "Portugal", "M96", "K1", "K1"),
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
            String m97Alt = scoredRow("M97", route("W89", "Spain", "M89", "I1", "I1"), route("W90", "Colombia", "M90", "F1", "F1"),
                    "alt", "Spain (61%)", 14, -4, "Austria:-2", "Croatia:-3");
            String m98Alt = scoredRow("M98", route("W93", "Spain", "M93", "H1", "H1"), route("W94", "Turkey", "M94", "D1", "D1"),
                    "alt", "Spain (73%)", 14, -24, "Austria:-2", "Algeria:0");
            // M99 predicted: Spain beats France — prior chain uses Japan (unambiguous non-alt team)
            String m99Pred = scoredRow("M99", route("W91", "Spain", "M91", "I1", "I1"), route("W92", "France", "M92", "L1", "L1"),
                    "predicted", "Spain (54%)", 113, 78, "Austria:-2 > Japan:-10", "United States:0 > Germany:-14");
            String m100Pred = scoredRow("M100", route("W95", "Germany", "M95", "J1", "J1"), route("W96", "Germany", "M96", "K1", "K1"),
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
            String m99Pred = scoredRow("M99", route("W91", "Norway", "M91", "I2", "I2"), route("W92", "England", "M92", "L1", "L1"),
                    "predicted", "England (57%)", 110, 3, "Morocco:0 > Germany:-14", "Uzbekistan:0 > Senegal:-10");
            String m100Pred = scoredRow("M100", route("W95", "Argentina", "M95", "J1", "J1"), route("W96", "Portugal", "M96", "K1", "K1"),
                    "predicted", "Argentina (65%)", -35, 108, "Uruguay:0 > Paraguay:0", "England:-10 > Austria:-3");

            List<String> output = builder.buildLast4Lines(elos, semiBracket(), Arrays.asList(HEADER, m99Pred, m100Pred));

            String m102 = output.stream()
                    .filter(l -> l.startsWith("M102,") && l.contains(",predicted,"))
                    .findFirst().orElseThrow();
            String t1Chain = m102.split(",", -1)[7];

            // Prior chain preserved, Norway appended as latest segment
            assertTrue(t1Chain.startsWith("Uzbekistan:0 > Senegal:-10 > K@M99|Norway:"),
                    "Chain should be prior history + Norway: " + t1Chain);
        }

        @Test
        void bothTeamsChainsBuiltIndependently() {
            Map<String, Integer> elos = Map.of(
                    "England", 2020, "Norway", 1917, "Argentina", 2113, "Portugal", 1984);

            String m99Pred = scoredRow("M99", route("W91", "Norway", "M91", "I2", "I2"), route("W92", "England", "M92", "L1", "L1"),
                    "predicted", "England (57%)", 110, 3, "Morocco:0 > Germany:-14", "Uzbekistan:0 > Senegal:-10");
            String m100Pred = scoredRow("M100", route("W95", "Argentina", "M95", "J1", "J1"), route("W96", "Portugal", "M96", "K1", "K1"),
                    "predicted", "Argentina (65%)", -35, 108, "Uruguay:0 > Paraguay:0", "England:-10 > Austria:-3");

            List<String> output = builder.buildLast4Lines(elos, semiBracket(), Arrays.asList(HEADER, m99Pred, m100Pred));

            String m102 = output.stream()
                    .filter(l -> l.startsWith("M102,") && l.contains(",predicted,"))
                    .findFirst().orElseThrow();
            String[] cols = m102.split(",", -1);
            String t2Chain = cols[8]; // Argentina's chain

            assertTrue(t2Chain.startsWith("Uruguay:0 > Paraguay:0 > K@M100|Portugal:"),
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
            String m98Alt = scoredRow("M98", route("W93", "England", "M93", "L2", "L2"), route("W94", "Turkey", "M94", "D1", "D1"),
                    "alt", "England (64%)", 3, -24, "Uzbekistan:0", "Algeria:0");
            String m99Alt = scoredRow("M99", route("W91", "Norway", "M91", "I2", "I2"), route("W92", "England", "M92", "L1", "L1"),
                    "alt", "England (57%)", 110, 3, "Morocco:0", "Uzbekistan:0");
            String m100Pred = scoredRow("M100", route("W95", "Argentina", "M95", "J1", "J1"), route("W96", "Portugal", "M96", "K1", "K1"),
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
            String m99 = scoredRow("M99", route("W91", "Norway", "M91", "I2", "I2"), route("W92", "England", "M92", "L1", "L1"),
                    "predicted", "England (57%)", 110, 3, "", "Uzbekistan:0 > Senegal:-10");
            String m100 = scoredRow("M100", route("W95", "Argentina", "M95", "J1", "J1"), route("W96", "Portugal", "M96", "K1", "K1"),
                    "predicted", "Argentina (65%)", -35, 108, "Uruguay:0", "");

            List<String> output = builder.buildLast4Lines(elos, semiBracket(), Arrays.asList(HEADER, m99, m100));
            assertTrue(output.get(0).startsWith("match_id"), "First line must be header");
        }

        @Test
        void winnerReflectsEloStrength() {
            // Argentina (2113) should beat England (2020) in the SF prediction
            Map<String, Integer> elos = Map.of(
                    "England", 2020, "Norway", 1917, "Argentina", 2113, "Portugal", 1984);
            String m99 = scoredRow("M99", route("W91", "Norway", "M91", "I2", "I2"), route("W92", "England", "M92", "L1", "L1"),
                    "predicted", "England (57%)", 110, 3, "Morocco:0", "Uzbekistan:0 > Senegal:-10");
            String m100 = scoredRow("M100", route("W95", "Argentina", "M95", "J1", "J1"), route("W96", "Portugal", "M96", "K1", "K1"),
                    "predicted", "Argentina (65%)", -35, 108, "Uruguay:0", "");

            List<String> output = builder.buildLast4Lines(elos, semiBracket(), Arrays.asList(HEADER, m99, m100));
            String m102 = output.stream()
                    .filter(l -> l.startsWith("M102,") && l.contains(",predicted,"))
                    .findFirst().orElseThrow();

            assertTrue(m102.contains("Argentina"), "Argentina (higher ELO) should be predicted winner");
        }
    }
}

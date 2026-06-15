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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Last8LineBuilderTest {

    private Last8LineBuilder builder;

    // Scored prior-round format with structured route metadata.
    private static final String HEADER = "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,"
            + "team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,"
            + "team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot";

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
         * Team has an alt win in M90 (scanned first) and a predicted win in M92 (correct R16 match).
         * Expected: QF output uses the R16 opponent from the predicted match.
         */
        @Test
        void usesPredictedR16OpponentNotAltWinInEarlierMatch() {
            Map<String, Integer> elos = Map.of(
                    "England", 2020, "Senegal", 1867, "Mexico", 1867, "Norway", 1917);

            // M90 alt: England wins Mexico (earlier match ID, wrong bracket slot)
            String m90Alt = scoredRow("M90", route("W83", "England", "M83", "K2", "K2"), route("W79", "Mexico", "M79", "A1", "A1"),
                    "alt", "England (70%)", -77, -16, "Uzbekistan:0", "Canada:0");
            // M92 predicted: England beats Senegal (correct R16)
            String m92Pred = scoredRow("M92", route("W80", "England", "M80", "L1", "L1"), route("W79", "Senegal", "M79", "CEFHI3", "CEFHI3"),
                    "predicted", "England (61%)", -77, 7, "Uzbekistan:0", "Mexico:-1");
            // M91 for the other quarter slot
            String m91Pred = scoredRow("M91", route("W77", "Norway", "M77", "I1", "I1"), route("W78", "France", "M78", "I2", "I2"),
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
            String m91Pred = scoredRow("M91", route("W77", "Norway", "M77", "I1", "I1"), route("W78", "France", "M78", "I2", "I2"),
                    "predicted", "Norway (56%)", 0, 0, "", "");
            String m92Pred = scoredRow("M92", route("W80", "England", "M80", "L1", "L1"), route("W79", "Senegal", "M79", "CEFHI3", "CEFHI3"),
                    "predicted", "England (61%)", -77, 7, "Uzbekistan:0", "Mexico:-1");

            List<String> output = builder.buildLast8Lines(elos, quarterBracket(), Arrays.asList(HEADER, m91Pred, m92Pred));

            String m99 = output.stream()
                    .filter(l -> l.startsWith("M99,") && l.contains(",predicted,"))
                    .findFirst().orElseThrow();
            String[] cols = m99.split(",", -1);
            String t2Chain = cols[8]; // England is team2

            // Uzbekistan (R32 from prior chain) then Senegal (R16 just added)
            assertTrue(t2Chain.startsWith("Uzbekistan:0 > K@M92|Senegal:"),
                    "Chain should start with R32 opponent then R16 opponent: " + t2Chain);
        }
    }


    @Nested
    class RouteValidation {

        @Test
        void skipsQuarterRowsWhenOpponentPathRepeatsATeam() {
            Map<String, Integer> elos = Map.of(
                    "Canada", 1820, "Australia", 1810, "Czechia", 1800, "Panama", 1760);
            String m91 = scoredRow("M91", route("W77", "Canada", "M77", "", "W77"), route("W78", "Czechia", "M78", "", "W78"),
                    "alt", "Canada (54%)", 0, 0,
                    "D4|Bosnia and Herzegovina:0 > K@M82|Czechia:-4 > K@M86|Bosnia and Herzegovina:-8", "");
            String m92 = scoredRow("M92", route("W79", "Australia", "M79", "", "W79"), route("W80", "Panama", "M80", "", "W80"),
                    "alt", "Australia (55%)", 0, 0, "K@M83|Egypt:-1", "");

            List<String> output = builder.buildLast8Lines(elos, quarterBracket(), Arrays.asList(HEADER, m91, m92));

            assertTrue(output.stream().noneMatch(line -> line.startsWith("M99,Canada,Australia,")),
                    "Canada/Australia quarter should not be generated when Canada's route repeats Bosnia: " + output);
        }

        @Test
        void skipsQuarterRowsWhenTeamsWouldReplayEachOther() {
            Map<String, Integer> elos = Map.of(
                    "Canada", 1820, "Australia", 1810, "Czechia", 1800, "Panama", 1760);
            String m91 = scoredRow("M91", route("W77", "Canada", "M77", "", "W77"), route("W78", "Czechia", "M78", "", "W78"),
                    "alt", "Canada (54%)", 0, 0, "K@M80|Australia:0 > K@M86|Panama:-4", "");
            String m92 = scoredRow("M92", route("W79", "Australia", "M79", "", "W79"), route("W80", "Panama", "M80", "", "W80"),
                    "alt", "Australia (55%)", 0, 0, "K@M83|Egypt:-1", "");

            List<String> output = builder.buildLast8Lines(elos, quarterBracket(), Arrays.asList(HEADER, m91, m92));

            assertTrue(output.stream().noneMatch(line -> line.startsWith("M99,Canada,Australia,")),
                    "Canada/Australia quarter should not be generated after Canada already faced Australia: " + output);
        }
    }

    // ─── Output structure ─────────────────────────────────────────────────────

    @Nested
    class OutputStructure {

        @Test
        void forwardsPredictedLosersAsAlternativeQuarterFinalists() {
            Map<String, Integer> elos = Map.of(
                    "England", 2020, "DR Congo", 1655, "Mexico", 1867, "Norway", 1917);

            String m91 = scoredRow("M91", route("W77", "Norway", "M77", "", "W77"), route("W78", "Mexico", "M78", "", "W78"),
                    "predicted", "Norway (55%)", 0, 0, "", "");
            String m92 = scoredRow("M92", route("W80", "England", "M80", "", "W80"), route("W79", "DR Congo", "M79", "", "W79"),
                    "predicted", "England (83%)", 0, 0, "", "");

            List<String> output = builder.buildLast8Lines(elos, quarterBracket(), Arrays.asList(HEADER, m91, m92));

            assertTrue(output.stream().anyMatch(line ->
                    line.startsWith("M99,") && line.contains("DR Congo") && line.contains(",alt,")));
            assertTrue(output.stream().anyMatch(line ->
                    line.startsWith("M99,") && line.contains("Mexico") && line.contains(",alt,")));
        }

        @Test
        void alternativeExpectedWinnerIsNotMislabelledAsUpset() {
            Map<String, Integer> elos = Map.of(
                    "England", 2020, "Mexico", 1867, "DR Congo", 1655,
                    "Norway", 1917, "France", 2081);

            String m91 = scoredRow("M91", route("W77", "Norway", "M77", "", "W77"), route("W78", "France", "M78", "", "W78"),
                    "predicted", "Norway (55%)", 0, 0, "", "");
            // Mexico is scanned first as a loser, but in its valid alternative matchup it is favored.
            String m92MexicoLoses = scoredRow("M92", route("W80", "England", "M80", "", "W80"), route("W79", "Mexico", "M79", "", "W79"),
                    "predicted", "England (70%)", 0, 0, "", "");
            String m92MexicoWins = scoredRow("M92", route("W80", "DR Congo", "M80", "", "W80"), route("W79", "Mexico", "M79", "", "W79"),
                    "alt", "Mexico (83%)", 0, 0, "", "");

            List<String> output = builder.buildLast8Lines(elos, quarterBracket(),
                    Arrays.asList(HEADER, m91, m92MexicoLoses, m92MexicoWins));

            String mexicoExpectedWinnerRoute = output.stream()
                    .filter(line -> line.startsWith("M99,") && line.contains("Mexico") && line.contains(",alt,")
                            && line.contains("K@M92|DR Congo:"))
                    .findFirst().orElseThrow();
            assertFalse(mexicoExpectedWinnerRoute.contains("U@M92|DR Congo"), mexicoExpectedWinnerRoute);
            assertTrue(output.stream().anyMatch(line -> line.startsWith("M99,") && line.contains("Mexico")
                    && line.contains(",alt,") && line.contains("U@M92|England:")),
                    "Non-predicted winner branch should still be present as an alternative route: " + output);
        }

        @Test
        void outputIncludesHeaderRow() {
            Map<String, Integer> elos = Map.of("England", 2020, "Senegal", 1867, "Norway", 1917);
            String m91 = scoredRow("M91", route("W77", "Norway", "M77", "I1", "I1"), route("W78", "France", "M78", "I2", "I2"), "predicted", "Norway (56%)", 0, 0, "", "");
            String m92 = scoredRow("M92", route("W80", "England", "M80", "L1", "L1"), route("W79", "Senegal", "M79", "CEFHI3", "CEFHI3"), "predicted", "England (61%)", -77, 7, "Uzbekistan:0", "");

            List<String> output = builder.buildLast8Lines(elos, quarterBracket(), Arrays.asList(HEADER, m91, m92));
            assertTrue(output.get(0).startsWith("match_id"), "First line should be header");
        }
    }
}

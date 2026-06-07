package com.tournamentpredictor.web;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebControllerFormatTest {

    @Test
    void positiveBonus_prefixedWithPlus() {
        assertEquals("+18", WebController.formatQualBonus("18"));
    }

    @Test
    void negativeBonus_showsMinusSign() {
        assertEquals("-5", WebController.formatQualBonus("-5"));
    }

    @Test
    void zeroBonus_showsHost() {
        assertEquals("Host", WebController.formatQualBonus("0"));
    }

    @Test
    void nullInput_showsDash() {
        assertEquals("—", WebController.formatQualBonus(null));
    }

    @Test
    void blankInput_showsDash() {
        assertEquals("—", WebController.formatQualBonus("   "));
    }

    @Test
    void emptyInput_showsDash() {
        assertEquals("—", WebController.formatQualBonus(""));
    }

    @Test
    void nonNumericInput_returnedAsIs() {
        assertEquals("N/A", WebController.formatQualBonus("N/A"));
    }

    @Test
    void maxBonus_prefixedWithPlus() {
        assertEquals("+50", WebController.formatQualBonus("50"));
    }

    @Test
    void maxNegativeBonus_showsMinusSign() {
        assertEquals("-50", WebController.formatQualBonus("-50"));
    }

    
    @Test
    void routeAverageLast16RowsAveragesPredictedAndAlternativeMatchups() {
        List<Map<String, String>> rows = WebController.routeAverageLast16Rows(List.of(
                Map.of("team1", "A1(France)", "team2", "B2(Egypt)", "prediction", "France (90%)"),
                Map.of("team1", "A1(France)", "team2", "C2(Brazil)", "prediction", "Brazil (60%)"),
                Map.of("team1", "D1(Canada)", "team2", "A2(France)", "prediction", "France (70%)"),
                Map.of("team1", "Broken", "team2", "", "prediction", "")
        ));

        assertEquals("66.7", routePct(rows, "France"));
        assertEquals("10.0", routePct(rows, "Egypt"));
        assertEquals("60.0", routePct(rows, "Brazil"));
        assertEquals("30.0", routePct(rows, "Canada"));
        assertEquals("3", rows.get(0).get("route_matchups"));
    }


    
    @Test
    void routeAverageLast16RowsWeightsRoutesByGroupSlotLikelihood() {
        List<Map<String, String>> matchups = List.of(
                Map.of("team1", "A1(France)", "team2", "B2(Egypt)", "prediction", "France (90%)"),
                Map.of("team1", "A2(France)", "team2", "C2(Brazil)", "prediction", "Brazil (60%)")
        );
        List<Map<String, String>> groups = List.of(
                Map.of("team", "France", "predicted_position", "1 (80%)", "group_winner", "yes", "runner_up", "maybe", "3rd_place", "no"),
                Map.of("team", "Egypt", "predicted_position", "2 (70%)", "group_winner", "no", "runner_up", "yes", "3rd_place", "maybe"),
                Map.of("team", "Brazil", "predicted_position", "2 (70%)", "group_winner", "maybe", "runner_up", "yes", "3rd_place", "no")
        );

        List<Map<String, String>> rows = WebController.routeAverageLast16Rows(matchups, groups);

        assertEquals("74.8", routePct(rows, "France"));
        assertEquals("true", rows.get(0).get("route_weighted"));
        assertEquals("2", rows.get(0).get("route_matchups"));
    }

    
    @Test
    void matchupLikelihoodMapShowsLikelihoodForEachExactRoute() {
        List<Map<String, String>> matchups = List.of(
                Map.of("match_id", "M77", "team1", "A1(France)", "team2", "B2(Egypt)"),
                Map.of("match_id", "M78", "team1", "A2(France)", "team2", "C2(Brazil)")
        );
        List<Map<String, String>> groups = List.of(
                Map.of("team", "France", "predicted_position", "1 (80%)", "group_winner", "yes", "runner_up", "maybe", "3rd_place", "no"),
                Map.of("team", "Egypt", "predicted_position", "2 (70%)", "group_winner", "no", "runner_up", "yes", "3rd_place", "maybe"),
                Map.of("team", "Brazil", "predicted_position", "2 (70%)", "group_winner", "maybe", "runner_up", "yes", "3rd_place", "no")
        );

        Map<String, String> likelihoods = WebController.matchupLikelihoodMap(matchups, groups);

        assertEquals("60.0", likelihoods.get(WebController.matchupLikelihoodKey("M77", "France", "Egypt")));
        assertEquals("26.3", likelihoods.get(WebController.matchupLikelihoodKey("M78", "France", "Brazil")));
    }

    @Test
    void recursiveRouteLikelihoodPropagatesAlternativeWinners() {
        List<Map<String, String>> feederRows = List.of(
                Map.of("match_id", "M89", "team1", "W77(France)", "team2", "W74(Germany)", "prediction", "France (75%)"),
                Map.of("match_id", "M89", "team1", "W77(Egypt)", "team2", "W74(Germany)", "prediction", "Germany (80%)"),
                Map.of("match_id", "M90", "team1", "W75(Netherlands)", "team2", "W73(Canada)", "prediction", "Netherlands (60%)")
        );
        Map<String, String> feederLikelihoods = Map.of(
                "M89|France|Germany", "60.0",
                "M89|Egypt|Germany", "40.0",
                "M90|Netherlands|Canada", "100.0"
        );
        List<Map<String, String>> nextRows = List.of(
                Map.of("match_id", "M97", "team1", "W89(France)", "team2", "W90(Netherlands)"),
                Map.of("match_id", "M97", "team1", "W89(Egypt)", "team2", "W90(Canada)")
        );

        Map<String, String> likelihoods = WebController.routeWeightedNextRoundMatchupLikelihoodMap(
                nextRows, feederRows, feederLikelihoods);

        assertEquals("27.0", likelihoods.get("M97|France|Netherlands"));
        assertEquals("3.2", likelihoods.get("M97|Egypt|Canada"));
    }

    @Test
    void simulationAdvanceMapUsesRequestedLaterRoundColumn() {
        Map<String, String> percentages = WebController.simulationAdvanceMap(List.of(
                Map.of("team", "France", "reach_last_8", "72.4"),
                Map.of("team", "Brazil", "reach_last_8", "61.1")
        ), "reach_last_8");

        assertEquals("72.4", percentages.get("France"));
        assertEquals("61.1", percentages.get("Brazil"));
    }

    @Test
    void simulationMatchupLikelihoodMapUsesCurrentStageRows() {
        Map<String, String> percentages = WebController.simulationMatchupLikelihoodMap(List.of(
                Map.of("stage", "last_16", "match_id", "M89", "team1", "France", "team2", "Brazil", "matchup_pct", "100.0", "scoreline", "1-0"),
                Map.of("stage", "last_16", "match_id", "M89", "team1", "France", "team2", "Brazil", "matchup_pct", "100.0", "scoreline", "2-1"),
                Map.of("stage", "last_8", "match_id", "M97", "team1", "France", "team2", "Spain", "matchup_pct", "48.2", "scoreline", "1-1")
        ), "last_16");

        assertEquals(Map.of("M89|France|Brazil", "100.0"), percentages);
    }

    
    void historicalOutcomeSelectsHighestProbabilityIncludingDraw() {
        assertEquals("Home", WebController.outcome(0.50, 0.30, 0.20));
        assertEquals("Draw", WebController.outcome(0.30, 0.40, 0.30));
        assertEquals("Away", WebController.outcome(0.20, 0.30, 0.50));
    }

    private static String routePct(List<Map<String, String>> rows, String team) {
        return rows.stream()
                .filter(row -> team.equals(row.get("team")))
                .findFirst()
                .orElseThrow()
                .get("reach_last_16");
    }
}

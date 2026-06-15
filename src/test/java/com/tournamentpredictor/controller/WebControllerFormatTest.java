package com.tournamentpredictor.controller;

import com.tournamentpredictor.config.PredictionConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.LinkedHashMap;
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
                matchupRow("France", "A1", "Egypt", "B2", "France (90%)"),
                matchupRow("France", "A1", "Brazil", "C2", "Brazil (60%)"),
                matchupRow("Canada", "D1", "France", "A2", "France (70%)"),
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
                matchupRow("France", "A1", "Egypt", "B2", "France (90%)"),
                matchupRow("France", "A2", "Brazil", "C2", "Brazil (60%)")
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
                row("M77", "France", "A1", "Egypt", "B2"),
                row("M78", "France", "A2", "Brazil", "C2")
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
                row("M89", "France", "W77", "M77", "Germany", "W74", "M74", "France (75%)"),
                row("M89", "Egypt", "W77", "M77", "Germany", "W74", "M74", "Germany (80%)"),
                row("M90", "Netherlands", "W75", "M75", "Canada", "W73", "M73", "Netherlands (60%)")
        );
        Map<String, String> feederLikelihoods = Map.of(
                "M89|France|Germany", "60.0",
                "M89|Egypt|Germany", "40.0",
                "M90|Netherlands|Canada", "100.0"
        );
        List<Map<String, String>> nextRows = List.of(
                row("M97", "France", "W89", "M89", "Netherlands", "W90", "M90", ""),
                row("M97", "Egypt", "W89", "M89", "Canada", "W90", "M90", "")
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


    @Test
    void visualizePageRequiresTeamSelectionBeforeRenderingGraph() throws Exception {
        ViewController controller = new ViewController(new PredictionConfig());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.visualizePaths("world_cup_2026", true, null, "all", "", model);

        assertEquals("path-visualization", view);
        assertEquals(true, model.getAttribute("teamRequired"));
        assertEquals("{\"nodes\":[],\"edges\":[]}", model.getAttribute("graphJson"));
        @SuppressWarnings("unchecked")
        List<String> teams = (List<String>) model.getAttribute("teamOptions");
        assertNotNull(teams);
    }


    @Test
    void roundSpecificVisualizeUrlRedirectsToTournamentVisualize() {
        ViewController controller = new ViewController(new PredictionConfig());

        String view = controller.redirectRoundVisualization("last_16_match", "world_cup_2026", false, null, "all", "Germany");

        assertEquals("redirect:/view/visualize?tournament=world_cup_2026&path=all&results=false&team=Germany", view);
    }

    @Test
    void oldUpsetPathAliasesToAlternativeMatchups() throws Exception {
        WebController controller = new WebController(new PredictionConfig());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.viewRound("last_8_match", "world_cup_2022", null, null, "upset", "", 1, model);

        assertEquals("result", view);
        String html = (String) model.getAttribute("output");
        assertNotNull(html);
        assertTrue(html.contains("data-path=\"upset\"") || html.contains("data-path=\"alt\""));
        assertTrue(html.contains("Alternative Matchup"));
        assertFalse(html.contains("Result / Upset"));
        assertFalse(html.contains(">Upset</span>"));
    }



    @Test
    void alternativeMatchupPageEnrichesSimulatedRowsWithGroupSlots() throws Exception {
        WebController controller = new WebController(new PredictionConfig());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.viewRound("last_16_match", "world_cup_2022", null, false, "alt", "", 1, model);

        assertEquals("result", view);
        String html = (String) model.getAttribute("output");
        assertNotNull(html);
        assertTrue(html.contains("United States"));
        assertTrue(html.contains("Ecuador"));
        assertTrue(html.contains(">B1</span>"),
                "Simulated alternative rows should use the bracket slot for United States instead of an empty Tournament Path");
        assertTrue(html.contains(">A2</span>"),
                "Simulated alternative rows should use the bracket slot for Ecuador instead of an empty Tournament Path");
    }

    @Test
    void visualizationEnrichesAlternativeRoutesWithGroupSlotFallback() throws Exception {
        ViewController controller = new ViewController(new PredictionConfig());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.visualizePaths("world_cup_2022", false, null, "alt", "United States", model);

        assertEquals("path-visualization", view);
        String graphJson = (String) model.getAttribute("graphJson");
        assertNotNull(graphJson);
        assertTrue(graphJson.contains("seed:United States:B2"));
        assertTrue(graphJson.contains("team:United States->seed:United States:B2"));
        assertTrue(graphJson.contains("\"team\":\"Ecuador\""));
        assertTrue(graphJson.contains("stage-instance:united_states:b2:root:last_16"));
    }


    @Test
    void visualizationUsesGroupRouteLikelihoodsToSizeLast32AlternativeNodes() throws Exception {
        ViewController controller = new ViewController(new PredictionConfig());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.visualizePaths("world_cup_2026", true, null, "all", "England", model);

        assertEquals("path-visualization", view);
        String graphJson = (String) model.getAttribute("graphJson");
        assertNotNull(graphJson);
        assertTrue(graphJson.contains("\"team\":\"Norway\"")
                        && graphJson.contains("\"matchupPct\":\"61.5\"")
                        && graphJson.contains("\"likelihood\":\"large\""),
                "Norway should use explicit group-finish metadata for its route likelihood instead of the default tiny fallback");
        assertTrue(graphJson.contains("\"team\":\"Norway\"")
                        && graphJson.contains("\"opponentSeed\":\"EHIJK3\""),
                "Norway should keep the Last 32 composite third-place fixture slot in the tooltip metadata");
    }


    @Test
    void visualizationIncludesBracketSlotBranchForDirectLast16Tournament() throws Exception {
        ViewController controller = new ViewController(new PredictionConfig());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.visualizePaths("world_cup_2022", true, null, "all", "England", model);

        assertEquals("path-visualization", view);
        String graphJson = (String) model.getAttribute("graphJson");
        assertNotNull(graphJson);
        assertTrue(graphJson.contains("seed:England:B1"));
        assertTrue(graphJson.contains("seed:England:B2"));
        assertTrue(graphJson.contains("team:England->seed:England:B2"));
        assertTrue(graphJson.contains("\"team\":\"Netherlands\""));
        assertTrue(graphJson.contains("stage-instance:england:b1:root:last_16"));
        assertTrue(graphJson.contains("stage-instance:england:b2:root:last_16"));
        assertTrue(graphJson.contains("\"source\":\"stage-instance:england:b1:root:last_16\"")
                        && graphJson.contains("\"team\":\"Senegal\"")
                        && graphJson.contains("\"path\":\"results\""),
                "The actual M51 England result should be routed through England's B1 branch");
        assertFalse(graphJson.contains("stage-instance:england:b2:root:last_16->route-instance:england_b2_b2_m51_senegal_results"),
                "The actual M51 result must not be copied onto England's B2 alternative branch");
    }

    @Test
    void allPathInActualModeIncludesPredictedAndActualRows() throws Exception {
        WebController controller = new WebController(new PredictionConfig());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.viewRound("last_8_match", "world_cup_2022", true, "all", "", 1, model);

        assertEquals("result", view);
        String html = (String) model.getAttribute("output");
        assertNotNull(html);
        assertTrue(html.contains("data-path=\"prediction\""));
        assertTrue(html.contains("data-path=\"results\"") || html.contains("data-path=\"result_upset\""));
        assertTrue(html.contains("data-server-paging=\"true\" data-actual-mode=\"true\""));
    }


    @Test
    void actualModeRoundViewShowsPlayedScoreline() throws Exception {
        WebController controller = new WebController(new PredictionConfig());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.viewRound("last_8_match", "world_cup_2022", null, null, "results", "", 1, model);

        assertEquals("result", view);
        String html = (String) model.getAttribute("output");
        assertNotNull(html);
        assertTrue(html.contains("data-path=\"results\"") || html.contains("data-path=\"fixture\"") || html.contains("data-path=\"result_upset\""));
        assertTrue(html.contains("Brazil"));
        assertTrue(html.contains("Results"));
    }

    @Test
    void actualModeRoundViewInfersKnockoutAdvancersFromLaterRounds() throws Exception {
        WebController controller = new WebController(new PredictionConfig());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.viewRound("last_8_match", "world_cup_2022", null, null, "results", "", 1, model);

        assertEquals("result", view);
        String html = (String) model.getAttribute("output");
        assertNotNull(html);
        assertTrue(html.contains("Croatia"));
        assertTrue(html.contains("Argentina"));
    }

    @Test
    void roundWithFixturesDoesNotShowLivePredictions() throws Exception {
        WebController controller = new WebController(new PredictionConfig());
        ExtendedModelMap resultsModel = new ExtendedModelMap();
        ExtendedModelMap predictionModel = new ExtendedModelMap();
        ExtendedModelMap allModel = new ExtendedModelMap();

        String resultsView = controller.viewRound("last_16_match", "world_cup_2014", null, null, "results", "Greece", 1, resultsModel);
        String predictionView = controller.viewRound("last_16_match", "world_cup_2014", null, true, "prediction", "Greece", 1, predictionModel);
        String allView = controller.viewRound("last_16_match", "world_cup_2014", null, true, "all", "Greece", 1, allModel);

        assertEquals("result", resultsView);
        assertEquals("result", predictionView);
        assertEquals("result", allView);
        String resultsHtml = (String) resultsModel.getAttribute("output");
        String predictionHtml = (String) predictionModel.getAttribute("output");
        String allHtml = (String) allModel.getAttribute("output");
        assertNotNull(resultsHtml);
        assertNotNull(predictionHtml);
        assertNotNull(allHtml);
        assertFalse(resultsHtml.contains("data-path=\"live\""));
        assertFalse(predictionHtml.contains("data-path=\"live\""));
        assertFalse(allHtml.contains("data-path=\"live\""));
    }



    private static Map<String, String> matchupRow(String team1, String team1Slot, String team2, String team2Slot, String prediction) {
        return row("", team1, team1Slot, "", team2, team2Slot, "", prediction);
    }

    private static Map<String, String> row(String matchId, String team1, String team1Slot, String team2, String team2Slot) {
        return row(matchId, team1, team1Slot, "", team2, team2Slot, "", "");
    }

    private static Map<String, String> row(String matchId, String team1, String team1Slot, String team1Source,
                                           String team2, String team2Slot, String team2Source, String prediction) {
        Map<String, String> row = new LinkedHashMap<>();
        if (!matchId.isBlank()) row.put("match_id", matchId);
        row.put("team1", team1);
        row.put("team2", team2);
        row.put("team1_team", team1);
        row.put("team2_team", team2);
        row.put("team1_slot", team1Slot);
        row.put("team2_slot", team2Slot);
        row.put("team1_bracket_slot", team1Slot);
        row.put("team2_bracket_slot", team2Slot);
        if (!team1Source.isBlank()) row.put("team1_source_match", team1Source);
        if (!team2Source.isBlank()) row.put("team2_source_match", team2Source);
        if (!prediction.isBlank()) row.put("prediction", prediction);
        return row;
    }

    private static String routePct(List<Map<String, String>> rows, String team) {
        return rows.stream()
                .filter(row -> team.equals(row.get("team")))
                .findFirst()
                .orElseThrow()
                .get("reach_last_16");
    }
}

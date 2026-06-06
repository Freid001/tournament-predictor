package com.tournamentpredictor.web.view;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationResultsRendererTest {
    @Test
    void renderShowsSummaryMetadataFinishColumnsAndEscapesTeamNames() {
        String html = SimulationResultsRenderer.render(List.of(
                Map.ofEntries(
                        entry("team", "A&B <Test>"),
                        entry("reach_last_16", "70.0"),
                        entry("reach_last_8", "40.5"),
                        entry("reach_last_4", "22.0"),
                        entry("reach_final", "10.0"),
                        entry("champion", "4.5"),
                        entry("predicted_finish", "Exit Last 16"),
                        entry("predicted_finish_pct", "42.0"),
                        entry("best_realistic_finish", "Champion"),
                        entry("best_realistic_pct", "4.5"),
                        entry("simulation_runs", "25000"),
                        entry("simulation_seed", "20260605")
                ),
                Map.ofEntries(
                        entry("team", "Winner"),
                        entry("reach_last_16", "90.0"),
                        entry("reach_last_8", "80.0"),
                        entry("reach_last_4", "70.0"),
                        entry("reach_final", "60.0"),
                        entry("champion", "50.0"),
                        entry("predicted_finish", "Champion"),
                        entry("predicted_finish_pct", "50.0"),
                        entry("best_realistic_finish", "Champion"),
                        entry("best_realistic_pct", "50.0"),
                        entry("simulation_runs", "25000"),
                        entry("simulation_seed", "20260605")
                )
        ), List.of(
                Map.ofEntries(
                        entry("team", "Winner"),
                        entry("finish", "Champion"),
                        entry("path", "Alpha > Beta > Gamma > Delta > Final Team"),
                        entry("count", "1250"),
                        entry("percentage", "5.0"),
                        entry("simulation_runs", "25000"),
                        entry("simulation_seed", "20260605")
                )
        ), List.of(
                Map.of(
                        "stage", "last_32",
                        "match_id", "M73",
                        "team1", "Winner",
                        "team2", "Opponent",
                        "scoreline", "2-1",
                        "winner", "Winner",
                        "count", "320",
                        "scoreline_pct", "32.0",
                        "matchup_runs", "1000",
                        "matchup_pct", "4.0")
        ), "", "world_cup_2026");

        assertTrue(html.contains("Monte Carlo Simulation"));
        assertTrue(html.contains("Top champion chances"));
        assertTrue(html.contains("Common simulated paths"));
        assertTrue(html.contains("Common simulated scorelines"));
        assertTrue(html.contains("2-1"));
        assertTrue(html.contains("32.0%"));
        assertTrue(html.contains("4.0%"));
        assertTrue(html.contains("Alpha &gt; Beta &gt; Gamma &gt; Delta &gt; Final Team"));
        assertTrue(html.contains("/view/simulation?tournament=world_cup_2026&team=Winner"));
        assertTrue(html.contains("Predicted Finish"));
        assertTrue(html.contains("Best Realistic Finish"));
        assertTrue(html.contains("25000 runs"));
        assertTrue(html.contains("20260605 seed"));
        assertTrue(html.contains("Exit Last 16"));
        assertTrue(html.contains("Champion"));
        assertTrue(html.contains("A&amp;B &lt;Test&gt;"));
        assertTrue(html.contains("50.0%"));
        assertFalse(html.contains("A&B <Test>"));
    }

    @Test
    void renderSnapshotShowsCompactRoundContext() {
        String html = SimulationResultsRenderer.renderSnapshot(List.of(
                Map.ofEntries(
                        entry("team", "Spain"),
                        entry("reach_last_16", "88.4"),
                        entry("reach_last_4", "44.3"),
                        entry("reach_final", "30.2"),
                        entry("champion", "20.0"),
                        entry("best_realistic_finish", "Champion"),
                        entry("best_realistic_pct", "20.0"),
                        entry("simulation_runs", "25000"),
                        entry("market_odds", "6/5")
                ),
                Map.ofEntries(
                        entry("team", "Scotland"),
                        entry("reach_last_16", "12.5"),
                        entry("reach_last_4", "1.6"),
                        entry("reach_final", "0.4"),
                        entry("champion", "0.1"),
                        entry("best_realistic_finish", "Exit SF"),
                        entry("best_realistic_pct", "1.1")
                )
        ), "world_cup_2026");

        assertTrue(html.contains("Most likely teams to proceed to Last 16"));
        assertTrue(html.contains("Across 25,000 simulations from the last 32 stage."));
        assertFalse(html.contains("Monte Carlo proceed chance"));
        assertTrue(html.contains("Spain"));
        assertTrue(html.contains("data-team=\"Spain\""));
        assertTrue(html.contains("filterTeamValue(this.dataset.team)"));
        assertTrue(html.contains("fi fi-es"));
        assertFalse(html.contains("team=Spain"));
        assertTrue(html.contains("88.4%"));
        assertTrue(html.contains("last 32 win chance"));
        assertTrue(html.contains("Market to reach Last 16: <span class=\"fw-semibold\">6/5"));
        assertTrue(html.contains("&pound;10 bet: &pound;12.00 net"));
        assertTrue(html.contains("Strong Candidate"));
        assertTrue(html.contains("Full simulation"));
        assertFalse(html.contains("Most likely champion"));
    }

    @Test
    void renderFiltersCommonPathsByTeam() {
        String html = SimulationResultsRenderer.render(List.of(Map.ofEntries(
                entry("team", "Scotland"),
                entry("reach_last_16", "20.0"),
                entry("reach_last_8", "8.0"),
                entry("reach_last_4", "2.0"),
                entry("reach_final", "1.0"),
                entry("champion", "0.2"),
                entry("predicted_finish", "Exit Last 32"),
                entry("predicted_finish_pct", "80.0"),
                entry("best_realistic_finish", "Exit SF"),
                entry("best_realistic_pct", "2.0"),
                entry("simulation_runs", "25000"),
                entry("simulation_seed", "20260605")
        )), List.of(
                Map.of("team", "Scotland", "finish", "Exit QF", "path", "Germany > Portugal", "count", "200", "percentage", "0.8"),
                Map.of("team", "Spain", "finish", "Champion", "path", "USA > France", "count", "1000", "percentage", "4.0")
        ), List.of(
                Map.of(
                        "stage", "last_16",
                        "match_id", "M89",
                        "team1", "Scotland",
                        "team2", "Germany",
                        "scoreline", "1-1",
                        "winner", "Scotland",
                        "count", "90",
                        "scoreline_pct", "18.0",
                        "matchup_runs", "500",
                        "matchup_pct", "2.0"),
                Map.of(
                        "stage", "last_16",
                        "match_id", "M90",
                        "team1", "Spain",
                        "team2", "France",
                        "scoreline", "2-0",
                        "winner", "Spain",
                        "count", "120",
                        "scoreline_pct", "24.0",
                        "matchup_runs", "500",
                        "matchup_pct", "2.0")
        ), "Scotland", "world_cup_2026");

        assertTrue(html.contains("Filtered to Scotland"));
        assertTrue(html.contains("Germany &gt; Portugal"));
        assertTrue(html.contains("Clear"));
        assertTrue(html.contains("1-1"));
        assertFalse(html.contains("USA &gt; France"));
        assertFalse(html.contains("2-0"));
    }
}

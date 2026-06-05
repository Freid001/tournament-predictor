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
        ));

        assertTrue(html.contains("Monte Carlo Simulation"));
        assertTrue(html.contains("Top champion chances"));
        assertTrue(html.contains("Common simulated paths"));
        assertTrue(html.contains("Alpha &gt; Beta &gt; Gamma &gt; Delta &gt; Final Team"));
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
}

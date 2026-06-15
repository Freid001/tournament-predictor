package com.tournamentpredictor.services.web;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathVisualizationServiceTest {

    @Test
    void graphJsonSerializesTooltipMetadataForVisualizationNodes() {
        PathVisualizationService service = new PathVisualizationService();
        PathVisualizationService.Graph graph = new PathVisualizationService.Graph(List.of(
                new PathVisualizationService.Node(
                        "route:Last 32:Colombia",
                        "M80",
                        "team",
                        "alt",
                        "Colombia",
                        "/vendor/flag-icons/flags/4x3/co.svg",
                        "small",
                        "28.7",
                        "G|Brazil:-3 > U@M83|Austria:-8",
                        "L2",
                        "Last 32")),
                List.of(),
                Set.of("England"));

        String json = service.toJson(graph);

        assertTrue(json.contains("\"opponentPath\":\"G|Brazil:-3 > U@M83|Austria:-8\""));
        assertTrue(json.contains("\"opponentSeed\":\"L2\""));
        assertTrue(json.contains("\"roundLabel\":\"Last 32\""));
        assertTrue(json.contains("\"matchupPct\":\"28.7\""));
        assertTrue(json.contains("\"likelihood\":\"small\""));
    }

    @Test
    void focusedGraphBuildsPredictedRouteFromStructuredMetadata() {
        PathVisualizationService service = new PathVisualizationService();
        List<PathVisualizationService.RoundRows> rounds = List.of(
                round("last_32_match", "Last 32", List.of(
                        row("M80", side("England", "L1", ""), side("Senegal", "A2", ""), "predicted", "England (62%)"))),
                round("last_16_match", "Last 16", List.of(
                        row("M92", side("England", "L1", "M80"), side("Mexico", "A1", "M79"), "predicted", "England (54%)"))),
                round("last_8_match", "Quarter Finals", List.of(
                        row("M99", side("Ecuador", "I1", "M91"), side("England", "L1", "M92"), "predicted", "England (56%)"))),
                round("last_4_match", "Semi Finals", List.of(
                        row("M102", side("England", "L1", "M99"), side("Argentina", "J1", "M100"), "predicted", "Argentina (71%)"))),
                round("final_match", "Final", List.of(
                        row("M103", side("Argentina", "J1", "M102"), side("Spain", "I1", "M101"), "predicted", "Argentina (58%)"))));

        PathVisualizationService.Graph graph = service.build(rounds, "last_16_match", "prediction", "England");
        String json = service.toJson(graph);

        assertTrue(json.contains("team:England"));
        assertTrue(json.contains("/vendor/flag-icons/flags/4x3/gb-eng.svg"));
        assertTrue(json.contains("Senegal"));
        assertTrue(json.contains("Mexico"));
        assertTrue(json.contains("Ecuador"));
        assertTrue(json.contains("Argentina"));
        assertTrue(json.contains("Eliminated"));
        assertTrue(graph.edges().stream().anyMatch(edge -> edge.source().equals("team:England")
                && edge.target().equals("seed:England:L1")));
        assertTrue(graph.nodes().stream().anyMatch(node -> node.id().equals("stage:L1:Last 16")));
        assertTrue(graph.nodes().stream().anyMatch(node -> node.id().equals("stage:L1:Quarter Finals")));
        assertFalse(json.contains("Spain"), "The focused route should stop when England is eliminated");
    }

    @Test
    void alternativeRouteUsesSelectedTeamPathOnlyAndKeepsSeed() {
        PathVisualizationService service = new PathVisualizationService();
        List<PathVisualizationService.RoundRows> rounds = List.of(
                round("last_8_match", "Quarter Finals", List.of(
                        row("M98", side("England", "L2", "M93"), side("Turkey", "D1", "M94"), "alt", "England (56%)",
                                "G|Croatia:0 > K@M83|Colombia:-8 > K@M93|Austria:-3 > K@M98|Turkey:-14",
                                "G|Paraguay:0 > K@M81|Algeria:0 > K@M94|Belgium:-5"))));

        PathVisualizationService.Graph graph = service.build(rounds, "last_8_match", "alt", "England");
        String json = service.toJson(graph);

        assertTrue(json.contains("Colombia"));
        assertTrue(json.contains("Austria"));
        assertTrue(json.contains("Turkey"));
        assertTrue(json.contains("L2"));
        assertFalse(json.contains("Paraguay"));
        assertFalse(json.contains("Belgium"));
        assertTrue(graph.edges().stream().anyMatch(edge -> edge.source().equals("team:England")
                && edge.target().equals("seed:England:L2")));
        assertTrue(graph.edges().stream().anyMatch(edge -> edge.source().equals("stage:L2:Quarter Finals")
                && edge.target().startsWith("route:Quarter Finals:Turkey:from:")));
    }

    @Test
    void projectedAlternativeContinuationUsesSourceMatchAndBranchSeed() {
        PathVisualizationService service = new PathVisualizationService();
        List<PathVisualizationService.RoundRows> rounds = List.of(
                round("last_32_match", "Last 32", List.of(
                        row("M80", side("England", "L1", ""), side("Norway", "EHIJK3", ""), "alt", "England (63%)"))),
                round("last_16_match", "Last 16", List.of(
                        row("M91", side("England", "L1", "M78"), side("France", "A1", "M77"), "predicted", "England (55%)"),
                        row("M92", side("England", "L1", "M80"), side("Mexico", "A1", "M79"), "predicted", "England (54%)"))),
                round("last_8_match", "Quarter Finals", List.of(
                        row("M99", side("Brazil", "B1", "M91"), side("England", "L1", "M92"), "predicted", "Brazil (56%)"))));

        PathVisualizationService.Graph graph = service.build(rounds, "", "all", "England");

        assertTrue(graph.edges().stream().anyMatch(edge -> edge.source().contains("Norway")
                && edge.target().equals("stage:L1:Last 16")));
        assertTrue(graph.edges().stream().anyMatch(edge -> edge.source().equals("stage:L1:Last 16")
                && edge.target().contains("Mexico") && edge.label().equals("M92")));
        assertFalse(graph.edges().stream().anyMatch(edge -> edge.source().equals("stage:L1:Last 16")
                && edge.target().contains("France") && edge.label().equals("M91")),
                "Projected alternatives must follow the matching source match, not the first future team row");
        assertTrue(graph.edges().stream().anyMatch(edge -> edge.source().equals("stage:L1:Quarter Finals")
                && edge.target().contains("Brazil") && edge.label().equals("M99")));
    }

    @Test
    void focusedGraphMergesDuplicateEdgesUsingPathPriority() {
        PathVisualizationService service = new PathVisualizationService();
        List<PathVisualizationService.RoundRows> rounds = List.of(
                round("last_32_match", "Last 32", List.of(
                        row("M80", side("England", "L1", ""), side("Senegal", "A2", ""), "predicted", "England (62%)"))),
                round("last_16_match", "Last 16", List.of(
                        row("M92", side("England", "L1", "M80"), side("Mexico", "A1", "M79"), "alt", "England (54%)",
                                "K@M80|Senegal:-3", "K@M79|Ivory Coast:0"),
                        row("M92", side("England", "L1", "M80"), side("Mexico", "A1", "M79"), "predicted", "England (54%)"))));

        PathVisualizationService.Graph graph = service.build(rounds, "last_16_match", "all", "England");

        List<PathVisualizationService.Edge> sharedEdges = graph.edges().stream()
                .filter(edge -> edge.source().equals("stage:L1:Last 16"))
                .filter(edge -> edge.target().startsWith("route:Last 16:Mexico:from:"))
                .toList();

        assertEquals(1, sharedEdges.size());
        assertEquals("predicted", sharedEdges.get(0).path());
    }

    @Test
    void repeatedOpponentAlternativeRoutesAreSkipped() {
        PathVisualizationService service = new PathVisualizationService();
        List<PathVisualizationService.RoundRows> rounds = List.of(
                round("final_match", "Final", List.of(
                        row("M103", side("England", "L1", "M102"), side("Turkey", "D1", "M101"), "alt", "England (57%)",
                                "K@M80|Senegal:-3 > K@M92|Turkey:-7 > K@M99|Ecuador:-15 > K@M102|France:-20",
                                "K@M81|Algeria:0 > K@M94|Belgium:-5 > K@M98|Croatia:-2 > K@M101|Spain:-8"),
                        row("M103", side("England", "L1", "M102"), side("Spain", "H1", "M101"), "alt", "England (54%)",
                                "K@M80|Senegal:-3 > K@M92|Mexico:-14 > K@M99|Ecuador:-15 > K@M102|France:-20",
                                "K@M84|Austria:-1 > K@M93|Colombia:-16 > K@M98|Turkey:-14 > K@M101|France:-37"))));

        PathVisualizationService.Graph graph = service.build(rounds, "final_match", "alt", "England");
        String json = service.toJson(graph);

        assertFalse(json.contains("Turkey"));
        assertTrue(json.contains("Spain"));
        assertTrue(graph.nodes().stream().anyMatch(node -> node.team().equals("Spain")
                && node.label().contains("Final") && node.label().contains("M103")));
    }

    @Test
    void stageNodesStaySeparateForDifferentStructuredGroupFinishBranches() {
        PathVisualizationService service = new PathVisualizationService();
        List<PathVisualizationService.RoundRows> rounds = List.of(
                round("last_16_match", "Last 16", List.of(
                        row("M92", side("England", "L1", "M80"), side("Mexico", "A1", "M79"), "alt", "England (54%)",
                                "K@M80|Senegal:-3", "K@M79|Ivory Coast:0"),
                        row("M93", side("England", "L2", "M83"), side("Spain", "B1", "M84"), "alt", "England (52%)",
                                "K@M83|Croatia:-3", "K@M84|Austria:0"))),
                round("last_8_match", "Quarter Finals", List.of(
                        row("M99", side("England", "L1", "M92"), side("Brazil", "B1", "M91"), "predicted", "England (51%)"),
                        row("M98", side("England", "L2", "M93"), side("Turkey", "D1", "M94"), "alt", "England (51%)"))));

        PathVisualizationService.Graph graph = service.build(rounds, "", "all", "England");

        assertTrue(graph.nodes().stream().anyMatch(node -> node.id().equals("stage:L1:Last 16")));
        assertTrue(graph.nodes().stream().anyMatch(node -> node.id().equals("stage:L2:Last 16")));
        assertTrue(graph.nodes().stream().anyMatch(node -> node.id().equals("stage:L1:Quarter Finals")));
        assertTrue(graph.nodes().stream().anyMatch(node -> node.id().equals("stage:L2:Quarter Finals")));
        assertFalse(graph.edges().stream().anyMatch(edge -> edge.source().startsWith("stage:L1")
                && edge.target().contains("Spain")));
        assertFalse(graph.edges().stream().anyMatch(edge -> edge.source().startsWith("stage:L2")
                && edge.target().contains("Mexico")));
    }

    @Test
    void focusedGraphNormalizesCompositeThirdPlaceSeedWhenSimpleSeedIsKnown() {
        PathVisualizationService service = new PathVisualizationService();
        List<PathVisualizationService.RoundRows> rounds = List.of(
                round("last_32_match", "Last 32", List.of(
                        row("M74", side("Germany", "E1", ""), side("Scotland", "C3", ""), "alt", "Germany (69%)"),
                        row("M79", side("Mexico", "A1", ""), side("Scotland", "CEFHI3", "", "C3"), "alt", "Scotland (52%)"))));

        PathVisualizationService.Graph graph = service.build(rounds, "", "all", "Scotland");

        assertTrue(graph.nodes().stream().anyMatch(node -> node.type().equals("seed")
                && node.team().equals("Scotland") && node.label().equals("C3")));
        assertFalse(graph.nodes().stream().anyMatch(node -> node.type().equals("seed")
                && node.team().equals("Scotland") && node.label().equals("CEFHI3")));
    }

    @Test
    void focusedGraphAddsChampionStatusWhenTeamWinsFinal() {
        PathVisualizationService service = new PathVisualizationService();
        List<PathVisualizationService.RoundRows> rounds = List.of(
                round("final_match", "Final", List.of(
                        row("M103", side("England", "L1", "M102"), side("Spain", "H1", "M101"), "predicted", "England (58%)"))));

        PathVisualizationService.Graph graph = service.build(rounds, "final_match", "prediction", "England");
        String json = service.toJson(graph);

        assertTrue(json.contains("Spain"));
        assertTrue(json.contains("Final"));
        assertTrue(json.contains("M103"));
        assertTrue(json.contains("Champions"));
        assertTrue(json.contains("\"type\":\"champion\""));
        assertFalse(json.contains("Eliminated"));
    }

    private static PathVisualizationService.RoundRows round(String key, String label, List<Map<String, String>> rows) {
        return new PathVisualizationService.RoundRows(key, label, rows);
    }

    private static Side side(String team, String seed, String sourceMatch) {
        return side(team, seed, sourceMatch, seed);
    }

    private static Side side(String team, String seed, String sourceMatch, String groupFinish) {
        return new Side(team, seed, sourceMatch, groupFinish);
    }

    private static Map<String, String> row(String matchId, Side team1, Side team2, String path, String prediction) {
        return row(matchId, team1, team2, path, prediction, "", "");
    }

    private static Map<String, String> row(String matchId, Side team1, Side team2, String path, String prediction,
                                           String team1PathOpponent, String team2PathOpponent) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("match_id", matchId);
        row.put("team1", team1.team());
        row.put("team2", team2.team());
        row.put("path", path);
        row.put("prediction", prediction);
        addSide(row, "team1", team1);
        addSide(row, "team2", team2);
        if (!team1PathOpponent.isBlank()) row.put("team1_path_opponent", team1PathOpponent);
        if (!team2PathOpponent.isBlank()) row.put("team2_path_opponent", team2PathOpponent);
        return row;
    }

    private static void addSide(Map<String, String> row, String prefix, Side side) {
        row.put(prefix + "_team", side.team());
        row.put(prefix + "_slot", side.sourceMatch().isBlank() ? side.seed() : "W" + side.sourceMatch().substring(1));
        row.put(prefix + "_bracket_slot", side.seed());
        row.put(prefix + "_group_finish", side.groupFinish());
        if (!side.sourceMatch().isBlank()) row.put(prefix + "_source_match", side.sourceMatch());
    }

    private record Side(String team, String seed, String sourceMatch, String groupFinish) {}
}

package com.tournamentpredictor.services.web;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteInstanceServiceTest {
    @Test
    void preservesConcretePriorOpponentWhenLaterRoundUsesWinnerToken() {
        RouteInstanceService service = new RouteInstanceService();
        List<PathVisualizationService.RoundRows> rounds = List.of(
                new PathVisualizationService.RoundRows("last_32_match", "Last 32", List.of(
                        row("M80", "England", "L1", "", "L1", "L1", "Senegal", "EHIJK3", "", "K3", "EHIJK3",
                                "predicted", "England (63%)", Map.of("team2_path_opponent", "G|Brazil:-3 > G|Turkey:-2")),
                        row("M80", "England", "L1", "", "L1", "L1", "Norway", "EHIJK3", "", "I3", "EHIJK3",
                                "alt", "England (63%)"))),
                new PathVisualizationService.RoundRows("last_16_match", "Last 16", List.of(
                        row("M92", "England", "W80", "M80", "L1", "L1", "Mexico", "W79", "M79", "A1", "A1",
                                "predicted", "England (54%)"))));

        List<String> lines = service.buildLines(rounds);

        assertEquals("route_id,parent_route_id,round,match_id,team,seed,opponent,opponent_seed,path,winner,advanced,source_match_id,matchup_pct,likelihood,path_history,opponent_path", lines.get(0));
        assertTrue(lines.stream().anyMatch(line -> line.contains("M92,England,L1,Mexico,A1,predicted,England,true,M80,,very-large,L1:M80|Senegal|predicted|W > M92|Mexico|predicted|W")),
                "The Senegal M80 branch should continue separately into M92");
        assertTrue(lines.stream().anyMatch(line -> line.contains("M92,England,L1,Mexico,A1,predicted,England,true,M80,,very-large,L1:M80|Norway|alt|W > M92|Mexico|predicted|W")),
                "The Norway M80 branch should continue separately into M92");

        assertTrue(lines.stream().anyMatch(line -> line.contains("G|Brazil:-3 > G|Turkey:-2")),
                "Route-instance rows should carry the opposition path for tooltips");
    }


    @Test
    void selectedTeamAlternativeWinIsExpandedAtRuntimeWithoutStoredUpsetRow() {
        RouteInstanceService service = new RouteInstanceService();
        List<String> lines = service.buildLines(List.of(
                new PathVisualizationService.RoundRows("last_32_match", "Last 32", List.of(
                        row("M75", "Japan", "F1", "", "F1", "F1", "Morocco", "C2", "", "C2", "C2",
                                "predicted", "Japan (60%)"))),
                new PathVisualizationService.RoundRows("last_16_match", "Last 16", List.of(
                        row("M90", "Morocco", "W75", "M75", "C2", "C2", "Canada", "W73", "M73", "B2", "B2",
                                "alt", "Canada (55%)")))), "Morocco");

        assertTrue(lines.stream().anyMatch(line -> line.contains(",M90,Morocco,C2,Canada,B2,")
                        && line.contains("C2:M75|Japan|alt|W > M90|Canada|alt|L")),
                "Morocco should reach M90 through a runtime alternative win over Japan even without a stored upset row: " + lines);
    }


    @Test
    void selectedTeamContinuationCanBeSynthesizedFromSameSourceMatchWhenExactLaterRowIsMissing() {
        RouteInstanceService service = new RouteInstanceService();
        List<String> lines = service.buildLines(List.of(
                new PathVisualizationService.RoundRows("last_32_match", "Last 32", List.of(
                        row("M75", "Japan", "F1", "", "F1", "F1", "Morocco", "C2", "", "C2", "C2",
                                "predicted", "Japan (60%)"))),
                new PathVisualizationService.RoundRows("last_16_match", "Last 16", List.of(
                        row("M90", "Sweden", "W75", "M75", "C2", "C2", "Canada", "W73", "M73", "B2", "B2",
                                "alt", "Canada (55%)")))), "Morocco");

        assertTrue(lines.stream().anyMatch(line -> line.contains(",M90,Morocco,C2,Canada,B2,alt,Canada,false,M75")),
                "Morocco should be synthesized into M90 from the W75 source slot even without an exact M90 row: " + lines);
        assertTrue(lines.stream().anyMatch(line -> line.contains("C2:M75|Japan|alt|W > M90|Canada|alt|L")),
                "The synthesized row should preserve Morocco's runtime path history: " + lines);
    }

    @Test
    void graphUsesConcreteRouteInstancesInsteadOfCollapsedWinnerToken() {
        RouteInstanceService service = new RouteInstanceService();
        List<String> lines = service.buildLines(List.of(
                new PathVisualizationService.RoundRows("last_32_match", "Last 32", List.of(
                        row("M80", "England", "L1", "", "L1", "L1", "Senegal", "EHIJK3", "", "K3", "EHIJK3",
                                "predicted", "England (63%)", Map.of("team2_path_opponent", "G|Brazil:-3 > G|Turkey:-2")),
                        row("M80", "England", "L1", "", "L1", "L1", "Norway", "EHIJK3", "", "I3", "EHIJK3",
                                "alt", "England (63%)"))),
                new PathVisualizationService.RoundRows("last_16_match", "Last 16", List.of(
                        row("M92", "England", "W80", "M80", "L1", "L1", "Mexico", "W79", "M79", "A1", "A1",
                                "predicted", "England (54%)")))));

        PathVisualizationService.Graph graph = service.buildGraph(toRows(lines), "England", "all");

        long mexicoBranches = graph.nodes().stream()
                .filter(node -> node.team().equals("Mexico") && node.label().contains("M92"))
                .count();
        assertEquals(1, mexicoBranches, "Duplicate predicted/alternative M92 relationships should merge into one visual node");

        assertTrue(graph.nodes().stream().anyMatch(node -> node.team().equals("Norway")
                && node.likelihood().equals("very-small")),
                "Alternative nodes without a matchup percentage should render as very-small likelihood");

        assertTrue(graph.nodes().stream().anyMatch(node -> node.team().equals("Senegal")
                && node.opponentPath().contains("Brazil")
                && node.opponentSeed().equals("EHIJK3")
                && node.roundLabel().equals("Last 32")),
                "Opponent nodes should expose the opposition path, seed, and round for hover tooltips");

        assertTrue(graph.nodes().stream().anyMatch(node -> node.type().equals("stage")
                && node.label().equals("Last 16")),
                "Route-instance graphs should include compact intermediate round nodes");
    }

    @Test
    void graphFansRouteVariantsIntoSharedRoundStageAndKeepsAltContinuationGrey() {
        RouteInstanceService service = new RouteInstanceService();
        List<String> lines = service.buildLines(List.of(
                new PathVisualizationService.RoundRows("last_32_match", "Last 32", List.of(
                        row("M80", "England", "L1", "", "L1", "L1", "Senegal", "EHIJK3", "", "K3", "EHIJK3",
                                "predicted", "England (63%)", Map.of("team2_path_opponent", "G|Brazil:-3 > G|Turkey:-2")),
                        row("M80", "England", "L1", "", "L1", "L1", "Norway", "EHIJK3", "", "I3", "EHIJK3",
                                "alt", "England (63%)"))),
                new PathVisualizationService.RoundRows("last_16_match", "Last 16", List.of(
                        row("M92", "England", "W80", "M80", "L1", "L1", "Mexico", "W79", "M79", "A1", "A1",
                                "predicted", "England (54%)")))));

        PathVisualizationService.Graph graph = service.buildGraph(toRows(lines), "England", "all");

        long last16Stages = graph.nodes().stream()
                .filter(node -> node.type().equals("stage") && node.label().equals("Last 16"))
                .count();
        assertEquals(1, last16Stages, "M80 route variants should fan into one shared Last 16 stage node");

        long predictedM92Edges = graph.edges().stream()
                .filter(edge -> edge.target().contains(":m92:mexico:w") && edge.path().equals("predicted"))
                .count();
        long altM92Edges = graph.edges().stream()
                .filter(edge -> edge.target().contains(":m92:mexico:w") && edge.path().equals("alt"))
                .count();
        long labelledM92Edges = graph.edges().stream()
                .filter(edge -> edge.label().equals("M92"))
                .count();

        assertEquals(1, predictedM92Edges, "Predicted should win when an alternative relationship duplicates it");
        assertEquals(0, altM92Edges, "Duplicate alternative relationship should be hidden");
        assertEquals(0, labelledM92Edges, "Match ids should render in nodes, not on relationship labels");
    }


    @Test
    void compositeThirdPlaceBracketSlotIsPreservedForOpponentTooltipSeed() {
        RouteInstanceService service = new RouteInstanceService();
        List<String> lines = service.buildLines(List.of(
                new PathVisualizationService.RoundRows("last_32_match", "Last 32", List.of(
                        row("M80", "England", "L1", "", "L1", "L1", "Norway", "I3", "", "I3", "EHIJK3",
                                "alt", "England (63%)")))));

        assertTrue(lines.stream().anyMatch(line -> line.contains(",M80,England,L1,Norway,EHIJK3,")),
                "Tooltip seed should keep the bracket wildcard slot instead of collapsing to Norway's resolved I3 finish");

        PathVisualizationService.Graph graph = service.buildGraph(toRows(lines), "England", "all");

        assertTrue(graph.nodes().stream().anyMatch(node -> node.team().equals("Norway")
                        && node.opponentSeed().equals("EHIJK3")),
                "Visualization nodes should expose the composite third-place slot used by the fixture");
    }


    @Test
    void bracketSlotDrivesVisualizationBranchWhenGroupFinishDiffers() {
        RouteInstanceService service = new RouteInstanceService();
        Map<String, String> row = new java.util.LinkedHashMap<>();
        row.put("match_id", "M83");
        row.put("team1", "Colombia");
        row.put("team2", "England");
        row.put("path", "alt");
        row.put("prediction", "England (52%)");
        row.put("team1_team", "Colombia");
        row.put("team1_group_finish", "K2");
        row.put("team1_bracket_slot", "K2");
        row.put("team2_team", "England");
        row.put("team2_group_finish", "L1");
        row.put("team2_bracket_slot", "L2");
        List<String> lines = service.buildLines(List.of(
                new PathVisualizationService.RoundRows("last_32_match", "Last 32", List.of(row))));

        assertTrue(lines.stream().anyMatch(line -> line.contains(",M83,England,L2,Colombia,K2,")),
                "The graph branch should use England's bracket slot L2, not collapse to group finish L1");

        PathVisualizationService.Graph graph = service.buildGraph(toRows(lines), "England", "all");
        assertTrue(graph.nodes().stream().anyMatch(node -> node.id().equals("seed:England:L2")),
                "Visualization should expose the L2 branch so it can be laid out to the right of the central team");
    }

    @Test
    void graphNodesExposeVisualizationTooltipMetadata() {
        RouteInstanceService service = new RouteInstanceService();
        List<String> lines = service.buildLines(List.of(
                new PathVisualizationService.RoundRows("last_32_match", "Last 32", List.of(
                        row("M80", "England", "L2", "", "L2", "L2", "Colombia", "I1", "", "I1", "I1",
                                "alt", "England (57%)", Map.of("team2_path_opponent", "G|Brazil:-3 > U@M83|Austria:-8", "matchup_pct", "28.7"))))));

        PathVisualizationService.Graph graph = service.buildGraph(toRows(lines), "England", "all");

        assertTrue(graph.nodes().stream().anyMatch(node -> node.team().equals("Colombia")
                        && node.roundLabel().equals("Last 32")
                        && node.opponentSeed().equals("I1")
                        && node.opponentPath().equals("G|Brazil:-3 > U@M83|Austria:-8")
                        && node.matchupPct().equals("28.7")
                        && node.likelihood().equals("small")),
                "Country nodes should keep the fields used by the network tooltip and size buckets");
    }


    @Test
    void childRoutesKeepParentBranchSeedWhenLaterRowsResolveToDifferentSlot() {
        RouteInstanceService service = new RouteInstanceService();
        List<String> lines = service.buildLines(List.of(
                new PathVisualizationService.RoundRows("last_8_match", "Quarter Finals", List.of(
                        row("M99", "DR Congo", "EHIJK3", "", "K3", "EHIJK3", "Mexico", "A1", "", "A1", "A1",
                                "alt", "DR Congo (52%)"))),
                new PathVisualizationService.RoundRows("last_4_match", "Semi Finals", List.of(
                        row("M102", "DR Congo", "W99", "M99", "K3", "EHIJK3", "Ecuador", "K1", "", "K1", "K1",
                                "alt", "DR Congo (51%)"))),
                new PathVisualizationService.RoundRows("final_match", "Final", List.of(
                        row("M103", "DR Congo", "W102", "M102", "K3", "K1", "Spain", "W101", "M101", "A1", "A1",
                                "alt", "Spain (54%)")))));

        assertTrue(lines.stream().anyMatch(line -> line.contains(",M103,DR Congo,EHIJK3,Spain,A1,")),
                "Final route should inherit the EHIJK3 parent branch instead of switching to K1 from the final row token");

        PathVisualizationService.Graph graph = service.buildGraph(toRows(lines), "DR Congo", "all");
        assertTrue(graph.nodes().stream().anyMatch(node -> node.id().equals("stage-instance:dr_congo:ehijk3:m102:final")),
                "The final stage should stay on the EHIJK3 branch");
        assertTrue(graph.nodes().stream().noneMatch(node -> node.id().equals("stage-instance:dr_congo:k1:m102:final")),
                "The EHIJK3 route must not be rendered through a K1 final stage");
    }

    @Test
    void graphSharesOneEliminatedNodePerBranchStage() {
        RouteInstanceService service = new RouteInstanceService();
        List<String> lines = service.buildLines(List.of(
                new PathVisualizationService.RoundRows("last_32_match", "Last 32", List.of(
                        row("M80", "England", "L1", "", "L1", "L1", "Senegal", "EHIJK3", "", "K3", "EHIJK3",
                                "alt", "Senegal (51%)"),
                        row("M80", "England", "L1", "", "L1", "L1", "Norway", "EHIJK3", "", "I3", "EHIJK3",
                                "alt", "Norway (51%)")))));

        PathVisualizationService.Graph graph = service.buildGraph(toRows(lines), "England", "all");

        long eliminatedNodes = graph.nodes().stream()
                .filter(node -> node.type().equals("eliminated"))
                .count();
        long eliminatedEdges = graph.edges().stream()
                .filter(edge -> edge.target().endsWith(":eliminated"))
                .count();

        assertEquals(1, eliminatedNodes, "Losing options in the same branch round should share one eliminated node");
        assertEquals(2, eliminatedEdges, "Each losing matchup should still point to the shared eliminated node");
        assertTrue(graph.nodes().stream().anyMatch(node -> node.id().equals("stage-instance:england:l1:root:last_32:eliminated")));
    }

    @Test
    void graphSharesOneChampionNodePerBranchFinalStage() {
        RouteInstanceService service = new RouteInstanceService();
        List<String> lines = service.buildLines(List.of(
                new PathVisualizationService.RoundRows("last_4_match", "Semi Finals", List.of(
                        row("M102", "England", "L1", "", "L1", "L1", "Brazil", "K1", "", "K1", "K1",
                                "alt", "England (51%)"))),
                new PathVisualizationService.RoundRows("final_match", "Final", List.of(
                        row("M103", "England", "W102", "M102", "L1", "L1", "Spain", "W101", "M101", "A1", "A1",
                                "alt", "England (52%)"),
                        row("M103", "England", "W102", "M102", "L1", "L1", "France", "W101", "M101", "A1", "A1",
                                "alt", "England (53%)")))));

        PathVisualizationService.Graph graph = service.buildGraph(toRows(lines), "England", "all");

        long championNodes = graph.nodes().stream()
                .filter(node -> node.type().equals("champion"))
                .count();
        long championEdges = graph.edges().stream()
                .filter(edge -> edge.target().endsWith(":champion"))
                .count();

        assertEquals(1, championNodes, "Winning options in the same branch final should share one champion node");
        assertEquals(2, championEdges, "Each title-winning matchup should still point to the shared champion node");
        assertTrue(graph.nodes().stream().anyMatch(node -> node.id().equals("stage-instance:england:l1:m102:final:champion")));
    }

    @Test
    void graphKeepsChampionNodesSeparateForDifferentBranchFinalStages() {
        RouteInstanceService service = new RouteInstanceService();
        List<String> lines = service.buildLines(List.of(
                new PathVisualizationService.RoundRows("last_4_match", "Semi Finals", List.of(
                        row("M102", "England", "L1", "", "L1", "L1", "Brazil", "K1", "", "K1", "K1",
                                "alt", "England (51%)"),
                        row("M104", "England", "L2", "", "L2", "L2", "Argentina", "K2", "", "K2", "K2",
                                "alt", "England (52%)"))),
                new PathVisualizationService.RoundRows("final_match", "Final", List.of(
                        row("M103", "England", "W102", "M102", "L1", "L1", "Spain", "W101", "M101", "A1", "A1",
                                "alt", "England (52%)"),
                        row("M103", "England", "W104", "M104", "L2", "L2", "France", "W101", "M101", "A1", "A1",
                                "alt", "England (53%)")))));

        PathVisualizationService.Graph graph = service.buildGraph(toRows(lines), "England", "all");

        long championNodes = graph.nodes().stream()
                .filter(node -> node.type().equals("champion"))
                .count();

        assertEquals(2, championNodes, "Separate branch finals need separate champion nodes so K1/K2/K3 layouts do not merge visually");
        assertTrue(graph.nodes().stream().anyMatch(node -> node.id().equals("stage-instance:england:l1:m102:final:champion")));
        assertTrue(graph.nodes().stream().anyMatch(node -> node.id().equals("stage-instance:england:l2:m104:final:champion")));
    }

    @Test
    void buildLinesDropsRoutesThatWouldReplayTheSameOpponent() {
        RouteInstanceService service = new RouteInstanceService();
        List<String> lines = service.buildLines(List.of(
                new PathVisualizationService.RoundRows("last_32_match", "Last 32", List.of(
                        row("M80", "Canada", "L1", "", "L1", "L1", "Australia", "D4", "", "D4", "D4",
                                "alt", "Canada (51%)"))),
                new PathVisualizationService.RoundRows("last_16_match", "Last 16", List.of(
                        row("M92", "Canada", "W80", "M80", "L1", "L1", "Bosnia and Herzegovina", "A1", "", "A1", "A1",
                                "alt", "Canada (52%)"))),
                new PathVisualizationService.RoundRows("last_8_match", "Quarter Finals", List.of(
                        row("M99", "Canada", "W92", "M92", "L1", "L1", "Australia", "A2", "", "A2", "A2",
                                "alt", "Canada (53%)")))));

        assertTrue(lines.stream().anyMatch(line -> line.contains(",M80,Canada,L1,Australia,D4,")));
        assertTrue(lines.stream().noneMatch(line -> line.contains(",M99,Canada,L1,Australia,A2,")),
                "A route should stop before the same matchup opponent can appear twice");
    }

    @Test
    void graphSkipsRowsWhoseOpponentPathRepeatsATeam() {
        RouteInstanceService service = new RouteInstanceService();
        List<String> lines = List.of(
                "route_id,parent_route_id,round,match_id,team,seed,opponent,opponent_seed,path,winner,advanced,source_match_id,matchup_pct,likelihood,path_history,opponent_path",
                "canada_l1_root_m103_australia_alt_l,,final_match,M103,Canada,L1,Australia,D4,alt,Australia,false,,25,small,L1:M103|Australia|alt|L,G|Bosnia and Herzegovina:0 > K@M94|Czechia:0 > K@M98|Panama:0 > K@M101|Bosnia and Herzegovina:0"
        );

        PathVisualizationService.Graph graph = service.buildGraph(toRows(lines), "Canada", "all");

        assertTrue(graph.nodes().stream().noneMatch(node -> node.team().equals("Australia")),
                "Legacy/generated rows with repeated teams in the opponent path should not be visualized");
    }

    @Test
    void buildLinesCanBeScopedToSelectedTeamForOnDemandVisualization() {
        RouteInstanceService service = new RouteInstanceService();
        List<String> lines = service.buildLines(List.of(
                new PathVisualizationService.RoundRows("last_16_match", "Last 16", List.of(
                        row("M51", "England", "B1", "", "B1", "B1", "Senegal", "A2", "", "A2", "A2",
                                "results", "England (3-0)"),
                        row("M52", "Netherlands", "A1", "", "A1", "A1", "United States", "B2", "", "B2", "B2",
                                "results", "Netherlands (3-1)")))), "England");

        assertTrue(lines.stream().anyMatch(line -> line.contains(",M51,England,B1,Senegal,A2,")));
        assertTrue(lines.stream().noneMatch(line -> line.contains(",United States,")),
                "On-demand visualization route rows should not materialize unrelated teams");
    }


    private Map<String, String> row(String matchId, String team1, String team1Slot, String team1Source,
                                    String team1GroupFinish, String team1BracketSlot,
                                    String team2, String team2Slot, String team2Source,
                                    String team2GroupFinish, String team2BracketSlot,
                                    String path, String prediction) {
        Map<String, String> row = new java.util.LinkedHashMap<>();
        row.put("match_id", matchId);
        row.put("team1", team1);
        row.put("team2", team2);
        row.put("path", path);
        row.put("prediction", prediction);
        row.put("team1_slot", team1Slot);
        row.put("team1_team", team1);
        row.put("team1_source_match", team1Source);
        row.put("team1_group_finish", team1GroupFinish);
        row.put("team1_bracket_slot", team1BracketSlot);
        row.put("team2_slot", team2Slot);
        row.put("team2_team", team2);
        row.put("team2_source_match", team2Source);
        row.put("team2_group_finish", team2GroupFinish);
        row.put("team2_bracket_slot", team2BracketSlot);
        return row;
    }

    private Map<String, String> row(String matchId, String team1, String team1Slot, String team1Source,
                                    String team1GroupFinish, String team1BracketSlot,
                                    String team2, String team2Slot, String team2Source,
                                    String team2GroupFinish, String team2BracketSlot,
                                    String path, String prediction, Map<String, String> extra) {
        Map<String, String> row = new java.util.LinkedHashMap<>(row(matchId, team1, team1Slot, team1Source, team1GroupFinish,
                team1BracketSlot, team2, team2Slot, team2Source, team2GroupFinish, team2BracketSlot, path, prediction));
        row.putAll(extra);
        return row;
    }

    private List<Map<String, String>> toRows(List<String> lines) {
        String[] headers = lines.get(0).split(",", -1);
        return lines.stream().skip(1).map(line -> {
            String[] values = line.split(",", -1);
            Map<String, String> row = new java.util.LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                row.put(headers[i], i < values.length ? values[i] : "");
            }
            return row;
        }).toList();
    }

}

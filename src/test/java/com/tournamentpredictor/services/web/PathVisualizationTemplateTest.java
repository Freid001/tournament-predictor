package com.tournamentpredictor.services.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathVisualizationTemplateTest {

    @Test
    void templateRendersStageNodesAsDistinctRoundDividers() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/path-visualization.html"));

        assertTrue(template.contains("data.type === 'stage') return { width: 132, height: 36 }"));
        assertTrue(template.contains("ranker: 'network-simplex', nodesep: 74, ranksep: 190, edgesep: 30"));
        assertTrue(template.contains("function centeredEdgePoints(edge, nodeById)"));
        assertTrue(template.contains("const label = String(seed.label || seed.id || '')"));
        assertTrue(template.contains("if (/^[A-Z]{2,}3$/.test(label)) return 0"));
        assertTrue(template.contains("rankdir: side === 0 ? 'TB' : 'LR'"));
        assertTrue(template.contains("const branchRankSep = side === 0 ? 148 : 230"));
        assertTrue(template.contains("ranksep: branchRankSep"));
        assertTrue(template.contains("const downBranches = branchLayouts.filter(branch => branch.side === 0)"));
        assertTrue(template.contains("const y = (node.y - branchLayout.seedNode.y) + 176"));
        assertTrue(template.contains("positioned.set(root.id, { ...root, x: 0, y: 0 })"));
        assertTrue(template.contains("const alignedPathNode = node.type === 'stage' || node.type === 'seed' || node.path === 'predicted' || node.path === 'results'"));
        assertTrue(template.contains("function clearSpineY(node, rawY, centerY)"));
        assertTrue(template.contains("const clearance = 92"));
        assertTrue(template.contains("function positionAltNodesAroundSpine(nodes, edges, verticalNodeIds = new Set())"));
        assertTrue(template.contains("function outgoingEdgesBySource(edges)"));
        assertTrue(template.contains("function nodeLeadsToElimination(node, outgoing, nodeById)"));
        assertTrue(template.contains("?.type === 'eliminated'"));
        assertTrue(template.contains("function nodeLeadsToChampion(node, outgoing, nodeById)"));
        assertTrue(template.contains("?.type === 'champion'"));
        assertTrue(template.contains("function terminalTargetsFor(nodes, outgoing, nodeById, terminalType)"));
        assertTrue(template.contains("function eliminatedTargetsFor(nodes, outgoing, nodeById)"));
        assertTrue(template.contains("function championTargetsFor(nodes, outgoing, nodeById)"));
        assertTrue(template.contains("target?.type === terminalType && !seen.has(target.id)"));
        assertTrue(template.contains("function orderedByLikelihood(nodes)"));
        assertTrue(template.contains("likelihoodScore(b) - likelihoodScore(a)"));
        assertTrue(template.contains("function placeOutcomeLane(nodes, lane, axis)"));
        assertTrue(template.contains("function averageCoordinate(nodes, key, fallback)"));
        assertTrue(template.contains("function terminalXBeyondMatchups(nodes, stageX)"));
        assertTrue(template.contains("const outward = stageX < 0 ? -1 : 1"));
        assertTrue(template.contains("const terminalNodeSize = { width: 98, height: 46 }"));
        assertTrue(template.contains("const terminalHalfWidth = terminalNodeSize.width / 2"));
        assertTrue(template.contains("const terminalGap = 142"));
        assertTrue(template.contains("outerEdgeX + (outward * (terminalGap + terminalHalfWidth))"));
        assertTrue(template.contains("function continuationStageXFor(nodes, outgoing, nodeById, fallbackX)"));
        assertTrue(template.contains("function terminalYBeyondMatchups(nodes, stageY)"));
        assertTrue(template.contains("const terminalHalfHeight = terminalNodeSize.height / 2"));
        assertTrue(template.contains("return outerEdgeY + terminalGap + terminalHalfHeight"));
        assertTrue(template.contains("function continuationStageYFor(nodes, outgoing, nodeById, fallbackY)"));
        assertTrue(template.contains("return averageCoordinate(stages, 'y', fallbackY)"));
        assertTrue(template.contains("target?.type === 'stage' && !seen.has(target.id)"));
        assertTrue(template.contains("else if (nodeLeadsToElimination(target, outgoing, nodeById)) group.eliminated.push(target)"));
        assertTrue(template.contains("placeOutcomeLane(group.advancing, { direction: -1, origin: centerY, cursor: offset }, 'y')"));
        assertTrue(template.contains("placeOutcomeLane(group.eliminated, { direction: 1, origin: centerY, cursor: offset }, 'y')"));
        assertTrue(template.contains("const eliminatedCenterY = averageCoordinate(group.eliminated, 'y', centerY)"));
        assertTrue(template.contains("const eliminatedFallbackX = terminalXBeyondMatchups(group.eliminated, group.stage.x)"));
        assertTrue(template.contains("const eliminatedTerminalX = continuationStageXFor(group.advancing, outgoing, nodeById, eliminatedFallbackX)"));
        assertTrue(template.contains("node.x = eliminatedTerminalX"));
        assertTrue(template.contains("node.y = eliminatedCenterY"));
        assertTrue(template.contains("const championNodes = group.advancing.filter(node => nodeLeadsToChampion(node, outgoing, nodeById))"));
        assertTrue(template.contains("const championTerminalX = terminalXBeyondMatchups(championNodes, group.stage.x)"));
        assertTrue(template.contains("championTargetsFor(championNodes, outgoing, nodeById).forEach(node =>"));
        assertTrue(template.contains("node.x = championTerminalX"));
        assertTrue(template.contains("node.y = championCenterY"));
        assertTrue(template.contains("placeOutcomeLane(group.advancing, { direction: -1, origin: group.stage.x, cursor: offset }, 'x')"));
        assertTrue(template.contains("placeOutcomeLane(group.eliminated, { direction: 1, origin: group.stage.x, cursor: offset }, 'x')"));
        assertTrue(template.contains("const eliminatedCenterX = averageCoordinate(group.eliminated, 'x', group.stage.x)"));
        assertTrue(template.contains("const eliminatedFallbackY = terminalYBeyondMatchups(group.eliminated, group.stage.y)"));
        assertTrue(template.contains("const eliminatedTerminalY = continuationStageYFor(group.advancing, outgoing, nodeById, eliminatedFallbackY)"));
        assertTrue(template.contains("node.x = eliminatedCenterX"));
        assertTrue(template.contains("node.y = eliminatedTerminalY"));
        assertTrue(template.contains("const championCenterX = averageCoordinate(championNodes, 'x', group.stage.x)"));
        assertTrue(template.contains("const championTerminalY = terminalYBeyondMatchups(championNodes, group.stage.y)"));
        assertTrue(template.contains("node.x = championCenterX"));
        assertTrue(template.contains("node.y = championTerminalY"));
        assertTrue(template.contains("function resolveAltNodeCollisions(nodes)"));
        assertTrue(template.contains("node.type !== 'eliminated' && node.type !== 'champion'"));
        assertTrue(template.contains("const key = `${node.branchKey || ''}:${Math.round(node.x / 90)}`"));
        assertTrue(template.contains("branchKey: branchLayout.seed.id"));
        assertTrue(template.contains("const minimumGap = ((previous.height || 46) + (current.height || 46)) / 2 + 18"));
        assertTrue(template.contains("positionAltNodesAroundSpine(nodeList, provisionalEdges, downNodeIds)"));
        assertTrue(template.contains("function clearVerticalSpineX(node, rawX)"));
        assertTrue(template.contains("const x = alignedPathNode ? 0 : clearVerticalSpineX(node, rawX)"));
        assertTrue(template.contains("function positionAltNodesAroundVerticalSpine(nodes, edges, verticalNodeIds)"));
        assertTrue(template.contains("if (verticalNodeIds.has(source.id)) return"));
        assertTrue(template.contains("positionAltNodesAroundVerticalSpine(nodeList, provisionalEdges, downNodeIds)"));
        assertTrue(template.contains("const routeStyle = downNodeIds.has(edge.data.source) && downNodeIds.has(edge.data.target) ? 'vertical' : 'horizontal'"));
        assertTrue(template.contains("resolveAltNodeCollisions(nodeList)"));
        assertTrue(template.contains("const y = alignedPathNode ? centerY : clearSpineY(node, rawY, centerY)"));
        assertTrue(template.contains("assignFanOffsets(dagEdges, dagNodeById)"));
        assertTrue(template.contains("const focusTransform = d3.zoomIdentity"));
        assertTrue(template.contains("wrap.clientHeight * 0.5"));
        assertTrue(template.contains("id=\"node-tooltip\""));
        assertTrue(template.contains("function nodeTooltipHtml(data)"));
        assertTrue(template.contains("const flagUrlsByTeam = new Map((graph.nodes || [])"));
        assertTrue(template.contains("function tooltipTournamentPath(data)"));
        assertTrue(template.contains("const seed = String(data.opponentSeed || '').trim()"));
        assertTrue(template.contains("pathSegmentStage(segment) === 'G'"));
        assertTrue(template.contains("function pathSegmentUpset(token)"));
        assertTrue(template.contains("/^U@/i.test(pathSegmentStage(token))"));
        assertTrue(template.contains("upset: pathSegmentUpset(segment)"));
        assertTrue(template.contains("function pathItemHtml(item)"));
        assertTrue(template.contains("(Upset)"));
        assertTrue(template.contains("width:18px;height:12px"));
        assertTrue(template.contains("if (data.type !== 'team' || !data.team || isFocusedTeamNode(data)) return ''"));
        assertTrue(template.contains("const heading = `${escapeHtml(data.team)} vs ${escapeHtml(focusedTeam)}"));
        assertTrue(template.contains("<span class=\"text-muted\">›</span>"));
        assertTrue(template.contains("attachNodeTooltips(node)"));
        assertFalse(template.contains("node.append('title')"));
        assertTrue(template.contains("zoomFocus.onclick = () => svg.transition().duration(180).call(zoom.transform, focusTransform)"));
        assertTrue(template.contains("touchesStage"));
        assertTrue(template.contains("const touchesTerminal = source?.type === 'champion' || source?.type === 'eliminated'"));
        assertTrue(template.contains("if (touchesTerminal)"));
        assertTrue(template.contains("function pathTone(path)"));
        assertTrue(template.contains("return { fill: '#f1f3f5', stroke: '#adb5bd', text: '#495057' }"));
        assertTrue(template.contains("d.type === 'stage' ? pathTone(d.path).fill"));
        assertTrue(template.contains("d.type === 'stage' ? pathTone(d.path).stroke"));
        assertTrue(template.contains("d.type === 'stage' ? pathTone(d.path).text"));
    }
    @Test
    void templateSizesTeamNodesByLikelihoodAndShowsMatchIdUnderFlag() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/path-visualization.html"));

        assertTrue(template.contains("function isFocusedTeamNode(data)"));
        assertTrue(template.contains("const teamNodeSizes = {"));
        assertTrue(template.contains("focused: { width: 150, height: 112 }"));
        assertTrue(template.contains("'very-large': { width: 158, height: 118 }"));
        assertTrue(template.contains("large: { width: 124, height: 94 }"));
        assertTrue(template.contains("medium: { width: 96, height: 74 }"));
        assertTrue(template.contains("small: { width: 74, height: 58 }"));
        assertTrue(template.contains("'very-small': { width: 56, height: 46 }"));
        assertTrue(template.contains("const teamFlagSizes = {"));
        assertTrue(template.contains("focused: { width: 96, height: 64 }"));
        assertTrue(template.contains("'very-large': { width: 102, height: 68 }"));
        assertTrue(template.contains("large: { width: 78, height: 52 }"));
        assertTrue(template.contains("medium: { width: 60, height: 40 }"));
        assertTrue(template.contains("small: { width: 44, height: 30 }"));
        assertTrue(template.contains("'very-small': { width: 32, height: 22 }"));
        assertTrue(template.contains("function likelihoodKey(data)"));
        assertTrue(template.contains("if (data.type === 'champion' || data.type === 'eliminated') return terminalNodeSize"));
        assertTrue(template.contains("if (data.type === 'team') return isFocusedTeamNode(data) ? teamNodeSizes.focused : teamNodeSizes[likelihoodKey(data)]"));
        assertTrue(template.contains("function terminalLabelLines(data)"));
        assertTrue(template.contains("return data.type === 'champion' ? ['Champion 🏆'] : ['Eliminated']"));
        assertTrue(template.contains("d.type === 'team' || d.type === 'champion' || d.type === 'eliminated' ? 8"));
        assertTrue(template.contains("renderText(d3.select(this), terminalLabelLines(d), 0, 0, 'terminal-label')"));
        assertTrue(template.contains(".attr('font-size', d => d.type === 'champion' ? 11.2 : 11.4)"));
        assertTrue(template.contains("data.flagUrl ? 1"));
        assertTrue(template.contains("background:#adb5bd;color:#212529"));
    }


    @Test
    void horizontalEliminatedNodeAlignsToContinuationStageAndLosingStack() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/path-visualization.html"));

        assertTrue(template.contains("const eliminatedCenterY = averageCoordinate(group.eliminated, 'y', centerY)"),
                "Horizontal branches should keep the eliminated node vertically centered on losing matchups");
        assertTrue(template.contains("const eliminatedFallbackX = terminalXBeyondMatchups(group.eliminated, group.stage.x)"),
                "Horizontal branches should still have an outward fallback if no continuation stage exists");
        assertTrue(template.contains("const eliminatedTerminalX = continuationStageXFor(group.advancing, outgoing, nodeById, eliminatedFallbackX)"),
                "Horizontal branches should align eliminated node x with the continuing round stage when available");
        assertTrue(template.contains("node.x = eliminatedTerminalX"));
        assertTrue(template.contains("node.y = eliminatedCenterY"));
    }


    @Test
    void championNodeUsesTerminalAlignmentLikeEliminatedNodes() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/path-visualization.html"));

        assertTrue(template.contains("function nodeLeadsToChampion(node, outgoing, nodeById)"));
        assertTrue(template.contains("function championTargetsFor(nodes, outgoing, nodeById)"));
        assertTrue(template.contains("const championNodes = group.advancing.filter(node => nodeLeadsToChampion(node, outgoing, nodeById))"));
        assertTrue(template.contains("const championCenterY = averageCoordinate(championNodes, 'y', centerY)"),
                "Horizontal champion node should be vertically centered on the winning matchup stack");
        assertTrue(template.contains("const championTerminalX = terminalXBeyondMatchups(championNodes, group.stage.x)"),
                "Horizontal champion node should sit beyond the winning matchup stack like a terminal node");
        assertTrue(template.contains("const championTerminalY = terminalYBeyondMatchups(championNodes, group.stage.y)"),
                "Vertical/K3 champion terminal should mirror the down-branch terminal spacing below the matchup stack");
        assertTrue(template.contains("node.y = championCenterY"));
    }


    @Test
    void verticalBranchTerminalsMirrorAroundRoundSpine() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/path-visualization.html"));

        assertTrue(template.contains("const eliminatedCenterX = averageCoordinate(group.eliminated, 'x', group.stage.x)"),
                "K3/down eliminated terminal should be horizontally centered on losing matchup nodes");
        assertTrue(template.contains("const eliminatedTerminalY = continuationStageYFor(group.advancing, outgoing, nodeById, eliminatedFallbackY)"),
                "K3/down eliminated terminal should align vertically with the next round stage lane when available");
        assertTrue(template.contains("const championCenterX = averageCoordinate(championNodes, 'x', group.stage.x)"),
                "K3/down champion terminal should be horizontally centered on winning matchup nodes");
        assertTrue(template.contains("const championTerminalY = terminalYBeyondMatchups(championNodes, group.stage.y)"),
                "K3/down champion terminal should sit below the winning matchup edge");
        assertTrue(template.contains("node.y = championTerminalY"));
    }

    @Test
    void horizontalChampionTerminalUsesWinningStackAndOutwardPlacement() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/path-visualization.html"));

        assertTrue(template.contains("const championNodes = group.advancing.filter(node => nodeLeadsToChampion(node, outgoing, nodeById))"),
                "Champion placement should only use matchups that directly win the final");
        assertTrue(template.contains("const championCenterY = averageCoordinate(championNodes, 'y', centerY)"),
                "Horizontal champion terminal should be centered on the winning matchup stack");
        assertTrue(template.contains("const championTerminalX = terminalXBeyondMatchups(championNodes, group.stage.x)"),
                "Horizontal champion terminal should sit beyond the title-winning matchups, not on the round spine");
        assertTrue(template.contains("championTargetsFor(championNodes, outgoing, nodeById).forEach(node =>"));
        assertTrue(template.contains("node.x = championTerminalX"));
        assertTrue(template.contains("node.y = championCenterY"));
    }

    @Test
    void downBranchChampionTerminalMirrorsEliminatedTerminal() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/path-visualization.html"));

        assertTrue(template.contains("placeOutcomeLane(group.advancing, { direction: -1, origin: group.stage.x, cursor: offset }, 'x')"),
                "K3/down branch winning matchups should fan to the left of the vertical round spine");
        assertTrue(template.contains("placeOutcomeLane(group.eliminated, { direction: 1, origin: group.stage.x, cursor: offset }, 'x')"),
                "K3/down branch losing matchups should fan to the right of the vertical round spine");
        assertTrue(template.contains("eliminatedTargetsFor(group.eliminated, outgoing, nodeById).forEach(node =>"));
        assertTrue(template.contains("const eliminatedCenterX = averageCoordinate(group.eliminated, 'x', group.stage.x)"));
        assertTrue(template.contains("const eliminatedTerminalY = continuationStageYFor(group.advancing, outgoing, nodeById, eliminatedFallbackY)"));
        assertTrue(template.contains("node.x = eliminatedCenterX"));
        assertTrue(template.contains("node.y = eliminatedTerminalY"));
        assertTrue(template.contains("championTargetsFor(championNodes, outgoing, nodeById).forEach(node =>"));
        assertTrue(template.contains("const championCenterX = averageCoordinate(championNodes, 'x', group.stage.x)"));
        assertTrue(template.contains("const championTerminalY = terminalYBeyondMatchups(championNodes, group.stage.y)"));
        assertTrue(template.contains("node.x = championCenterX"));
        assertTrue(template.contains("node.y = championTerminalY"));
    }

    @Test
    void terminalNodesStayCompactAndOutsideCollisionResolution() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/path-visualization.html"));

        assertTrue(template.contains("if (data.type === 'champion' || data.type === 'eliminated') return terminalNodeSize"),
                "Terminal nodes should use a compact dedicated size that fits word labels");
        assertTrue(template.contains("return data.type === 'champion' ? ['Champion 🏆'] : ['Eliminated']"),
                "Terminal nodes should render explicit Champion and Eliminated labels");
        assertTrue(template.contains("node.type !== 'eliminated' && node.type !== 'champion'"),
                "Terminal nodes should not be moved by the generic collision shifter after anchoring");
    }

    @Test
    void terminalTargetLookupDeduplicatesSharedStageTerminals() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/path-visualization.html"));

        assertTrue(template.contains("function terminalTargetsFor(nodes, outgoing, nodeById, terminalType)"));
        assertTrue(template.contains("const seen = new Set()"));
        assertTrue(template.contains("target?.type === terminalType && !seen.has(target.id)"),
                "Shared eliminated/champion stage terminals should be emitted once per stage, not once per matchup");
        assertTrue(template.contains("return terminalTargetsFor(nodes, outgoing, nodeById, 'eliminated')"));
        assertTrue(template.contains("return terminalTargetsFor(nodes, outgoing, nodeById, 'champion')"));
    }

    @Test
    void templateKeepsIntermediateStageEdgesStraightAndUnfanned() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/path-visualization.html"));

        assertTrue(template.contains("const stageTeamLink = touchesStage && ((source?.type === 'stage' && target?.type === 'team')"));
        assertTrue(template.contains("|| (source?.type === 'team' && target?.type === 'stage'))"));
        assertTrue(template.contains("if (stageTeamLink)"));
        assertTrue(template.contains("return linePath(edge.data.points || [])"));
        assertTrue(template.contains("if (touchesStage)"));
        assertTrue(template.contains("return linePath(centeredEdgePoints(edge, nodeById))"));
        assertTrue(template.contains("if (source?.type === 'stage' || target?.type === 'stage') return"));
        assertTrue(template.contains(".attr('d', edge => routedLinePath(edge, dagNodeById))"));
    }

}

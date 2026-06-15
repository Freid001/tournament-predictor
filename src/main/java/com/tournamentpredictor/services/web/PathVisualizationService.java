package com.tournamentpredictor.services.web;

import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PathVisualizationService {
    private static final int MAX_FOCUSED_ALT_ROUTES_PER_ROUND = 36;
    private final EloCalculator elo = new EloCalculator();

    public Graph build(List<RoundRows> rounds, String focusRound, String pathFilter, String teamFilter) {
        String safeTeam = WebText.trim(teamFilter);
        String safeFilter = normalizeFilter(pathFilter);
        if (!safeTeam.isBlank()) {
            return buildFocusedRoutes(rounds, focusRound, safeTeam, safeFilter);
        }

        Set<String> focusTeams = focusTeams(rounds, focusRound, pathFilter, teamFilter);
        Map<String, Node> nodes = new LinkedHashMap<>();
        Map<String, Edge> edges = new LinkedHashMap<>();
        for (RoundRows round : rounds) {
            for (Map<String, String> row : round.rows()) {
                String pathType = normalizePath(row.getOrDefault("path", ""));
                String team1 = teamName(row.getOrDefault("team1", ""));
                String team2 = teamName(row.getOrDefault("team2", ""));
                String winner = winnerName(row);
                if (!focusTeams.isEmpty() && !focusTeams.contains(team1) && !focusTeams.contains(team2)
                        && !focusTeams.contains(winner)) {
                    continue;
                }
                String matchId = row.getOrDefault("match_id", "").trim();
                if (matchId.isBlank() || team1.isBlank() || team2.isBlank()) {
                    continue;
                }
                String rawPath = row.getOrDefault("path", "").trim();
                String matchNodeId = "match:" + round.round() + ":" + matchId + ":" + rawPath + ":" + team1 + ":" + team2;
                nodes.putIfAbsent(teamNodeId(team1), teamNode(team1));
                nodes.putIfAbsent(teamNodeId(team2), teamNode(team2));
                nodes.putIfAbsent(matchNodeId, new Node(matchNodeId, matchId + "\n" + round.label(), "match", pathType, "", ""));
                addEdge(edges, teamNodeId(team1), matchNodeId, pathType, team1 + " to " + matchId);
                addEdge(edges, teamNodeId(team2), matchNodeId, pathType, team2 + " to " + matchId);
                if (!winner.isBlank() && !"Draw".equalsIgnoreCase(winner)) {
                    nodes.putIfAbsent(teamNodeId(winner), teamNode(winner));
                    addEdge(edges, matchNodeId, teamNodeId(winner), pathType, winner);
                }
            }
        }
        return new Graph(new ArrayList<>(nodes.values()), new ArrayList<>(edges.values()), focusTeams);
    }

    private Graph buildFocusedRoutes(List<RoundRows> rounds, String focusRound, String focusTeam, String filter) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        Map<String, Edge> edges = new LinkedHashMap<>();
        Set<String> focusTeams = new LinkedHashSet<>();
        focusTeams.add(focusTeam);
        nodes.putIfAbsent(teamNodeId(focusTeam), teamNode(focusTeam));

        boolean includeAlt = "all".equals(filter) || "alt".equals(filter);
        List<AltRoute> altRoutes = includeAlt ? alternativeRoutes(rounds, focusRound, focusTeam) : List.of();
        int split = Math.min(altRoutes.size(), Math.max(0, altRoutes.size() / 2));
        addAlternativeRoutes(focusTeam, altRoutes.subList(0, split), seedForTeamInRounds(rounds, focusTeam), rounds, nodes, edges);

        if ("all".equals(filter) || "results".equals(filter)) {
            traceRoute(rounds, focusTeam, "results", nodes, edges);
        }
        if ("all".equals(filter) || "prediction".equals(filter)) {
            traceRoute(rounds, focusTeam, "predicted", nodes, edges);
        }

        addAlternativeRoutes(focusTeam, altRoutes.subList(split, altRoutes.size()), seedForTeamInRounds(rounds, focusTeam), rounds, nodes, edges);
        return new Graph(new ArrayList<>(nodes.values()), new ArrayList<>(edges.values()), focusTeams);
    }

    private void traceRoute(List<RoundRows> rounds, String focusTeam, String routeType,
                            Map<String, Node> nodes, Map<String, Edge> edges) {
        String previousNodeId = teamNodeId(focusTeam);
        String currentTeam = focusTeam;
        boolean started = false;
        boolean seedAdded = false;
        boolean firstMatch = true;
        String branchKey = "";
        for (RoundRows round : rounds) {
            Map<String, String> row = routeRow(round.rows(), currentTeam, routeType);
            if (row == null) {
                if (started) {
                    break;
                }
                continue;
            }
            started = true;
            String team1 = teamName(row.getOrDefault("team1", ""));
            String team2 = teamName(row.getOrDefault("team2", ""));
            boolean currentIsTeam1 = currentTeam.equalsIgnoreCase(team1);
            String opponent = currentIsTeam1 ? team2 : team1;
            if (opponent.isBlank()) {
                break;
            }
            String pathType = normalizePath(row.getOrDefault("path", ""));
            if ("results".equals(routeType)) {
                pathType = "results";
            } else {
                pathType = "predicted";
            }
            String matchId = row.getOrDefault("match_id", "").trim();
            if (!seedAdded) {
                String seed = seedForTeam(row, currentTeam);
                if (seed.isBlank()) {
                    seed = seedForTeamInRounds(rounds, currentTeam);
                }
                seed = normalizeCompositeThirdPlaceSeed(seed, currentTeam, rounds);
                branchKey = seed;
                previousNodeId = addSeedNode(routeType, pathType, previousNodeId, seed, currentTeam, nodes, edges);
                seedAdded = true;
            }
            String parentNodeId = firstMatch
                    ? previousNodeId
                    : addStageNode(pathType, branchKey, round.label(), previousNodeId, nodes, edges);
            String opponentNodeId = routeNodeId(routeType, round.round(), matchId, opponent, parentNodeId);
            String winner = winnerName(row);
            boolean eliminated = !winner.isBlank() && !winner.equalsIgnoreCase(currentTeam) && !"Draw".equalsIgnoreCase(winner);
            boolean champion = isFinalRound(round.round()) && !winner.isBlank() && winner.equalsIgnoreCase(currentTeam);
            String label = opponentLabel(opponent, round.label(), matchId);
            nodes.putIfAbsent(opponentNodeId, new Node(opponentNodeId, label, "team", pathType, opponent, flagUrl(opponent)));
            addEdge(edges, parentNodeId, opponentNodeId, pathType, matchId.isBlank() ? round.label() : matchId);
            previousNodeId = opponentNodeId;
            firstMatch = false;
            if (champion) {
                addChampionNode(routeType, previousNodeId, nodes, edges);
                break;
            }
            if (eliminated) {
                addEliminatedNode(routeType, previousNodeId, nodes, edges);
                break;
            }
            if (winner.isBlank() && "results".equals(routeType)) {
                break;
            }
        }
    }

    private List<AltRoute> alternativeRoutes(List<RoundRows> rounds, String focusRound, String focusTeam) {
        List<AltRoute> routes = new ArrayList<>();
        int focusedRoundIndex = focusedRoundIndex(rounds, focusRound);
        if (focusedRoundIndex >= 0) {
            addRoundAlternativeCandidates(rounds.get(focusedRoundIndex), focusTeam, routes);
        }
        for (int i = 0; i < rounds.size(); i++) {
            if (i == focusedRoundIndex) {
                continue;
            }
            addRoundAlternativeCandidates(rounds.get(i), focusTeam, routes);
        }
        return routes;
    }

    private void addRoundAlternativeCandidates(RoundRows round, String focusTeam, List<AltRoute> routes) {
        List<AltRoute> roundRoutes = new ArrayList<>();
        addAlternativeCandidates(round, focusTeam, "alt", roundRoutes, MAX_FOCUSED_ALT_ROUTES_PER_ROUND);
        routes.addAll(roundRoutes);
    }

    private int focusedRoundIndex(List<RoundRows> rounds, String focusRound) {
        String safeFocusRound = WebText.trim(focusRound);
        if (safeFocusRound.isBlank()) {
            return -1;
        }
        for (int i = 0; i < rounds.size(); i++) {
            if (safeFocusRound.equalsIgnoreCase(rounds.get(i).round())) {
                return i;
            }
        }
        return -1;
    }

    private void addAlternativeCandidates(RoundRows round, String focusTeam, String rawPath, List<AltRoute> routes, int routeLimit) {
        for (Map<String, String> row : round.rows()) {
            if (routes.size() >= routeLimit) {
                return;
            }
            if (!rawPath.equalsIgnoreCase(WebText.trim(row.getOrDefault("path", "")))) {
                continue;
            }
            if (!"alt".equals(normalizePath(row.getOrDefault("path", "")))) {
                continue;
            }
            String team1 = teamName(row.getOrDefault("team1", ""));
            String team2 = teamName(row.getOrDefault("team2", ""));
            boolean focusIsTeam1 = focusTeam.equalsIgnoreCase(team1);
            boolean focusIsTeam2 = focusTeam.equalsIgnoreCase(team2);
            if (!focusIsTeam1 && !focusIsTeam2) {
                continue;
            }
            String opponent = focusIsTeam1 ? team2 : team1;
            String focusPathColumn = focusIsTeam1 ? row.getOrDefault("team1_path_opponent", "")
                    : row.getOrDefault("team2_path_opponent", "");
            List<RouteStep> focusSteps = priorSteps(routeSteps(focusPathColumn), row.getOrDefault("match_id", ""));
            if (!opponent.isBlank() && !hasRepeatedOpponent(focusSteps, opponent)) {
                routes.add(new AltRoute(round.label(), row, opponent, seedForTeam(row, focusTeam), focusSteps));
            }
        }
    }

    private boolean hasRepeatedOpponent(List<RouteStep> steps, String finalOpponent) {
        Set<String> opponents = new LinkedHashSet<>();
        for (RouteStep step : steps) {
            String opponent = WebText.trim(step.opponent()).toLowerCase(java.util.Locale.ROOT);
            if (!opponent.isBlank() && !opponents.add(opponent)) {
                return true;
            }
        }
        String opponent = WebText.trim(finalOpponent).toLowerCase(java.util.Locale.ROOT);
        return !opponent.isBlank() && !opponents.add(opponent);
    }

    private void addAlternativeRoutes(String focusTeam, List<AltRoute> routes, String fallbackSeed, List<RoundRows> rounds,
                                      Map<String, Node> nodes, Map<String, Edge> edges) {
        for (AltRoute route : routes) {
            String seed = route.focusSeed().isBlank() ? fallbackSeed : route.focusSeed();
            seed = normalizeCompositeThirdPlaceSeed(seed, focusTeam, rounds);
            addAlternativeRoute(rounds, route.roundLabel(), focusTeam, route.row(), route.opponent(), seed,
                    route.focusSteps(), route.hashCode(), nodes, edges);
        }
    }

    private void addAlternativeRoute(List<RoundRows> rounds, String roundLabel, String focusTeam, Map<String, String> row, String opponent,
                                     String focusSeed, List<RouteStep> focusSteps, int routeIndex,
                                     Map<String, Node> nodes, Map<String, Edge> edges) {
        String matchId = row.getOrDefault("match_id", "").trim();
        boolean finalRound = isFinalLabel(roundLabel);
        String focusRootId = addSeedNode("alt:" + routeIndex + ":focus", "alt", teamNodeId(focusTeam), focusSeed,
                focusTeam, nodes, edges);
        String focusPreviousNodeId = addAlternativeBranch("focus", routeIndex, focusRootId, focusSeed, focusSteps,
                nodes, edges);

        String targetParentNodeId = focusSteps.isEmpty()
                ? focusPreviousNodeId
                : addStageNode("alt", focusSeed, roundLabel, focusPreviousNodeId, nodes, edges);
        String targetNodeId = focusedTeamRoundNodeId(roundLabel, opponent, targetParentNodeId);
        Node targetNode = new Node(targetNodeId, opponentLabel(opponent, roundLabel, matchId), "team", "alt", opponent, flagUrl(opponent));
        nodes.putIfAbsent(targetNodeId, targetNode);
        addEdge(edges, targetParentNodeId, targetNodeId, "alt", matchId.isBlank() ? "Alt" : matchId);

        String winner = winnerName(row);
        boolean champion = finalRound && !winner.isBlank() && winner.equalsIgnoreCase(focusTeam);
        boolean eliminated = !winner.isBlank() && !winner.equalsIgnoreCase(focusTeam) && !"Draw".equalsIgnoreCase(winner);
        if (champion) {
            addChampionNode("alt", targetNodeId, nodes, edges);
        } else if (eliminated) {
            addEliminatedNode("alt", targetNodeId, nodes, edges);
        } else if (!winner.isBlank() && winner.equalsIgnoreCase(focusTeam)) {
            traceProjectedAlternativeContinuation(rounds, roundLabel, focusTeam, focusSeed, matchId, targetNodeId, nodes, edges);
        }
    }

    private void traceProjectedAlternativeContinuation(List<RoundRows> rounds, String afterRoundLabel, String focusTeam,
                                                       String branchKey, String feederMatchId, String previousNodeId,
                                                       Map<String, Node> nodes, Map<String, Edge> edges) {
        boolean afterCurrentRound = false;
        String feeder = WebText.trim(feederMatchId);
        for (RoundRows round : rounds) {
            if (!afterCurrentRound) {
                afterCurrentRound = round.label().equalsIgnoreCase(WebText.trim(afterRoundLabel));
                continue;
            }
            if (feeder.isBlank()) {
                return;
            }
            Map<String, String> row = routeRowForFeeder(round.rows(), focusTeam, feeder, branchKey, "predicted");
            if (row == null) {
                row = routeRowForFeeder(round.rows(), focusTeam, feeder, branchKey, "alt");
            }
            if (row == null) {
                continue;
            }
            String team1 = teamName(row.getOrDefault("team1", ""));
            String team2 = teamName(row.getOrDefault("team2", ""));
            boolean focusIsTeam1 = focusTeam.equalsIgnoreCase(team1);
            boolean focusIsTeam2 = focusTeam.equalsIgnoreCase(team2);
            if (!focusIsTeam1 && !focusIsTeam2) {
                continue;
            }
            String opponent = focusIsTeam1 ? team2 : team1;
            if (opponent.isBlank()) {
                continue;
            }

            String stageNodeId = addStageNode("alt", branchKey, round.label(), previousNodeId, nodes, edges);
            String matchId = row.getOrDefault("match_id", "").trim();
            String opponentNodeId = focusedTeamRoundNodeId(round.label(), opponent, stageNodeId);
            nodes.putIfAbsent(opponentNodeId, new Node(opponentNodeId, opponentLabel(opponent, round.label(), matchId),
                    "team", "alt", opponent, flagUrl(opponent)));
            addEdge(edges, stageNodeId, opponentNodeId, "alt", matchId.isBlank() ? round.label() : matchId);
            previousNodeId = opponentNodeId;

            String winner = winnerName(row);
            if (isFinalRound(round.round()) && winner.equalsIgnoreCase(focusTeam)) {
                addChampionNode("alt", previousNodeId, nodes, edges);
                return;
            }
            if (!winner.isBlank() && !winner.equalsIgnoreCase(focusTeam) && !"Draw".equalsIgnoreCase(winner)) {
                addEliminatedNode("alt", previousNodeId, nodes, edges);
                return;
            }
            feeder = matchId;
        }
    }


    private Map<String, String> routeRowForFeeder(List<Map<String, String>> rows, String team, String feederMatchId,
                                                  String branchKey, String routeType) {
        String feederToken = "W" + WebText.trim(feederMatchId).replaceFirst("^M", "");
        for (Map<String, String> row : rows) {
            String pathType = normalizePath(row.getOrDefault("path", ""));
            if (!routeType.equals(pathType)) {
                continue;
            }
            String team1 = teamName(row.getOrDefault("team1", ""));
            String team2 = teamName(row.getOrDefault("team2", ""));
            if (!team.equalsIgnoreCase(team1) && !team.equalsIgnoreCase(team2)) {
                continue;
            }
            if ((feederToken.equalsIgnoreCase(sourceMatchForTeam(row, "team1"))
                    || feederToken.equalsIgnoreCase(sourceMatchForTeam(row, "team2")))
                    && branchSeedMatches(row, team, branchKey)) {
                return row;
            }
        }
        return null;
    }


    private boolean branchSeedMatches(Map<String, String> row, String team, String branchKey) {
        String safeBranch = WebText.trim(branchKey);
        if (safeBranch.isBlank()) {
            return true;
        }
        String rowSeed = seedForTeam(row, team);
        return rowSeed.isBlank() || safeBranch.equalsIgnoreCase(rowSeed);
    }

    private String addStageNode(String pathType, String branchKey, String roundLabel, String previousNodeId,
                                Map<String, Node> nodes, Map<String, Edge> edges) {
        String safeRound = WebText.trim(roundLabel);
        if (safeRound.isBlank()) {
            return previousNodeId;
        }
        String safeBranch = WebText.trim(branchKey);
        if (safeBranch.isBlank()) {
            safeBranch = "selected";
        }
        String nodeId = "stage:" + safeBranch + ":" + safeRound;
        nodes.putIfAbsent(nodeId, new Node(nodeId, safeRound, "stage", pathType, "", ""));
        addEdge(edges, previousNodeId, nodeId, pathType, "");
        return nodeId;
    }

    private String addAlternativeBranch(String side, int routeIndex, String rootNodeId, String branchKey, List<RouteStep> steps,
                                        Map<String, Node> nodes, Map<String, Edge> edges) {
        String previousNodeId = rootNodeId;
        boolean firstStep = true;
        for (RouteStep step : steps) {
            if (step.opponent().isBlank()) {
                continue;
            }
            String roundLabel = roundLabelForMatchId(step.matchId());
            String parentNodeId = firstStep
                    ? previousNodeId
                    : addStageNode("alt", branchKey, roundLabel, previousNodeId, nodes, edges);
            String nodeId = focusedTeamRoundNodeId(roundLabel, step.opponent(), parentNodeId);
            String label = opponentLabel(step.opponent(), roundLabel, step.matchId());
            nodes.putIfAbsent(nodeId, new Node(nodeId, label, "team", "alt", step.opponent(), flagUrl(step.opponent())));
            addEdge(edges, parentNodeId, nodeId, "alt", step.matchId().isBlank() ? "Alt" : step.matchId());
            previousNodeId = nodeId;
            firstStep = false;
        }
        return previousNodeId;
    }

    private List<RouteStep> priorSteps(List<RouteStep> steps, String matchId) {
        String safeMatchId = WebText.trim(matchId);
        if (safeMatchId.isBlank() || steps.isEmpty()) {
            return steps;
        }
        List<RouteStep> prior = new ArrayList<>();
        for (RouteStep step : steps) {
            if (safeMatchId.equalsIgnoreCase(step.matchId())) {
                break;
            }
            prior.add(step);
        }
        return prior;
    }

    private String matchLabel(String matchId, String roundLabel) {
        return matchLabel(matchId, roundLabel, "");
    }

    private String matchLabel(String matchId, String roundLabel, String opponent) {
        String safeMatchId = WebText.trim(matchId);
        String safeRoundLabel = WebText.trim(roundLabel);
        String safeOpponent = WebText.trim(opponent);
        StringBuilder label = new StringBuilder();
        if (!safeMatchId.isBlank()) {
            label.append(safeMatchId);
        }
        if (!safeRoundLabel.isBlank()) {
            if (!label.isEmpty()) label.append("\n");
            label.append(safeRoundLabel);
        }
        if (!safeOpponent.isBlank()) {
            if (!label.isEmpty()) label.append("\n");
            label.append("vs ").append(safeOpponent);
        }
        return label.toString();
    }

    private String addSeedNode(String routeType, String pathType, String previousNodeId, String seed, String team,
                               Map<String, Node> nodes, Map<String, Edge> edges) {
        String safeSeed = WebText.trim(seed);
        if (safeSeed.isBlank()) {
            return previousNodeId;
        }
        String nodeId = "seed:" + team + ":" + safeSeed;
        nodes.putIfAbsent(nodeId, new Node(nodeId, safeSeed, "seed", pathType, team, ""));
        addEdge(edges, previousNodeId, nodeId, pathType, "Group finish");
        return nodeId;
    }

    private String normalizeCompositeThirdPlaceSeed(String seed, String team, List<RoundRows> rounds) {
        String safeSeed = WebText.trim(seed);
        if (!isCompositeThirdPlaceSeed(safeSeed)) {
            return safeSeed;
        }
        String simpleSeed = simpleThirdPlaceSeedForTeamInRounds(rounds, team);
        return simpleSeed.isBlank() ? safeSeed : simpleSeed;
    }

    private boolean isCompositeThirdPlaceSeed(String seed) {
        String safeSeed = WebText.trim(seed);
        return safeSeed.length() > 2 && safeSeed.endsWith("3");
    }

    private String simpleThirdPlaceSeedForTeamInRounds(List<RoundRows> rounds, String team) {
        for (RoundRows round : rounds) {
            for (Map<String, String> row : round.rows()) {
                String seed = seedForTeam(row, team);
                if (isSimpleThirdPlaceSeed(seed)) return seed;
            }
        }
        return "";
    }

    private boolean isSimpleThirdPlaceSeed(String seed) {
        String safeSeed = WebText.trim(seed);
        return safeSeed.length() == 2 && Character.isLetter(safeSeed.charAt(0)) && safeSeed.endsWith("3");
    }

    private String seedForTeam(Map<String, String> row, String team) {
        String safeTeam = WebText.trim(team);
        if (safeTeam.isBlank()) {
            return "";
        }
        if (safeTeam.equalsIgnoreCase(teamName(row.getOrDefault("team1", "")))) {
            return structuredSeed(row, "team1");
        }
        if (safeTeam.equalsIgnoreCase(teamName(row.getOrDefault("team2", "")))) {
            return structuredSeed(row, "team2");
        }
        return "";
    }

    private String structuredSeed(Map<String, String> row, String prefix) {
        String bracketSlot = WebText.trim(row.getOrDefault(prefix + "_bracket_slot", ""));
        if (!bracketSlot.isBlank()) {
            return bracketSlot;
        }
        String groupFinish = WebText.trim(row.getOrDefault(prefix + "_group_finish", ""));
        if (!groupFinish.isBlank()) {
            return groupFinish;
        }
        return WebText.trim(row.getOrDefault(prefix + "_slot", ""));
    }

    private String sourceMatchForTeam(Map<String, String> row, String prefix) {
        String source = WebText.trim(row.getOrDefault(prefix + "_source_match", ""));
        if (source.startsWith("M")) {
            return "W" + source.substring(1);
        }
        if (source.startsWith("W")) {
            return source;
        }
        return "";
    }

    private String seedForTeamInRounds(List<RoundRows> rounds, String team) {
        for (RoundRows round : rounds) {
            String seed = seedForTeamInRows(round.rows(), team);
            if (!seed.isBlank()) {
                return seed;
            }
        }
        return "";
    }

    private String seedForTeamInRows(List<Map<String, String>> rows, String team) {
        for (Map<String, String> row : rows) {
            String seed = seedForTeam(row, team);
            if (!seed.isBlank()) {
                return seed;
            }
        }
        return "";
    }

    private List<RouteStep> routeSteps(String pathColumn) {
        List<RouteStep> steps = new ArrayList<>();
        String value = WebText.trim(pathColumn);
        if (value.isBlank() || value.equalsIgnoreCase("Group stage")) {
            return steps;
        }
        for (String rawPart : value.split(" > ")) {
            String part = rawPart.trim();
            int at = part.indexOf('@');
            int pipe = part.indexOf('|', at + 1);
            if (at < 0 || pipe < 0) {
                continue;
            }
            String matchId = part.substring(at + 1, pipe).trim();
            String opponentPart = part.substring(pipe + 1).trim();
            int colon = opponentPart.lastIndexOf(':');
            String opponent = colon >= 0 ? opponentPart.substring(0, colon).trim() : opponentPart;
            if (!matchId.isBlank() && !opponent.isBlank()) {
                steps.add(new RouteStep(matchId, opponent));
            }
        }
        return steps;
    }

    private Map<String, String> routeRow(List<Map<String, String>> rows, String team, String routeType) {
        for (Map<String, String> row : rows) {
            String pathType = normalizePath(row.getOrDefault("path", ""));
            if (!routeType.equals(pathType)) {
                continue;
            }
            String team1 = teamName(row.getOrDefault("team1", ""));
            String team2 = teamName(row.getOrDefault("team2", ""));
            if (team.equalsIgnoreCase(team1) || team.equalsIgnoreCase(team2)) {
                return row;
            }
        }
        return null;
    }

    private String routeNodeId(String routeType, String round, String matchId, String opponent, String previousNodeId) {
        return focusedTeamRoundNodeId(displayRoundForKey(round, matchId), opponent, previousNodeId);
    }

    private String focusedTeamRoundNodeId(String roundLabel, String opponent, String previousNodeId) {
        return "route:" + WebText.trim(roundLabel) + ":" + opponent + ":from:" + previousNodeId;
    }

    private String displayRoundForKey(String round, String matchId) {
        String safeRound = WebText.trim(round);
        if (!safeRound.isBlank()) {
            return switch (safeRound) {
                case "last_32_match" -> "Last 32";
                case "last_16_match" -> "Last 16";
                case "last_8_match" -> "Quarter Finals";
                case "last_4_match" -> "Semi Finals";
                case "final_match" -> "Final";
                default -> roundLabelForMatchId(matchId);
            };
        }
        return roundLabelForMatchId(matchId);
    }

    private String roundLabelForMatchId(String matchId) {
        String safeMatchId = WebText.trim(matchId);
        if (!safeMatchId.startsWith("M")) {
            return "";
        }
        try {
            int number = Integer.parseInt(safeMatchId.substring(1));
            if (number >= 49 && number <= 56) return "Last 16";
            if (number >= 57 && number <= 60) return "Quarter Finals";
            if (number >= 61 && number <= 62) return "Semi Finals";
            if (number == 63) return "Final";
            if (number >= 73 && number <= 88) return "Last 32";
            if (number >= 89 && number <= 96) return "Last 16";
            if (number >= 97 && number <= 100) return "Quarter Finals";
            if (number >= 101 && number <= 102) return "Semi Finals";
            if (number == 103) return "Final";
        } catch (NumberFormatException ignored) {
            return "";
        }
        return "";
    }

    private String opponentLabel(String opponent, String roundLabel, String matchId) {
        StringBuilder label = new StringBuilder(opponent);
        if (roundLabel != null && !roundLabel.isBlank()) {
            label.append("\n").append(roundLabel);
        }
        if (matchId != null && !matchId.isBlank()) {
            label.append(" ").append(matchId);
        }
        return label.toString();
    }

    private String addChampionNode(String routeType, String previousNodeId, Map<String, Node> nodes, Map<String, Edge> edges) {
        String nodeId = previousNodeId + ":champion";
        nodes.putIfAbsent(nodeId, new Node(nodeId, "Champions 🏆", "champion", routeType, "", ""));
        addEdge(edges, previousNodeId, nodeId, routeType, "Champion");
        return nodeId;
    }

    private String addEliminatedNode(String routeType, String previousNodeId, Map<String, Node> nodes, Map<String, Edge> edges) {
        String nodeId = previousNodeId + ":eliminated";
        nodes.putIfAbsent(nodeId, new Node(nodeId, "Eliminated ❌", "eliminated", routeType, "", ""));
        addEdge(edges, previousNodeId, nodeId, routeType, "Eliminated");
        return nodeId;
    }

    private boolean isFinalRound(String round) {
        return WebText.trim(round).equalsIgnoreCase("final_match");
    }

    private boolean isFinalLabel(String label) {
        return WebText.trim(label).equalsIgnoreCase("Final");
    }

    private Set<String> focusTeams(List<RoundRows> rounds, String focusRound, String pathFilter, String teamFilter) {
        String safePath = normalizeFilter(pathFilter);
        String safeTeam = WebText.trim(teamFilter);
        Set<String> teams = new LinkedHashSet<>();
        if (!safeTeam.isBlank()) {
            teams.add(safeTeam);
            return teams;
        }
        for (RoundRows round : rounds) {
            if (!round.round().equalsIgnoreCase(focusRound)) {
                continue;
            }
            for (Map<String, String> row : round.rows()) {
                String team1 = teamName(row.getOrDefault("team1", ""));
                String team2 = teamName(row.getOrDefault("team2", ""));
                if (!pathMatches(row.getOrDefault("path", ""), safePath)) {
                    continue;
                }
                if (!safeTeam.isBlank() && !safeTeam.equalsIgnoreCase(team1) && !safeTeam.equalsIgnoreCase(team2)) {
                    continue;
                }
                if (!team1.isBlank()) teams.add(team1);
                if (!team2.isBlank()) teams.add(team2);
            }
        }
        return teams;
    }

    private boolean pathMatches(String rowPath, String filter) {
        String path = normalizePath(rowPath);
        if ("all".equals(filter)) return true;
        if ("results".equals(filter)) return "results".equals(path);
        if ("prediction".equals(filter)) return "predicted".equals(path);
        if ("alt".equals(filter)) return "alt".equals(path);
        return true;
    }

    private String normalizeFilter(String filter) {
        String value = WebText.trim(filter).toLowerCase(java.util.Locale.ROOT);
        if (value.equals("predicted")) return "prediction";
        if (value.equals("upset")) return "alt";
        if (Set.of("all", "results", "prediction", "alt").contains(value)) return value;
        return "all";
    }

    private String normalizePath(String path) {
        String value = WebText.trim(path).toLowerCase(java.util.Locale.ROOT);
        if (value.equals("results") || value.equals("result") || value.equals("fixture") || value.equals("actual") || value.equals("result_upset")) return "results";
        if (value.equals("predicted") || value.equals("live")) return "predicted";
        if (value.equals("alt") || value.equals("upset")) return "alt";
        return value.isBlank() ? "alt" : value;
    }

    private String teamName(String value) {
        return elo.extractTeamName(value);
    }

    private String winnerName(Map<String, String> row) {
        String winner = elo.parseTeamFromPrediction(row.getOrDefault("prediction", ""));
        if (winner.isBlank()) winner = elo.parseTeamFromPrediction(row.getOrDefault("elo", ""));
        return winner;
    }

    private Node teamNode(String team) {
        return new Node(teamNodeId(team), team, "team", "", team, flagUrl(team));
    }

    private String flagUrl(String team) {
        String code = HtmlReporter.isoCodeForTeam(team);
        if (code == null || code.isBlank()) {
            return "";
        }
        return "/vendor/flag-icons/flags/4x3/" + code + ".svg";
    }

    private String teamNodeId(String team) {
        return "team:" + team;
    }

    private void addEdge(Map<String, Edge> edges, String source, String target, String pathType, String label) {
        String id = source + "->" + target;
        Edge existing = edges.get(id);
        Edge candidate = new Edge(id, source, target, pathType, label);
        if (existing == null || edgePriority(pathType) > edgePriority(existing.path())) {
            edges.put(id, candidate);
        }
    }

    private int edgePriority(String pathType) {
        return switch (WebText.trim(pathType)) {
            case "results" -> 3;
            case "predicted" -> 2;
            case "alt" -> 1;
            default -> 0;
        };
    }

    public String toJson(Graph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"nodes\":[");
        for (int i = 0; i < graph.nodes().size(); i++) {
            Node n = graph.nodes().get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"data\":{")
                    .append(jsonProp("id", n.id())).append(',')
                    .append(jsonProp("label", n.label())).append(',')
                    .append(jsonProp("type", n.type())).append(',')
                    .append(jsonProp("path", n.path())).append(',')
                    .append(jsonProp("team", n.team())).append(',')
                    .append(jsonProp("flagUrl", n.flagUrl())).append(',')
                    .append(jsonProp("likelihood", n.likelihood())).append(',')
                    .append(jsonProp("matchupPct", n.matchupPct())).append(',')
                    .append(jsonProp("opponentPath", n.opponentPath())).append(',')
                    .append(jsonProp("opponentSeed", n.opponentSeed())).append(',')
                    .append(jsonProp("roundLabel", n.roundLabel()))
                    .append("}}");
        }
        sb.append("],\"edges\":[");
        for (int i = 0; i < graph.edges().size(); i++) {
            Edge e = graph.edges().get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"data\":{")
                    .append(jsonProp("id", e.id())).append(',')
                    .append(jsonProp("source", e.source())).append(',')
                    .append(jsonProp("target", e.target())).append(',')
                    .append(jsonProp("path", e.path())).append(',')
                    .append(jsonProp("label", e.label()))
                    .append("}}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String jsonProp(String key, String value) {
        return "\"" + jsonEscape(key) + "\":\"" + jsonEscape(value == null ? "" : value) + "\"";
    }

    private String jsonEscape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    public record RoundRows(String round, String label, List<Map<String, String>> rows) {}
    public record Graph(List<Node> nodes, List<Edge> edges, Set<String> focusTeams) {}
    public record Node(String id, String label, String type, String path, String team, String flagUrl,
                       String likelihood, String matchupPct, String opponentPath, String opponentSeed, String roundLabel) {
        public Node(String id, String label, String type, String path, String team, String flagUrl) {
            this(id, label, type, path, team, flagUrl, "medium", "", "", "", "");
        }

        public Node(String id, String label, String type, String path, String team, String flagUrl,
                    String likelihood, String matchupPct) {
            this(id, label, type, path, team, flagUrl, likelihood, matchupPct, "", "", "");
        }

        public Node(String id, String label, String type, String path, String team, String flagUrl,
                    String likelihood, String matchupPct, String opponentPath) {
            this(id, label, type, path, team, flagUrl, likelihood, matchupPct, opponentPath, "", "");
        }

        public Node(String id, String label, String type, String path, String team, String flagUrl,
                    String likelihood, String matchupPct, String opponentPath, String opponentSeed) {
            this(id, label, type, path, team, flagUrl, likelihood, matchupPct, opponentPath, opponentSeed, "");
        }
    }
    public record Edge(String id, String source, String target, String path, String label) {}
    private record AltRoute(String roundLabel, Map<String, String> row, String opponent, String focusSeed,
                            List<RouteStep> focusSteps) {}
    private record RouteStep(String matchId, String opponent) {
        boolean same(String otherMatchId, String otherOpponent) {
            return matchId.equalsIgnoreCase(WebText.trim(otherMatchId))
                    && opponent.equalsIgnoreCase(WebText.trim(otherOpponent));
        }
    }
}

package com.tournamentpredictor.services.web;

import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.report.HtmlReporter;
import com.tournamentpredictor.services.route.RoutePathValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RouteInstanceService {
    private static final String HEADER = "route_id,parent_route_id,round,match_id,team,seed,opponent,opponent_seed,path,winner,advanced,source_match_id,matchup_pct,likelihood,path_history,opponent_path";

    private final EloCalculator elo = new EloCalculator();


    public PathVisualizationService.Graph buildGraph(List<Map<String, String>> routeRows, String teamFilter, String pathFilter) {
        String focusTeam = WebText.trim(teamFilter);
        if (focusTeam.isBlank()) {
            return new PathVisualizationService.Graph(List.of(), List.of(), Set.of());
        }
        String filter = normalizeFilter(pathFilter);
        Map<String, PathVisualizationService.Node> nodes = new LinkedHashMap<>();
        Map<String, PathVisualizationService.Edge> edges = new LinkedHashMap<>();
        Map<String, String> routeNodeIds = new LinkedHashMap<>();
        putNode(nodes, teamNode(focusTeam));
        for (Map<String, String> row : routeRows) {
            String team = WebText.trim(row.getOrDefault("team", ""));
            if (!focusTeam.equalsIgnoreCase(team)) {
                continue;
            }
            String path = visualPath(row);
            if (!pathMatches(path, filter)) {
                continue;
            }
            String routeId = WebText.trim(row.getOrDefault("route_id", ""));
            String parentRouteId = WebText.trim(row.getOrDefault("parent_route_id", ""));
            String seed = WebText.trim(row.getOrDefault("seed", ""));
            String opponent = WebText.trim(row.getOrDefault("opponent", ""));
            String round = displayRound(row.getOrDefault("round", ""));
            String matchId = WebText.trim(row.getOrDefault("match_id", ""));
            boolean advanced = Boolean.parseBoolean(WebText.trim(row.getOrDefault("advanced", "")));
            String opponentPath = WebText.trim(row.getOrDefault("opponent_path", ""));
            String pathHistory = WebText.trim(row.getOrDefault("path_history", ""));
            if (routeId.isBlank() || opponent.isBlank() || RoutePathValidator.hasRepeatedPathTeam(pathHistory)
                    || RoutePathValidator.hasRepeatedPathTeam(opponentPath)) {
                continue;
            }
            String parentNodeId;
            if (parentRouteId.isBlank()) {
                parentNodeId = seedNodeId(focusTeam, seed);
                putNode(nodes, new PathVisualizationService.Node(parentNodeId, seed, "seed", path, focusTeam, ""));
                addEdge(edges, teamNodeId(focusTeam), parentNodeId, path, "");
            } else {
                parentNodeId = routeNodeIds.getOrDefault(parentRouteId, routeNodeId(parentRouteId));
            }
            String stageNodeId = routeStageNodeId(focusTeam, seed, parentRouteId, routeId,
                    WebText.trim(row.getOrDefault("source_match_id", "")), round);
            putNode(nodes, new PathVisualizationService.Node(stageNodeId, round, "stage", path, "", ""));
            addEdge(edges, parentNodeId, stageNodeId, path, "");

            String nodeId = routeVisualNodeId(focusTeam, seed, WebText.trim(row.getOrDefault("source_match_id", "")),
                    matchId, opponent, advanced);
            routeNodeIds.put(routeId, nodeId);
            String matchupPct = WebText.trim(row.getOrDefault("matchup_pct", ""));
            String likelihood = likelihoodLevel(matchupPct, path);
            String nodeLabel = matchId.isBlank() ? opponent : matchId;
            String opponentSeed = WebText.trim(row.getOrDefault("opponent_seed", ""));
            putNode(nodes, new PathVisualizationService.Node(nodeId, nodeLabel,
                    "team", path, opponent, flagUrl(opponent), likelihood, matchupPct, opponentPath, opponentSeed, round));
            addEdge(edges, stageNodeId, nodeId, path, "");
            if (!advanced) {
                String eliminatedId = stageNodeId + ":eliminated";
                putNode(nodes, new PathVisualizationService.Node(eliminatedId, "Eliminated ❌", "eliminated", path, "", ""));
                addEdge(edges, nodeId, eliminatedId, path, "");
            } else if ("Final".equals(round)) {
                String championId = stageNodeId + ":champion";
                putNode(nodes, new PathVisualizationService.Node(championId, "Champions 🏆", "champion", path, "", ""));
                addEdge(edges, nodeId, championId, path, "");
            }
        }
        return new PathVisualizationService.Graph(new ArrayList<>(nodes.values()), new ArrayList<>(edges.values()), Set.of(focusTeam));
    }

    public List<String> buildLines(List<PathVisualizationService.RoundRows> rounds) {
        return buildLines(rounds, "");
    }

    public List<String> buildLines(List<PathVisualizationService.RoundRows> rounds, String teamFilter) {
        List<String> out = new ArrayList<>();
        out.add(HEADER);
        Map<String, List<RouteInstance>> advancedByMatchTeam = new LinkedHashMap<>();
        Set<String> emitted = new java.util.LinkedHashSet<>();
        String focusTeam = WebText.trim(teamFilter);
        for (PathVisualizationService.RoundRows round : rounds) {
            for (Map<String, String> row : round.rows()) {
                addPerspective(round, row, true, focusTeam, advancedByMatchTeam, emitted, out);
                addPerspective(round, row, false, focusTeam, advancedByMatchTeam, emitted, out);
            }
        }
        return out;
    }

    private void addPerspective(PathVisualizationService.RoundRows round, Map<String, String> row, boolean team1Perspective,
                                String focusTeam, Map<String, List<RouteInstance>> advancedByMatchTeam,
                                Set<String> emitted, List<String> out) {
        String matchId = WebText.trim(row.getOrDefault("match_id", ""));
        String teamPrefix = team1Perspective ? "team1" : "team2";
        String opponentPrefix = team1Perspective ? "team2" : "team1";
        String teamExpr = row.getOrDefault(teamPrefix, "");
        String opponentExpr = row.getOrDefault(opponentPrefix, "");
        String team = routeValue(row, teamPrefix + "_team", elo.extractTeamName(teamExpr));
        String opponent = routeValue(row, opponentPrefix + "_team", elo.extractTeamName(opponentExpr));
        String sourceMatchId = routeValue(row, teamPrefix + "_source_match", "");
        boolean synthesizedFocusTeam = false;
        if (!focusTeam.isBlank() && !focusTeam.equalsIgnoreCase(team)) {
            if (sourceMatchId.isBlank() || parentRoutes(sourceMatchId, focusTeam, advancedByMatchTeam).isEmpty()) {
                return;
            }
            team = focusTeam;
            synthesizedFocusTeam = true;
        }
        if (matchId.isBlank() || team.isBlank() || opponent.isBlank()) {
            return;
        }
        String winner = elo.parseTeamFromPrediction(row.getOrDefault("prediction", ""));
        if (winner.isBlank() || "Draw".equalsIgnoreCase(winner)) {
            return;
        }
        if (synthesizedFocusTeam && !winner.equalsIgnoreCase(team) && !winner.equalsIgnoreCase(opponent)) {
            winner = opponent;
        }
        boolean advanced = winner.equalsIgnoreCase(team);
        boolean synthesizeAlternativeAdvance = !focusTeam.isBlank() && focusTeam.equalsIgnoreCase(team) && !advanced;
        String seed = routeValue(row, teamPrefix + "_bracket_slot",
                routeValue(row, teamPrefix + "_bracket_seed",
                        routeValue(row, teamPrefix + "_group_finish", "")));
        String opponentSeed = routeValue(row, opponentPrefix + "_bracket_slot",
                routeValue(row, opponentPrefix + "_bracket_seed",
                        routeValue(row, opponentPrefix + "_group_finish", "")));
        String path = normalizePath(row.getOrDefault("path", ""));
        String matchupPct = WebText.trim(row.getOrDefault("matchup_pct", ""));
        String likelihood = likelihoodLevel(matchupPct, path);
        String opponentPath = team1Perspective ? row.getOrDefault("team2_path_opponent", "")
                : row.getOrDefault("team1_path_opponent", "");
        List<RouteInstance> parents = parentRoutes(sourceMatchId, team, advancedByMatchTeam);
        for (RouteInstance parent : parents) {
            String parentId = parent == null ? "" : parent.routeId();
            String safeSeed = parent != null && !parent.seed().isBlank() ? parent.seed() : seed;
            String step = matchId + "|" + opponent + "|" + path + "|" + (advanced ? "W" : "L");
            String history = parent == null || parent.pathHistory().isBlank() ? safeSeed + ":" + step : parent.pathHistory() + " > " + step;
            if (RoutePathValidator.hasRepeatedPathTeam(history) || RoutePathValidator.hasRepeatedPathTeam(opponentPath)) {
                continue;
            }
            String cumulativePath = cumulativePath(parent == null ? "" : parent.pathHistory(), path);
            String routeId = compactRouteId(team, safeSeed, sourceMatchId, matchId, opponent, cumulativePath, advanced);
            RouteInstance instance = new RouteInstance(routeId, safeSeed, history);
            String emitKey = parentId + "|" + routeId + "|" + matchId + "|" + cumulativePath;
            if (emitted.add(emitKey)) {
                out.add(csv(routeId, parentId, round.round(), matchId, team, safeSeed, opponent, opponentSeed, path, winner,
                        String.valueOf(advanced), sourceMatchId, matchupPct, likelihood, history, opponentPath));
            }
            if (advanced) {
                addAdvancedRoute(advancedByMatchTeam, matchId, team, instance);
            } else if (synthesizeAlternativeAdvance) {
                String altHistory = parent == null || parent.pathHistory().isBlank()
                        ? safeSeed + ":" + matchId + "|" + opponent + "|alt|W"
                        : parent.pathHistory() + " > " + matchId + "|" + opponent + "|alt|W";
                if (!RoutePathValidator.hasRepeatedPathTeam(altHistory)
                        && !RoutePathValidator.hasRepeatedPathTeam(opponentPath)) {
                    String altRouteId = compactRouteId(team, safeSeed, sourceMatchId, matchId, opponent,
                            cumulativePath(parent == null ? "" : parent.pathHistory(), "alt"), true);
                    addAdvancedRoute(advancedByMatchTeam, matchId, team, new RouteInstance(altRouteId, safeSeed, altHistory));
                }
            }
        }
    }

    private void addAdvancedRoute(Map<String, List<RouteInstance>> advancedByMatchTeam, String matchId, String team,
                                  RouteInstance instance) {
        String key = matchId + "|" + team.toLowerCase(java.util.Locale.ROOT);
        List<RouteInstance> routes = advancedByMatchTeam.computeIfAbsent(key, ignored -> new ArrayList<>());
        boolean exists = routes.stream().anyMatch(route -> route.routeId().equals(instance.routeId()));
        if (!exists) {
            routes.add(instance);
        }
    }

    private List<RouteInstance> parentRoutes(String sourceMatchId, String team, Map<String, List<RouteInstance>> advancedByMatchTeam) {
        if (sourceMatchId.isBlank()) {
            List<RouteInstance> roots = new ArrayList<>();
            roots.add(null);
            return roots;
        }
        List<RouteInstance> parents = advancedByMatchTeam.get(sourceMatchId + "|" + team.toLowerCase(java.util.Locale.ROOT));
        return parents == null || parents.isEmpty() ? List.of() : parents;
    }



    private String likelihoodLevel(String matchupPct, String path) {
        double pct = parsePct(matchupPct);
        if (!Double.isNaN(pct)) {
            if (pct < 20.0) return "very-small";
            if (pct < 40.0) return "small";
            if (pct < 60.0) return "medium";
            if (pct < 80.0) return "large";
            return "very-large";
        }
        String safePath = WebText.trim(path);
        if ("results".equals(safePath) || "predicted".equals(safePath)) return "very-large";
        return "very-small";
    }

    private double parsePct(String value) {
        String safe = WebText.trim(value).replace("%", "");
        if (safe.isBlank()) return Double.NaN;
        try {
            return Double.parseDouble(safe);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private boolean pathMatches(String path, String filter) {
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

    private String displayRound(String round) {
        return switch (WebText.trim(round)) {
            case "last_32_match", "last_32" -> "Last 32";
            case "last_16_match", "last_16" -> "Last 16";
            case "last_8_match", "last_8" -> "Quarter Finals";
            case "last_4_match", "last_4" -> "Semi Finals";
            case "final_match", "final" -> "Final";
            default -> WebText.trim(round);
        };
    }

    private PathVisualizationService.Node teamNode(String team) {
        return new PathVisualizationService.Node(teamNodeId(team), team, "team", "", team, flagUrl(team));
    }

    private String teamNodeId(String team) {
        return "team:" + team;
    }

    private String seedNodeId(String team, String seed) {
        return "seed:" + team + ":" + seed;
    }


    private String routeStageNodeId(String team, String seed, String parentRouteId, String routeId,
                                    String sourceMatchId, String round) {
        String safeTeam = safeId(team);
        String safeSeed = safeId(seed);
        String safeRound = safeId(round);
        String parent = WebText.trim(parentRouteId);
        if (parent.isBlank()) {
            return "stage-instance:" + safeTeam + ":" + safeSeed + ":root:" + safeRound;
        }
        String source = safeId(sourceMatchId);
        if (source.isBlank()) {
            source = safeId(routeId);
        }
        return "stage-instance:" + safeTeam + ":" + safeSeed + ":" + source + ":" + safeRound;
    }

    private String routeNodeId(String routeId) {
        return "route-instance:" + routeId;
    }

    private String routeVisualNodeId(String team, String seed, String sourceMatchId, String matchId,
                                     String opponent, boolean advanced) {
        String source = safeId(sourceMatchId);
        if (source.isBlank()) {
            source = "root";
        }
        return "route-instance:" + safeId(team) + ":" + safeId(seed) + ":" + source + ":"
                + safeId(matchId) + ":" + safeId(opponent) + ":" + (advanced ? "w" : "l");
    }

    private void putNode(Map<String, PathVisualizationService.Node> nodes, PathVisualizationService.Node candidate) {
        PathVisualizationService.Node existing = nodes.get(candidate.id());
        if (existing == null || edgePriority(candidate.path()) > edgePriority(existing.path())) {
            nodes.put(candidate.id(), candidate);
        }
    }

    private String safeId(String value) {
        return WebText.trim(value).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String flagUrl(String team) {
        String code = HtmlReporter.isoCodeForTeam(team);
        return code == null || code.isBlank() ? "" : "/vendor/flag-icons/flags/4x3/" + code + ".svg";
    }

    private void addEdge(Map<String, PathVisualizationService.Edge> edges, String source, String target, String path, String label) {
        String id = source + "->" + target;
        PathVisualizationService.Edge existing = edges.get(id);
        PathVisualizationService.Edge candidate = new PathVisualizationService.Edge(id, source, target, path, label);
        if (existing == null || edgePriority(path) > edgePriority(existing.path())) {
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

    private String routeValue(Map<String, String> row, String column, String fallback) {
        String value = WebText.trim(row.getOrDefault(column, ""));
        return value.isBlank() ? fallback : value;
    }


    private String compactRouteId(String team, String seed, String sourceMatchId, String matchId, String opponent,
                                  String path, boolean advanced) {
        String source = WebText.trim(sourceMatchId).isBlank() ? "root" : sourceMatchId;
        String raw = team + "|" + seed + "|" + source + "|" + matchId + "|" + opponent + "|" + path + "|" + (advanced ? "w" : "l");
        String normalized = raw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return normalized.replaceAll("^_+|_+$", "");
    }

    private String cumulativePath(String priorHistory, String currentPath) {
        String history = WebText.trim(priorHistory).toLowerCase(java.util.Locale.ROOT);
        if (history.contains("|result|") || history.contains("|results|") || history.contains("|fixture|")) {
            return "results";
        }
        if (history.contains("|alt|") || history.contains("|upset|")) {
            return "alt";
        }
        return normalizePath(currentPath);
    }

    private String visualPath(Map<String, String> row) {
        String path = normalizePath(row.getOrDefault("path", ""));
        String history = WebText.trim(row.getOrDefault("path_history", "")).toLowerCase(java.util.Locale.ROOT);
        if (history.contains("|result|") || history.contains("|results|") || history.contains("|fixture|")) {
            return "results";
        }
        if (history.contains("|alt|") || history.contains("|upset|")) {
            return "alt";
        }
        return path;
    }

    private String normalizePath(String path) {
        String value = WebText.trim(path).toLowerCase(java.util.Locale.ROOT);
        if (value.equals("results") || value.equals("result") || value.equals("fixture") || value.equals("actual") || value.equals("result_upset")) return "results";
        if (value.equals("predicted") || value.equals("live")) return "predicted";
        if (value.equals("upset")) return "alt";
        return value.isBlank() ? "alt" : value;
    }

    private String csv(String... values) {
        List<String> escaped = new ArrayList<>();
        for (String value : values) {
            escaped.add(escape(value));
        }
        return String.join(",", escaped);
    }

    private String escape(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private record RouteInstance(String routeId, String seed, String pathHistory) {}
}

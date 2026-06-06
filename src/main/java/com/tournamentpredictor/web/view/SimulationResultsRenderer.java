package com.tournamentpredictor.web.view;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.tournamentpredictor.service.util.HtmlReporter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class SimulationResultsRenderer {
    private static final int TOP_CHAMPION_COUNT = 8;
    private static final int TOP_PATH_COUNT = 30;
    private static final int TOP_SCORELINE_COUNT = 30;

    private SimulationResultsRenderer() {
    }

    public static String render(List<Map<String, String>> rows) {
        return render(rows, List.of(), List.of(), "", "");
    }

    public static String render(List<Map<String, String>> rows, List<Map<String, String>> pathRows) {
        return render(rows, pathRows, List.of(), "", "");
    }

    public static String render(List<Map<String, String>> rows, List<Map<String, String>> pathRows,
                                String teamFilter, String tournament) {
        return render(rows, pathRows, List.of(), teamFilter, tournament);
    }

    public static String render(List<Map<String, String>> rows, List<Map<String, String>> pathRows,
                                List<Map<String, String>> scorelineRows, String teamFilter, String tournament) {
        String activeTeam = teamFilter == null ? "" : teamFilter.trim();
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"card shadow-sm border-0 mb-3\">")
                .append("<div class=\"card-body\">")
                .append("<div class=\"d-flex flex-column flex-md-row justify-content-between gap-2 align-items-md-center mb-3\">")
                .append("<div><h2 class=\"h4 mb-1\">Monte Carlo Simulation</h2>")
                .append("<div class=\"text-muted small\">Last 32 onward, based on current adjusted ELO, route fatigue, and xG advance probabilities.</div></div>")
                .append("<div class=\"d-flex flex-wrap gap-2 align-self-start align-self-md-center\">")
                .append(metaBadge(rows, "simulation_runs", "runs"))
                .append(metaBadge(rows, "simulation_seed", "seed"))
                .append("<span class=\"badge text-bg-primary\">").append(rows.size()).append(" teams</span>")
                .append("</div></div>");

        appendTopChampions(html, rows);
        appendFullTable(html, rows, tournament);
        appendCommonPaths(html, pathRows, activeTeam, tournament);
        appendCommonScorelines(html, scorelineRows, activeTeam, tournament);

        html.append("</div></div>");
        return html.toString();
    }

    public static String renderSnapshot(List<Map<String, String>> rows, String tournament) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        List<Map<String, String>> last16Leaders = rows.stream()
                .sorted(Comparator.comparingDouble((Map<String, String> row) -> pctValue(row, "reach_last_16")).reversed()
                        .thenComparing(row -> row.getOrDefault("team", "")))
                .limit(16)
                .toList();

        String routeMatchups = rows.get(0).getOrDefault("route_matchups", "");
        boolean routeWeighted = "true".equalsIgnoreCase(rows.get(0).getOrDefault("route_weighted", ""));
        String simulationRuns = rows.get(0).getOrDefault("simulation_runs", "");
        String simulationSubtitle = !routeMatchups.isBlank()
                ? (routeWeighted
                ? "Weighted across " + formatCount(routeMatchups) + " possible last 32 matchup projections using group outcome likelihood."
                : "Averaged across " + formatCount(routeMatchups) + " possible last 32 matchup projections.")
                : (simulationRuns.isBlank()
                ? "Across all simulated last 32 routes."
                : "Across " + formatCount(simulationRuns) + " simulations from the last 32 stage.");

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"border rounded-2 bg-light p-3 mb-3\">")
                .append("<div class=\"d-flex flex-column flex-md-row justify-content-between gap-2 align-items-md-center mb-2\">")
                .append("<div><div class=\"fw-semibold\">Most likely teams to proceed to Last 16</div>")
                .append("<div class=\"text-muted small\">Conditional on qualifying for the last 32. ").append(simulationSubtitle).append("</div></div>")
                .append("<a class=\"btn btn-outline-secondary btn-sm align-self-start align-self-md-center\" href=\"/view/simulation?tournament=")
                .append(url(tournament)).append("\">Full simulation</a></div>")
                .append("<div class=\"row g-2\">");
        for (Map<String, String> row : last16Leaders) {
            html.append("<div class=\"col-6 col-md-3 col-xl-2\">")
                    .append("<div class=\"border rounded-2 bg-white px-2 py-2 h-100 sim-snapshot-team\" role=\"button\" tabindex=\"0\" style=\"cursor:pointer\" data-team=\"")
                    .append(escapeHtml(row.getOrDefault("team", "")))
                    .append("\" onclick=\"filterTeamValue(this.dataset.team)\" onkeydown=\"if(event.key==='Enter'||event.key===' '){event.preventDefault();filterTeamValue(this.dataset.team);}\">")
                    .append("<div class=\"d-flex justify-content-between align-items-center gap-2\"><div class=\"fw-semibold text-truncate\">")
                    .append(escapeHtml(row.getOrDefault("team", "")))
                    .append("</div><div class=\"fs-5 flex-shrink-0\">")
                    .append(HtmlReporter.flagHtml(row.getOrDefault("team", "")))
                    .append("</div></div>")
                    .append("<div class=\"h5 mb-0\">").append(escapeHtml(row.getOrDefault("reach_last_16", ""))).append("%</div>")
                    .append("<div class=\"text-muted small\">last 32 win chance</div>");
            String marketOdds = row.getOrDefault("market_odds", "");
            if (!marketOdds.isBlank()) {
                String netProfit = netWinnings(marketOdds, 10.0);
                String candidateBadge = betCandidateBadge(pctValue(row, "reach_last_16"), netProfit);
                html.append("<div class=\"border-top mt-2 pt-2 small\">")
                        .append("<div>Market to reach Last 16: <span class=\"fw-semibold\">")
                        .append(escapeHtml(marketOdds)).append("</span></div>")
                        .append("<div class=\"d-flex justify-content-between align-items-center gap-1 flex-wrap\">")
                        .append("<span class=\"text-muted\">&pound;10 bet: &pound;")
                        .append(escapeHtml(netProfit)).append(" net</span>")
                        .append(candidateBadge).append("</div></div>");
            }
            html.append("</div></div>");
        }
        html.append("</div></div>");
        return html.toString();
    }

    private static void appendTopChampions(StringBuilder html, List<Map<String, String>> rows) {
        List<Map<String, String>> leaders = rows.stream()
                .sorted(Comparator.comparingDouble(SimulationResultsRenderer::championPct).reversed()
                        .thenComparing(row -> row.getOrDefault("team", "")))
                .limit(TOP_CHAMPION_COUNT)
                .toList();
        if (leaders.isEmpty()) {
            return;
        }

        html.append("<div class=\"mb-3\">")
                .append("<div class=\"text-muted small fw-semibold mb-2\">Top champion chances</div>")
                .append("<div class=\"row g-2\">");
        for (Map<String, String> row : leaders) {
            html.append("<div class=\"col-6 col-md-3 col-xl-2\">")
                    .append("<div class=\"border rounded-2 px-2 py-2 h-100\">")
                    .append("<div class=\"fw-semibold text-truncate\">").append(escapeHtml(row.getOrDefault("team", ""))).append("</div>")
                    .append("<div class=\"h5 mb-0\">").append(escapeHtml(row.getOrDefault("champion", ""))).append("%</div>")
                    .append("<div class=\"text-muted small\">predicted ").append(finishText(row, "predicted_finish", "predicted_finish_pct")).append("</div>")
                    .append("<div class=\"text-muted small\">best ").append(finishText(row, "best_realistic_finish", "best_realistic_pct")).append("</div>")
                    .append("</div></div>");
        }
        html.append("</div></div>");
    }

    private static void appendFullTable(StringBuilder html, List<Map<String, String>> rows, String tournament) {
        html.append("<div class=\"table-responsive mb-4\"><table class=\"table table-sm align-middle mb-0\">")
                .append("<thead><tr>")
                .append("<th>Team</th><th>Predicted Finish</th><th>Best Realistic Finish</th>")
                .append("<th class=\"text-end\">Last 16</th><th class=\"text-end\">QF</th>")
                .append("<th class=\"text-end\">SF</th><th class=\"text-end\">Final</th><th class=\"text-end\">Champion</th>")
                .append("</tr></thead><tbody>");
        for (Map<String, String> row : rows) {
            String team = row.getOrDefault("team", "");
            html.append("<tr>")
                    .append("<td class=\"fw-semibold\">").append(teamLink(team, tournament)).append("</td>")
                    .append("<td>").append(finishText(row, "predicted_finish", "predicted_finish_pct")).append("</td>")
                    .append("<td>").append(finishText(row, "best_realistic_finish", "best_realistic_pct")).append("</td>")
                    .append(percentCell(row, "reach_last_16"))
                    .append(percentCell(row, "reach_last_8"))
                    .append(percentCell(row, "reach_last_4"))
                    .append(percentCell(row, "reach_final"))
                    .append("<td class=\"text-end fw-semibold\">").append(escapeHtml(row.getOrDefault("champion", ""))).append("%</td>")
                    .append("</tr>");
        }
        html.append("</tbody></table></div>");
    }

    private static void appendCommonPaths(StringBuilder html, List<Map<String, String>> pathRows,
                                          String teamFilter, String tournament) {
        List<Map<String, String>> filteredRows = pathRows.stream()
                .filter(row -> teamFilter.isBlank() || row.getOrDefault("team", "").equalsIgnoreCase(teamFilter))
                .toList();
        List<Map<String, String>> topPaths = filteredRows.stream()
                .sorted(Comparator.comparingInt(SimulationResultsRenderer::count).reversed()
                        .thenComparing(row -> row.getOrDefault("team", ""))
                        .thenComparing(row -> row.getOrDefault("path", "")))
                .limit(TOP_PATH_COUNT)
                .toList();
        if (topPaths.isEmpty()) {
            return;
        }

        html.append("<div class=\"border-top pt-3\">")
                .append("<div class=\"d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-2 mb-2\">")
                .append("<div>")
                .append("<div class=\"fw-semibold\">Common simulated paths</div>");
        if (!teamFilter.isBlank()) {
            html.append("<div class=\"text-muted small\">Filtered to ").append(escapeHtml(teamFilter)).append("</div>");
        }
        html.append("</div><div class=\"d-flex flex-wrap gap-2 align-items-center\">")
                .append("<div class=\"text-muted small\">Top ").append(topPaths.size()).append(" exact routes</div>");
        if (!teamFilter.isBlank() && !tournament.isBlank()) {
            html.append("<a class=\"btn btn-outline-secondary btn-sm\" href=\"/view/simulation?tournament=")
                    .append(url(tournament)).append("\">Clear</a>");
        }
        html.append("</div></div>")
                .append("<div class=\"table-responsive\"><table class=\"table table-sm align-middle mb-0\">")
                .append("<thead><tr><th>Team</th><th>Finish</th><th>Path</th><th class=\"text-end\">Runs</th><th class=\"text-end\">Chance</th></tr></thead><tbody>");
        for (Map<String, String> row : topPaths) {
            String team = row.getOrDefault("team", "");
            html.append("<tr>")
                    .append("<td class=\"fw-semibold\">").append(teamLink(team, tournament)).append("</td>")
                    .append("<td>").append(escapeHtml(row.getOrDefault("finish", ""))).append("</td>")
                    .append("<td class=\"text-break\">").append(escapeHtml(row.getOrDefault("path", ""))).append("</td>")
                    .append("<td class=\"text-end\">").append(escapeHtml(row.getOrDefault("count", ""))).append("</td>")
                    .append("<td class=\"text-end fw-semibold\">").append(escapeHtml(row.getOrDefault("percentage", ""))).append("%</td>")
                    .append("</tr>");
        }
        html.append("</tbody></table></div></div>");
    }

    private static void appendCommonScorelines(StringBuilder html, List<Map<String, String>> scorelineRows,
                                               String teamFilter, String tournament) {
        List<Map<String, String>> filteredRows = scorelineRows.stream()
                .filter(row -> teamFilter.isBlank()
                        || row.getOrDefault("team1", "").equalsIgnoreCase(teamFilter)
                        || row.getOrDefault("team2", "").equalsIgnoreCase(teamFilter)
                        || row.getOrDefault("winner", "").equalsIgnoreCase(teamFilter))
                .sorted(Comparator.comparingDouble((Map<String, String> row) -> pctValue(row, "matchup_pct")).reversed()
                        .thenComparing(Comparator.comparingInt(SimulationResultsRenderer::count).reversed())
                        .thenComparing(row -> row.getOrDefault("stage", ""))
                        .thenComparing(row -> row.getOrDefault("match_id", ""))
                        .thenComparing(row -> row.getOrDefault("scoreline", "")))
                .limit(TOP_SCORELINE_COUNT)
                .toList();
        if (filteredRows.isEmpty()) {
            return;
        }

        html.append("<div class=\"border-top pt-3 mt-3\">")
                .append("<div class=\"d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-2 mb-2\">")
                .append("<div>")
                .append("<div class=\"fw-semibold\">Common simulated scorelines</div>");
        if (!teamFilter.isBlank()) {
            html.append("<div class=\"text-muted small\">Filtered to ").append(escapeHtml(teamFilter)).append("</div>");
        }
        html.append("</div><div class=\"text-muted small\">Top ").append(filteredRows.size()).append(" scoreline outcomes</div></div>")
                .append("<div class=\"table-responsive\"><table class=\"table table-sm align-middle mb-0\">")
                .append("<thead><tr><th>Round</th><th>Matchup</th><th>Score</th><th>Winner</th>")
                .append("<th class=\"text-end\">Score %</th><th class=\"text-end\">Matchup %</th></tr></thead><tbody>");
        for (Map<String, String> row : filteredRows) {
            html.append("<tr>")
                    .append("<td>").append(stageLabel(row.getOrDefault("stage", ""))).append("</td>")
                    .append("<td class=\"text-break\">").append(teamLink(row.getOrDefault("team1", ""), tournament))
                    .append(" <span class=\"text-muted\">v</span> ")
                    .append(teamLink(row.getOrDefault("team2", ""), tournament)).append("</td>")
                    .append("<td class=\"fw-semibold\">").append(escapeHtml(row.getOrDefault("scoreline", ""))).append("</td>")
                    .append("<td>").append(teamLink(row.getOrDefault("winner", ""), tournament)).append("</td>")
                    .append("<td class=\"text-end fw-semibold\">").append(escapeHtml(row.getOrDefault("scoreline_pct", ""))).append("%</td>")
                    .append("<td class=\"text-end\">").append(escapeHtml(row.getOrDefault("matchup_pct", ""))).append("%</td>")
                    .append("</tr>");
        }
        html.append("</tbody></table></div></div>");
    }

    private static String snapshotItem(String label, String team, String detailHtml, String tournament) {
        return "<div class=\"col-12 col-md-3\"><div class=\"small text-muted\">" + escapeHtml(label)
                + "</div><div class=\"fw-semibold\">" + teamLink(team, tournament) + " " + detailHtml + "</div></div>";
    }

    private static String pctDetail(Map<String, String> row, String key) {
        return "<span class=\"text-muted\">" + escapeHtml(row.getOrDefault(key, "")) + "%</span>";
    }

    private static String teamLink(String team, String tournament) {
        if (team == null || team.isBlank() || tournament == null || tournament.isBlank()) {
            return escapeHtml(team == null ? "" : team);
        }
        return "<a href=\"/view/simulation?tournament=" + url(tournament) + "&team=" + url(team)
                + "\" class=\"link-primary link-offset-2\">" + escapeHtml(team) + "</a>";
    }

    private static String finishText(Map<String, String> row, String finishKey, String pctKey) {
        String finish = row.getOrDefault(finishKey, "");
        String pct = row.getOrDefault(pctKey, "");
        if (finish.isBlank()) {
            return "";
        }
        if (pct.isBlank()) {
            return escapeHtml(finish);
        }
        return escapeHtml(finish) + " <span class=\"text-muted\">" + escapeHtml(pct) + "%</span>";
    }

    private static String metaBadge(List<Map<String, String>> rows, String key, String label) {
        if (rows.isEmpty()) return "";
        String value = rows.get(0).getOrDefault(key, "");
        if (value.isBlank()) return "";
        return "<span class=\"badge text-bg-light border\">" + escapeHtml(value) + " " + label + "</span>";
    }

    private static double championPct(Map<String, String> row) {
        return pctValue(row, "champion");
    }

    private static double pctValue(Map<String, String> row, String key) {
        try {
            return Double.parseDouble(row.getOrDefault(key, "0"));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static int bestFinishRank(Map<String, String> row) {
        return switch (row.getOrDefault("best_realistic_finish", "")) {
            case "Champion" -> 5;
            case "Runner-up" -> 4;
            case "Exit SF" -> 3;
            case "Exit QF" -> 2;
            case "Exit Last 16" -> 1;
            default -> 0;
        };
    }

    private static String stageLabel(String stage) {
        return switch (stage) {
            case "last_32" -> "Last 32";
            case "last_16" -> "Last 16";
            case "last_8" -> "QF";
            case "last_4" -> "SF";
            case "final" -> "Final";
            default -> escapeHtml(stage);
        };
    }

    private static int count(Map<String, String> row) {
        try {
            return Integer.parseInt(row.getOrDefault("count", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String betCandidateBadge(double pct, String netProfit) {
        double profit;
        try {
            profit = Double.parseDouble(netProfit);
        } catch (NumberFormatException e) {
            return "";
        }
        if (pct >= 55 && profit >= 10) return "<span class=\"badge text-bg-success\">Strong Candidate</span>";
        if (pct >= 40 && profit >= 10) return "<span class=\"badge text-bg-primary\">Candidate</span>";
        if (pct >= 40 && profit > 5) return "<span class=\"badge text-bg-info\">Weak Candidate</span>";
        if (pct >= 30 && profit >= 10) return "<span class=\"badge text-bg-warning\">Risky Candidate</span>";
        if (pct < 30 && profit >= 20) return "<span class=\"badge text-bg-secondary\">Moonshot</span>";
        return "";
    }

    private static String netWinnings(String odds, double stake) {
        if (odds == null || odds.isBlank()) return "";
        String[] parts = odds.trim().split("/", 2);
        if (parts.length != 2) return "";
        try {
            double denominator = Double.parseDouble(parts[1].trim());
            if (denominator == 0) return "";
            double profit = Double.parseDouble(parts[0].trim()) / denominator * stake;
            return String.format(java.util.Locale.UK, "%.2f", profit);
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private static String formatCount(String value) {
        try {
            return String.format(java.util.Locale.US, "%,d", Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return escapeHtml(value);
        }
    }

    private static String percentCell(Map<String, String> row, String key) {
        return "<td class=\"text-end\">" + escapeHtml(row.getOrDefault(key, "")) + "%</td>";
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

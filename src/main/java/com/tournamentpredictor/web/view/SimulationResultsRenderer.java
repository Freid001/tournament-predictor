package com.tournamentpredictor.web.view;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class SimulationResultsRenderer {
    private static final int TOP_CHAMPION_COUNT = 8;
    private static final int TOP_PATH_COUNT = 30;

    private SimulationResultsRenderer() {
    }

    public static String render(List<Map<String, String>> rows) {
        return render(rows, List.of());
    }

    public static String render(List<Map<String, String>> rows, List<Map<String, String>> pathRows) {
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
        appendFullTable(html, rows);
        appendCommonPaths(html, pathRows);

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
            html.append("<div class=\"col-6 col-md-3\">")
                    .append("<div class=\"border rounded-2 px-2 py-2 h-100\">")
                    .append("<div class=\"fw-semibold text-truncate\">").append(escapeHtml(row.getOrDefault("team", ""))).append("</div>")
                    .append("<div class=\"h5 mb-0\">").append(escapeHtml(row.getOrDefault("champion", ""))).append("%</div>")
                    .append("<div class=\"text-muted small\">predicted ").append(finishText(row, "predicted_finish", "predicted_finish_pct")).append("</div>")
                    .append("<div class=\"text-muted small\">best ").append(finishText(row, "best_realistic_finish", "best_realistic_pct")).append("</div>")
                    .append("</div></div>");
        }
        html.append("</div></div>");
    }

    private static void appendFullTable(StringBuilder html, List<Map<String, String>> rows) {
        html.append("<div class=\"table-responsive mb-4\"><table class=\"table table-sm align-middle mb-0\">")
                .append("<thead><tr>")
                .append("<th>Team</th><th>Predicted Finish</th><th>Best Realistic Finish</th>")
                .append("<th class=\"text-end\">Last 16</th><th class=\"text-end\">QF</th>")
                .append("<th class=\"text-end\">SF</th><th class=\"text-end\">Final</th><th class=\"text-end\">Champion</th>")
                .append("</tr></thead><tbody>");
        for (Map<String, String> row : rows) {
            html.append("<tr>")
                    .append("<td class=\"fw-semibold\">").append(escapeHtml(row.getOrDefault("team", ""))).append("</td>")
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

    private static void appendCommonPaths(StringBuilder html, List<Map<String, String>> pathRows) {
        List<Map<String, String>> topPaths = pathRows.stream()
                .sorted(Comparator.comparingInt(SimulationResultsRenderer::count).reversed()
                        .thenComparing(row -> row.getOrDefault("team", ""))
                        .thenComparing(row -> row.getOrDefault("path", "")))
                .limit(TOP_PATH_COUNT)
                .toList();
        if (topPaths.isEmpty()) {
            return;
        }

        html.append("<div class=\"border-top pt-3\">")
                .append("<div class=\"d-flex justify-content-between align-items-center gap-2 mb-2\">")
                .append("<div class=\"fw-semibold\">Common simulated paths</div>")
                .append("<div class=\"text-muted small\">Top ").append(topPaths.size()).append(" exact routes</div>")
                .append("</div>")
                .append("<div class=\"table-responsive\"><table class=\"table table-sm align-middle mb-0\">")
                .append("<thead><tr><th>Team</th><th>Finish</th><th>Path</th><th class=\"text-end\">Runs</th><th class=\"text-end\">Chance</th></tr></thead><tbody>");
        for (Map<String, String> row : topPaths) {
            html.append("<tr>")
                    .append("<td class=\"fw-semibold\">").append(escapeHtml(row.getOrDefault("team", ""))).append("</td>")
                    .append("<td>").append(escapeHtml(row.getOrDefault("finish", ""))).append("</td>")
                    .append("<td class=\"text-break\">").append(escapeHtml(row.getOrDefault("path", ""))).append("</td>")
                    .append("<td class=\"text-end\">").append(escapeHtml(row.getOrDefault("count", ""))).append("</td>")
                    .append("<td class=\"text-end fw-semibold\">").append(escapeHtml(row.getOrDefault("percentage", ""))).append("%</td>")
                    .append("</tr>");
        }
        html.append("</tbody></table></div></div>");
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
        try {
            return Double.parseDouble(row.getOrDefault("champion", "0"));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static int count(Map<String, String> row) {
        try {
            return Integer.parseInt(row.getOrDefault("count", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String percentCell(Map<String, String> row, String key) {
        return "<td class=\"text-end\">" + escapeHtml(row.getOrDefault(key, "")) + "%</td>";
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

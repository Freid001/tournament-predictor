package com.tournamentpredictor.services.web;

import com.tournamentpredictor.services.calculation.EloCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ViewLineService {
    private ViewLineService() {
    }

    public static List<String> allStageTeamNames(List<String> lines) {
        if (lines == null || lines.size() <= 1) {
            return List.of();
        }
        String[] headers = lines.get(0).split(",", -1);
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        if (team1Idx < 0 || team2Idx < 0) {
            return List.of();
        }
        EloCalculator elo = new EloCalculator();
        return lines.stream()
                .skip(1)
                .filter(line -> line != null && !line.isBlank())
                .flatMap(line -> {
                    String[] cols = line.split(",", -1);
                    return java.util.stream.Stream.of(
                            elo.extractTeamName(valueAt(cols, team1Idx)),
                            elo.extractTeamName(valueAt(cols, team2Idx)));
                })
                .filter(team -> team != null && !team.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public static List<String> filterViewLines(List<String> lines, String pathFilter, String teamFilter) {
        if (lines == null || lines.size() <= 1) {
            return lines;
        }
        String[] headers = lines.get(0).split(",", -1);
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int pathIdx = indexOf(headers, "path");
        if (pathIdx < 0) {
            return lines;
        }
        String normalizedPath = pathFilter == null || pathFilter.isBlank() ? "all" : pathFilter.trim().toLowerCase();
        if ("both".equals(normalizedPath)) {
            normalizedPath = "all";
        }
        if ("upset".equals(normalizedPath)) {
            normalizedPath = "alt";
        }
        String normalizedTeam = teamFilter == null ? "" : teamFilter.trim().toLowerCase();
        EloCalculator elo = new EloCalculator();
        List<String> out = new ArrayList<>();
        out.add(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String rowPath = valueAt(cols, pathIdx).trim().toLowerCase();
            if (rowPath.isBlank() && cols.length > 7) {
                rowPath = valueAt(cols, 7).trim().toLowerCase();
            }
            boolean pathMatches = switch (normalizedPath) {
                case "all" -> true;
                case "actual", "results" -> "actual".equals(rowPath) || "results".equals(rowPath) || "fixture".equals(rowPath) || "result_upset".equals(rowPath);
                case "prediction" -> "predicted".equals(rowPath) || "prediction".equals(rowPath) || "live".equals(rowPath);
                case "alt" -> "alt".equals(rowPath) || "upset".equals(rowPath);
                default -> normalizedPath.equals(rowPath);
            };
            if (!pathMatches) continue;
            if (!normalizedTeam.isBlank()) {
                String team1 = elo.extractTeamName(valueAt(cols, team1Idx)).toLowerCase();
                String team2 = elo.extractTeamName(valueAt(cols, team2Idx)).toLowerCase();
                if (!team1.contains(normalizedTeam) && !team2.contains(normalizedTeam)) continue;
            }
            out.add(line);
        }
        return out;
    }

    public static List<String> paginateLines(List<String> lines, int page, int pageSize) {
        if (lines == null || lines.size() <= 1 || pageSize <= 0) {
            return lines;
        }
        List<String> paged = new ArrayList<>();
        paged.add(lines.get(0));
        int start = 1 + Math.max(0, page - 1) * pageSize;
        int end = Math.min(lines.size(), start + pageSize);
        if (start >= lines.size()) {
            return paged;
        }
        paged.addAll(lines.subList(start, end));
        return paged;
    }

    public static String buildPageNavigationHtml(String tournament, String round, boolean actualMode, int currentPage, int pageCount) {
        String query = "?tournament=" + tournament + (actualMode ? "&results=true" : "");
        String prevUrl = "/view/" + round + query + "&page=" + Math.max(1, currentPage - 1);
        String nextUrl = "/view/" + round + query + "&page=" + Math.min(pageCount, currentPage + 1);
        return """
                <div class="d-flex justify-content-between align-items-center gap-2 flex-wrap mb-3">
                  <div class="text-muted small">Page %d of %d</div>
                  <div class="d-flex gap-2">
                    <a class="btn btn-outline-secondary btn-sm%s" href="%s">Previous</a>
                    <a class="btn btn-outline-secondary btn-sm%s" href="%s">Next</a>
                  </div>
                </div>
                """.formatted(
                currentPage,
                pageCount,
                currentPage <= 1 ? " disabled" : "",
                prevUrl,
                currentPage >= pageCount ? " disabled" : "",
                nextUrl);
    }

    private static int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static String valueAt(String[] cols, int idx) {
        return idx >= 0 && idx < cols.length ? cols[idx].trim() : "";
    }
}

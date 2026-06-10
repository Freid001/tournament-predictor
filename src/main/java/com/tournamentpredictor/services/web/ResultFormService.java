package com.tournamentpredictor.services.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResultFormService {
    public static final List<String> KNOCKOUT_HEADERS = List.of(
            "round", "match_id", "team1", "team2", "winner", "home_score", "away_score", "penalties"
    );
    public static final List<String> GROUP_HEADERS = List.of(
            "round", "group", "match_id", "team1", "team2", "winner", "home_score", "away_score", "penalties"
    );

    public List<Map<String, String>> knockoutRows(HttpServletRequest request, String round) {
        int rowCount = WebText.parseInt(request.getParameter("rowCount_" + round), 0);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            String prefix = round + "_" + i;
            ResultFields fields = fields(request, prefix);
            boolean penalties = request.getParameter("penalties_" + prefix) != null;
            if (fields.isBlank() && !penalties) {
                continue;
            }
            Map<String, String> row = baseRow(round, fields);
            row.put("penalties", penalties ? "yes" : "");
            rows.add(row);
        }
        return rows;
    }

    public List<Map<String, String>> groupRows(HttpServletRequest request) {
        int rowCount = WebText.parseInt(request.getParameter("rowCount"), 0);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            String group = WebText.trim(request.getParameter("group" + i));
            ResultFields fields = groupFields(request, i);
            if (group.isBlank() && fields.isBlank()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("round", "groups");
            row.put("group", group);
            row.putAll(baseRowWithoutRound(fields));
            row.put("penalties", "");
            rows.add(row);
        }
        return rows;
    }

    private ResultFields fields(HttpServletRequest request, String suffix) {
        return new ResultFields(
                WebText.trim(request.getParameter("matchId_" + suffix)),
                WebText.trim(request.getParameter("team1_" + suffix)),
                WebText.trim(request.getParameter("team2_" + suffix)),
                WebText.trim(request.getParameter("winner_" + suffix)),
                WebText.trim(request.getParameter("homeScore_" + suffix)),
                WebText.trim(request.getParameter("awayScore_" + suffix))
        );
    }

    private ResultFields groupFields(HttpServletRequest request, int index) {
        return new ResultFields(
                WebText.trim(request.getParameter("matchId" + index)),
                WebText.trim(request.getParameter("team1" + index)),
                WebText.trim(request.getParameter("team2" + index)),
                WebText.trim(request.getParameter("winner" + index)),
                WebText.trim(request.getParameter("homeScore" + index)),
                WebText.trim(request.getParameter("awayScore" + index))
        );
    }

    private Map<String, String> baseRow(String round, ResultFields fields) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("round", round);
        row.putAll(baseRowWithoutRound(fields));
        return row;
    }

    private Map<String, String> baseRowWithoutRound(ResultFields fields) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("match_id", fields.matchId());
        row.put("team1", fields.team1());
        row.put("team2", fields.team2());
        row.put("winner", fields.winner());
        row.put("home_score", fields.homeScore());
        row.put("away_score", fields.awayScore());
        return row;
    }

    private record ResultFields(String matchId, String team1, String team2, String winner,
                                String homeScore, String awayScore) {
        boolean isBlank() {
            return matchId.isBlank() && team1.isBlank() && team2.isBlank() && winner.isBlank()
                    && homeScore.isBlank() && awayScore.isBlank();
        }
    }
}

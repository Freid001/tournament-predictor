package com.tournamentpredictor.model.round;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class RoundViewRow {
        private final String matchId;
        private final String team1;
        private final String team2;
        private final String predictedWinner;
        private final String prediction;

        public RoundViewRow(String matchId, String team1, String team2, String predictedWinner, String prediction) {
            this.matchId = matchId;
            this.team1 = team1;
            this.team2 = team2;
            this.predictedWinner = predictedWinner;
            this.prediction = prediction;
        }

        public String getMatchId() { return matchId; }
        public String getTeam1Html() { return teamHtml(team1); }
        public String getTeam2Html() { return teamHtml(team2); }
        public String getPredictedWinnerHtml() { return HtmlReporter.flagHtml(extractWinnerName(predictedWinner)) + escapeHtml(predictedWinner); }
        public String getPrediction() { return prediction; }
        public boolean isTeam1Winner() { return isPredictedWinner(team1); }
        public boolean isTeam2Winner() { return isPredictedWinner(team2); }

        private boolean isPredictedWinner(String teamValue) {
            String winner = extractWinnerName(predictedWinner);
            String teamName = extractTeamName(teamValue);
            return !winner.isEmpty() && winner.equals(teamName);
        }

        private String teamHtml(String teamValue) {
            return HtmlReporter.flagHtml(extractTeamName(teamValue)) + escapeHtml(teamValue);
        }
    }

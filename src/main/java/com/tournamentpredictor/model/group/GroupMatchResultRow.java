package com.tournamentpredictor.model.group;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class GroupMatchResultRow {
        private final int rowIndex;
        private final String group;
        private final String matchId;
        private final String team1;
        private final String team2;
        private final String predictedOutcome;
        private final String winner;
        private final String homeScore;
        private final String awayScore;

        public GroupMatchResultRow(int rowIndex, String group, String matchId, String team1, String team2,
                                   String predictedOutcome, String winner, String homeScore, String awayScore) {
            this.rowIndex = rowIndex;
            this.group = group;
            this.matchId = matchId;
            this.team1 = team1;
            this.team2 = team2;
            this.predictedOutcome = predictedOutcome;
            this.winner = winner;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
        }

        public int getRowIndex() { return rowIndex; }
        public String getGroup() { return group; }
        public String getMatchId() { return matchId; }
        public String getTeam1() { return team1; }
        public String getTeam2() { return team2; }
        public String getPredictedOutcome() { return predictedOutcome; }
        public String getWinner() { return winner; }
        public String getHomeScore() { return homeScore; }
        public String getAwayScore() { return awayScore; }
        public String getTeam1Html() { return HtmlReporter.flagHtml(team1) + escapeHtml(team1); }
        public String getTeam2Html() { return HtmlReporter.flagHtml(team2) + escapeHtml(team2); }
    }

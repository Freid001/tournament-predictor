package com.tournamentpredictor.model.results;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class ResultEntryRow {
        private final String round;
        private final int rowIndex;
        private final String matchId;
        private final String team1;
        private final String team2;
        private final String winner;
        private final String homeScore;
        private final String awayScore;
        private final boolean penalties;

        public ResultEntryRow(String round, int rowIndex, String matchId, String team1, String team2,
                              String winner, String homeScore, String awayScore, boolean penalties) {
            this.round = round;
            this.rowIndex = rowIndex;
            this.matchId = matchId;
            this.team1 = team1;
            this.team2 = team2;
            this.winner = winner;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.penalties = penalties;
        }

        public String getRound() { return round; }
        public int getRowIndex() { return rowIndex; }
        public String getMatchId() { return matchId; }
        public String getTeam1() { return team1; }
        public String getTeam2() { return team2; }
        public String getWinner() { return winner; }
        public String getHomeScore() { return homeScore; }
        public String getAwayScore() { return awayScore; }
        public boolean isPenalties() { return penalties; }
        public String getTeam1Html() { return HtmlReporter.flagHtml(team1) + escapeHtml(team1); }
        public String getTeam2Html() { return HtmlReporter.flagHtml(team2) + escapeHtml(team2); }
    }

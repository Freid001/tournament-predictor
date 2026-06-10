package com.tournamentpredictor.model.round;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class DirectMatchupSummary {
        public final String matchId;
        public final String team1;
        public final String team2;
        public final Map<String, Integer> winnerCounts = new LinkedHashMap<>();
        public int matchupRuns;
        public String matchupPct = "";

        public DirectMatchupSummary(String matchId, String team1, String team2) {
            this.matchId = matchId;
            this.team1 = team1;
            this.team2 = team2;
        }
    }

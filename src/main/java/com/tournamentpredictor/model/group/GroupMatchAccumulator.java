package com.tournamentpredictor.model.group;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class GroupMatchAccumulator {
        public final String matchId;
        public final String group;
        public final String team1;
        public final String team2;
        public final Map<String, Integer> outcomes = new LinkedHashMap<>();
        public int total;

        public GroupMatchAccumulator(String matchId, String group, String team1, String team2) {
            this.matchId = matchId;
            this.group = group;
            this.team1 = team1;
            this.team2 = team2;
        }
    }

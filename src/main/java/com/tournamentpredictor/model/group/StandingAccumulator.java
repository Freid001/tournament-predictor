package com.tournamentpredictor.model.group;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class StandingAccumulator {
        public final String team;
        public int played;
        public int wins;
        public int draws;
        public int losses;
        public int goalsFor;
        public int goalsAgainst;
        public int points;

        public StandingAccumulator(String team) {
            this.team = team;
        }

        public int goalDifference() { return goalsFor - goalsAgainst; }
    }

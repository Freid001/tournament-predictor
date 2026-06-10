package com.tournamentpredictor.model.tournament;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class TournamentSummary {
        private final String name;
        private final String label;
        private final String stage;
        private final int completedSteps;
        private final boolean historicalComparison;

        public TournamentSummary(String name, String stage, int completedSteps) {
            this(name, stage, completedSteps, false);
        }

        public TournamentSummary(String name, String stage, int completedSteps, boolean historicalComparison) {
            this.name = name;
            this.label = displayTournament(name);
            this.stage = stage;
            this.completedSteps = completedSteps;
            this.historicalComparison = historicalComparison;
        }

        public String getName() { return name; }
        public String getLabel() { return label; }
        public String getStage() { return stage; }
        public int getCompletedSteps() { return completedSteps; }
        public boolean isHistoricalComparison() { return historicalComparison; }

        private static String displayTournament(String name) {
            if (name == null || name.isEmpty()) return name;
            return Arrays.stream(name.split("_"))
                    .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                    .collect(java.util.stream.Collectors.joining(" "));
        }
    }

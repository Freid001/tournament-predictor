package com.tournamentpredictor.model.results;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class ResultsRoundView {
        private final String round;
        private final String label;
        private final List<ResultEntryRow> rows;

        public ResultsRoundView(String round, String label, List<ResultEntryRow> rows) {
            this.round = round;
            this.label = label;
            this.rows = rows;
        }

        public String getRound() { return round; }
        public String getLabel() { return label; }
        public List<ResultEntryRow> getRows() { return rows; }
    }

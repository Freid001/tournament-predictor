package com.tournamentpredictor.model.history;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public record HistoricalComparison(String tournament, String label, int correct, int total,
                                       List<HistoricalMatchView> matches) {
        public String accuracy() {
            return total == 0 ? "0.0%" : String.format(java.util.Locale.ROOT, "%.1f%%", correct * 100.0 / total);
        }
    }

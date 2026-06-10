package com.tournamentpredictor.model.tournament;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class StageStatus {
        private final String icon;
        private final String text;
        private final String badge;

        public StageStatus(String icon, String text, String badge) {
            this.icon = icon;
            this.text = text;
            this.badge = badge;
        }

        public String getIcon() { return icon; }
        public String getText() { return text; }
        public String getBadge() { return badge; }
    }

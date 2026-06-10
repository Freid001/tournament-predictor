package com.tournamentpredictor.model.group;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class GroupResultRow {
        private final int rowIndex;
        private final String group;
        private final String team;
        private final String predictedPosition;
        private final String actualPosition;
        private final String qualified;
        private final String note;
        private final EloBreakdown breakdown;

        public GroupResultRow(int rowIndex, String group, String team, String predictedPosition,
                              String actualPosition, String qualified, String note, EloBreakdown breakdown) {
            this.rowIndex = rowIndex;
            this.group = group;
            this.team = team;
            this.predictedPosition = predictedPosition;
            this.actualPosition = actualPosition;
            this.qualified = qualified;
            this.note = note;
            this.breakdown = breakdown;
        }

        public int getRowIndex() { return rowIndex; }
        public String getGroup() { return group; }
        public String getTeam() { return team; }
        public String getTeamHtml() { return HtmlReporter.flagHtml(team) + escapeHtml(team); }
        public String getPredictedPosition() { return predictedPosition; }
        public String getActualPosition() { return actualPosition; }
        public String getQualified() { return qualified; }
        public String getNote() { return note; }
        public EloBreakdown getBreakdown() { return breakdown; }
        public String getBreakdownHtml() { return GroupViewRow.buildBreakdownHtml(team, breakdown); }
    }

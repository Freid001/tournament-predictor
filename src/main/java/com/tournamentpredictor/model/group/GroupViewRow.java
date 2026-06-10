package com.tournamentpredictor.model.group;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class GroupViewRow {
        private final String team;
        private final String qualificationForm;
        private final String predictedPosition;
        private final String groupWinner;
        private final String runnerUp;
        private final String thirdPlace;
        private final EloBreakdown breakdown;

        public GroupViewRow(String team, String qualificationForm, String predictedPosition,
                            String groupWinner, String runnerUp, String thirdPlace, EloBreakdown breakdown) {
            this.team = team;
            this.qualificationForm = qualificationForm;
            this.predictedPosition = predictedPosition;
            this.groupWinner = groupWinner;
            this.runnerUp = runnerUp;
            this.thirdPlace = thirdPlace;
            this.breakdown = breakdown;
        }

        public String getTeamHtml() { return HtmlReporter.flagHtml(team) + escapeHtml(team); }
        public String getQualificationForm() { return qualificationForm; }
        public String getPredictedPosition() { return predictedPosition; }

        public String getBreakdownHtml() {
            return buildBreakdownHtml(team, breakdown);
        }

        static String buildBreakdownHtml(String team, EloBreakdown b) {
            return HtmlReporter.buildTeamBreakdownHtml(team, b);
        }

        public String getGroupWinnerBadgeHtml() {
            return pickBadge(groupWinner, "Group Winner", "Winner Contender", "#FFD700", "#000");
        }

        public String getRunnerUpBadgeHtml() {
            return pickBadge(runnerUp, "Runner-up", "Runner-up Contender", "#C0C0C0", "#000");
        }

        public String getThirdPlaceBadgeHtml() {
            return pickBadge(thirdPlace, "Best 3rd", "3rd-place Contender", "#CD7F32", "#fff");
        }

        public String getPositionBadgeHtml() {
            return getGroupWinnerBadgeHtml() + " " + getRunnerUpBadgeHtml() + " " + getThirdPlaceBadgeHtml();
        }

        private static String pickBadge(String value, String yesLabel, String maybeLabel, String background, String color) {
            if (value == null || value.isBlank() || "no".equalsIgnoreCase(value)) return "";
            boolean maybe = "maybe".equalsIgnoreCase(value);
            String label = maybe ? maybeLabel : yesLabel;
            String style = maybe
                    ? "border:1px solid " + background + ";color:#212529;background-color:transparent"
                    : "background-color:" + background + ";color:" + color;
            String tooltip = maybe ? "Possible group outcome from manual picks." : "Selected group outcome from manual picks.";
            return "<span class=\"badge\" style=\"" + style + "\" title=\"" + tooltip + "\">"
                    + escapeHtml(label) + "</span>";
        }
    }

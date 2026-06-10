package com.tournamentpredictor.model.group;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class GroupPickRow {
        private final int rowIndex;
        private final String group;
        private final String team;
        private final String qualificationForm;
        private final String predictedPosition;
        private final String groupWinner;
        private final String runnerUp;
        private final String thirdPlace;
        private final int squadAgeProfile;
        private final int squadCohesion;
        private final EloBreakdown breakdown;

        public GroupPickRow(int rowIndex, String group, String team, String qualificationForm, String predictedPosition,
                            String groupWinner, String runnerUp, String thirdPlace, int squadAgeProfile, int squadCohesion,
                            EloBreakdown breakdown) {
            this.rowIndex = rowIndex;
            this.group = group;
            this.team = team;
            this.qualificationForm = qualificationForm;
            this.predictedPosition = predictedPosition;
            this.groupWinner = groupWinner;
            this.runnerUp = runnerUp;
            this.thirdPlace = thirdPlace;
            this.squadAgeProfile = squadAgeProfile;
            this.squadCohesion = squadCohesion;
            this.breakdown = breakdown;
        }

        public int getRowIndex() { return rowIndex; }
        public String getGroup() { return group; }
        public String getTeam() { return team; }
        public String getTeamHtml() { return HtmlReporter.flagHtml(team) + escapeHtml(team); }
        public String getQualificationForm() { return qualificationForm; }
        public String getPredictedPosition() { return predictedPosition; }
        public String getGroupWinner() { return groupWinner; }
        public String getRunnerUp() { return runnerUp; }
        public String getThirdPlace() { return thirdPlace; }
        public int getSquadAgeProfile() { return squadAgeProfile; }
        public int getSquadCohesion() { return squadCohesion; }

        public String getBreakdownHtml() {
            return GroupViewRow.buildBreakdownHtml(team, breakdown);
        }
    }

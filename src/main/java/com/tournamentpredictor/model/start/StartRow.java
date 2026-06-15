package com.tournamentpredictor.model.start;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public final class StartRow {
        private final String group;
        private final String team;
        private final boolean host;
        private final int injuryImpact;
        private final int heatImpact;
        private final String confederation;
        private final int confederationAdjustment;
        private final int squadDropouts;
        private final int squadAgeProfile;
        private final int squadCohesion;
        private final int squadDepth;
        private final int attackQuality;
        private final int defenceQuality;
        private final String dropoutNotes;
        private final String injuryNotes;
        private final String ageNotes;
        private final String cohesionNotes;
        private final String depthNotes;
        private final String qualityNotes;

        public StartRow(String group, String team, boolean host, int injuryImpact,
                        int heatImpact, String confederation, int confederationAdjustment, int squadDropouts, int squadAgeProfile,
                        int squadCohesion, int squadDepth, int attackQuality, int defenceQuality,
                        String dropoutNotes, String injuryNotes,
                        String ageNotes, String cohesionNotes,
                        String depthNotes, String qualityNotes) {
            this.group = group;
            this.team = team;
            this.host = host;
            this.injuryImpact = injuryImpact;
            this.heatImpact = heatImpact;
            this.confederation = confederation;
            this.confederationAdjustment = confederationAdjustment;
            this.squadDropouts = squadDropouts;
            this.squadAgeProfile = squadAgeProfile;
            this.squadCohesion = squadCohesion;
            this.squadDepth = squadDepth;
            this.attackQuality = attackQuality;
            this.defenceQuality = defenceQuality;
            this.dropoutNotes = dropoutNotes;
            this.injuryNotes = injuryNotes;
            this.ageNotes = ageNotes;
            this.cohesionNotes = cohesionNotes;
            this.depthNotes = depthNotes;
            this.qualityNotes = qualityNotes;
        }

        public String getGroup() { return group; }
        public String getTeam() { return team; }
        public String getTeamFlagHtml() { return HtmlReporter.flagHtml(team); }
        public boolean isHost() { return host; }
        public int getInjuryImpact() { return injuryImpact; }
        public int getHeatImpact() { return heatImpact; }
        public String getConfederation() { return confederation; }
        public int getConfederationAdjustment() { return confederationAdjustment; }
        public int getSquadDropouts() { return squadDropouts; }
        public int getSquadAgeProfile() { return squadAgeProfile; }
        public int getSquadCohesion() { return squadCohesion; }
        public int getSquadDepth() { return squadDepth; }
        public int getAttackQuality() { return attackQuality; }
        public int getDefenceQuality() { return defenceQuality; }
        public String getDropoutNotes() { return dropoutNotes; }
        public String getInjuryNotes() { return injuryNotes; }
        public String getAgeNotes() { return ageNotes; }
        public String getCohesionNotes() { return cohesionNotes; }
        public String getDepthNotes() { return depthNotes; }
        public String getQualityNotes() { return qualityNotes; }
    }

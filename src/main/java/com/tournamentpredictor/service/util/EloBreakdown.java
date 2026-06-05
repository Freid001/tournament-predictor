package com.tournamentpredictor.service.util;

import java.util.List;

public final class EloBreakdown {
    public final int baseElo;
    public final boolean isHost;
    public final int homeBonus;
    public final int injuryLevel;
    public final int injuryPenalty;
    public final int heatLevel;
    public final int heatBonus;
    public final int dropoutLevel;
    public final int dropoutPenalty;
    public final int qualBonus;
    public final int preTournamentBonus;
    public final int squadAgeLevel;
    public final int squadAgePenalty;
    public final int squadCohesionLevel;
    public final int squadCohesionPenalty;
    public final int squadDepthLevel;
    public final int squadDepthPenalty;
    public final int squadQualityLevel;
    public final int squadQualityBonus;
    public final String dropoutNotes;
    public final String injuryNotes;
    public final String ageNotes;
    public final String cohesionNotes;
    public final String depthNotes;
    public final String qualityNotes;
    public final int pathFatigueAdjustment;
    public final String pathFatigueLabel;
    public final String pathOpponent;
    public final int totalElo;
    /**
     * Qualifying game results in chronological order.
     * Each entry is a String[2]: [0]="W"/"D"/"L", [1]=tooltip e.g. "vs Honduras · 2–0"
     * Empty if host/no data.
     */
    public final List<String[]> qualResults;
    public final List<String[]> friendlyResults;

    public EloBreakdown(int baseElo, boolean isHost, int homeBonus,
                        int injuryLevel, int injuryPenalty,
                        int heatLevel, int heatBonus,
                        int dropoutLevel, int dropoutPenalty,
                        int qualBonus,
                        List<String[]> qualResults) {
        this(baseElo, isHost, homeBonus,
                injuryLevel, injuryPenalty,
                heatLevel, heatBonus,
                dropoutLevel, dropoutPenalty,
                qualBonus, 0,
                0, 0,
                0, 0,
                0, 0, 0, 0,
                "", "", "", "", "", "",
                qualResults, List.of(),
                0, "");
    }

    public EloBreakdown(int baseElo, boolean isHost, int homeBonus,
                        int injuryLevel, int injuryPenalty,
                        int heatLevel, int heatBonus,
                        int dropoutLevel, int dropoutPenalty,
                        int qualBonus, int preTournamentBonus,
                        int squadAgeLevel, int squadAgePenalty,
                        int squadCohesionLevel, int squadCohesionPenalty,
                        List<String[]> qualResults) {
        this(baseElo, isHost, homeBonus,
                injuryLevel, injuryPenalty,
                heatLevel, heatBonus,
                dropoutLevel, dropoutPenalty,
                qualBonus, preTournamentBonus,
                squadAgeLevel, squadAgePenalty,
                squadCohesionLevel, squadCohesionPenalty,
                0, 0, 0, 0,
                "", "", "", "", "", "",
                qualResults, List.of(),
                0, "");
    }

    public EloBreakdown(int baseElo, boolean isHost, int homeBonus,
                        int injuryLevel, int injuryPenalty,
                        int heatLevel, int heatBonus,
                        int dropoutLevel, int dropoutPenalty,
                        int qualBonus, int preTournamentBonus,
                        int squadAgeLevel, int squadAgePenalty,
                        int squadCohesionLevel, int squadCohesionPenalty,
                        String dropoutNotes, String injuryNotes,
                        String ageNotes, String cohesionNotes,
                        List<String[]> qualResults) {
        this(baseElo, isHost, homeBonus,
                injuryLevel, injuryPenalty,
                heatLevel, heatBonus,
                dropoutLevel, dropoutPenalty,
                qualBonus, preTournamentBonus,
                squadAgeLevel, squadAgePenalty,
                squadCohesionLevel, squadCohesionPenalty,
                0, 0, 0, 0,
                dropoutNotes, injuryNotes, ageNotes, cohesionNotes, "", "",
                qualResults, List.of(),
                0, "");
    }

    public EloBreakdown(int baseElo, boolean isHost, int homeBonus,
                        int injuryLevel, int injuryPenalty,
                        int heatLevel, int heatBonus,
                        int dropoutLevel, int dropoutPenalty,
                        int qualBonus, int preTournamentBonus,
                        int squadAgeLevel, int squadAgePenalty,
                        int squadCohesionLevel, int squadCohesionPenalty,
                        String dropoutNotes, String injuryNotes,
                        String ageNotes, String cohesionNotes,
                        List<String[]> qualResults, List<String[]> friendlyResults) {
        this(baseElo, isHost, homeBonus,
                injuryLevel, injuryPenalty,
                heatLevel, heatBonus,
                dropoutLevel, dropoutPenalty,
                qualBonus, preTournamentBonus,
                squadAgeLevel, squadAgePenalty,
                squadCohesionLevel, squadCohesionPenalty,
                0, 0, 0, 0,
                dropoutNotes, injuryNotes, ageNotes, cohesionNotes, "", "",
                qualResults, friendlyResults,
                0, "");
    }

    public EloBreakdown(int baseElo, boolean isHost, int homeBonus,
                        int injuryLevel, int injuryPenalty,
                        int heatLevel, int heatBonus,
                        int dropoutLevel, int dropoutPenalty,
                        int qualBonus, int preTournamentBonus,
                        int squadAgeLevel, int squadAgePenalty,
                        int squadCohesionLevel, int squadCohesionPenalty,
                        int squadDepthLevel, int squadDepthPenalty,
                        int squadQualityLevel, int squadQualityBonus,
                        String dropoutNotes, String injuryNotes,
                        String ageNotes, String cohesionNotes,
                        String depthNotes, String qualityNotes,
                        List<String[]> qualResults, List<String[]> friendlyResults,
                        int pathFatigueAdjustment, String pathFatigueLabel) {
        this(baseElo, isHost, homeBonus, injuryLevel, injuryPenalty, heatLevel, heatBonus,
                dropoutLevel, dropoutPenalty, qualBonus, preTournamentBonus, squadAgeLevel, squadAgePenalty,
                squadCohesionLevel, squadCohesionPenalty, squadDepthLevel, squadDepthPenalty,
                squadQualityLevel, squadQualityBonus, dropoutNotes, injuryNotes, ageNotes, cohesionNotes,
                depthNotes, qualityNotes, qualResults, friendlyResults, pathFatigueAdjustment, pathFatigueLabel, "");
    }

    public EloBreakdown(int baseElo, boolean isHost, int homeBonus,
                        int injuryLevel, int injuryPenalty,
                        int heatLevel, int heatBonus,
                        int dropoutLevel, int dropoutPenalty,
                        int qualBonus, int preTournamentBonus,
                        int squadAgeLevel, int squadAgePenalty,
                        int squadCohesionLevel, int squadCohesionPenalty,
                        int squadDepthLevel, int squadDepthPenalty,
                        int squadQualityLevel, int squadQualityBonus,
                        String dropoutNotes, String injuryNotes,
                        String ageNotes, String cohesionNotes,
                        String depthNotes, String qualityNotes,
                        List<String[]> qualResults, List<String[]> friendlyResults,
                        int pathFatigueAdjustment, String pathFatigueLabel, String pathOpponent) {
        this.baseElo = baseElo;
        this.isHost = isHost;
        this.homeBonus = homeBonus;
        this.injuryLevel = injuryLevel;
        this.injuryPenalty = injuryPenalty;
        this.heatLevel = heatLevel;
        this.heatBonus = heatBonus;
        this.dropoutLevel = dropoutLevel;
        this.dropoutPenalty = dropoutPenalty;
        this.qualBonus = qualBonus;
        this.preTournamentBonus = preTournamentBonus;
        this.squadAgeLevel = squadAgeLevel;
        this.squadAgePenalty = squadAgePenalty;
        this.squadCohesionLevel = squadCohesionLevel;
        this.squadCohesionPenalty = squadCohesionPenalty;
        this.squadDepthLevel = squadDepthLevel;
        this.squadDepthPenalty = squadDepthPenalty;
        this.squadQualityLevel = squadQualityLevel;
        this.squadQualityBonus = squadQualityBonus;
        this.dropoutNotes = dropoutNotes != null ? dropoutNotes : "";
        this.injuryNotes = injuryNotes != null ? injuryNotes : "";
        this.ageNotes = ageNotes != null ? ageNotes : "";
        this.cohesionNotes = cohesionNotes != null ? cohesionNotes : "";
        this.depthNotes = depthNotes != null ? depthNotes : "";
        this.qualityNotes = qualityNotes != null ? qualityNotes : "";
        this.pathFatigueAdjustment = pathFatigueAdjustment;
        this.pathFatigueLabel = pathFatigueLabel != null ? pathFatigueLabel : "";
        this.pathOpponent = pathOpponent != null ? pathOpponent : "";
        this.totalElo = baseElo + homeBonus - injuryPenalty + heatBonus - dropoutPenalty + qualBonus
                + preTournamentBonus - squadAgePenalty - squadCohesionPenalty
                - squadDepthPenalty + squadQualityBonus + pathFatigueAdjustment;
        this.qualResults = qualResults != null ? List.copyOf(qualResults) : List.of();
        this.friendlyResults = friendlyResults != null ? List.copyOf(friendlyResults) : List.of();
    }

    public EloBreakdown withPathFatigue(int adjustment, String label, String opponent) {
        return new EloBreakdown(baseElo, isHost, homeBonus,
                injuryLevel, injuryPenalty,
                heatLevel, heatBonus,
                dropoutLevel, dropoutPenalty,
                qualBonus, preTournamentBonus,
                squadAgeLevel, squadAgePenalty,
                squadCohesionLevel, squadCohesionPenalty,
                squadDepthLevel, squadDepthPenalty,
                squadQualityLevel, squadQualityBonus,
                dropoutNotes, injuryNotes, ageNotes, cohesionNotes, depthNotes, qualityNotes,
                qualResults, friendlyResults,
                adjustment, label, opponent);
    }

    public EloBreakdown withPathFatigue(int adjustment, String label) {
        return withPathFatigue(adjustment, label, "");
    }
}

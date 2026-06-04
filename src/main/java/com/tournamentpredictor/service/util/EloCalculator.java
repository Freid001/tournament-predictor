package com.tournamentpredictor.service.util;

import com.tournamentpredictor.config.PredictionConfig;

import java.util.Map;

public class EloCalculator {
    private final double eloWeight;
    private final double qualFormWeight;
    private final double eloScaleDivisor;

    public EloCalculator() {
        this(0.85, 0.15, 400.0);
    }

    public EloCalculator(PredictionConfig config) {
        this(config.getEloWeight(), config.getQualFormWeight(), config.getEloScaleDivisor());
    }

    private EloCalculator(double eloWeight, double qualFormWeight, double eloScaleDivisor) {
        this.eloWeight = eloWeight;
        this.qualFormWeight = qualFormWeight;
        this.eloScaleDivisor = eloScaleDivisor;
    }

    public String computeEloPrediction(String team1, String team2, Map<String, Integer> eloRatings) {
        int rating1 = eloRatings.getOrDefault(team1, 0);
        int rating2 = eloRatings.getOrDefault(team2, 0);
        if (rating1 == 0 && rating2 == 0) {
            return "";
        }
        double team1Probability = 1.0 / (1.0 + Math.pow(10.0, (rating2 - rating1) / eloScaleDivisor));
        int pct = (int) Math.round(team1Probability * 100);
        return rating1 >= rating2 ? team1 + " (" + pct + "%)" : team2 + " (" + (100 - pct) + "%)";
    }

    public String parseTeamFromPrediction(String prediction) {
        if (prediction == null || prediction.isEmpty()) {
            return "";
        }
        int openParen = prediction.lastIndexOf('(');
        return openParen > 0 ? prediction.substring(0, openParen).trim() : prediction.trim();
    }

    public int parsePctFromPrediction(String prediction) {
        if (prediction == null || prediction.isEmpty()) {
            return 50;
        }
        int openParen = prediction.lastIndexOf('(');
        int pctMarker = prediction.lastIndexOf('%');
        if (openParen >= 0 && pctMarker > openParen) {
            try {
                return Integer.parseInt(prediction.substring(openParen + 1, pctMarker).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 50;
    }

    public String combinePredictionsWithQual(String team1Name, String team2Name,
                                             String eloPred, double qualRelativePct, boolean hasQualData) {
        double effectiveQualWeight = hasQualData ? qualFormWeight : 0.0;
        double effectiveEloWeight = eloWeight + (hasQualData ? 0.0 : qualFormWeight);
        double eloPct = extractTeam1Pct(team1Name, eloPred);
        double finalPct = effectiveEloWeight * eloPct + effectiveQualWeight * qualRelativePct;
        int roundedPct = (int) Math.round(finalPct * 100);
        return roundedPct >= 50
                ? team1Name + " (" + roundedPct + "%)"
                : team2Name + " (" + (100 - roundedPct) + "%)";
    }

    private double extractTeam1Pct(String team1Name, String predStr) {
        if (predStr == null || predStr.isEmpty()) {
            return 0.5;
        }
        String winner = parseTeamFromPrediction(predStr);
        int pct = parsePctFromPrediction(predStr);
        return winner.equalsIgnoreCase(team1Name) ? pct / 100.0 : (100 - pct) / 100.0;
    }

    public String applyDisagreeOverride(String doYouDisagree, String eloPrediction, String team1Display, String team2Display) {
        if (!"yes".equalsIgnoreCase(doYouDisagree)) {
            return eloPrediction;
        }
        String eloWinner = parseTeamFromPrediction(eloPrediction);
        int eloPct = parsePctFromPrediction(eloPrediction);
        String team1 = extractTeamName(team1Display);
        String team2 = extractTeamName(team2Display);
        String other = team1.equalsIgnoreCase(eloWinner) ? team2 : team1;
        return other + " (" + (100 - eloPct) + "%)";
    }

    public String extractTeamName(String display) {
        if (display == null) {
            return "";
        }
        int openParen = display.lastIndexOf('(');
        if (openParen >= 0) {
            int closeParen = display.indexOf(')', openParen + 1);
            if (closeParen > openParen) {
                return display.substring(openParen + 1, closeParen);
            }
        }
        return display;
    }
}

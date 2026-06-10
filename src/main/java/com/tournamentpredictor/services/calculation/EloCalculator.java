package com.tournamentpredictor.services.calculation;

import com.tournamentpredictor.config.PredictionConfig;

import java.util.Map;

public class EloCalculator {
    private final double eloScaleDivisor;

    public EloCalculator() {
        this(400.0);
    }

    public EloCalculator(PredictionConfig config) {
        this(config.getEloScaleDivisor());
    }

    private EloCalculator(double eloScaleDivisor) {
        this.eloScaleDivisor = eloScaleDivisor;
    }

    public String computeEloPrediction(String team1, String team2, Map<String, Integer> eloRatings) {
        int rating1 = eloRatings.getOrDefault(team1, 0);
        int rating2 = eloRatings.getOrDefault(team2, 0);
        return computeEloPredictionFromElos(team1, team2, rating1, rating2);
    }

    public String computeEloPredictionFromElos(String team1, String team2, int rating1, int rating2) {
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

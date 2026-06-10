package com.tournamentpredictor.services.calculation;

public class WinningsCalculator {
    public String calculateWinnings(String fractionalOdds, double stake) {
        if (fractionalOdds == null || fractionalOdds.isEmpty()) {
            return "";
        }
        int slash = fractionalOdds.indexOf('/');
        if (slash < 0) {
            return "";
        }
        try {
            double numerator = Double.parseDouble(fractionalOdds.substring(0, slash).trim());
            double denominator = Double.parseDouble(fractionalOdds.substring(slash + 1).trim());
            if (denominator == 0) {
                return "";
            }
            double profit = (numerator / denominator) * stake;
            return String.format("%.2f", profit + stake);
        } catch (NumberFormatException e) {
            return "";
        }
    }
}

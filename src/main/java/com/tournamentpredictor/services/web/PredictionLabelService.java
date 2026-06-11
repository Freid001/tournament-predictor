package com.tournamentpredictor.services.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PredictionLabelService {
    private PredictionLabelService() {
    }

    public static List<String> labelsForPath(String rowPath, String predictedWinner, String actualWinner) {
        String path = normalize(rowPath);
        String predicted = WebText.trim(predictedWinner);
        String actual = WebText.trim(actualWinner);
        boolean hasActual = !actual.isBlank() && !"Draw".equalsIgnoreCase(actual);
        boolean predictionWrong = hasActual && !predicted.isBlank() && !actual.equalsIgnoreCase(predicted);
        List<String> labels = new ArrayList<>();
        switch (path) {
            case "results", "actual" -> labels.add("Result");
            case "fixture" -> labels.add(hasActual ? "Result" : "Fixture");
            case "live" -> labels.add("Live Prediction");
            case "predicted", "prediction", "" -> labels.add("Predicted Matchup");
            case "result_upset" -> labels.add("Result");
            case "upset" -> labels.add("Alternative Matchup");
            default -> labels.add("Alternative Matchup");
        }
        return List.copyOf(labels);
    }

    public static String combinedLabel(String rowPath, String predictedWinner, String actualWinner) {
        return String.join(" / ", labelsForPath(rowPath, predictedWinner, actualWinner));
    }

    public static String pathForActualResult(String predictedWinner, String actualWinner) {
        String predicted = WebText.trim(predictedWinner);
        String actual = WebText.trim(actualWinner);
        if (!predicted.isBlank() && !actual.isBlank() && !"Draw".equalsIgnoreCase(actual)
                && !actual.equalsIgnoreCase(predicted)) {
            return "result_upset";
        }
        return "results";
    }

    public static String pathForLivePrediction(boolean originalPredictionStillValid, boolean wasAlternativePath) {
        return originalPredictionStillValid || wasAlternativePath ? "live" : "predicted";
    }

    private static String normalize(String value) {
        return WebText.trim(value).toLowerCase(Locale.ROOT);
    }
}

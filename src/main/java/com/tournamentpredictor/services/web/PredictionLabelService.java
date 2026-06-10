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
            case "results", "actual" -> labels.add("Results");
            case "fixture" -> labels.add(hasActual ? "Fixture / Results" : "Fixture");
            case "live" -> {
                labels.add("Predicted");
                labels.add("Live");
            }
            case "predicted", "prediction", "" -> {
                labels.add("Predicted");
                if (predictionWrong) {
                    labels.add("Wrong");
                } else if (hasActual) {
                    labels.add("Live");
                }
            }
            case "result_upset" -> {
                labels.add("Results");
                labels.add("Upset");
            }
            case "upset" -> labels.add("Upset");
            default -> labels.add("Alternative");
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

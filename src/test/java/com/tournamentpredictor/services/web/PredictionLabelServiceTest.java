package com.tournamentpredictor.services.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PredictionLabelServiceTest {

    @Test
    void predictedStaysPredictedWhenNoResultExists() {
        assertEquals(List.of("Predicted"),
                PredictionLabelService.labelsForPath("predicted", "France", ""));
    }

    @Test
    void predictedBecomesPredictedWrongWhenOriginalPredictionLoses() {
        assertEquals("Predicted / Wrong",
                PredictionLabelService.combinedLabel("predicted", "France", "Spain"));
    }

    @Test
    void predictedBecomesPredictedLiveWhenOriginalPredictionWins() {
        assertEquals("Predicted / Live",
                PredictionLabelService.combinedLabel("predicted", "France", "France"));
    }

    @Test
    void livePredictionUsesTwoLabels() {
        assertEquals(List.of("Predicted", "Live"),
                PredictionLabelService.labelsForPath("live", "Brazil", ""));
    }

    @Test
    void fixtureLabelGainsResultsWhenActualWinnerExists() {
        assertEquals("Fixture",
                PredictionLabelService.combinedLabel("fixture", "Brazil", ""));
        assertEquals("Fixture / Results",
                PredictionLabelService.combinedLabel("fixture", "Brazil", "Germany"));
    }

    @Test
    void resultUpsetShowsResultsAndUpsetLabels() {
        assertEquals(List.of("Results", "Upset"),
                PredictionLabelService.labelsForPath("result_upset", "France", "Morocco"));
    }

    @Test
    void actualResultPathMarksUpsetComparedToPrediction() {
        assertEquals("result_upset", PredictionLabelService.pathForActualResult("France", "Morocco"));
        assertEquals("results", PredictionLabelService.pathForActualResult("France", "France"));
    }

    @Test
    void alternativePathCanBecomeLivePredictionPath() {
        assertEquals("live", PredictionLabelService.pathForLivePrediction(false, true));
    }
}

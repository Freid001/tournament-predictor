package com.tournamentpredictor.services.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PredictionLabelServiceTest {

    @Test
    void predictedStaysPredictedWhenNoResultExists() {
        assertEquals(List.of("Predicted Matchup"),
                PredictionLabelService.labelsForPath("predicted", "France", ""));
    }

    @Test
    void predictedMatchupTextStaysStableWhenOriginalPredictionLoses() {
        assertEquals("Predicted Matchup",
                PredictionLabelService.combinedLabel("predicted", "France", "Spain"));
    }

    @Test
    void predictedStaysPredictedWhenOriginalPredictionWins() {
        assertEquals("Predicted Matchup",
                PredictionLabelService.combinedLabel("predicted", "France", "France"));
    }

    @Test
    void livePredictionUsesTwoLabels() {
        assertEquals(List.of("Live Prediction"),
                PredictionLabelService.labelsForPath("live", "Brazil", ""));
    }

    @Test
    void fixtureLabelBecomesResultsWhenActualWinnerExists() {
        assertEquals("Fixture",
                PredictionLabelService.combinedLabel("fixture", "Brazil", ""));
        assertEquals("Result",
                PredictionLabelService.combinedLabel("fixture", "Brazil", "Germany"));
    }

    @Test
    void resultUpsetShowsResultsAndUpsetLabels() {
        assertEquals(List.of("Result", "Upset"),
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

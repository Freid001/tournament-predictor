package com.tournamentpredictor.services.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultsModeServiceTest {

    @Test
    void defaultsToResultsWhenStoredResultsExist() {
        assertTrue(ResultsModeService.shouldShowResults(null, null, true));
    }

    @Test
    void defaultsToPredictionOnlyWhenNoStoredResultsExist() {
        assertFalse(ResultsModeService.shouldShowResults(null, null, false));
    }

    @Test
    void resultsParameterOverridesStoredResults() {
        assertFalse(ResultsModeService.shouldShowResults(false, null, true));
        assertTrue(ResultsModeService.shouldShowResults(true, null, false));
    }

    @Test
    void legacyActualParameterStillWorksWhenResultsParameterMissing() {
        assertTrue(ResultsModeService.shouldShowResults(null, true, false));
        assertFalse(ResultsModeService.shouldShowResults(null, false, true));
    }

    @Test
    void resultsParameterWinsOverLegacyActualParameter() {
        assertFalse(ResultsModeService.shouldShowResults(false, true, true));
        assertTrue(ResultsModeService.shouldShowResults(true, false, false));
    }
}

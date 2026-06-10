package com.tournamentpredictor.services.web;

public final class ResultsModeService {
    private ResultsModeService() {
    }

    public static boolean shouldShowResults(Boolean results, Boolean legacyActual, boolean hasStoredResults) {
        if (results != null) {
            return results;
        }
        if (legacyActual != null) {
            return legacyActual;
        }
        return hasStoredResults;
    }
}

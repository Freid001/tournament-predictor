package com.tournamentpredictor.services.prediction.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredictionsFileValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    void validatePredictionsFile_acceptsHeaderDrivenColumns() throws IOException {
        Path file = tempDir.resolve("last_32.csv");
        Files.write(file, List.of(
                "match_id,team1,team2,path,elo,history_competitions,history_friendlies,prediction",
                "M1,England,Germany,predicted,England (60%),England (55%),England (54%),England (60%)",
                "M1,England,Germany,alt,England (60%),England (55%),England (54%),"
        ));

        assertDoesNotThrow(() -> new PredictionsFileValidator().validatePredictionsFile(file));
    }

    @Test
    void validatePredictionsFile_rejectsMissingRequiredHeaders() throws IOException {
        Path file = tempDir.resolve("bad_headers.csv");
        Files.write(file, List.of(
                "match_id,team1,path,elo",
                "M1,England,predicted,England (60%)"
        ));

        IOException error = assertThrows(IOException.class,
                () -> new PredictionsFileValidator().validatePredictionsFile(file));

        assertTrue(error.getMessage().contains("Header must include match_id, team1, team2, and path columns"));
    }

    @Test
    void validatePredictionsFile_rejectsMatchWithoutPrimaryPrediction() throws IOException {
        Path file = tempDir.resolve("no_primary.csv");
        Files.write(file, List.of(
                "match_id,team1,team2,path,elo",
                "M1,England,Germany,alt,England (60%)"
        ));

        IOException error = assertThrows(IOException.class,
                () -> new PredictionsFileValidator().validatePredictionsFile(file));

        assertTrue(error.getMessage().contains("Match M1: expected at least 1 primary row, got 0"));
    }
}

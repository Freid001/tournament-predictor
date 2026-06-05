package com.tournamentpredictor.service.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PredictionsFileValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    void validatePredictionsFile_acceptsHeaderDrivenColumns() throws IOException {
        Path file = tempDir.resolve("last_32.csv");
        Files.write(file, List.of(
                "match_id,team1,team2,path,elo,history_competitions,history_friendlies,prediction,do_you_disagree",
                "M1,England,Germany,predicted,England (60%),England (55%),England (54%),yes",
                "M1,England,Germany,alt,England (60%),England (55%),England (54%),"
        ));

        assertDoesNotThrow(() -> new PredictionsFileValidator().validatePredictionsFile(file));
    }
}

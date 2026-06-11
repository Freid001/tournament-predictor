package com.tournamentpredictor.services.web;

import com.tournamentpredictor.services.report.HtmlReporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebControllerServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void ensureLiveSimulationExistsReturnsWhenRoundMissing() {
        WebControllerService service = new WebControllerService(null);

        assertDoesNotThrow(() -> service.ensureLiveSimulationExists("world_cup_2026", null));
    }

    @Test
    void ensureLast32PredictionSeedReturnsWhenSeedAlreadyExists() throws IOException {
        WebControllerService service = tempRootService();
        Path seed = service.predictionFile("test", "last_32.csv");
        Files.createDirectories(seed.getParent());
        Files.writeString(seed, "match_id,team1,team2,path,elo,prediction\n");

        assertDoesNotThrow(() -> service.ensureLast32PredictionSeed("test", new HtmlReporter()));
    }

    @Test
    void ensureLast32PredictionSeedRequiresCompletedGroupStage() {
        WebControllerService service = tempRootService();

        IOException error = assertThrows(IOException.class,
                () -> service.ensureLast32PredictionSeed("test", new HtmlReporter()));

        assertEquals("Run Group Stage before running Last 32.", error.getMessage());
    }

    private WebControllerService tempRootService() {
        return new WebControllerService(null) {
            @Override
            public Path predictionFile(String tournament, String fileName) {
                return tempDir.resolve("data/predictions").resolve(tournament).resolve(fileName);
            }

            @Override
            public Path simulationFile(String tournament, String fileName) {
                return tempDir.resolve("data/simulations").resolve(tournament).resolve(fileName);
            }
        };
    }
}

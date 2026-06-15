package com.tournamentpredictor.services.prediction.handler;

import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PredictionScorer;
import com.tournamentpredictor.services.io.CsvHelper;
import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.prediction.validation.PredictionsFileValidator;
import com.tournamentpredictor.services.report.ConsoleReporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.tournamentpredictor.services.storage.GeneratedDataStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnockoutRoundFileServiceTest {
    @TempDir
    Path projectRoot;

    @Test
    void validatePredictionFileReportsPreviousModeWhenFileIsMissing() {
        KnockoutRoundFileService service = service();
        Path missing = projectRoot.resolve("data/predictions/test/last_8.csv");

        IOException error = assertThrows(IOException.class,
                () -> service.validatePredictionFile(missing, "last_16"));

        assertTrue(error.getMessage().contains("Run mode=last_16 first"));
    }

    @Test
    void readRequiredSimulationRowsLoadsExpectedRoundFile() throws IOException {
        KnockoutRoundFileService service = service();
        Path file = projectRoot.resolve("data/simulations/test/matchup_paths_last_16.csv");
        Files.createDirectories(file.getParent());
        Files.write(file, List.of("match_id,team1,team2,path,elo", "M1,Spain,Germany,predicted,Spain (55%)"));

        List<String> rows = service.readRequiredSimulationRows("test", "last_16", "last_16");

        assertEquals(2, rows.size());
        assertEquals("M1,Spain,Germany,predicted,Spain (55%)", rows.get(1));
    }


    @Test
    void reportLockedMatchupsDetectsCsvOnlyGeneratedOutput() throws IOException {
        String previous = System.getProperty("tournament.generated.exportCsv");
        System.setProperty("tournament.generated.exportCsv", "false");
        try {
            Path file = projectRoot.resolve("data/simulations/test/matchup_paths_last_16.csv");
            new GeneratedDataStore(projectRoot).writeLines(file,
                    List.of("match_id,team1,team2,path,elo", "M1,Spain,Germany,predicted,Spain (55%)"));
            assertFalse(Files.exists(file));

            boolean locked = service().reportLockedMatchups(file, "Last 16 matchups", null,
                    java.util.Map.of(), new ConsoleReporter(), new EloCalculator(), "locked: ");

            assertTrue(locked);
        } finally {
            if (previous == null) {
                System.clearProperty("tournament.generated.exportCsv");
            } else {
                System.setProperty("tournament.generated.exportCsv", previous);
            }
        }
    }

    @Test
    void readRequiredSimulationRowsLoadsFromCsv() throws IOException {
        String previous = System.getProperty("tournament.generated.exportCsv");
        System.setProperty("tournament.generated.exportCsv", "false");
        try {
            Path file = projectRoot.resolve("data/simulations/test/matchup_paths_last_16.csv");
            new GeneratedDataStore(projectRoot).writeLines(file,
                    List.of("match_id,team1,team2,path,elo", "M1,Spain,Germany,predicted,Spain (55%)"));
            assertFalse(Files.exists(file));

            List<String> rows = service().readRequiredSimulationRows("test", "last_16", "last_16");

            assertEquals(2, rows.size());
            assertEquals("M1,Spain,Germany,predicted,Spain (55%)", rows.get(1));
        } finally {
            if (previous == null) {
                System.clearProperty("tournament.generated.exportCsv");
            } else {
                System.setProperty("tournament.generated.exportCsv", previous);
            }
        }
    }

    private KnockoutRoundFileService service() {
        EloCalculator eloCalculator = new EloCalculator();
        return new KnockoutRoundFileService(
                new CsvLoader(projectRoot),
                projectRoot,
                new CsvHelper(),
                new PredictionsFileValidator(),
                new PredictionScorer(eloCalculator)
        );
    }
}

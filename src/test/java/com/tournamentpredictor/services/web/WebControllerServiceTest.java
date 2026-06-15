package com.tournamentpredictor.services.web;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.services.report.HtmlReporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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


    @Test
    void writeCsvCanPersistGeneratedRowsToCsv() throws IOException {
        String previous = System.getProperty("tournament.generated.exportCsv");
        System.setProperty("tournament.generated.exportCsv", "false");
        try {
            WebControllerService service = new WebControllerService(null, tempDir);
            Path groups = service.predictionFile("test", "groups.csv");

            service.writeCsv(groups, java.util.List.of("team", "group", "predicted_position"), java.util.List.of(
                    java.util.Map.of("team", "Spain", "group", "B", "predicted_position", "1"),
                    java.util.Map.of("team", "England", "group", "C", "predicted_position", "2")));

            assertTrue(Files.exists(groups));
            assertEquals(2, service.readCsv(groups).rows().size());
            assertEquals("Spain", service.readCsv(groups).rows().get(0).get("team"));
        } finally {
            if (previous == null) {
                System.clearProperty("tournament.generated.exportCsv");
            } else {
                System.setProperty("tournament.generated.exportCsv", previous);
            }
        }
    }

    @Test
    void progressStatusUsesCsvOnlyStartData() throws IOException {
        String previous = System.getProperty("tournament.generated.exportCsv");
        System.setProperty("tournament.generated.exportCsv", "false");
        try {
            WebControllerService service = new WebControllerService(null, tempDir);
            Path start = service.predictionFile("test", "start.csv");

            service.writeCsv(start, java.util.List.of("group", "team", "host", "injury_impact"), java.util.List.of(
                    java.util.Map.of("group", "A", "team", "England", "host", "no", "injury_impact", "0")));

            assertFalse(Files.exists(start));
            assertEquals(1, service.completedStepCount("test"));
            assertEquals("Team Setup complete", service.describeCurrentStage("test"));
        } finally {
            if (previous == null) {
                System.clearProperty("tournament.generated.exportCsv");
            } else {
                System.setProperty("tournament.generated.exportCsv", previous);
            }
        }
    }

    @Test
    void deletePathsEvictsCsvOnlyGeneratedData() throws IOException {
        String previous = System.getProperty("tournament.generated.exportCsv");
        System.setProperty("tournament.generated.exportCsv", "false");
        try {
            WebControllerService service = new WebControllerService(null, tempDir);
            Path groups = service.predictionFile("test", "groups.csv");

            service.writeCsv(groups, java.util.List.of("team", "group", "predicted_position"), java.util.List.of(
                    java.util.Map.of("team", "Spain", "group", "B", "predicted_position", "1")));
            assertTrue(Files.exists(groups));
            assertEquals(1, service.readCsv(groups).rows().size());

            service.deletePaths(java.util.List.of(groups));

            assertFalse(service.generatedDataExists(groups));
            assertEquals(0, service.readCsv(groups).rows().size());
        } finally {
            if (previous == null) {
                System.clearProperty("tournament.generated.exportCsv");
            } else {
                System.setProperty("tournament.generated.exportCsv", previous);
            }
        }
    }

    @Test
    void directOpeningMatchupsUseCsvOnlySourceMetadata() throws IOException {
        String previous = System.getProperty("tournament.generated.exportCsv");
        System.setProperty("tournament.generated.exportCsv", "false");
        try {
            WebControllerService service = new WebControllerService(new TestPredictionConfig(), tempDir);
            Path snapshotDir = tempDir.resolve("data/elo/snapshots/test");
            Files.createDirectories(snapshotDir);
            Files.writeString(snapshotDir.resolve("teams.csv"), String.join("\n",
                    "rank,team_code,team_name,rating",
                    "1,ENG,England,2000",
                    "2,SPA,Spain,1900"
            ));
            Path bracketDir = tempDir.resolve("data/bracket");
            Files.createDirectories(bracketDir);
            Files.writeString(bracketDir.resolve("test.csv"), String.join("\n",
                    "type,stage,match_id,team1,team2",
                    "match,LAST_16,M89,A1,B2"
            ));
            service.writeCsv(service.predictionFile("test", "groups.csv"),
                    java.util.List.of("group", "team", "elo_ranking", "group_winner", "runner_up", "3rd_place"),
                    java.util.List.of(
                            java.util.Map.of("group", "A", "team", "England", "elo_ranking", "2000", "group_winner", "yes", "runner_up", "no", "3rd_place", "no"),
                            java.util.Map.of("group", "B", "team", "Spain", "elo_ranking", "1900", "group_winner", "no", "runner_up", "yes", "3rd_place", "no")));
            service.writeCsv(service.simulationFile("test", "simulation_scorelines_last_16.csv"),
                    java.util.List.of("stage", "match_id", "team1", "team2", "scoreline", "winner", "count", "scoreline_pct", "matchup_runs", "matchup_pct", "simulation_runs", "simulation_seed"),
                    java.util.List.of(java.util.Map.ofEntries(
                            java.util.Map.entry("stage", "last_16"),
                            java.util.Map.entry("match_id", "M89"),
                            java.util.Map.entry("team1", "England"),
                            java.util.Map.entry("team2", "Spain"),
                            java.util.Map.entry("scoreline", "2-1"),
                            java.util.Map.entry("winner", "England"),
                            java.util.Map.entry("count", "10"),
                            java.util.Map.entry("scoreline_pct", "100"),
                            java.util.Map.entry("matchup_runs", "10"),
                            java.util.Map.entry("matchup_pct", "100"),
                            java.util.Map.entry("simulation_runs", "10"),
                            java.util.Map.entry("simulation_seed", "99"))));
            Path source = service.simulationFile("test", "matchup_paths_last_16.csv");
            service.writeCsv(source,
                    java.util.List.of("match_id", "team1", "team2", "path", "prediction", "team1_slot", "team1_team", "team1_source_match", "team1_group_finish", "team1_bracket_slot", "team2_slot", "team2_team", "team2_source_match", "team2_group_finish", "team2_bracket_slot"),
                    java.util.List.of(java.util.Map.ofEntries(
                            java.util.Map.entry("match_id", "M89"),
                            java.util.Map.entry("team1", "England"),
                            java.util.Map.entry("team2", "Spain"),
                            java.util.Map.entry("path", "predicted"),
                            java.util.Map.entry("prediction", "England"),
                            java.util.Map.entry("team1_slot", "A1"),
                            java.util.Map.entry("team1_team", "England"),
                            java.util.Map.entry("team1_source_match", ""),
                            java.util.Map.entry("team1_group_finish", "A1"),
                            java.util.Map.entry("team1_bracket_slot", "A1"),
                            java.util.Map.entry("team2_slot", "B2"),
                            java.util.Map.entry("team2_team", "Spain"),
                            java.util.Map.entry("team2_source_match", ""),
                            java.util.Map.entry("team2_group_finish", "B2"),
                            java.util.Map.entry("team2_bracket_slot", "B2"))));
            assertFalse(Files.exists(source));

            service.writeDirectOpeningMatchups("test", "last_16");

            Path output = service.matchupViewFile("test", "last_16.csv");
            assertFalse(Files.exists(output));
            java.util.Map<String, String> row = service.readCsv(output).rows().get(0);
            assertEquals("A1", row.get("team1_group_finish"));
            assertEquals("B2", row.get("team2_group_finish"));
        } finally {
            if (previous == null) {
                System.clearProperty("tournament.generated.exportCsv");
            } else {
                System.setProperty("tournament.generated.exportCsv", previous);
            }
        }
    }

    @Test
    void actualPathFatigueUsesCsvOnlyResultRows() throws IOException {
        String previous = System.getProperty("tournament.generated.exportCsv");
        System.setProperty("tournament.generated.exportCsv", "false");
        try {
            WebControllerService service = new WebControllerService(new TestPredictionConfig(), tempDir);
            Path snapshotDir = tempDir.resolve("data/elo/snapshots/test");
            Files.createDirectories(snapshotDir);
            Files.writeString(snapshotDir.resolve("teams.csv"), String.join("\n",
                    "rank,team_code,team_name,rating",
                    "1,SPA,Spain,2000",
                    "2,ENG,England,1900"
            ));
            Path result = service.resultsFile("test", "last_16");

            service.writeCsv(result, java.util.List.of("match_id", "team1", "team2", "winner", "home_score", "away_score", "penalties"), java.util.List.of(
                    java.util.Map.of("match_id", "M89", "team1", "England", "team2", "Spain",
                            "winner", "England", "home_score", "2", "away_score", "1", "penalties", "no")));

            assertFalse(Files.exists(result));
            assertEquals(1, service.loadActualRoundResultRows("test", "last_16").size());
            assertEquals(188, service.actualPathFatigueByTeam("test", "last_8").get("England"));
        } finally {
            if (previous == null) {
                System.clearProperty("tournament.generated.exportCsv");
            } else {
                System.setProperty("tournament.generated.exportCsv", previous);
            }
        }
    }

    @Test
    void hasAnyRoundResultsUsesCsvOnlyResults() throws IOException {
        String previous = System.getProperty("tournament.generated.exportCsv");
        System.setProperty("tournament.generated.exportCsv", "false");
        try {
            WebControllerService service = new WebControllerService(null, tempDir);
            Path result = service.resultsFile("test", "last_16");

            service.writeCsv(result,
                    java.util.List.of("match_id", "team1", "team2", "winner", "home_score", "away_score"),
                    java.util.List.of(java.util.Map.of("match_id", "M1", "team1", "Spain", "team2", "Germany",
                            "winner", "Spain", "home_score", "2", "away_score", "1")));

            assertFalse(Files.exists(result));
            assertEquals(true, service.hasAnyRoundResults("test"));
        } finally {
            if (previous == null) {
                System.clearProperty("tournament.generated.exportCsv");
            } else {
                System.setProperty("tournament.generated.exportCsv", previous);
            }
        }
    }

    @Test
    void readCsvUsesCsvForGeneratedPredictionFiles() throws IOException {
        WebControllerService service = new WebControllerService(null, tempDir);
        Path groups = service.predictionFile("test", "groups.csv");
        service.writeCsv(groups, java.util.List.of("team", "group", "predicted_position"),
                java.util.List.of(java.util.Map.of("team", "Spain", "group", "B", "predicted_position", "1")));

        assertEquals(1, service.readCsv(groups).rows().size());
        assertEquals("Spain", service.readCsv(groups).rows().get(0).get("team"));
        service.writeCsv(groups, java.util.List.of("team", "group", "predicted_position"),
                java.util.List.of(
                        java.util.Map.of("team", "Spain", "group", "B", "predicted_position", "1"),
                        java.util.Map.of("team", "England", "group", "C", "predicted_position", "2")));

        assertEquals(2, service.readCsv(groups).rows().size());
    }

    @Test
    void readCsvUsesCsvForGeneratedSimulationFiles() throws IOException {
        WebControllerService service = new WebControllerService(null, tempDir);
        Path scorelines = service.simulationFile("test", "simulation_scorelines_last_32.csv");
        service.writeCsv(scorelines, java.util.List.of("stage", "match_id", "team1", "team2", "winner", "matchup_pct"),
                java.util.List.of(java.util.Map.of("stage", "last_32", "match_id", "M1", "team1", "Spain",
                        "team2", "Germany", "winner", "Spain", "matchup_pct", "58.2")));

        assertEquals(1, service.readCsv(scorelines).rows().size());
        assertEquals("M1", service.readCsv(scorelines).rows().get(0).get("match_id"));
    }

    static class TestPredictionConfig extends PredictionConfig {
        @Override
        public int getPathFatigueTournamentAvgElo() { return 1850; }
        @Override
        public double getPathFatigueStageMultLast16() { return 1.0; }
        @Override
        public double getPathFatigueUpsetMultiplier() { return 1.25; }
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

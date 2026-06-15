package com.tournamentpredictor.services.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsvLoaderSnapshotTest {

    @TempDir
    Path root;

    @Test
    void loadEloForTournament_requiresSnapshotEvenWhenCurrentExists() throws IOException {
        Path current = root.resolve("data/elo/current");
        Files.createDirectories(current);
        Files.writeString(current.resolve("world.csv"), """
                rank,team_code,team_name,rating
                1,EN,England,2000
                """);

        IOException error = assertThrows(IOException.class,
                () -> new CsvLoader(root).loadEloForTournament("test"));

        assertTrue(error.getMessage().contains("Tournament snapshot not found"));
        assertTrue(error.getMessage().contains("snapshot-refresh"));
    }

    @Test
    void tournamentEloAndHistoryUseSnapshotOnly() throws IOException {
        Path current = root.resolve("data/elo/current");
        Files.createDirectories(current.resolve("history"));
        Files.writeString(current.resolve("world.csv"), """
                rank,team_code,team_name,rating
                1,EN,England,2000
                """);

        Path snapshot = root.resolve("data/elo/snapshots/test");
        Files.createDirectories(snapshot.resolve("history"));
        Files.writeString(snapshot.resolve("teams.csv"), """
                rank,team_code,team_name,rating
                1,EN,England,1900
                """);
        Files.writeString(snapshot.resolve("history/England.tsv"), "header\n");

        CsvLoader loader = new CsvLoader(root);
        Map<String, Integer> elo = loader.loadEloForTournament("test");

        assertEquals(1900, elo.get("England"));
        assertEquals(snapshot.resolve("history"), loader.historyDirForTournament("test"));
    }

    @Test
    void snapshotMetadataOverridesTournamentPropertiesForReplaySettings() throws IOException {
        Path tournamentDir = root.resolve("data/predictions/test");
        Files.createDirectories(tournamentDir);
        Files.writeString(tournamentDir.resolve("tournament.properties"), """
                qual.form.since.year=2020
                qual.form.until.year=2021
                """);

        Path snapshot = root.resolve("data/elo/snapshots/test");
        Files.createDirectories(snapshot);
        Files.writeString(snapshot.resolve("teams.csv"), "rank,team_code,team_name,rating\n");
        Files.writeString(snapshot.resolve("metadata.properties"), """
                qual_form_since=2023
                qual_form_until=2026
                """);

        CsvLoader loader = new CsvLoader(root);

        assertEquals(2023, loader.resolveSnapshotBackedSetting(
                "test", "qual.form.since.year", "qual_form_since", 1999));
        assertEquals(2026, loader.resolveSnapshotBackedSetting(
                "test", "qual.form.until.year", "qual_form_until", 1999));
    }

}

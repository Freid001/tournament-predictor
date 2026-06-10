package com.tournamentpredictor.services.snapshot;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.services.io.CsvLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TournamentSnapshotHandlerTest {

    @TempDir
    Path root;

    @Test
    void snapshotRefreshCopiesOnlyTournamentTeamsAndConfiguredHistoryWindow() throws IOException {
        writeStartCsv("world_cup_test", "England", "USA");
        writeTournamentProperties("world_cup_test");
        writeCurrentWorldCsv();
        writeHistory("England",
                row(2023, "England", "A", 1, 0, "WQ"),
                row(2024, "England", "B", 2, 0, "WQ"),
                row(2026, "England", "C", 3, 0, "F"),
                eloRowDate(2026, 6, 10, "England", "BeforeElo", 1, 0, "F", 1910, 1700),
                rowDate(2026, 6, 11, "England", "OpeningDay", 5, 0, "F"),
                rowDate(2026, 6, 12, "England", "After", 5, 0, "F"),
                row(2027, "England", "D", 4, 0, "F"));
        writeHistory("USA",
                row(2024, "USA", "A", 1, 1, "WQ"));
        writeHistory("Germany",
                row(2024, "Germany", "A", 5, 0, "WQ"));

        TournamentSnapshotHandler handler = new TournamentSnapshotHandler(
                new CsvLoader(root), root, new PredictionConfig());
        handler.handle("world_cup_test");

        Path snapshot = root.resolve("data/elo/snapshots/world_cup_test");
        List<String> teams = Files.readAllLines(snapshot.resolve("teams.csv"));
        assertEquals(3, teams.size(), "header plus two tournament teams");
        assertTrue(teams.stream().anyMatch(line -> line.contains("England")));
        assertTrue(teams.stream().anyMatch(line -> line.contains("England,1910")),
                "ELO must come from the last match before the tournament rather than current world.csv");
        assertTrue(teams.stream().anyMatch(line -> line.contains("USA")));
        assertFalse(teams.stream().anyMatch(line -> line.contains("Germany")));

        List<String> englandHistory = Files.readAllLines(snapshot.resolve("history/England.tsv"));
        assertEquals(4, englandHistory.size(), "header plus rows strictly before the tournament start date");
        assertFalse(englandHistory.stream().anyMatch(line -> line.startsWith("2023	")));
        assertTrue(englandHistory.stream().anyMatch(line -> line.startsWith("2024	")));
        assertTrue(englandHistory.stream().anyMatch(line -> line.startsWith("2026	")));
        assertFalse(englandHistory.stream().anyMatch(line -> line.contains("OpeningDay")));
        assertFalse(englandHistory.stream().anyMatch(line -> line.contains("After")));
        assertFalse(englandHistory.stream().anyMatch(line -> line.startsWith("2027\t")));

        String metadata = Files.readString(snapshot.resolve("metadata.properties"));
        assertTrue(metadata.contains("elo_source=data/elo/current/world.csv"));
        assertTrue(metadata.contains("history_source=data/elo/current/history"));
        assertTrue(metadata.contains("qual_form_since=2024"));
        assertTrue(metadata.contains("qual_form_until=2025"));
        assertTrue(metadata.contains("pre_tournament_form_since=2026"));
        assertTrue(metadata.contains("pre_tournament_form_until=2026"));
        assertTrue(metadata.contains("tournament_start_date=2026-06-11"));
    }

    @Test
    void snapshotRoundInferenceUsesMatchCounts() throws Exception {
        TournamentSnapshotHandler handler = new TournamentSnapshotHandler(new CsvLoader(root), root, new PredictionConfig());
        var startField = TournamentSnapshotHandler.class.getDeclaredField("tournamentStartDate");
        startField.setAccessible(true);
        startField.set(handler, java.time.LocalDate.parse("2026-06-11"));

        var roundMethod = TournamentSnapshotHandler.class.getDeclaredMethod("roundForMatchIndex", int.class);
        roundMethod.setAccessible(true);

        assertEquals("group", roundMethod.invoke(handler, 1));
        assertEquals("group", roundMethod.invoke(handler, 72));
        assertEquals("last_32", roundMethod.invoke(handler, 73));
        assertEquals("last_16", roundMethod.invoke(handler, 89));
        assertEquals("last_8", roundMethod.invoke(handler, 97));
        assertEquals("last_4", roundMethod.invoke(handler, 101));
        assertEquals("third_place", roundMethod.invoke(handler, 103));
        assertEquals("final", roundMethod.invoke(handler, 104));
    }

    @Test
    void snapshotRefreshUsesHistoricalAliasWhenSelectingPreTournamentRating() throws IOException {
        writeStartCsv("euros_test", "Czechia");
        writeTournamentProperties("euros_test");
        Path current = root.resolve("data/elo/current");
        Files.createDirectories(current);
        Files.writeString(current.resolve("world.csv"), """
                rank,team_code,team_name,rating
                1,CZ,Czechia,1800
                """);
        writeHistory("Czechia",
                eloRowDate(2026, 6, 10, "Czech Republic", "BeforeElo", 1, 0, "F", 1910, 1700));

        new TournamentSnapshotHandler(new CsvLoader(root), root, new PredictionConfig()).handle("euros_test");

        List<String> teams = Files.readAllLines(root.resolve("data/elo/snapshots/euros_test/teams.csv"));
        assertTrue(teams.stream().anyMatch(line -> line.contains("Czechia,1910")));
    }

    private void writeStartCsv(String tournament, String... teams) throws IOException {
        Path dir = root.resolve("data/predictions").resolve(tournament);
        Files.createDirectories(dir);
        StringBuilder csv = new StringBuilder("group,team,host,injury_impact\n");
        char group = 'A';
        for (String team : teams) {
            csv.append(group).append(',').append(team).append(",no,0\n");
        }
        Files.writeString(dir.resolve("start.csv"), csv.toString());
    }

    private void writeTournamentProperties(String tournament) throws IOException {
        Path dir = root.resolve("data/predictions").resolve(tournament);
        Files.writeString(dir.resolve("tournament.properties"), """
                qual.form.since.year=2024
                qual.form.until.year=2025
                pre.tournament.form.since.year=2026
                pre.tournament.form.until.year=2026
                tournament.start.date=2026-06-11
                """);
    }

    private void writeCurrentWorldCsv() throws IOException {
        Path dir = root.resolve("data/elo/current");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("world.csv"), """
                rank,team_code,team_name,rating
                1,EN,England,2000
                2,US,USA,1800
                3,DE,Germany,1900
                """);
    }

    private void writeHistory(String team, String... rows) throws IOException {
        Path dir = root.resolve("data/elo/current/history");
        Files.createDirectories(dir);
        StringBuilder tsv = new StringBuilder("year\tmonth\tday\thome_team\taway_team\thome_score\taway_score\tmatch_type\n");
        for (String row : rows) {
            tsv.append(row).append("\n");
        }
        Files.writeString(dir.resolve(team + ".tsv"), tsv.toString());
    }

    private static String row(int year, String home, String away, int homeScore, int awayScore, String type) {
        return rowDate(year, 1, 1, home, away, homeScore, awayScore, type);
    }

    private static String eloRowDate(int year, int month, int day, String home, String away,
                                     int homeScore, int awayScore, String type, int homeElo, int awayElo) {
        return rowDate(year, month, day, home, away, homeScore, awayScore, type)
                + "\t\t0\t" + homeElo + "\t" + awayElo;
    }

    private static String rowDate(int year, int month, int day, String home, String away,
                                  int homeScore, int awayScore, String type) {
        return year + "\t" + month + "\t" + day + "\t" + home + "\t" + away + "\t"
                + homeScore + "\t" + awayScore + "\t" + type;
    }
}

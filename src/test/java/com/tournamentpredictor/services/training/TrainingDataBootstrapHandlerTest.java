package com.tournamentpredictor.services.training;

import com.tournamentpredictor.services.history.HistoricalProfileProvider;
import com.tournamentpredictor.services.storage.GeneratedDataStore;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingDataBootstrapHandlerTest {
    @TempDir
    Path tempDir;

    private final TrainingDataBootstrapHandler handler = new TrainingDataBootstrapHandler(
            Path.of("."), HttpClient.newHttpClient(), null, null, null);

    @Test
    void parsesNumericEuroGroupsAndCanonicalizesCis() throws Exception {
        String source = """
                Group 1 | Sweden  Denmark  France  England
                Group 2 | Netherlands  Scotland  Germany  CIS

                ▪ Group 1
                Sweden 1-1 France
                Denmark 0-0 England
                France 0-0 England
                Sweden 1-0 Denmark
                Sweden 2-1 England
                France 1-2 Denmark

                ▪ Group 2
                Netherlands 1-0 Scotland
                CIS 1-1 Germany
                Scotland 0-2 Germany
                Netherlands 0-0 CIS
                Netherlands 3-1 Germany
                Scotland 3-0 CIS
                """;

        var parsed = handler.parse(source, tournament("euros_1992"));

        assertEquals(Set.of("A", "B"), parsed.groups().keySet());
        assertEquals(12, parsed.matches().size());
        assertTrue(parsed.teams().contains("Russia"));
    }

    @Test
    void doesNotMatchUsaInsideBusan() throws Exception {
        String source = """
                Group D | South Korea  USA  Portugal  Poland
                ▪ Group D
                South Korea 2-0 Poland @ Busan
                USA 3-2 Portugal
                South Korea 1-1 USA
                Portugal 4-0 Poland
                Portugal 0-1 South Korea
                Poland 3-1 USA
                """;

        var parsed = handler.parse(source, tournament("world_cup_2002"));

        assertEquals(6, parsed.matches().size());
        assertEquals("South Korea", parsed.matches().get(0).team1());
        assertEquals("Poland", parsed.matches().get(0).team2());
        assertTrue(parsed.teams().contains("United States"));
    }

    @Test
    void acceptsIvoryCoastSourceAliasInMatchLines() throws Exception {
        String source = """
                Group C | Colombia  Greece  Ivory Coast  Japan
                ▪ Group C
                Colombia 3-0 Greece
                Côte d'Ivoire 2-1 Japan
                Colombia 2-1 Côte d'Ivoire
                Japan 0-0 Greece
                Japan 1-4 Colombia
                Greece 2-1 Côte d'Ivoire
                """;

        var parsed = handler.parse(source, tournament("world_cup_2014"));

        assertEquals(6, parsed.matches().size());
        assertTrue(parsed.teams().contains("Ivory Coast"));
        assertEquals("Ivory Coast", parsed.matches().get(1).team1());
    }

    @Test
    void writeTournamentWritesStartToCsvAndDeletesStaleGroups() throws Exception {
        String previous = System.getProperty("tournament.generated.exportCsv");
        System.setProperty("tournament.generated.exportCsv", "false");
        try {
            TrainingDataBootstrapHandler local = new TrainingDataBootstrapHandler(
                    tempDir, HttpClient.newHttpClient(), null, null, null);
            TrainingDataBootstrapHandler.Tournament tournament = tournament("world_cup_2099");
            TrainingDataBootstrapHandler.SourceTeam spain = new TrainingDataBootstrapHandler.SourceTeam("Spain", "Spain");
            TrainingDataBootstrapHandler.ParsedTournament parsed = new TrainingDataBootstrapHandler.ParsedTournament(
                    Map.of("A", List.of(spain)), Set.of("Spain"),
                    List.of(new TrainingDataBootstrapHandler.Match("A", tournament.startDate(), "Spain", "Spain", 1, 0)));
            Path groups = tempDir.resolve("data/predictions/world_cup_2099/groups.csv");
            new GeneratedDataStore(tempDir).writeLines(groups, List.of(
                    "group,team,elo_ranking",
                    "A,Stale,1"));
            assertEquals(1, new GeneratedDataStore(tempDir).readLines(groups).size() - 1);

            Method writeTournament = TrainingDataBootstrapHandler.class.getDeclaredMethod(
                    "writeTournament", TrainingDataBootstrapHandler.Tournament.class,
                    TrainingDataBootstrapHandler.ParsedTournament.class, HistoricalProfileProvider.class);
            writeTournament.setAccessible(true);
            writeTournament.invoke(local, tournament, parsed, new StubProfileProvider(tempDir));

            Path start = tempDir.resolve("data/predictions/world_cup_2099/start.csv");
            assertFalse(Files.exists(start));
            assertEquals("A,Spain,no,0,,0,,0,,0,0,,0,,0,,0",
                    new GeneratedDataStore(tempDir).readLines(start).get(1));
            assertEquals(List.of(), new GeneratedDataStore(tempDir).readLines(groups));
        } finally {
            if (previous == null) {
                System.clearProperty("tournament.generated.exportCsv");
            } else {
                System.setProperty("tournament.generated.exportCsv", previous);
            }
        }
    }

    private static class StubProfileProvider extends HistoricalProfileProvider {
        StubProfileProvider(Path root) {
            super(root, HttpClient.newHttpClient());
        }

        @Override
        public Profile profile(String tournament, LocalDate startDate, boolean euro, String team) {
            return new Profile(0, "", 0, "", 0, "", 0, 0, "", 0, "", 0, "", 0, "");
        }
    }

    private static TrainingDataBootstrapHandler.Tournament tournament(String name) {
        return new TrainingDataBootstrapHandler.Tournament(
                name, "https://example.invalid/source", LocalDate.of(2000, 1, 1), Set.of(), false);
    }
}

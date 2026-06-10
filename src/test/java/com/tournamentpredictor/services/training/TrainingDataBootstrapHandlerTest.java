package com.tournamentpredictor.services.training;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingDataBootstrapHandlerTest {
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

    private static TrainingDataBootstrapHandler.Tournament tournament(String name) {
        return new TrainingDataBootstrapHandler.Tournament(
                name, "https://example.invalid/source", LocalDate.of(2000, 1, 1), Set.of(), false);
    }
}

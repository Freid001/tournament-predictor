package com.tournamentpredictor.controller.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TournamentPredictorCommandTest {

    @Test
    void browserModePrintsUiUrlAndDoesNotRequireResolver() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            int exitCode = new CommandLine(new TournamentPredictorCommand(null)).execute("--browser");

            assertEquals(0, exitCode);
        } finally {
            System.setOut(originalOut);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Browser UI running at http://localhost:8080"));
        assertTrue(text.contains("Press Ctrl+C to stop."));
    }
}

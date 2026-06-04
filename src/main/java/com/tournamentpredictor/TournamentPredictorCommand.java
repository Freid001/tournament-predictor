package com.tournamentpredictor;

import com.tournamentpredictor.service.MatchResolver;
import com.tournamentpredictor.service.util.ConsoleReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

@Command(
        name = "tournament-predictor",
        mixinStandardHelpOptions = true,
        description = "Tournament bracket prediction CLI",
        footer = {
                "",
                "Modes (run in order):",
                "  elo-refresh   Refresh ELO ratings and team match histories",
                "  start         Generate group rankings from start.csv",
                "  groups        Generate last_32 matchup permutations",
                "  last_32       Score last_32, generate last_16 matchups",
                "  last_16       Score last_16, generate last_8 matchups",
                "  last_8        Score last_8, generate last_4 matchups",
                "  last_4        Score last_4, generate final matchups",
                "  final         Score final predictions",
                "  path          Trace a team's bracket route (requires --filter)"
        }
)
@Component
public class TournamentPredictorCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TournamentPredictorCommand.class);

    @Autowired
    private MatchResolver resolver;

    @Autowired
    private ConsoleReporter consoleReporter;

    @Option(names = "--tournament",
            description = "Tournament subfolder under data/ (required for all modes except elo-refresh)")
    private String tournament;

    @Option(names = "--mode", defaultValue = "groups",
            description = "Mode to run (default: ${DEFAULT-VALUE})")
    private String mode;

    @Option(names = "--filter",
            description = "Filter console output to matchups involving this team (required for path mode)")
    private String filter;

    @Option(names = "--path", defaultValue = "both",
            description = "For path mode: primary, alt, both (default: ${DEFAULT-VALUE})")
    private String path;

    @Option(names = {"--page", "--p"}, defaultValue = "1",
            description = "Page number for path/last_x output (default: ${DEFAULT-VALUE})")
    private int page;

    @Option(names = "--flags", defaultValue = "false",
            description = "Show emoji flags in output (default: ${DEFAULT-VALUE})")
    private boolean flags;

    @Option(names = "--browser", defaultValue = "false",
            description = "Start web UI on localhost:8080")
    private boolean browser;

    @Override
    public void run() {
        if (browser) {
            System.out.println("Browser UI running at http://localhost:8080");
            System.out.println("Press Ctrl+C to stop.");
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(URI.create("http://localhost:8080"));
                } catch (Exception ignored) {
                }
            }
            return;
        }

        if (!"primary".equals(path) && !"alt".equals(path) && !"both".equals(path)) {
            fail("--path must be one of: primary, alt, both", 2);
            return;
        }
        if (page < 1) {
            fail("--page must be a positive integer", 2);
            return;
        }
        consoleReporter.setTeamFilter(filter);
        consoleReporter.setPathFilter(path);
        consoleReporter.setPageNumber(page);
        consoleReporter.setShowFlags(flags);

        try {
            resolver.resolveAndWrite(mode, tournament);
            log.info("Done.");
        } catch (IOException e) {
            fail("Error: " + e.getMessage(), 2, e);
        }
    }

    private void fail(String message, int exitCode) {
        fail(message, exitCode, null);
    }

    private void fail(String message, int exitCode, Exception exception) {
        log.error(message);
        if (!browser) {
            System.exit(exitCode);
        }
        if (exception instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException(message, exception);
    }
}

package com.tournamentpredictor.controller.cli;

import com.tournamentpredictor.services.orchestration.MatchResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

@Command(
        name = "predict",
        mixinStandardHelpOptions = true,
        description = "WC2026 tournament bracket predictor. Use --browser to open the full UI.",
        footer = {
                "",
                "Examples:",
                "  predict --browser --tournament=world_cup_2026",
                "      Start the browser UI (recommended)",
                "",
                "  predict --mode=elo-refresh",
                "      Refresh global ELO ratings and team match histories from latest data",
                "",
                "  predict --mode=snapshot-refresh --tournament=world_cup_2026",
                "      Freeze tournament-local ELO ratings and recent history for selected teams",
                "",
                "  predict --tournament=world_cup_2026",
                "      Run the default pipeline mode (start) — generate group rankings",
                "",
                "Pipeline modes (run in order for a full bracket):",
                "  snapshot-refresh  Freeze tournament ELO/history inputs",
                "  start     Generate group rankings from start.csv         [default]",
                "  groups    Generate last_32 matchup permutations from group picks",
                "  last_32   Score last_32 predictions, generate last_16 matchups",
                "  last_16   Score last_16 predictions, generate last_8 matchups",
                "  last_8    Score last_8 predictions, generate last_4 matchups",
                "  last_4    Score last_4 predictions, generate final matchup",
                "  final     Score final predictions",
                "  simulate  Simulate Last 32 onward and write simulation_last_32.csv",
                "  training             Download generated history and run all calibration grids",
                "  training-grid         Evaluate ELO/xG parameters with tournament holdouts",
                "  training-core-grid    Evaluate home, quality, and recent-weighted joint parameters",
                "  training-context-grid Evaluate age, cohesion, depth, omissions, injuries, and heat",
                "  training-warmup-grid  Evaluate warm-up friendly ELO caps with holdouts"
        }
)
@Component
public class TournamentPredictorCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TournamentPredictorCommand.class);

    private final MatchResolver resolver;

    public TournamentPredictorCommand(MatchResolver resolver) {
        this.resolver = resolver;
    }

    @Option(names = "--tournament",
            description = "Tournament subfolder under data/ (e.g. world_cup_2026). Required for all pipeline modes.")
    private String tournament;

    @Option(names = "--mode", defaultValue = "start",
            description = "Pipeline mode to run. Default: start. See footer for full list.")
    private String mode;

    @Option(names = "--browser", defaultValue = "false",
            description = "Launch the browser UI at http://localhost:8080")
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

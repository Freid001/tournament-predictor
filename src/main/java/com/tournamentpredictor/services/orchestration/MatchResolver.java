package com.tournamentpredictor.services.orchestration;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.prediction.builder.FinalLineBuilder;
import com.tournamentpredictor.services.prediction.builder.Last16LineBuilder;
import com.tournamentpredictor.services.prediction.builder.Last32LineBuilder;
import com.tournamentpredictor.services.prediction.builder.Last4LineBuilder;
import com.tournamentpredictor.services.prediction.builder.Last8LineBuilder;
import com.tournamentpredictor.services.snapshot.EloRefreshHandler;
import com.tournamentpredictor.services.prediction.handler.FinalHandler;
import com.tournamentpredictor.services.prediction.handler.GroupsHandler;
import com.tournamentpredictor.services.prediction.handler.Last16Handler;
import com.tournamentpredictor.services.prediction.handler.Last32Handler;
import com.tournamentpredictor.services.prediction.handler.Last4Handler;
import com.tournamentpredictor.services.prediction.handler.Last8Handler;
import com.tournamentpredictor.services.prediction.handler.StartHandler;
import com.tournamentpredictor.services.snapshot.TournamentSnapshotHandler;
import com.tournamentpredictor.services.training.TrainingCoreGridHandler;
import com.tournamentpredictor.services.training.TrainingContextGridHandler;
import com.tournamentpredictor.services.training.TrainingDataBootstrapHandler;
import com.tournamentpredictor.services.training.TrainingGridHandler;
import com.tournamentpredictor.services.training.TrainingWarmupGridHandler;
import com.tournamentpredictor.services.simulation.SimulationHandler;
import com.tournamentpredictor.services.io.CsvHelper;
import com.tournamentpredictor.services.report.ConsoleReporter;
import com.tournamentpredictor.services.bracket.DisplayBuilder;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.ExpectedGoalsCalculator;
import com.tournamentpredictor.services.calculation.PathCalculator;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.calculation.PredictionScorer;
import com.tournamentpredictor.services.calculation.SlotStatusEvaluator;
import com.tournamentpredictor.services.bracket.ThirdPlaceResolver;
import com.tournamentpredictor.services.bracket.TokenResolver;
import com.tournamentpredictor.services.prediction.validation.PredictionsFileValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class MatchResolver {
    private final GroupsHandler groupsHandler;
    private final Last32Handler last32Handler;
    private final Last16Handler last16Handler;
    private final Last8Handler last8Handler;
    private final Last4Handler last4Handler;
    private final FinalHandler finalHandler;
    private final EloRefreshHandler eloRefreshHandler;
    private final TournamentSnapshotHandler tournamentSnapshotHandler;
    private final StartHandler startHandler;
    private final SimulationHandler simulationHandler;
    private final TrainingDataBootstrapHandler trainingDataBootstrapHandler;
    private final TrainingGridHandler trainingGridHandler;
    private final TrainingCoreGridHandler trainingCoreGridHandler;
    private final TrainingContextGridHandler trainingContextGridHandler;
    private final TrainingWarmupGridHandler trainingWarmupGridHandler;
    @Autowired
    public MatchResolver(ConsoleReporter consoleReporter, PredictionConfig predictionConfig) {
        this(new CsvLoader().withQualYears(predictionConfig.getQualFormSinceYear(), predictionConfig.getQualFormUntilYear()), consoleReporter, predictionConfig);
    }

    MatchResolver(CsvLoader loader, ConsoleReporter consoleReporter) {
        this(loader == null ? new CsvLoader() : loader, Path.of(System.getProperty("user.dir")), consoleReporter, null);
    }

    public static MatchResolver forWeb(ConsoleReporter reporter) {
        return new MatchResolver(new CsvLoader(), reporter);
    }

    public static MatchResolver forWeb(ConsoleReporter reporter, PredictionConfig predictionConfig) {
        return new MatchResolver(new CsvLoader()
                .withQualYears(predictionConfig.getQualFormSinceYear(), predictionConfig.getQualFormUntilYear())
                .withConfig(predictionConfig), reporter, predictionConfig);
    }

    MatchResolver(CsvLoader loader, ConsoleReporter consoleReporter, PredictionConfig predictionConfig) {
        this(loader == null ? new CsvLoader() : loader, Path.of(System.getProperty("user.dir")), consoleReporter, predictionConfig);
    }

    private MatchResolver(CsvLoader loader, Path projectRoot, ConsoleReporter consoleReporter, PredictionConfig config) {
        EloCalculator eloCalculator = config != null ? new EloCalculator(config) : new EloCalculator();
        TokenResolver tokenResolver = new TokenResolver();
        DisplayBuilder displayBuilder = new DisplayBuilder(tokenResolver);
        SlotStatusEvaluator slotStatusEvaluator = new SlotStatusEvaluator(eloCalculator);
        PathCalculator pathCalculator = new PathCalculator(slotStatusEvaluator, eloCalculator);
        PathFatigueCalculator pathFatigueCalculator = config != null
                ? new PathFatigueCalculator().withConfig(config)
                : new PathFatigueCalculator();
        ThirdPlaceResolver thirdPlaceResolver = new ThirdPlaceResolver(projectRoot);
        CsvHelper csvHelper = new CsvHelper();
        PredictionsFileValidator predictionsFileValidator = new PredictionsFileValidator();
        PredictionScorer predictionScorer = new PredictionScorer(eloCalculator);

        Last32LineBuilder last32LineBuilder = new Last32LineBuilder(displayBuilder, pathCalculator, eloCalculator, thirdPlaceResolver, pathFatigueCalculator);
        Last16LineBuilder last16LineBuilder = new Last16LineBuilder(displayBuilder, pathCalculator, eloCalculator, pathFatigueCalculator);
        Last8LineBuilder last8LineBuilder = new Last8LineBuilder(pathCalculator, eloCalculator, pathFatigueCalculator);
        Last4LineBuilder last4LineBuilder = new Last4LineBuilder(pathCalculator, eloCalculator, pathFatigueCalculator);
        FinalLineBuilder finalLineBuilder = new FinalLineBuilder(pathCalculator, eloCalculator, pathFatigueCalculator);

        this.groupsHandler = new GroupsHandler(loader, projectRoot, csvHelper, last32LineBuilder, eloCalculator);
        this.last32Handler = new Last32Handler(loader, projectRoot, csvHelper, predictionsFileValidator,
                eloCalculator, predictionScorer, last32LineBuilder, last16LineBuilder, consoleReporter);
        this.last16Handler = new Last16Handler(loader, projectRoot, csvHelper, predictionsFileValidator,
                eloCalculator, predictionScorer,
                last16LineBuilder, last8LineBuilder, consoleReporter);
        this.last8Handler = new Last8Handler(loader, projectRoot, csvHelper, predictionsFileValidator,
                eloCalculator, predictionScorer,
                last8LineBuilder, last4LineBuilder, consoleReporter);
        this.last4Handler = new Last4Handler(loader, projectRoot, csvHelper, predictionsFileValidator,
                eloCalculator, predictionScorer,
                last4LineBuilder, finalLineBuilder, consoleReporter);
        this.finalHandler = new FinalHandler(loader, projectRoot, csvHelper, predictionsFileValidator,
                eloCalculator, predictionScorer, finalLineBuilder, consoleReporter);
        this.eloRefreshHandler = new EloRefreshHandler(loader, projectRoot, thirdPlaceResolver, tokenResolver, displayBuilder);
        this.tournamentSnapshotHandler = config != null ? new TournamentSnapshotHandler(loader, projectRoot, config) : null;
        this.startHandler = config != null
                ? new StartHandler(loader, projectRoot, csvHelper, config)
                : new StartHandler(loader, projectRoot, csvHelper);
        ExpectedGoalsCalculator expectedGoalsCalculator = config == null
                ? new ExpectedGoalsCalculator()
                : new ExpectedGoalsCalculator(config.getEloScaleDivisor(), 2.60, config.getGoalDiffPer400Elo(),
                        config.getExpectedGoalsMultiplier());
        int simulationRuns = config == null ? SimulationHandler.DEFAULT_RUNS : config.getSimulationRuns();
        long simulationSeed = config == null ? SimulationHandler.DEFAULT_SEED : config.getSimulationSeed();
        this.simulationHandler = new SimulationHandler(loader, projectRoot, expectedGoalsCalculator,
                eloCalculator, pathFatigueCalculator, simulationRuns, simulationSeed);
        this.trainingGridHandler = new TrainingGridHandler(loader, projectRoot);
        this.trainingCoreGridHandler = new TrainingCoreGridHandler(projectRoot,
                config == null ? 400.0 : config.getEloScaleDivisor());
        this.trainingContextGridHandler = new TrainingContextGridHandler(projectRoot,
                config == null ? 400.0 : config.getEloScaleDivisor());
        this.trainingWarmupGridHandler = new TrainingWarmupGridHandler(projectRoot,
                config == null ? 400.0 : config.getEloScaleDivisor(),
                config == null ? 1.20 : config.getGoalDiffPer400Elo(),
                config == null ? 0.93 : config.getExpectedGoalsMultiplier());
        this.trainingDataBootstrapHandler = config == null ? null
                : new TrainingDataBootstrapHandler(projectRoot, eloRefreshHandler, tournamentSnapshotHandler, startHandler);
    }

    public void resolveAndWriteLast32(String tournament) throws IOException {
        last32Handler.handle(tournament);
    }

    public void resolveAndWriteSimulation(String tournament, String startRound) throws IOException {
        simulationHandler.handle(tournament, startRound);
    }

    public void resolveAndWriteKnockoutsFromGroups(String tournament) throws IOException {
        simulationHandler.handleKnockoutsFromGroups(tournament);
    }

    public void resolveAndWriteLiveSimulation(String tournament, String startRound, java.util.List<String> startRows) throws IOException {
        simulationHandler.handleLive(tournament, startRound, startRows);
    }

    public void resolveAndWrite(String mode, String tournament) throws IOException {
        if (mode == null) {
            mode = "groups";
        }
        if (mode.equalsIgnoreCase("start")) {
            requireTournament(mode, tournament);
            startHandler.handle(tournament);
            return;
        }
        if (mode.equalsIgnoreCase("groups")) {
            requireTournament(mode, tournament);
            groupsHandler.handle(tournament);
            return;
        }
        if (mode.equalsIgnoreCase("last_32")) {
            requireTournament(mode, tournament);
            last32Handler.handle(tournament);
            return;
        }
        if (mode.equalsIgnoreCase("last_16")) {
            requireTournament(mode, tournament);
            last16Handler.handle(tournament);
            return;
        }
        if (mode.equalsIgnoreCase("last_8")) {
            requireTournament(mode, tournament);
            last8Handler.handle(tournament);
            return;
        }
        if (mode.equalsIgnoreCase("last_4")) {
            requireTournament(mode, tournament);
            last4Handler.handle(tournament);
            return;
        }
        if (mode.equalsIgnoreCase("final")) {
            requireTournament(mode, tournament);
            finalHandler.handle(tournament);
            return;
        }
        if (mode.equalsIgnoreCase("group-simulation")) {
            requireTournament(mode, tournament);
            simulationHandler.handle(tournament, "groups");
            return;
        }
        if (mode.equalsIgnoreCase("knockout-simulation")) {
            requireTournament(mode, tournament);
            simulationHandler.handleKnockoutsFromGroups(tournament);
            return;
        }
        if (mode.equalsIgnoreCase("simulate")) {
            requireTournament(mode, tournament);
            simulationHandler.handle(tournament);
            return;
        }
        if (mode.equalsIgnoreCase("training") || mode.equalsIgnoreCase("trainning")) {
            if (trainingDataBootstrapHandler == null) {
                throw new IOException("Mode training requires application config; run through the Spring Boot CLI.");
            }
            trainingDataBootstrapHandler.handle();
            trainingGridHandler.handle();
            trainingWarmupGridHandler.handle();
            trainingCoreGridHandler.handle();
            trainingContextGridHandler.handle();
            return;
        }
        if (mode.equalsIgnoreCase("training-grid") || mode.equalsIgnoreCase("trainning-grid")) {
            trainingGridHandler.handle();
            return;
        }
        if (mode.equalsIgnoreCase("training-core-grid") || mode.equalsIgnoreCase("trainning-core-grid")) {
            trainingCoreGridHandler.handle();
            return;
        }
        if (mode.equalsIgnoreCase("training-warmup-grid") || mode.equalsIgnoreCase("trainning-warmup-grid")) {
            trainingWarmupGridHandler.handle();
            return;
        }
        if (mode.equalsIgnoreCase("training-context-grid") || mode.equalsIgnoreCase("trainning-context-grid")) {
            trainingContextGridHandler.handle();
            return;
        }
        if (mode.equalsIgnoreCase("elo-refresh") || mode.equalsIgnoreCase("elo")) {
            eloRefreshHandler.handle();
            return;
        }
        if (mode.equalsIgnoreCase("snapshot-refresh") || mode.equalsIgnoreCase("snapshot")) {
            requireTournament(mode, tournament);
            if (tournamentSnapshotHandler == null) {
                throw new IOException("Mode " + mode + " requires application config; run through Spring Boot CLI or UI.");
            }
            tournamentSnapshotHandler.handle(tournament);
            return;
        }
        throw new IOException("Unknown mode: " + mode + ". Use --browser for the full UI, or run pipeline modes: start, groups, last_32, last_16, last_8, last_4, final, simulate, group-simulation, knockout-simulation, training, training-grid, training-core-grid, training-warmup-grid, elo-refresh, snapshot-refresh");
    }

    private void requireTournament(String mode, String tournament) throws IOException {
        if (tournament == null || tournament.isEmpty()) {
            throw new IOException("Mode " + mode + " requires --tournament=<name>");
        }
    }
}

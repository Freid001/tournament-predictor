package com.tournamentpredictor.service;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.builder.FinalLineBuilder;
import com.tournamentpredictor.service.builder.Last16LineBuilder;
import com.tournamentpredictor.service.builder.Last32LineBuilder;
import com.tournamentpredictor.service.builder.Last4LineBuilder;
import com.tournamentpredictor.service.builder.Last8LineBuilder;
import com.tournamentpredictor.service.handler.EloRefreshHandler;
import com.tournamentpredictor.service.handler.FinalHandler;
import com.tournamentpredictor.service.handler.GroupsHandler;
import com.tournamentpredictor.service.handler.Last16Handler;
import com.tournamentpredictor.service.handler.Last32Handler;
import com.tournamentpredictor.service.handler.Last4Handler;
import com.tournamentpredictor.service.handler.Last8Handler;
import com.tournamentpredictor.service.handler.StartHandler;
import com.tournamentpredictor.service.mapper.DisagreeMapMapper;
import com.tournamentpredictor.service.util.CsvHelper;
import com.tournamentpredictor.service.util.ConsoleReporter;
import com.tournamentpredictor.service.util.DisplayBuilder;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.PathCalculator;
import com.tournamentpredictor.service.util.PathFatigueCalculator;
import com.tournamentpredictor.service.util.PredictionScorer;
import com.tournamentpredictor.service.util.SlotStatusEvaluator;
import com.tournamentpredictor.service.util.ThirdPlaceResolver;
import com.tournamentpredictor.service.util.TokenResolver;
import com.tournamentpredictor.service.validator.PredictionsFileValidator;
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
    private final StartHandler startHandler;
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
        DisagreeMapMapper disagreeMapMapper = new DisagreeMapMapper();
        PredictionScorer predictionScorer = new PredictionScorer(eloCalculator);

        Last32LineBuilder last32LineBuilder = new Last32LineBuilder(displayBuilder, pathCalculator, eloCalculator, thirdPlaceResolver);
        Last16LineBuilder last16LineBuilder = new Last16LineBuilder(displayBuilder, pathCalculator, eloCalculator, pathFatigueCalculator);
        Last8LineBuilder last8LineBuilder = new Last8LineBuilder(pathCalculator, eloCalculator, pathFatigueCalculator);
        Last4LineBuilder last4LineBuilder = new Last4LineBuilder(pathCalculator, eloCalculator, pathFatigueCalculator);
        FinalLineBuilder finalLineBuilder = new FinalLineBuilder(pathCalculator, eloCalculator, pathFatigueCalculator);

        this.groupsHandler = new GroupsHandler(loader, projectRoot, csvHelper, last32LineBuilder, eloCalculator);
        this.last32Handler = new Last32Handler(loader, projectRoot, csvHelper, predictionsFileValidator,
                disagreeMapMapper, eloCalculator, predictionScorer, last32LineBuilder, last16LineBuilder, consoleReporter);
        this.last16Handler = new Last16Handler(loader, projectRoot, csvHelper, predictionsFileValidator,
                disagreeMapMapper, eloCalculator, predictionScorer,
                last16LineBuilder, last8LineBuilder, consoleReporter);
        this.last8Handler = new Last8Handler(loader, projectRoot, csvHelper, predictionsFileValidator,
                disagreeMapMapper, eloCalculator, predictionScorer,
                last8LineBuilder, last4LineBuilder, consoleReporter);
        this.last4Handler = new Last4Handler(loader, projectRoot, csvHelper, predictionsFileValidator,
                disagreeMapMapper, eloCalculator, predictionScorer,
                last4LineBuilder, finalLineBuilder, consoleReporter);
        this.finalHandler = new FinalHandler(loader, projectRoot, csvHelper, predictionsFileValidator,
                disagreeMapMapper, eloCalculator, predictionScorer, finalLineBuilder, consoleReporter);
        this.eloRefreshHandler = new EloRefreshHandler(loader, projectRoot, thirdPlaceResolver, tokenResolver, displayBuilder);
        this.startHandler = config != null
                ? new StartHandler(loader, projectRoot, csvHelper, config)
                : new StartHandler(loader, projectRoot, csvHelper);
    }

    public void resolveAndWriteLast32(String tournament) throws IOException {
        last32Handler.handle(tournament);
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
        if (mode.equalsIgnoreCase("elo-refresh") || mode.equalsIgnoreCase("elo")) {
            eloRefreshHandler.handle();
            return;
        }
        throw new IOException("Unknown mode: " + mode + ". Use --browser for the full UI, or run pipeline modes: start, groups, last_32, last_16, last_8, last_4, final, elo-refresh");
    }

    private void requireTournament(String mode, String tournament) throws IOException {
        if (tournament == null || tournament.isEmpty()) {
            throw new IOException("Mode " + mode + " requires --tournament=<name>");
        }
    }
}

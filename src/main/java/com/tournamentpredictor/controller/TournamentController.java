package com.tournamentpredictor.controller;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.services.web.WebControllerService;
import com.tournamentpredictor.services.web.TournamentPageService;
import com.tournamentpredictor.services.web.WebText;
import com.tournamentpredictor.services.web.RouteLikelihoodService;
import com.tournamentpredictor.services.web.SimulationViewDataService;
import com.tournamentpredictor.services.orchestration.MatchResolver;
import com.tournamentpredictor.services.simulation.SimulationHandler;
import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.report.HtmlReporter;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.calculation.ExpectedGoalsCalculator;
import com.tournamentpredictor.view.SimulationResultsRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import com.tournamentpredictor.view.KnockoutViewRows;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.model.common.*;
import com.tournamentpredictor.model.tournament.*;
import com.tournamentpredictor.model.history.*;
import com.tournamentpredictor.model.group.*;
import com.tournamentpredictor.model.round.*;
import com.tournamentpredictor.model.results.*;
import com.tournamentpredictor.model.start.*;

@Controller
public class TournamentController {

    protected final PredictionConfig predictionConfig;
    protected final WebControllerService web;
    protected final TournamentPageService pages;

    public TournamentController(PredictionConfig predictionConfig, WebControllerService web, TournamentPageService pages) {
        this.predictionConfig = predictionConfig;
        this.web = web;
        this.pages = pages;
    }

    @Autowired
    public TournamentController(PredictionConfig predictionConfig) {
        this(predictionConfig, new WebControllerService(predictionConfig));
    }

    public TournamentController(PredictionConfig predictionConfig, WebControllerService web) {
        this(predictionConfig, web, new TournamentPageService(web));
    }

    @GetMapping("/tournament/{name}")
    public String tournament(@PathVariable("name") String name, Model model) {
        String tournament = web.safeTournament(name);
        model.addAttribute("tournament", new TournamentSummary(tournament, web.describeCurrentStage(tournament), web.completedStepCount(tournament)));
        model.addAttribute("stages", pages.buildStages(tournament));
        return "tournament";
    }

    @PostMapping("/run/{mode}")
    public String run(@PathVariable("mode") String mode, @RequestParam(value = "tournament", required = false) String tournament, Model model, RedirectAttributes redirect) {
        String safeMode = web.safeMode(mode);
        HtmlReporter reporter = new HtmlReporter().withConfig(predictionConfig);
        if ("elo-refresh".equals(safeMode)) {
            try {
                MatchResolver.forWeb(reporter, predictionConfig).resolveAndWrite(safeMode, null);
                redirect.addFlashAttribute("refreshMessage", "ELO history refreshed.");
            } catch (Exception e) {
                redirect.addFlashAttribute("refreshMessage", "ELO refresh failed: " + (e.getMessage() == null ? "unexpected error" : e.getMessage()));
            }
            return "redirect:/";
        }
        String safeTournament = web.safeTournament(tournament);
        boolean hasError = false;

        try {
            boolean startsAtLast16 = !web.bracketHasStage(safeTournament, "LAST_32")
                    && web.bracketHasStage(safeTournament, "LAST_16");
            if ("group-simulation".equals(safeMode)) {
                web.runGroupStagePipeline(safeTournament, reporter);
                web.warmGeneratedData(safeTournament);
                redirect.addFlashAttribute("runOutput", reporter.getHtml());
                return web.redirectAfterSaveRun(safeMode, safeTournament);
            }
            if ("tournament-snapshot-refresh".equals(safeMode)) {
                return "redirect:/edit/results?tournament=" + WebText.webEncode(safeTournament);
            }
            if ("tournament".equals(safeMode)) {
                web.runTournamentPipeline(safeTournament, reporter);
                web.warmGeneratedData(safeTournament);
                redirect.addFlashAttribute("runOutput", reporter.getHtml());
                return web.redirectAfterSaveRun(safeMode, safeTournament);
            }
            if (startsAtLast16 && List.of("last_16", "last_8", "last_4", "final").contains(safeMode)) {
                web.cascadeDeleteAfterRoundEdit(safeTournament, safeMode);
                web.runDirectKnockoutRoundFromGroups(safeTournament, safeMode, reporter);
                web.warmGeneratedData(safeTournament);
                redirect.addFlashAttribute("runOutput", reporter.getHtml());
                return web.redirectAfterSaveRun(safeMode, safeTournament);
            }

            Path lockedPath = web.outputPathForMode(safeTournament, safeMode);
            boolean lockedBefore = lockedPath != null && web.generatedDataExists(lockedPath);
            if (lockedBefore && !"start".equals(safeMode) && !"groups".equals(safeMode)) {
                reporter.appendWarning("Output already exists: " + lockedPath + " — delete or reset to re-run.");
            }

            if ("snapshot-refresh".equals(safeMode)) {
                MatchResolver.forWeb(reporter, predictionConfig).resolveAndWrite("elo-refresh", null);
            }
            if ("last_32".equals(safeMode)) {
                web.ensureLast32PredictionSeed(safeTournament, reporter);
            }
            if (List.of("last_32", "last_16", "last_8", "last_4", "final").contains(safeMode)) {
                web.cascadeDeleteAfterRoundEdit(safeTournament, safeMode);
            }
            MatchResolver.forWeb(reporter, predictionConfig).resolveAndWrite(safeMode, safeTournament);
            if ("snapshot-refresh".equals(safeMode)) {
                web.cascadeDeleteAfterStart(safeTournament);
                reporter.appendInfo("Current ELO source refreshed and tournament snapshot frozen. Group rankings and tournament results were reset; review Team Setup and run the workflow again.");
            }
            web.autoRunSimulation(safeMode, safeTournament, reporter);
            web.warmGeneratedData(safeTournament);
            web.appendBrowserOnlyMessages(reporter, safeMode, safeTournament, lockedBefore);
            if (reporter.getHtml().isBlank()) {
                reporter.appendInfo("Completed " + web.displayMode(safeMode) + ".");
            }
        } catch (Exception e) {
            reporter.appendError(e.getMessage() == null ? "Unexpected error" : e.getMessage());
            hasError = true;
        }

        if (!hasError) {
            redirect.addFlashAttribute("runOutput", reporter.getHtml());
            return web.redirectAfterSaveRun(safeMode, safeTournament);
        }

        model.addAttribute("mode", web.displayMode(safeMode));
        model.addAttribute("tournament", safeTournament);
        model.addAttribute("tournamentLabel", web.displayTournament(safeTournament));
        model.addAttribute("output", reporter.getHtml());
        model.addAttribute("hasNextRound", false);
        model.addAttribute("hasPrevRound", false);
        model.addAttribute("canNextRun", false);
        return "result";
    }


    @GetMapping("/reset/{step}")
    public String reset(@PathVariable("step") String step, @RequestParam("tournament") String tournament) throws IOException {
        String safeStep = web.safeResetStep(step);
        String safeTournament = web.safeTournament(tournament);
        web.cascadeDeleteForReset(safeTournament, safeStep);
        return web.redirectToTournament(safeTournament);
    }
}

package com.tournamentpredictor.controller;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.services.web.WebControllerService;
import com.tournamentpredictor.services.web.WebText;
import com.tournamentpredictor.services.web.PageModelService;
import com.tournamentpredictor.services.web.ResultFormService;
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
public class ResultsController {

    protected final PredictionConfig predictionConfig;
    protected final WebControllerService web;
    private final PageModelService pageModels;
    private final ResultFormService resultForms = new ResultFormService();

    public ResultsController(PredictionConfig predictionConfig, WebControllerService web) {
        this.predictionConfig = predictionConfig;
        this.web = web;
        this.pageModels = new PageModelService(web);
    }

    @Autowired
    public ResultsController(PredictionConfig predictionConfig) {
        this(predictionConfig, new WebControllerService(predictionConfig));
    }

    @GetMapping("/edit/results")
    public String editResults(@RequestParam("tournament") String tournament,
                              @RequestParam(value = "round", required = false) String round,
                              Model model,
                              RedirectAttributes redirectAttributes) throws IOException {
        String safeTournament = web.safeTournament(tournament);
        String safeRound = WebText.trim(round);
        if (!safeRound.isBlank() && !web.hasResultsPrerequisite(safeTournament, safeRound)) {
            redirectAttributes.addFlashAttribute("refreshMessage",
                    "Complete the previous stage results before entering " + web.displayViewMode(safeRound) + " results.");
            return "redirect:/tournament/" + safeTournament;
        }
        List<ResultsRoundView> rounds = web.buildResultsEditorRounds(safeTournament, safeRound.isBlank() ? null : safeRound);
        if (rounds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No knockout matchups found");
        }
        pageModels.addTournamentPage(model, safeTournament,
                safeRound.isBlank() ? "Tournament Results" : web.displayViewMode(safeRound) + " Results");
        model.addAttribute("selectedRound", safeRound);
        model.addAttribute("rounds", rounds);
        pageModels.addDisabledNavigation(model);
        return "edit-results";
    }

    @PostMapping("/edit/results")
    public String saveResults(@RequestParam("tournament") String tournament,
                              @RequestParam(value = "round", required = false) String round,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes) throws IOException {
        String safeTournament = web.safeTournament(tournament);
        String safeRound = WebText.trim(round);
        List<String> targetRounds = safeRound.isBlank() ? WebControllerService.RESULTS_ROUNDS : List.of(safeRound);
        for (String targetRound : targetRounds) {
            if (!web.hasResultsPrerequisite(safeTournament, targetRound)) {
                redirectAttributes.addFlashAttribute("refreshMessage",
                        "Complete the previous stage results before entering " + web.displayViewMode(targetRound) + " results.");
                return "redirect:/tournament/" + safeTournament;
            }
            List<Map<String, String>> rows = resultForms.knockoutRows(request, targetRound);
            if (!rows.isEmpty()) {
                web.writeCsv(web.projectRoot.resolve("data").resolve("results").resolve(safeTournament).resolve(targetRound + ".csv"),
                        ResultFormService.KNOCKOUT_HEADERS, rows);
            }
        }
        boolean liveUpdated = false;
        if (!safeRound.isBlank()) {
            liveUpdated = web.refreshLiveSimulationAfterResultsSave(safeTournament, safeRound);
        }
        redirectAttributes.addFlashAttribute("refreshMessage", liveUpdated
                ? "Tournament results saved. Live predictions updated."
                : "Tournament results saved.");
        return safeRound.isBlank() ? "redirect:/tournament/" + safeTournament : "redirect:/edit/results?tournament=" + safeTournament + "&round=" + WebText.webEncode(safeRound);
    }


    @GetMapping("/edit/group-results")
    public String editGroupResults(@RequestParam("tournament") String tournament, Model model) throws IOException {
        String safeTournament = web.safeTournament(tournament);
        List<GroupMatchResultRow> rows = web.buildGroupResultsEditorRows(safeTournament);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No group fixtures found");
        }
        pageModels.addTournamentPage(model, safeTournament, "Group Results");
        model.addAttribute("rows", rows);
        model.addAttribute("groupedRows", pageModels.groupBy(rows, GroupMatchResultRow::getGroup));
        pageModels.addDisabledNavigation(model);
        return "edit-group-results";
    }

    @PostMapping("/edit/group-results")
    public String saveGroupResults(@RequestParam("tournament") String tournament,
                                   HttpServletRequest request,
                                   RedirectAttributes redirectAttributes) throws IOException {
        String safeTournament = web.safeTournament(tournament);
        List<Map<String, String>> rows = resultForms.groupRows(request);
        if (!rows.isEmpty()) {
            web.writeCsv(web.projectRoot.resolve("data").resolve("results").resolve(safeTournament).resolve("groups.csv"),
                    ResultFormService.GROUP_HEADERS, rows);
        }
        boolean liveUpdated = web.refreshLiveSimulationAfterResultsSave(safeTournament, "groups");
        redirectAttributes.addFlashAttribute("refreshMessage", liveUpdated
                ? "Group match results saved. Live predictions updated."
                : "Group match results saved.");
        return "redirect:/tournament/" + safeTournament;
    }
}

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
public class HistoryController {

    protected final PredictionConfig predictionConfig;
    protected final WebControllerService web;
    protected final TournamentPageService pages;

    public HistoryController(PredictionConfig predictionConfig, WebControllerService web, TournamentPageService pages) {
        this.predictionConfig = predictionConfig;
        this.web = web;
        this.pages = pages;
    }

    @Autowired
    public HistoryController(PredictionConfig predictionConfig) {
        this(predictionConfig, new WebControllerService(predictionConfig));
    }

    public HistoryController(PredictionConfig predictionConfig, WebControllerService web) {
        this(predictionConfig, web, new TournamentPageService(web));
    }

    @GetMapping("/history/{name}")
    public String historicalComparison(@PathVariable("name") String name, Model model) throws IOException {
        String tournament = web.safeTournament(name);
        if (!WebControllerService.HISTORICAL_COMPARISONS.contains(tournament)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        HistoricalComparison comparison = pages.buildHistoricalComparison(tournament);
        model.addAttribute("comparison", comparison);
        model.addAttribute("tournament", tournament);
        model.addAttribute("tournamentLabel", web.displayTournament(tournament));
        return "historical-comparison";
    }
}

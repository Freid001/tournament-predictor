package com.tournamentpredictor.controller;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.services.web.WebControllerService;
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

public class WebController extends ViewController {

    @Autowired
    public WebController(PredictionConfig predictionConfig) {
        super(predictionConfig, new WebControllerService(predictionConfig));
    }
    static String formatQualBonus(String raw) { return WebText.formatQualBonus(raw); }
    static java.util.List<java.util.Map<String, String>> routeAverageLast16Rows(java.util.List<java.util.Map<String, String>> matchupRows) { return RouteLikelihoodService.routeAverageLast16Rows(matchupRows); }
    static java.util.List<java.util.Map<String, String>> routeAverageLast16Rows(java.util.List<java.util.Map<String, String>> matchupRows, java.util.List<java.util.Map<String, String>> groupRows) { return RouteLikelihoodService.routeAverageLast16Rows(matchupRows, groupRows); }
    static java.util.Map<String, String> matchupLikelihoodMap(java.util.List<java.util.Map<String, String>> matchupRows, java.util.List<java.util.Map<String, String>> groupRows) { return RouteLikelihoodService.matchupLikelihoodMap(matchupRows, groupRows); }
    static String matchupLikelihoodKey(String matchId, String team1, String team2) { return RouteLikelihoodService.matchupLikelihoodKey(matchId, team1, team2); }
    static java.util.Map<String, String> routeWeightedNextRoundMatchupLikelihoodMap(java.util.List<java.util.Map<String, String>> nextRows, java.util.List<java.util.Map<String, String>> feederRows, java.util.Map<String, String> feederLikelihoods) { return RouteLikelihoodService.routeWeightedNextRoundMatchupLikelihoodMap(nextRows, feederRows, feederLikelihoods); }
    static java.util.Map<String, String> simulationAdvanceMap(java.util.List<java.util.Map<String, String>> rows, String column) { return SimulationViewDataService.simulationAdvanceMap(rows, column); }
    static java.util.Map<String, String> simulationMatchupLikelihoodMap(java.util.List<java.util.Map<String, String>> rows, String stage) { return SimulationViewDataService.simulationMatchupLikelihoodMap(rows, stage); }
    static String outcome(double home, double draw, double away) { return WebText.outcome(home, draw, away); }
}

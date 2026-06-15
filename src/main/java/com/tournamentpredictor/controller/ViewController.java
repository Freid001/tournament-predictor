package com.tournamentpredictor.controller;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.services.web.WebControllerService;
import com.tournamentpredictor.services.web.WebText;
import com.tournamentpredictor.services.web.RouteLikelihoodService;
import com.tournamentpredictor.services.web.SimulationViewDataService;
import com.tournamentpredictor.services.web.StartRowViewService;
import com.tournamentpredictor.services.web.ResultsModeService;
import com.tournamentpredictor.services.web.RouteInstanceService;
import com.tournamentpredictor.services.web.RuntimeMatchupRowsService;
import com.tournamentpredictor.services.web.PathVisualizationService;
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
import java.util.concurrent.ConcurrentHashMap;
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
public class ViewController {

    protected final PredictionConfig predictionConfig;
    protected final WebControllerService web;
    private final StartRowViewService startRowViewService = new StartRowViewService();
    private final RuntimeMatchupRowsService runtimeMatchupRowsService = new RuntimeMatchupRowsService();
    private final RouteInstanceService routeInstanceService = new RouteInstanceService();
    private final Map<String, CachedVisualization> visualizationCache = new ConcurrentHashMap<>();

    public ViewController(PredictionConfig predictionConfig, WebControllerService web) {
        this.predictionConfig = predictionConfig;
        this.web = web;
    }

    @Autowired
    public ViewController(PredictionConfig predictionConfig) {
        this(predictionConfig, new WebControllerService(predictionConfig));
    }

    @GetMapping("/view/start")
    public String viewStart(@RequestParam("tournament") String tournament, Model model) throws IOException {
        String safeTournament = web.safeTournament(tournament);
        CsvData csvData = web.readCsv(web.predictionFile(safeTournament, "start.csv"));
        if (csvData.rows().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "start.csv not found");
        }
        var rows = startRowViewService.build(csvData.rows(), false);
        model.addAttribute("tournament", safeTournament);
        model.addAttribute("tournamentLabel", web.displayTournament(safeTournament));
        model.addAttribute("pageTitle", "Team Setup");
        model.addAttribute("rows", rows);

        String nextRound = "groups";
        boolean nextExists = web.generatedDataExists(web.predictionFile(safeTournament, "groups.csv"));
        model.addAttribute("editUrl", "/edit/start?tournament=" + safeTournament);
        model.addAttribute("prevNavUrl", null);
        model.addAttribute("nextNavUrl", "/view/" + nextRound + "?tournament=" + safeTournament);
        model.addAttribute("nextNavLabel", "Group Rankings →");
        model.addAttribute("nextNavEnabled", nextExists);
        model.addAttribute("canNextRun", false);
        // legacy attrs kept for keyboard JS
        model.addAttribute("nextViewUrl", "/view/" + nextRound + "?tournament=" + safeTournament);
        model.addAttribute("nextViewEnabled", nextExists);
        return "view-start";
    }

    @GetMapping("/view/path-game")
    public String viewPathGame(@RequestParam("tournament") String tournament,
                               @RequestParam("round") String round,
                               @RequestParam("team") String team,
                               @RequestParam("opponent") String opponent,
                               @RequestParam(value = "match", required = false) String sourceMatch) throws IOException {
        String safeTournament = web.safeTournament(tournament);
        String safeRound = web.safeRound(round);
        int currentIndex = WebControllerService.VIEW_ROUND_SEQUENCE.indexOf(safeRound);
        EloCalculator elo = new EloCalculator();

        for (int i = currentIndex - 1; i >= 0; i--) {
            String candidateRound = WebControllerService.VIEW_ROUND_SEQUENCE.get(i);
            if (!candidateRound.endsWith("_match")) continue;
            Path candidateFile = web.roundFileForView(safeTournament, candidateRound);
            if (!web.generatedDataExists(candidateFile)) continue;
            for (Map<String, String> row : web.readCsv(candidateFile).rows()) {
                String team1 = elo.extractTeamName(row.getOrDefault("team1", ""));
                String team2 = elo.extractTeamName(row.getOrDefault("team2", ""));
                boolean correctMatch = sourceMatch == null || sourceMatch.isBlank()
                        || sourceMatch.equalsIgnoreCase(row.getOrDefault("match_id", ""));
                boolean matches = correctMatch && ((team.equalsIgnoreCase(team1) && opponent.equalsIgnoreCase(team2))
                        || (team.equalsIgnoreCase(team2) && opponent.equalsIgnoreCase(team1)));
                if (matches) {
                    String matchId = row.getOrDefault("match_id", "");
                    return "redirect:/view/" + candidateRound
                            + "?tournament=" + WebText.webEncode(safeTournament)
                            + "&focusMatch=" + WebText.webEncode(matchId)
                            + "&focusTeam=" + WebText.webEncode(team)
                            + "&focusOpponent=" + WebText.webEncode(opponent);
                }
            }
        }
        return "redirect:/view/" + safeRound + "?tournament=" + WebText.webEncode(safeTournament);
    }


    private Map<String, String[]> fixtureSlotsByMatch(String tournament, String round) {
        try {
            Map<String, String[]> slots = new LinkedHashMap<>();
            for (CsvLoader.BracketEntry entry : new CsvLoader(web.projectRoot).loadBrackets(tournament)) {
                if (round.equalsIgnoreCase(knockoutRoundForStage(entry.stage))
                        && entry.matchId != null && !entry.matchId.isBlank()) {
                    slots.put(entry.matchId, new String[]{entry.token1, entry.token2});
                }
            }
            return slots;
        } catch (IOException e) {
            return Map.of();
        }
    }


    private String knockoutRoundForStage(String stage) {
        return switch (WebText.trim(stage).toUpperCase(java.util.Locale.ROOT)) {
            case "LAST_32" -> "last_32";
            case "LAST_16" -> "last_16";
            case "QUARTER_FINAL", "QUARTER_FINALS", "LAST_8" -> "last_8";
            case "SEMI_FINAL", "SEMI_FINALS", "LAST_4" -> "last_4";
            case "FINAL" -> "final";
            default -> "";
        };
    }

    public String viewRound(String round, String tournament, boolean actual, String path, String team, int page, Model model) throws IOException {
        return viewRound(round, tournament, actual, null, path, team, page, model);
    }

    @GetMapping("/view/{round}")
    public String viewRound(@PathVariable("round") String round,
                            @RequestParam("tournament") String tournament,
                            @RequestParam(value = "actual", required = false) Boolean actual,
                            @RequestParam(value = "results", required = false) Boolean results,
                            @RequestParam(value = "path", defaultValue = "all") String path,
                            @RequestParam(value = "team", defaultValue = "") String team,
                            @RequestParam(value = "page", defaultValue = "1") int page,
                            Model model) throws IOException {
        String safeRound = web.safeRound(round);
        String safeTournament = web.safeTournament(tournament);
        Path file = web.roundFileForView(safeTournament, safeRound);
        boolean groupsMode = "groups".equals(safeRound);
        CsvData csvData = groupsMode ? web.readCsv(file) : new CsvData(List.of(), List.of());
        if (groupsMode && csvData.rows().isEmpty()) {
            if (web.generatedDataExists(web.predictionFile(safeTournament, "start.csv"))) {
                return "redirect:/view/start?tournament=" + WebText.webEncode(safeTournament);
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, file.getFileName() + " not found");
        }
        if (!groupsMode && !web.generatedDataExists(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, file.getFileName() + " not found");
        }

        boolean hasStoredResults = web.hasResultsData(safeTournament, safeRound);
        boolean actualMode = ResultsModeService.shouldShowResults(results, actual, hasStoredResults);
        String actualQuery = actualMode ? "&results=true" : "";
        model.addAttribute("tournament", safeTournament);
        model.addAttribute("tournamentLabel", web.displayTournament(safeTournament));
        model.addAttribute("round", safeRound);
        model.addAttribute("roundLabel", web.displayViewMode(safeRound));
        model.addAttribute("groupsMode", groupsMode);
        if (groupsMode) {
            // Manual group picks are not part of prediction workflow; future actual results use a separate mode.
            model.addAttribute("editUrl", null);
        }

        String nextRound = web.nextViewRound(safeRound);
        boolean nextExists = nextRound != null && web.generatedDataExists(web.roundFileForView(safeTournament, nextRound));
        model.addAttribute("nextViewUrl", nextRound != null ? "/view/" + nextRound + "?tournament=" + safeTournament + actualQuery : "#");
        model.addAttribute("nextViewLabel", nextRound != null ? "Go to " + web.displayViewMode(nextRound) : "Go to Next");
        model.addAttribute("nextViewEnabled", nextExists);
        model.addAttribute("hasNextRound", nextRound != null);
        model.addAttribute("nextNavUrl", nextRound != null ? "/view/" + nextRound + "?tournament=" + safeTournament + actualQuery : null);
        model.addAttribute("nextNavLabel", nextRound != null ? web.displayViewMode(nextRound) + " →" : null);
        model.addAttribute("nextNavEnabled", nextExists);

        // Offer a direct model run when the next stage has not been generated
        String nextRunMode = web.nextRunModeForView(safeRound);
        String nextRunPrereq = web.nextRunPrereqForView(safeRound);
        boolean canNextRun = !nextExists && nextRunMode != null
                && nextRunPrereq != null && web.generatedDataExists(web.predictionFile(safeTournament, nextRunPrereq));
        model.addAttribute("nextRunMode", nextRunMode);
        model.addAttribute("canNextRun", canNextRun);
        model.addAttribute("nextRunLabel", nextRunMode != null ? "Run " + web.displayMode(nextRunMode) : "");
        if (groupsMode && !web.bracketHasStage(safeTournament, "LAST_32")
                && web.bracketHasStage(safeTournament, "LAST_16")) {
            boolean directResults = web.generatedDataExists(web.simulationFile(safeTournament, "simulation_last_16.csv"));
            String directUrl = "/view/last_16_match?tournament=" + safeTournament + actualQuery;
            model.addAttribute("nextViewUrl", directUrl);
            model.addAttribute("nextViewLabel", "Go to Last 16");
            model.addAttribute("nextViewEnabled", directResults);
            model.addAttribute("nextNavUrl", directResults ? directUrl : null);
            model.addAttribute("nextNavLabel", "Last 16 →");
            model.addAttribute("nextNavEnabled", directResults);
            model.addAttribute("nextRunMode", "tournament");
            model.addAttribute("canNextRun", !directResults && web.generatedDataExists(
                    web.simulationFile(safeTournament, "simulation_group_routes.csv")));
            model.addAttribute("nextRunLabel", "Run Knockout Rounds");
        }

        String prevRound = web.prevViewRound(safeRound);
        if ("last_16_match".equals(safeRound)
                && !web.bracketHasStage(safeTournament, "LAST_32")
                && web.bracketHasStage(safeTournament, "LAST_16")) {
            prevRound = "groups";
        }
        boolean prevExists = prevRound != null && web.generatedDataExists(web.roundFileForView(safeTournament, prevRound));
        model.addAttribute("prevViewUrl", prevRound != null ? "/view/" + prevRound + "?tournament=" + safeTournament + actualQuery : "#");
        model.addAttribute("prevViewLabel", prevRound != null ? "Back to " + web.displayViewMode(prevRound) : "Back to Previous");
        model.addAttribute("prevViewEnabled", prevExists);
        model.addAttribute("hasPrevRound", prevRound != null);
        model.addAttribute("prevNavUrl", prevExists ? "/view/" + prevRound + "?tournament=" + safeTournament + actualQuery : null);
        model.addAttribute("prevNavLabel", prevRound != null ? "← " + web.displayViewMode(prevRound) : null);
        model.addAttribute("prevNavEnabled", prevExists);
        model.addAttribute("pageTitle", "last_32_match".equals(safeRound) ? "Last 32" : "View " + web.displayViewMode(safeRound));

        if (groupsMode) {
            CsvLoader csvLoader = new CsvLoader(web.projectRoot).withConfig(predictionConfig);
            Map<String, EloBreakdown> eloBreakdowns;
            try { eloBreakdowns = csvLoader.loadEloBreakdowns(safeTournament); }
            catch (Exception e) { eloBreakdowns = Map.of(); }


            boolean hasGroupSimulation = web.generatedDataExists(web.simulationFile(safeTournament, "simulation_groups.csv"))
                    && web.generatedDataExists(web.simulationFile(safeTournament, "simulation_group_routes.csv"));
            List<Map<String, String>> groupSimulationRows = hasGroupSimulation
                    ? web.readCsv(web.simulationFile(safeTournament, "simulation_groups.csv")).rows()
                    : List.of();
            Map<String, String> groupByTeam = csvData.rows().stream().collect(Collectors.toMap(
                    row -> row.getOrDefault("team", ""), row -> row.getOrDefault("group", ""),
                    (first, ignored) -> first, LinkedHashMap::new));
            groupSimulationRows = groupSimulationRows.stream().map(row -> {
                Map<String, String> enriched = new LinkedHashMap<>(row);
                enriched.put("group", groupByTeam.getOrDefault(row.getOrDefault("team", ""), ""));
                return enriched;
            }).toList();
            if (actualMode) {
                Map<String, List<GroupStandingRow>> actualStandings = web.actualGroupStandings(safeTournament);
                Set<String> actualAdvancers = new LinkedHashSet<>();
                for (List<GroupStandingRow> table : actualStandings.values()) {
                    for (int i = 0; i < table.size() && i < 2; i++) {
                        actualAdvancers.add(table.get(i).team());
                    }
                }
                if (web.bracketHasStage(safeTournament, "LAST_32")) {
                    List<CsvLoader.BracketEntry> brackets = new CsvLoader(web.projectRoot).loadBrackets(safeTournament);
                    actualAdvancers.addAll(web.actualThirdPlaceAssignments(safeTournament, brackets).values());
                }
                groupSimulationRows = groupSimulationRows.stream().map(row -> {
                    Map<String, String> enriched = new LinkedHashMap<>(row);
                    String group = enriched.getOrDefault("group", "");
                    String teamName = enriched.getOrDefault("team", "");
                    List<GroupStandingRow> table = actualStandings.getOrDefault(group, List.of());
                    for (int i = 0; i < table.size(); i++) {
                        if (teamName.equals(table.get(i).team())) {
                            enriched.put("actual_position", String.valueOf(i + 1));
                            break;
                        }
                    }
                    enriched.put("actual_advanced", actualAdvancers.contains(teamName) ? "yes" : "no");
                    return enriched;
                }).toList();
            }
            model.addAttribute("groupSimulationHtml",
                    SimulationResultsRenderer.renderGroupSimulation(groupSimulationRows, safeTournament));
            List<Map<String, String>> groupScorelineRows = hasGroupSimulation
                    ? web.readMatchupPredictionRows(safeTournament, "groups")
                    : List.of();
            model.addAttribute("groupMatchesHtml",
                    web.renderGroupMatches(safeTournament, groupScorelineRows, eloBreakdowns, actualMode));

            LinkedHashMap<String, List<GroupViewRow>> groupedRows = new LinkedHashMap<>();
            for (Map<String, String> row : csvData.rows()) {
                String group = WebText.trim(row.getOrDefault("group", ""));
                if (group.isEmpty()) continue;
                String groupTeam = row.getOrDefault("team", "");
                groupedRows.computeIfAbsent(group, ignored -> new ArrayList<>()).add(new GroupViewRow(
                        groupTeam,
                        WebText.formatQualBonus(row.getOrDefault("qual_bonus", "")),
                        row.getOrDefault("predicted_position", ""),
                        row.getOrDefault("group_winner", ""),
                        row.getOrDefault("runner_up", ""),
                        row.getOrDefault("3rd_place", ""),
                        eloBreakdowns.get(groupTeam)
                ));
            }
            model.addAttribute("groupedRows", groupedRows);
            return "view-round";
        } else {
            HtmlReporter reporter = new HtmlReporter().withConfig(predictionConfig)
                    .withPathNavigation(safeTournament, safeRound);
            String resultsRound = safeRound.endsWith("_match")
                    ? safeRound.substring(0, safeRound.length() - "_match".length())
                    : safeRound;
            String requestedPath = WebText.trim(path);
            if ("upset".equalsIgnoreCase(requestedPath)) {
                requestedPath = "alt";
            }
            boolean actualViewRequested = actualMode || "results".equalsIgnoreCase(requestedPath) || "actual".equalsIgnoreCase(requestedPath);
            List<String> lines = List.of();
            List<Map<String, String>> actualRows = actualViewRequested
                    ? web.loadActualRoundResultRows(safeTournament, resultsRound)
                    : List.of();
            Map<String, String> actualResultLabels = actualViewRequested
                    ? web.loadActualRoundResultLabels(safeTournament, resultsRound)
                    : Map.of();
            List<ResultEntryRow> fixtureRows = actualViewRequested
                    ? web.buildActualResultsEditorRows(safeTournament, resultsRound)
                    : List.of();
            boolean hasActualRows = actualViewRequested
                    ? (!fixtureRows.isEmpty() || !actualRows.isEmpty() || !actualResultLabels.isEmpty())
                    : !actualResultLabels.isEmpty();
            Set<String> actualAdvancingTeams = actualViewRequested
                    ? actualResultLabels.values().stream()
                            .filter(label -> label != null && !label.isBlank() && !"Draw".equalsIgnoreCase(label))
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                    : Set.of();
            if (actualViewRequested && resultsRound.equals(web.openingKnockoutRound(safeTournament))
                    && web.hasResultsData(safeTournament, "groups")) {
                actualAdvancingTeams = web.actualGroupAdvancers(safeTournament);
            }
            String safePathFilter = requestedPath;
            if ("both".equalsIgnoreCase(safePathFilter)) {
                safePathFilter = "all";
            }
            if ("predicted".equalsIgnoreCase(safePathFilter)) {
                safePathFilter = "prediction";
            }
            if ("actual".equalsIgnoreCase(safePathFilter)) {
                safePathFilter = "results";
            }
            if ("upset".equalsIgnoreCase(safePathFilter)) {
                safePathFilter = "alt";
            }
            if (!Set.of("all", "results", "alt", "prediction").contains(safePathFilter)) {
                safePathFilter = "all";
            }
            String safeTeamFilter = WebText.trim(team);
            if (!safeTeamFilter.isBlank() && safeRound.endsWith("_match") && !web.generatedDataExists(file)) {
                lines = KnockoutViewRows.merge(lines, runtimeMatchupRowsService.matchupLinesForRound(
                        visualizationRoundRowsThrough(safeTournament, actualViewRequested, safeRound), safeRound, safeTeamFilter, lines));
            }
            int pageSize = WebControllerService.VIEW_PAGE_SIZE;
            List<String> teamNameSourceLines = lines;
            int totalRows;
            int pageCount;
            int currentPage;
            List<String> displayLines;
            List<Map<String, String>> displayRows;
            if (!actualViewRequested) {
                WebControllerService.MatchupPageLines pageLines = web.readCachedMatchupPage(
                        safeTournament, resultsRound, file, safePathFilter, safeTeamFilter, page, pageSize);
                totalRows = pageLines.totalRows();
                pageCount = Math.max(1, (totalRows + pageSize - 1) / pageSize);
                currentPage = Math.min(Math.max(1, page), pageCount);
                if (currentPage != Math.max(1, page)) {
                    pageLines = web.readCachedMatchupPage(safeTournament, resultsRound, file, safePathFilter, safeTeamFilter, currentPage, pageSize);
                }
                displayLines = pageLines.lines();
                displayRows = pageLines.rows();
                teamNameSourceLines = matchupTeamNameLines(pageLines.lines(), web.readCachedMatchupTeamNames(safeTournament, resultsRound, file));
                lines = displayLines;
            } else {
                WebControllerService.MatchupPageLines pageLines = web.readCachedMatchupPage(
                        safeTournament, resultsRound, file, safePathFilter, safeTeamFilter, page, pageSize);
                totalRows = pageLines.totalRows();
                pageCount = Math.max(1, (totalRows + pageSize - 1) / pageSize);
                currentPage = Math.min(Math.max(1, page), pageCount);
                if (currentPage != Math.max(1, page)) {
                    pageLines = web.readCachedMatchupPage(safeTournament, resultsRound, file, safePathFilter, safeTeamFilter, currentPage, pageSize);
                }
                displayLines = pageLines.lines();
                displayRows = pageLines.rows();
                teamNameSourceLines = matchupTeamNameLines(pageLines.lines(), web.readCachedMatchupTeamNames(safeTournament, resultsRound, file));

                Map<String, String> predictedWinners = KnockoutViewRows.predictedWinnersByMatch(displayLines);
                List<String> fixtureContextLines = displayLines;
                Map<String, String[]> fixtureSlotsByMatch = fixtureSlotsByMatch(safeTournament, resultsRound);
                List<String> fixtureLines = !fixtureRows.isEmpty()
                        ? KnockoutViewRows.buildFixtureRows(displayLines, fixtureRows, actualResultLabels, fixtureContextLines, fixtureSlotsByMatch)
                        : List.of();
                List<String> actualLines = fixtureLines.isEmpty() && !actualRows.isEmpty()
                        ? KnockoutViewRows.buildResultRows(displayLines, actualRows, predictedWinners)
                        : fixtureLines.isEmpty() && !actualResultLabels.isEmpty()
                                ? KnockoutViewRows.buildResultOnlyRows(displayLines, actualResultLabels)
                                : List.of();
                if (!fixtureLines.isEmpty()) {
                    displayLines = KnockoutViewRows.merge(displayLines, fixtureLines);
                }
                if (!actualLines.isEmpty()) {
                    displayLines = KnockoutViewRows.merge(displayLines, actualLines);
                }
                displayRows = csvLinesToRows(displayLines);
                lines = displayLines;
            }
            model.addAttribute("actualViewUrl", hasActualRows ? "/view/" + safeRound + "?tournament=" + safeTournament + (actualViewRequested ? "&results=false" : "&results=true") : null);
            model.addAttribute("actualViewLabel", actualViewRequested ? "Hide Results" : "Load Results");
            model.addAttribute("actualViewEnabled", hasActualRows);
            CsvLoader csvLoader = new CsvLoader(web.projectRoot).withConfig(predictionConfig);
            String oddsColumn = web.oddsColumnForRound(safeRound);
            Map<String, String> odds = oddsColumn != null ? csvLoader.loadOdds(safeTournament, oddsColumn) : Map.of();
            Map<String, com.tournamentpredictor.services.calculation.EloBreakdown> eloBreakdowns;
            try {
                eloBreakdowns = csvLoader.loadEloBreakdowns(safeTournament);
            } catch (Exception e) {
                eloBreakdowns = Map.of();
            }
            String simulationRound = SimulationViewDataService.simulationRoundForMatchView(safeRound);
            String likelihoodRound = simulationRound != null ? simulationRound : "last_32".equals(safeRound) ? "last_32" : null;
            List<Map<String, String>> currentSimulationRows = likelihoodRound == null
                    ? List.of()
                    : web.generatedDataExists(web.simulationFile(safeTournament, "simulation_" + likelihoodRound + ".csv"))
                            ? web.readCsv(web.simulationFile(safeTournament, "simulation_" + likelihoodRound + ".csv")).rows()
                            : List.of();
            String snapshotSimulationRound = simulationRound;
            List<Map<String, String>> snapshotSimulationRows = currentSimulationRows;
            if (snapshotSimulationRows.isEmpty() && "last_32".equals(simulationRound)) {
                snapshotSimulationRows = web.matchupAdvanceRows(safeTournament, resultsRound, file);
                snapshotSimulationRound = "last_32";
            }
            List<Map<String, String>> scorelineRows = likelihoodRound == null
                    ? List.of()
                    : web.generatedDataExists(web.simulationFile(safeTournament, "simulation_scorelines_" + likelihoodRound + ".csv"))
                            ? web.readCsv(web.simulationFile(safeTournament, "simulation_scorelines_" + likelihoodRound + ".csv")).rows()
                            : List.of();
            String filterQuery = "&path=" + WebText.webEncode(safePathFilter) + (!safeTeamFilter.isBlank() ? "&team=" + WebText.webEncode(safeTeamFilter) : "");
            String pageBaseUrl = "/view/" + safeRound + "?tournament=" + safeTournament + (actualViewRequested ? "&results=true" : "") + filterQuery;
            reporter.withServerPagination(pageBaseUrl, currentPage, pageCount, pageSize)
                    .withActualMode(actualViewRequested)
                    .withActiveFilters(safePathFilter, safeTeamFilter)
                    .withTeamNames(KnockoutViewRows.allTeamNames(teamNameSourceLines))
                    .withActualResultScores(web.actualScoreMap(actualRows))
                    .withActualResultLabels(actualResultLabels);
            if (likelihoodRound != null) {
                reporter.withSimulationAdvance(SimulationViewDataService.simulationAdvanceMap(currentSimulationRows, SimulationViewDataService.advanceColumnForRound(likelihoodRound)));
                Map<String, String> matchupLikelihoods = !safeTeamFilter.isBlank()
                        ? SimulationViewDataService.matchupLikelihoodMapFromRows(displayRows)
                        : "last_32".equals(likelihoodRound)
                                ? new LinkedHashMap<>(SimulationViewDataService.matchupLikelihoodMapFromRows(displayRows))
                                : web.routeWeightedMatchupLikelihoods(safeTournament, likelihoodRound, displayRows, web.readCsv(web.predictionFile(safeTournament, "groups.csv")).rows());
                if ("last_32".equals(likelihoodRound) && !scorelineRows.isEmpty()) {
                    matchupLikelihoods.putAll(SimulationViewDataService.simulationMatchupLikelihoodMap(scorelineRows, likelihoodRound));
                }
                reporter.withMatchupLikelihood(matchupLikelihoods);
                Map<String, String> matchupRuns = new LinkedHashMap<>(SimulationViewDataService.matchupRunsMapFromRows(displayRows));
                if (!scorelineRows.isEmpty()) {
                    matchupRuns.putAll(SimulationViewDataService.simulationMatchupRunsMap(scorelineRows, likelihoodRound));
                }
                reporter.withMatchupSimulationRuns(matchupRuns);
            }
            reporter.printMatchups(web.displayViewMode(safeRound), displayLines, new EloCalculator(), null, Map.of(), eloBreakdowns);
            String outputHtml = reporter.getHtml();
            if (snapshotSimulationRound != null && !snapshotSimulationRows.isEmpty()) {
                boolean showActualSnapshotColors = actualViewRequested && ("all".equalsIgnoreCase(safePathFilter) || "results".equalsIgnoreCase(safePathFilter));
                outputHtml = SimulationResultsRenderer.renderSnapshot(snapshotSimulationRows, safeTournament, snapshotSimulationRound,
                        showActualSnapshotColors ? actualAdvancingTeams : Set.of(), actualViewRequested && simulationRound != null ? web.loadLiveSimulationRows(safeTournament, simulationRound) : List.of())
                        + outputHtml;
            }
            model.addAttribute("output", outputHtml);
            model.addAttribute("mode", web.displayViewMode(safeRound));
            return "result";
        }
    }


    @GetMapping("/view/{round}/visualize")
    public String redirectRoundVisualization(@PathVariable("round") String round,
                                             @RequestParam("tournament") String tournament,
                                             @RequestParam(value = "results", required = false) Boolean results,
                                             @RequestParam(value = "actual", required = false) Boolean actual,
                                             @RequestParam(value = "path", defaultValue = "all") String path,
                                             @RequestParam(value = "team", defaultValue = "") String team) {
        String safeRound = web.safeRound(round);
        if (!safeRound.endsWith("_match")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Visualization is only available for knockout match pages");
        }
        StringBuilder url = new StringBuilder("redirect:/view/visualize?tournament=")
                .append(WebText.webEncode(web.safeTournament(tournament)))
                .append("&path=").append(WebText.webEncode(WebText.trim(path).isBlank() ? "all" : WebText.trim(path)));
        if (results != null) {
            url.append("&results=").append(results);
        }
        if (actual != null) {
            url.append("&actual=").append(actual);
        }
        if (!WebText.trim(team).isBlank()) {
            url.append("&team=").append(WebText.webEncode(WebText.trim(team)));
        }
        return url.toString();
    }

    @GetMapping("/view/visualize")
    public String visualizePaths(@RequestParam("tournament") String tournament,
                                 @RequestParam(value = "results", required = false) Boolean results,
                                 @RequestParam(value = "actual", required = false) Boolean actual,
                                 @RequestParam(value = "path", defaultValue = "all") String path,
                                 @RequestParam(value = "team", defaultValue = "") String team,
                                 Model model) throws IOException {
        String safeTournament = web.safeTournament(tournament);
        boolean actualMode = ResultsModeService.shouldShowResults(results, actual, hasAnyVisualizationResultsData(safeTournament));
        PathVisualizationService visualizer = new PathVisualizationService();
        String selectedTeam = WebText.trim(team);
        List<PathVisualizationService.RoundRows> roundRows = selectedTeam.isBlank()
                ? visualizationRoundRows(safeTournament, actualMode)
                : List.of();
        CachedVisualization cachedVisualization = selectedTeam.isBlank()
                ? new CachedVisualization(List.of(), new PathVisualizationService.Graph(List.of(), List.of(), Set.of()),
                        visualizationTeamOptions(roundRows, List.of()), visualizer.toJson(new PathVisualizationService.Graph(List.of(), List.of(), Set.of())))
                : cachedVisualization(safeTournament, actualMode, selectedTeam, path, visualizer);
        List<Map<String, String>> routeInstanceRows = cachedVisualization.routeInstanceRows();
        PathVisualizationService.Graph graph = cachedVisualization.graph();
        model.addAttribute("tournament", safeTournament);
        model.addAttribute("tournamentLabel", web.displayTournament(safeTournament));
        model.addAttribute("roundLabel", "Full Tournament");
        model.addAttribute("path", WebText.trim(path).isBlank() ? "all" : WebText.trim(path));
        model.addAttribute("team", selectedTeam);
        model.addAttribute("teamRequired", selectedTeam.isBlank());
        model.addAttribute("teamOptions", selectedTeam.isBlank()
                ? visualizationTeamOptions(roundRows, routeInstanceRows)
                : cachedVisualization.teamOptions());
        model.addAttribute("results", actualMode);
        model.addAttribute("graphJson", cachedVisualization.graphJson());
        model.addAttribute("nodeCount", graph.nodes().size());
        model.addAttribute("edgeCount", graph.edges().size());
        return "path-visualization";
    }

    private List<PathVisualizationService.RoundRows> visualizationRoundRows(String tournament, boolean actualMode) throws IOException {
        return visualizationRoundRowsThrough(tournament, actualMode, "");
    }

    private List<PathVisualizationService.RoundRows> visualizationRoundRowsThrough(String tournament, boolean actualMode, String finalRound) throws IOException {
        List<PathVisualizationService.RoundRows> roundRows = new ArrayList<>();
        String stopRound = WebText.trim(finalRound);
        for (String candidateRound : WebControllerService.VIEW_ROUND_SEQUENCE) {
            if (!candidateRound.endsWith("_match")) continue;
            Path candidateFile = web.roundFileForView(tournament, candidateRound);
            if (!web.generatedDataExists(candidateFile)) continue;
            roundRows.add(new PathVisualizationService.RoundRows(candidateRound,
                    web.displayViewMode(candidateRound), visualizationRowsForRound(tournament, candidateRound, actualMode)));
            if (!stopRound.isBlank() && candidateRound.equalsIgnoreCase(stopRound)) {
                break;
            }
        }
        return roundRows;
    }

    private boolean hasAnyVisualizationResultsData(String tournament) {
        for (String candidateRound : WebControllerService.VIEW_ROUND_SEQUENCE) {
            if (candidateRound.endsWith("_match") && web.hasResultsData(tournament, candidateRound)) {
                return true;
            }
        }
        return false;
    }


    private CachedVisualization cachedVisualization(String tournament, boolean actualMode, String selectedTeam, String path,
                                                    PathVisualizationService visualizer) throws IOException {
        String cacheKey = tournament + "|" + actualMode + "|" + selectedTeam.toLowerCase(java.util.Locale.ROOT)
                + "|" + WebText.trim(path).toLowerCase(java.util.Locale.ROOT);
        String signature = visualizationSignature(tournament, actualMode);
        CachedVisualization cached = visualizationCache.get(cacheKey);
        if (cached != null && signature.equals(cached.signature())) {
            return cached;
        }
        List<PathVisualizationService.RoundRows> roundRows = visualizationRoundRows(tournament, actualMode);
        List<Map<String, String>> routeInstanceRows = csvLinesToRows(routeInstanceService.buildLines(roundRows, selectedTeam));
        PathVisualizationService.Graph graph = routeInstanceService.buildGraph(routeInstanceRows, selectedTeam, path);
        CachedVisualization next = new CachedVisualization(signature, routeInstanceRows, graph,
                visualizationTeamOptions(roundRows, routeInstanceRows), visualizer.toJson(graph));
        visualizationCache.put(cacheKey, next);
        return next;
    }

    private String visualizationSignature(String tournament, boolean actualMode) throws IOException {
        StringBuilder signature = new StringBuilder();
        for (String candidateRound : WebControllerService.VIEW_ROUND_SEQUENCE) {
            if (!candidateRound.endsWith("_match")) continue;
            Path candidateFile = web.roundFileForView(tournament, candidateRound);
            appendFileSignature(signature, candidateFile);
            if (actualMode) {
                String resultsRound = candidateRound.substring(0, candidateRound.length() - "_match".length());
                appendFileSignature(signature, web.resultsFile(tournament, resultsRound));
            }
        }
        if (actualMode) {
            appendFileSignature(signature, web.resultsFile(tournament, "groups"));
        }
        return signature.toString();
    }

    private void appendFileSignature(StringBuilder signature, Path path) throws IOException {
        if (!web.generatedDataExists(path)) {
            signature.append(path.getFileName()).append(":missing;");
            return;
        }
        List<String> lines = web.readGeneratedLines(path);
        signature.append(path.getFileName()).append(':')
                .append(lines.size()).append(':')
                .append(lines.hashCode()).append(';');
    }

    private List<String> visualizationTeamOptions(List<PathVisualizationService.RoundRows> roundRows, List<Map<String, String>> routeInstanceRows) {
        EloCalculator elo = new EloCalculator();
        Set<String> teams = new LinkedHashSet<>();
        for (PathVisualizationService.RoundRows round : roundRows) {
            for (Map<String, String> row : round.rows()) {
                String team1 = elo.extractTeamName(row.getOrDefault("team1", ""));
                String team2 = elo.extractTeamName(row.getOrDefault("team2", ""));
                if (!team1.isBlank()) teams.add(team1);
                if (!team2.isBlank()) teams.add(team2);
            }
        }
        for (Map<String, String> row : routeInstanceRows) {
            String team = WebText.trim(row.getOrDefault("team", ""));
            String opponent = WebText.trim(row.getOrDefault("opponent", ""));
            if (!team.isBlank()) teams.add(team);
            if (!opponent.isBlank()) teams.add(opponent);
        }
        return teams.stream().sorted().toList();
    }

    private List<Map<String, String>> visualizationRowsForRound(String tournament, String round, boolean actualMode) throws IOException {
        Path file = web.roundFileForView(tournament, round);
        String resultsRound = round.endsWith("_match") ? round.substring(0, round.length() - "_match".length()) : round;
        List<String> lines = web.readCachedLines(tournament, "matchup", resultsRound, file);
        Path contextFile = web.matchupFile(tournament, resultsRound + ".csv");
        if (web.generatedDataExists(contextFile)) {
            lines = KnockoutViewRows.enrichPathContext(lines, web.readCachedLines(tournament, "matchup", resultsRound, contextFile), groupContextLines(tournament),
                    fixtureSlotsByMatch(tournament, resultsRound));
        }
        if (actualMode) {
            Map<String, String> predictedWinners = KnockoutViewRows.predictedWinnersByMatch(lines);
            List<Map<String, String>> actualRows = web.loadActualRoundResultRows(tournament, resultsRound);
            Map<String, String> actualResultLabels = web.loadActualRoundResultLabels(tournament, resultsRound);
            List<String> actualLines = !actualRows.isEmpty()
                    ? KnockoutViewRows.buildResultRows(lines, actualRows, predictedWinners)
                    : !actualResultLabels.isEmpty()
                            ? KnockoutViewRows.buildResultOnlyRows(lines, actualResultLabels)
                            : List.of();
            if (!actualLines.isEmpty()) {
                lines = KnockoutViewRows.merge(lines, actualLines);
            }
        }
        List<Map<String, String>> rows = csvLinesToRows(lines);
        enrichVisualizationFixtureSlots(tournament, resultsRound, rows);
        enrichVisualizationMatchupLikelihoods(tournament, resultsRound, rows);
        return rows;
    }

    private void enrichVisualizationFixtureSlots(String tournament, String round, List<Map<String, String>> rows) {
        if (rows.isEmpty()) return;
        Map<String, String[]> slots = fixtureSlotsByMatch(tournament, round);
        if (slots.isEmpty()) return;
        for (Map<String, String> row : rows) {
            String[] matchSlots = slots.get(WebText.trim(row.getOrDefault("match_id", "")));
            if (matchSlots == null || matchSlots.length < 2) continue;
            row.putIfAbsent("team1_bracket_seed", matchSlots[0]);
            row.putIfAbsent("team2_bracket_seed", matchSlots[1]);
        }
    }

    private void enrichVisualizationMatchupLikelihoods(String tournament, String round, List<Map<String, String>> rows) throws IOException {
        if (rows.isEmpty()) return;
        EloCalculator elo = new EloCalculator();
        Map<String, MatchupLikelihood> likelihoods = new LinkedHashMap<>(matchupLikelihoodsFromScorelines(tournament, round, elo));
        if ("last_32".equals(round)) {
            matchupLikelihoodsFromGroupSlotProbabilities(tournament, rows, elo)
                    .forEach(likelihoods::putIfAbsent);
        }
        if (likelihoods.isEmpty()) return;
        for (Map<String, String> row : rows) {
            if (!WebText.trim(row.getOrDefault("matchup_pct", "")).isBlank()) continue;
            String matchId = WebText.trim(row.getOrDefault("match_id", ""));
            String team1 = elo.extractTeamName(row.getOrDefault("team1", ""));
            String team2 = elo.extractTeamName(row.getOrDefault("team2", ""));
            MatchupLikelihood likelihood = likelihoods.get(matchupKey(matchId, team1, team2));
            if (likelihood == null) continue;
            row.put("matchup_pct", likelihood.percent());
            row.putIfAbsent("matchup_runs", likelihood.runs());
        }
    }

    private Map<String, MatchupLikelihood> matchupLikelihoodsFromScorelines(String tournament, String round, EloCalculator elo) throws IOException {
        String simulationRound = "last_32".equals(round) ? "last_32" : round;
        Path scorelines = web.simulationFile(tournament, "simulation_scorelines_" + simulationRound + ".csv");
        if (!web.generatedDataExists(scorelines)) return Map.of();
        Map<String, MatchupLikelihood> likelihoods = new LinkedHashMap<>();
        for (Map<String, String> row : web.readCsv(scorelines).rows()) {
            if (!round.equalsIgnoreCase(WebText.trim(row.getOrDefault("stage", "")))) continue;
            String matchId = WebText.trim(row.getOrDefault("match_id", ""));
            String team1 = elo.extractTeamName(row.getOrDefault("team1", ""));
            String team2 = elo.extractTeamName(row.getOrDefault("team2", ""));
            String pct = WebText.trim(row.getOrDefault("matchup_pct", ""));
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank() || pct.isBlank()) continue;
            likelihoods.putIfAbsent(matchupKey(matchId, team1, team2),
                    new MatchupLikelihood(pct, WebText.trim(row.getOrDefault("matchup_runs", ""))));
        }
        return likelihoods;
    }

    private Map<String, MatchupLikelihood> matchupLikelihoodsFromGroupSlotProbabilities(
            String tournament, List<Map<String, String>> rows, EloCalculator elo) throws IOException {
        Path groups = web.predictionFile(tournament, "groups.csv");
        if (!web.generatedDataExists(groups)) return Map.of();
        Map<String, String> weighted = RouteLikelihoodService.matchupLikelihoodMap(rows, web.readCsv(groups).rows());
        if (weighted.isEmpty()) return Map.of();
        Map<String, MatchupLikelihood> likelihoods = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String matchId = WebText.trim(row.getOrDefault("match_id", ""));
            String team1 = elo.extractTeamName(row.getOrDefault("team1", ""));
            String team2 = elo.extractTeamName(row.getOrDefault("team2", ""));
            String pct = weighted.get(RouteLikelihoodService.matchupLikelihoodKey(matchId, team1, team2));
            if (pct == null || pct.isBlank()) continue;
            likelihoods.put(matchupKey(matchId, team1, team2), new MatchupLikelihood(pct, ""));
        }
        return likelihoods;
    }

    private String matchupKey(String matchId, String team1, String team2) {
        String left = normalizeTeamKey(team1);
        String right = normalizeTeamKey(team2);
        if (left.compareTo(right) > 0) {
            String tmp = left;
            left = right;
            right = tmp;
        }
        return WebText.trim(matchId).toUpperCase(java.util.Locale.ROOT) + "|" + left + "|" + right;
    }

    private String normalizeTeamKey(String team) {
        return WebText.trim(team).toLowerCase(java.util.Locale.ROOT);
    }

    private record MatchupLikelihood(String percent, String runs) {}

    private record CachedVisualization(String signature, List<Map<String, String>> routeInstanceRows,
                                       PathVisualizationService.Graph graph, List<String> teamOptions, String graphJson) {
        CachedVisualization(List<Map<String, String>> routeInstanceRows, PathVisualizationService.Graph graph,
                            List<String> teamOptions, String graphJson) {
            this("", routeInstanceRows, graph, teamOptions, graphJson);
        }
    }


    private List<String> matchupTeamNameLines(List<String> displayLines, List<String> teams) {
        String header = displayLines == null || displayLines.isEmpty()
                ? "match_id,team1,team2,path,prediction"
                : displayLines.get(0);
        List<String> lines = new ArrayList<>();
        lines.add(header);
        for (String team : teams) {
            lines.add("," + team + ",,,");
        }
        return lines;
    }

    private List<String> groupContextLines(String tournament) throws IOException {
        Path groupsFile = web.predictionFile(tournament, "groups.csv");
        return web.generatedDataExists(groupsFile) ? web.readGeneratedLines(groupsFile) : List.of();
    }

    private List<Map<String, String>> csvLinesToRows(List<String> lines) throws IOException {
        String csv = String.join("\n", lines);
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(new java.io.StringReader(csv))) {
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (record.size() == 0 || record.get(0).isBlank()) continue;
                Map<String, String> row = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> header : parser.getHeaderMap().entrySet()) {
                    int idx = header.getValue();
                    row.put(header.getKey(), idx < record.size() ? record.get(idx) : "");
                }
                rows.add(row);
            }
            return rows;
        }
    }

    @GetMapping("/view/simulation")
    public String viewSimulation(@RequestParam("tournament") String tournament,
                                 @RequestParam(value = "simulationRound", defaultValue = "last_16") String simulationRound,
                                 @RequestParam(value = "team", defaultValue = "") String team, Model model,
                                 HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        String safeTournament = web.safeTournament(tournament);
        String safeSimulationRound = WebText.trim(simulationRound);
        if (!Set.of("last_32", "last_16", "last_8", "last_4", "final").contains(safeSimulationRound)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid simulation round");
        }
        if ("last_16".equals(safeSimulationRound)
                && !web.bracketHasStage(safeTournament, "LAST_32")
                && web.bracketHasStage(safeTournament, "LAST_16")
                && web.generatedDataExists(web.matchupFile(safeTournament, "last_16.csv"))) {
            return "redirect:/view/last_16_match?tournament=" + safeTournament;
        }
        List<Map<String, String>> rows = web.readCsv(web.simulationFile(safeTournament,
                "simulation_" + safeSimulationRound + ".csv")).rows();
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulation results not found");
        }
        List<Map<String, String>> paths = web.readCsv(web.simulationFile(safeTournament,
                "simulation_paths_" + safeSimulationRound + ".csv")).rows();
        List<Map<String, String>> scorelines = web.readCsv(web.simulationFile(safeTournament,
                "simulation_scorelines_" + safeSimulationRound + ".csv")).rows();
        model.addAttribute("tournament", safeTournament);
        model.addAttribute("tournamentLabel", web.displayTournament(safeTournament));
        model.addAttribute("pageTitle", "Tournament Probabilities");
        model.addAttribute("mode", "Tournament Probabilities");
        model.addAttribute("simulationRows", rows);
        model.addAttribute("simulationRuns", rows.get(0).getOrDefault("simulation_runs", ""));
        model.addAttribute("prevNavUrl", "/view/groups?tournament=" + safeTournament);
        model.addAttribute("prevNavLabel", "← Group Stage");
        model.addAttribute("prevNavEnabled", true);
        model.addAttribute("nextNavUrl", null);
        model.addAttribute("nextNavEnabled", false);
        model.addAttribute("canNextRun", false);
        return "simulation-results";
    }
}

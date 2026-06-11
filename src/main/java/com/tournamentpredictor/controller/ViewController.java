package com.tournamentpredictor.controller;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.services.web.WebControllerService;
import com.tournamentpredictor.services.web.WebText;
import com.tournamentpredictor.services.web.RouteLikelihoodService;
import com.tournamentpredictor.services.web.SimulationViewDataService;
import com.tournamentpredictor.services.web.StartRowViewService;
import com.tournamentpredictor.services.web.ResultsModeService;
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
public class ViewController {

    protected final PredictionConfig predictionConfig;
    protected final WebControllerService web;
    private final StartRowViewService startRowViewService = new StartRowViewService();

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
        boolean nextExists = Files.exists(web.predictionFile(safeTournament, "groups.csv"));
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
            if (!Files.exists(candidateFile)) continue;
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
        CsvData csvData = web.readCsv(file);
        if (csvData.rows().isEmpty()) {
            if ("groups".equals(safeRound) && Files.exists(web.predictionFile(safeTournament, "start.csv"))) {
                return "redirect:/view/start?tournament=" + WebText.webEncode(safeTournament);
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, file.getFileName() + " not found");
        }

        boolean groupsMode = "groups".equals(safeRound);
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
        boolean nextExists = nextRound != null && Files.exists(web.roundFileForView(safeTournament, nextRound));
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
                && nextRunPrereq != null && Files.exists(web.predictionFile(safeTournament, nextRunPrereq));
        model.addAttribute("nextRunMode", nextRunMode);
        model.addAttribute("canNextRun", canNextRun);
        model.addAttribute("nextRunLabel", nextRunMode != null ? "Run " + web.displayMode(nextRunMode) : "");
        if (groupsMode && !web.bracketHasStage(safeTournament, "LAST_32")
                && web.bracketHasStage(safeTournament, "LAST_16")) {
            boolean directResults = Files.exists(web.simulationFile(safeTournament, "simulation_last_16.csv"));
            String directUrl = "/view/last_16_match?tournament=" + safeTournament + actualQuery;
            model.addAttribute("nextViewUrl", directUrl);
            model.addAttribute("nextViewLabel", "Go to Last 16");
            model.addAttribute("nextViewEnabled", directResults);
            model.addAttribute("nextNavUrl", directResults ? directUrl : null);
            model.addAttribute("nextNavLabel", "Last 16 →");
            model.addAttribute("nextNavEnabled", directResults);
            model.addAttribute("nextRunMode", "tournament");
            model.addAttribute("canNextRun", !directResults && Files.exists(
                    web.simulationFile(safeTournament, "simulation_group_routes.csv")));
            model.addAttribute("nextRunLabel", "Run Knockout Rounds");
        }

        String prevRound = web.prevViewRound(safeRound);
        if ("last_16_match".equals(safeRound)
                && !web.bracketHasStage(safeTournament, "LAST_32")
                && web.bracketHasStage(safeTournament, "LAST_16")) {
            prevRound = "groups";
        }
        boolean prevExists = prevRound != null && Files.exists(web.roundFileForView(safeTournament, prevRound));
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


            boolean hasGroupSimulation = Files.exists(web.simulationFile(safeTournament, "simulation_groups.csv"))
                    && Files.exists(web.simulationFile(safeTournament, "simulation_group_routes.csv"));
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
                    && Files.exists(web.simulationFile(safeTournament, "simulation_scorelines_groups.csv"))
                    ? web.readCsv(web.simulationFile(safeTournament, "simulation_scorelines_groups.csv")).rows()
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
            List<String> lines = java.nio.file.Files.readAllLines(file);
            Map<String, String> predictedWinners = KnockoutViewRows.predictedWinnersByMatch(lines);
            String resultsRound = safeRound.endsWith("_match")
                    ? safeRound.substring(0, safeRound.length() - "_match".length())
                    : safeRound;
            String requestedPath = WebText.trim(path);
            if ("upset".equalsIgnoreCase(requestedPath)) {
                requestedPath = "alt";
            }
            boolean actualViewRequested = actualMode || "results".equalsIgnoreCase(requestedPath) || "actual".equalsIgnoreCase(requestedPath);
            List<Map<String, String>> actualRows = actualViewRequested
                    ? web.loadActualRoundResultRows(safeTournament, resultsRound)
                    : List.of();
            Map<String, String> actualResultLabels = actualViewRequested
                    ? web.loadActualRoundResultLabels(safeTournament, resultsRound)
                    : Map.of();
            List<ResultEntryRow> fixtureRows = actualViewRequested
                    ? web.buildActualResultsEditorRows(safeTournament, resultsRound)
                    : List.of();
            List<String> fixtureContextLines = lines;
            Path fixtureContextFile = web.matchupFile(safeTournament, resultsRound + ".csv");
            if (Files.exists(fixtureContextFile)) {
                fixtureContextLines = java.nio.file.Files.readAllLines(fixtureContextFile);
                lines = KnockoutViewRows.enrichPathContext(lines, fixtureContextLines);
            }
            Map<String, String[]> fixtureSlotsByMatch = actualViewRequested && !fixtureRows.isEmpty()
                    ? fixtureSlotsByMatch(safeTournament, resultsRound)
                    : Map.of();
            List<String> fixtureLines = actualViewRequested && !fixtureRows.isEmpty()
                    ? KnockoutViewRows.buildFixtureRows(lines, fixtureRows, actualResultLabels, fixtureContextLines, fixtureSlotsByMatch)
                    : List.of();
            List<String> actualLines = actualViewRequested && fixtureLines.isEmpty() && !actualRows.isEmpty()
                    ? KnockoutViewRows.buildResultRows(lines, actualRows, predictedWinners)
                    : actualViewRequested && fixtureLines.isEmpty() && !actualResultLabels.isEmpty()
                            ? KnockoutViewRows.buildResultOnlyRows(lines, actualResultLabels)
                            : List.of();
            boolean hasActualRows = actualViewRequested ? (!fixtureLines.isEmpty() || !actualLines.isEmpty()) : !actualResultLabels.isEmpty();
            if (actualViewRequested && !fixtureLines.isEmpty()) {
                lines = KnockoutViewRows.merge(lines, fixtureLines);
            }
            if (actualViewRequested && !actualLines.isEmpty()) {
                lines = KnockoutViewRows.merge(lines, actualLines);
            }
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
            List<String> teamNameSourceLines = lines;
            lines = KnockoutViewRows.filter(lines, safePathFilter, safeTeamFilter);
            int pageSize = WebControllerService.VIEW_PAGE_SIZE;
            int totalRows = Math.max(0, lines.size() - 1);
            int pageCount = Math.max(1, (totalRows + pageSize - 1) / pageSize);
            int currentPage = Math.min(Math.max(1, page), pageCount);
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
            boolean directLast16 = simulationRound != null
                    && !web.bracketHasStage(safeTournament, "LAST_32")
                    && web.bracketHasStage(safeTournament, "LAST_16");
            if (!directLast16) {
                web.ensureSimulationExists(safeTournament, "last_32");
            }
            List<Map<String, String>> baselineSimulationRows = simulationRound == null
                    ? List.of()
                    : web.readCsv(web.simulationFile(safeTournament, directLast16
                            ? "simulation_last_16.csv"
                            : "simulation_last_32.csv")).rows();
            if (directLast16) {
                baselineSimulationRows.forEach(row -> row.put("simulation_origin", "group_stage"));
            }
            List<Map<String, String>> groupProbabilityRows = simulationRound == null
                    ? List.of()
                    : web.readCsv(web.predictionFile(safeTournament, "groups.csv")).rows();
            List<Map<String, String>> snapshotRows = directLast16
                    ? baselineSimulationRows
                    : "last_32_match".equals(safeRound)
                            ? RouteLikelihoodService.routeAverageLast16Rows(csvData.rows(), groupProbabilityRows)
                            : baselineSimulationRows;
            web.ensureSimulationExists(safeTournament, simulationRound);
            List<Map<String, String>> currentSimulationRows = simulationRound == null
                    ? List.of()
                    : directLast16
                            ? baselineSimulationRows
                            : web.readCsv(web.simulationFile(safeTournament, "simulation_" + simulationRound + ".csv")).rows();
            if (actualViewRequested && simulationRound != null) {
                web.ensureLiveSimulationExists(safeTournament, simulationRound);
            }
            List<Map<String, String>> liveSimulationRows = actualViewRequested && simulationRound != null
                    ? web.loadLiveSimulationRows(safeTournament, simulationRound)
                    : List.of();
            boolean currentRoundHasFixturesOrResults = actualViewRequested
                    && (!fixtureLines.isEmpty() || !actualLines.isEmpty() || !actualRows.isEmpty() || !actualResultLabels.isEmpty());
            boolean predictionFilterAllowsLiveRows = "all".equalsIgnoreCase(safePathFilter)
                    || "prediction".equalsIgnoreCase(safePathFilter);
            if (actualViewRequested && simulationRound != null && !liveSimulationRows.isEmpty()
                    && !currentRoundHasFixturesOrResults && predictionFilterAllowsLiveRows) {
                lines = KnockoutViewRows.merge(lines, web.buildLiveRoundRows(safeTournament, simulationRound));
            } else if (actualViewRequested && simulationRound != null && !currentRoundHasFixturesOrResults
                    && predictionFilterAllowsLiveRows && web.hasEarlierResultsForLivePrediction(safeTournament, simulationRound)) {
                lines = KnockoutViewRows.relabelPredictedRowsAsLive(lines);
            }
            List<Map<String, String>> snapshotLiveRows = liveSimulationRows;
            if (actualViewRequested && simulationRound != null && web.isResultsRoundComplete(safeTournament, simulationRound)) {
                snapshotLiveRows = web.resolvedSnapshotLiveRows(snapshotRows, liveSimulationRows,
                        SimulationViewDataService.advanceColumnForRound(simulationRound), actualAdvancingTeams);
            }
            Map<String, String> currentRoundAdvance = SimulationViewDataService.simulationAdvanceMap(
                    currentSimulationRows, SimulationViewDataService.advanceColumnForRound(simulationRound));
            for (Map<String, String> row : snapshotRows) {
                String marketOdds = odds.getOrDefault(row.getOrDefault("team", ""), "");
                if (!marketOdds.isBlank()) row.put("market_odds", marketOdds);
                String currentRoundPct = currentRoundAdvance.getOrDefault(row.getOrDefault("team", ""), "");
                if (!currentRoundPct.isBlank()) row.put("bet_probability", currentRoundPct);
            }
            String filterQuery = "&path=" + WebText.webEncode(safePathFilter) + (!safeTeamFilter.isBlank() ? "&team=" + WebText.webEncode(safeTeamFilter) : "");
            String pageBaseUrl = "/view/" + safeRound + "?tournament=" + safeTournament + (actualViewRequested ? "&results=true" : "") + filterQuery;
            reporter.withServerPagination(pageBaseUrl, currentPage, pageCount, pageSize)
                    .withActualMode(actualViewRequested)
                    .withActiveFilters(safePathFilter, safeTeamFilter)
                    .withTeamNames(KnockoutViewRows.allTeamNames(teamNameSourceLines))
                    .withActualResultScores(web.actualScoreMap(actualRows))
                    .withActualResultLabels(actualResultLabels);
            if (simulationRound != null) {
                reporter.withSimulationAdvance(SimulationViewDataService.simulationAdvanceMap(snapshotRows, SimulationViewDataService.advanceColumnForRound(simulationRound)));
                Path tournamentScorelines = web.simulationFile(safeTournament, directLast16
                        ? "simulation_scorelines_last_16.csv"
                        : "simulation_scorelines_last_32.csv");
                if (Files.exists(tournamentScorelines)) {
                    List<Map<String, String>> scorelineRows = web.readCsv(tournamentScorelines).rows();
                    reporter.withMatchupLikelihood(directLast16
                            ? SimulationViewDataService.simulationMatchupLikelihoodMap(scorelineRows, simulationRound)
                            : web.routeWeightedMatchupLikelihoods(
                                    safeTournament, simulationRound, csvData.rows(), groupProbabilityRows));
                    reporter.withMatchupSimulationRuns(SimulationViewDataService.simulationMatchupRunsMap(scorelineRows, simulationRound));
                }
            }
            reporter.printMatchups(web.displayViewMode(safeRound), lines, new EloCalculator(), null, Map.of(), eloBreakdowns);
            String outputHtml = reporter.getHtml();
            boolean showActualSnapshotColors = actualViewRequested && ("all".equalsIgnoreCase(safePathFilter) || "results".equalsIgnoreCase(safePathFilter));
            if (simulationRound != null && !snapshotRows.isEmpty()) {
                outputHtml = SimulationResultsRenderer.renderSnapshot(snapshotRows, safeTournament, simulationRound,
                        showActualSnapshotColors ? actualAdvancingTeams : Set.of(), snapshotLiveRows) + outputHtml;
            }
            model.addAttribute("output", outputHtml);
            model.addAttribute("mode", web.displayViewMode(safeRound));
            return "result";
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
                && Files.exists(web.matchupFile(safeTournament, "last_16.csv"))) {
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

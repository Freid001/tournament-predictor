package com.tournamentpredictor.controller;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.model.common.CsvData;
import com.tournamentpredictor.model.group.GroupPickView;
import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.orchestration.MatchResolver;
import com.tournamentpredictor.services.report.HtmlReporter;
import com.tournamentpredictor.services.web.GroupPickViewService;
import com.tournamentpredictor.services.web.PageModelService;
import com.tournamentpredictor.services.web.StartRowViewService;
import com.tournamentpredictor.services.web.WebControllerService;
import com.tournamentpredictor.services.web.WebText;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class EditController {

    protected final PredictionConfig predictionConfig;
    protected final WebControllerService web;
    private final GroupPickViewService groupPickViewService = new GroupPickViewService();
    private final StartRowViewService startRowViewService = new StartRowViewService();
    private final PageModelService pageModels;

    public EditController(PredictionConfig predictionConfig, WebControllerService web) {
        this.predictionConfig = predictionConfig;
        this.web = web;
        this.pageModels = new PageModelService(web);
    }

    @Autowired
    public EditController(PredictionConfig predictionConfig) {
        this(predictionConfig, new WebControllerService(predictionConfig));
    }

    @GetMapping("/edit/start")
    public String editStart(@RequestParam("tournament") String tournament, Model model) throws IOException {
        String safeTournament = web.safeTournament(tournament);
        CsvData csvData = web.readCsv(web.predictionFile(safeTournament, "start.csv"));
        var rows = startRowViewService.build(csvData.rows(), true);
        pageModels.addTournamentPage(model, safeTournament, "Edit Team Setup");
        model.addAttribute("prevNavUrl", null);
        model.addAttribute("prevNavEnabled", false);
        model.addAttribute("nextNavUrl", "/view/start?tournament=" + safeTournament);
        model.addAttribute("nextNavLabel", "View Setup →");
        model.addAttribute("nextNavEnabled", web.generatedDataExists(web.predictionFile(safeTournament, "start.csv")));
        model.addAttribute("hasPrevRound", false);
        model.addAttribute("prevViewUrl", "#");
        model.addAttribute("prevViewEnabled", false);
        model.addAttribute("hasNextRound", true);
        model.addAttribute("nextViewUrl", "/view/start?tournament=" + safeTournament);
        model.addAttribute("nextViewEnabled", web.generatedDataExists(web.predictionFile(safeTournament, "start.csv")));
        model.addAttribute("canNextRun", false);
        model.addAttribute("editUrl", null);
        model.addAttribute("rows", rows);
        model.addAttribute("allTeams", HtmlReporter.getAllTeamNames());
        model.addAttribute("isoCodesJson", HtmlReporter.getIsoCodesJson());
        return "edit-start";
    }

    @PostMapping("/edit/start")
    public String saveStart(@RequestParam("tournament") String tournament, HttpServletRequest request) throws IOException {
        String safeTournament = web.safeTournament(tournament);
        CsvData existing = web.readCsv(web.predictionFile(safeTournament, "start.csv"));
        int rowCount = WebText.parseInt(request.getParameter("rowCount"), 0);
        List<String> headers = new ArrayList<>(List.of(
                "group", "team", "host",
                "squad_age_profile", "age_notes",
                "squad_cohesion", "cohesion_notes",
                "squad_depth", "depth_notes",
                "attack_quality", "defence_quality", "quality_notes",
                "squad_dropouts", "dropout_notes",
                "injury_impact", "injury_notes",
                "heat_impact", "confederation"));
        for (String header : existing.headers()) {
            if (!headers.contains(header) && !"squad_quality".equals(header)) {
                headers.add(header);
            }
        }

        List<Map<String, String>> rows = new ArrayList<>();
        java.util.Set<String> seenTeams = new java.util.LinkedHashSet<>();
        java.util.Set<String> duplicateTeams = new java.util.LinkedHashSet<>();
        for (int i = 0; i < rowCount; i++) {
            String group = WebText.trim(request.getParameter("group" + i));
            String team = WebText.trim(request.getParameter("team" + i));
            if (group.isEmpty() && team.isEmpty()) continue;
            if (!team.isEmpty() && !seenTeams.add(team)) duplicateTeams.add(team);
            Map<String, String> row = web.baseRow(existing.rows(), i, headers);
            row.put("group", group);
            row.put("team", team);
            row.put("host", request.getParameter("host" + i) != null ? "yes" : "no");
            row.put("injury_impact", String.valueOf(WebText.parseInt(request.getParameter("injuryImpact" + i), 0)));
            row.put("heat_impact", String.valueOf(WebText.parseInt(request.getParameter("heatImpact" + i), 0)));
            row.put("squad_dropouts", String.valueOf(WebText.parseInt(request.getParameter("squadDropouts" + i), 0)));
            row.put("squad_age_profile", String.valueOf(WebText.parseInt(request.getParameter("squadAgeProfile" + i), 0)));
            row.put("age_notes", WebText.sanitiseNote(request.getParameter("ageNotes" + i)));
            row.put("squad_cohesion", String.valueOf(WebText.parseInt(request.getParameter("squadCohesion" + i), 0)));
            row.put("cohesion_notes", WebText.sanitiseNote(request.getParameter("cohesionNotes" + i)));
            row.put("squad_depth", String.valueOf(WebText.parseInt(request.getParameter("squadDepth" + i), 0)));
            row.put("depth_notes", WebText.sanitiseNote(request.getParameter("depthNotes" + i)));
            row.remove("squad_quality");
            row.put("attack_quality", String.valueOf(WebText.parseInt(request.getParameter("attackQuality" + i), 0)));
            row.put("defence_quality", String.valueOf(WebText.parseInt(request.getParameter("defenceQuality" + i), 0)));
            row.put("quality_notes", WebText.sanitiseNote(request.getParameter("qualityNotes" + i)));
            row.put("squad_dropouts", String.valueOf(WebText.parseInt(request.getParameter("squadDropouts" + i), 0)));
            row.put("dropout_notes", WebText.sanitiseNote(request.getParameter("dropoutNotes" + i)));
            row.put("injury_impact", String.valueOf(WebText.parseInt(request.getParameter("injuryImpact" + i), 0)));
            row.put("injury_notes", WebText.sanitiseNote(request.getParameter("injuryNotes" + i)));
            row.put("heat_impact", String.valueOf(WebText.parseInt(request.getParameter("heatImpact" + i), 0)));
            String confederation = normaliseConfederation(request.getParameter("confederation" + i));
            row.put("confederation", confederation);
            row.put("confederation_adjustment", String.valueOf(confederationAdjustment(confederation)));
            rows.add(row);
        }

        if (!duplicateTeams.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Duplicate teams: " + String.join(", ", duplicateTeams));
        }

        web.writeCsv(web.predictionFile(safeTournament, "start.csv"), headers, rows);
        web.cascadeDeleteAfterStart(safeTournament);
        return web.redirectToTournament(safeTournament);
    }

    private static String normaliseConfederation(String value) {
        String confederation = WebText.trim(value).toUpperCase(java.util.Locale.ROOT);
        return switch (confederation) {
            case "UEFA", "CONMEBOL", "CONCACAF", "CAF", "AFC", "OFC" -> confederation;
            default -> "";
        };
    }

    private static int confederationAdjustment(String confederation) {
        return switch (normaliseConfederation(confederation)) {
            case "UEFA" -> 0;
            case "CONMEBOL" -> 0;
            case "CONCACAF" -> -25;
            case "CAF" -> -10;
            case "AFC" -> -25;
            case "OFC" -> -50;
            default -> 0;
        };
    }

    @GetMapping("/edit/{round}")
    public String editRound(@PathVariable("round") String round, @RequestParam("tournament") String tournament, Model model) throws IOException {
        String safeRound = web.safeRound(round);
        String safeTournament = web.safeTournament(tournament);
        if (!"groups".equals(safeRound)) {
            return web.redirectToTournament(safeTournament);
        }
        Path file = web.predictionFile(safeTournament, safeRound + ".csv");
        CsvData csvData = web.readCsv(file);
        if (csvData.rows().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, file.getFileName() + " not found");
        }

        pageModels.addTournamentPage(model, safeTournament, "Edit " + web.displayMode(safeRound));
        model.addAttribute("round", safeRound);
        model.addAttribute("roundLabel", web.displayMode(safeRound));
        model.addAttribute("groupsMode", "groups".equals(safeRound));

        GroupPickView groupPickView = groupPickViewService.build(csvData.rows(), loadEloBreakdowns(safeTournament));
        model.addAttribute("rows", groupPickView.rows());
        model.addAttribute("groupedRows", groupPickView.groupedRows());
        model.addAttribute("groupSimulationByTeam", web.groupSimulationByTeam(safeTournament));
        model.addAttribute("groupSimulationReady", web.generatedDataExists(web.simulationFile(safeTournament, "simulation_group_routes.csv")));

        addEditRoundNavigation(model, safeTournament, safeRound);

        return "edit-round";
    }

    @PostMapping("/edit/{round}")
    public String saveRound(@PathVariable("round") String round, @RequestParam("tournament") String tournament,
                            HttpServletRequest request, Model model) throws IOException {
        String safeRound = web.safeRound(round);
        String safeTournament = web.safeTournament(tournament);
        if (!"groups".equals(safeRound)) {
            return web.redirectToTournament(safeTournament);
        }
        Path file = web.predictionFile(safeTournament, safeRound + ".csv");
        CsvData existing = web.readCsv(file);
        int rowCount = WebText.parseInt(request.getParameter("rowCount"), 0);

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            Map<String, String> row = web.baseRow(existing.rows(), i, existing.headers());
            row.put("group_winner", WebText.trim(request.getParameter("groupWinner" + i)));
            row.put("runner_up", WebText.trim(request.getParameter("runnerUp" + i)));
            row.put("3rd_place", WebText.trim(request.getParameter("thirdPlace" + i)));
            rows.add(row);
        }

        List<String> errors = web.validateGroupPicks(rows);
        if (!errors.isEmpty()) {
            model.addAttribute("validationErrors", errors);
            pageModels.addTournamentPage(model, safeTournament, "Edit " + web.displayMode(safeRound));
            model.addAttribute("round", safeRound);
            model.addAttribute("roundLabel", web.displayMode(safeRound));
            model.addAttribute("groupsMode", true);
            GroupPickView groupPickView = groupPickViewService.build(rows, loadEloBreakdowns(safeTournament));
            model.addAttribute("rows", groupPickView.rows());
            model.addAttribute("groupedRows", groupPickView.groupedRows());
            model.addAttribute("groupSimulationByTeam", web.groupSimulationByTeam(safeTournament));
            model.addAttribute("groupSimulationReady", web.generatedDataExists(web.simulationFile(safeTournament, "simulation_group_routes.csv")));
            addEditRoundNavigation(model, safeTournament, safeRound);
            return "edit-round";
        }

        web.writeCsv(file, existing.headers(), rows);
        web.cascadeDeleteAfterGroupsEdit(safeTournament);
        try {
            HtmlReporter saveReporter = new HtmlReporter().withConfig(predictionConfig);
            MatchResolver.forWeb(saveReporter, predictionConfig).resolveAndWrite(safeRound, safeTournament);
        } catch (Exception e) {
            return web.redirectToTournament(safeTournament);
        }
        return web.redirectAfterSaveRun(safeRound, safeTournament);
    }
    private void addEditRoundNavigation(Model model, String tournament, String round) throws IOException {
        String prevRound = web.editPrevViewRound(round);
        if (prevRound != null) {
            boolean prevExists = web.generatedDataExists(web.roundFileForView(tournament, prevRound));
            model.addAttribute("hasPrevView", prevExists);
            model.addAttribute("prevNavUrl", prevExists ? "/view/" + prevRound + "?tournament=" + tournament : null);
            model.addAttribute("prevNavLabel", "← " + web.displayMode(prevRound));
            model.addAttribute("prevNavEnabled", prevExists);
            model.addAttribute("hasPrevRound", true);
            model.addAttribute("prevViewUrl", "/view/" + prevRound + "?tournament=" + tournament);
            model.addAttribute("prevViewEnabled", prevExists);
        } else {
            model.addAttribute("hasPrevView", false);
            model.addAttribute("prevNavUrl", null);
            model.addAttribute("prevNavEnabled", false);
            model.addAttribute("hasPrevRound", false);
            model.addAttribute("prevViewUrl", "#");
            model.addAttribute("prevViewEnabled", false);
        }

        String currentViewRound = web.viewRoundForEdit(round);
        if (currentViewRound != null) {
            boolean currentExists = web.generatedDataExists(web.roundFileForView(tournament, currentViewRound));
            model.addAttribute("hasCurrentView", currentExists);
            model.addAttribute("nextNavUrl", currentExists ? "/view/" + currentViewRound + "?tournament=" + tournament : null);
            model.addAttribute("nextNavLabel", web.displayViewMode(currentViewRound) + " →");
            model.addAttribute("nextNavEnabled", currentExists);
            model.addAttribute("hasNextRound", true);
            model.addAttribute("nextViewUrl", "/view/" + currentViewRound + "?tournament=" + tournament);
            model.addAttribute("nextViewEnabled", currentExists);
        } else {
            model.addAttribute("hasCurrentView", false);
            model.addAttribute("nextNavUrl", null);
            model.addAttribute("nextNavEnabled", false);
            model.addAttribute("hasNextRound", false);
            model.addAttribute("nextViewUrl", "#");
            model.addAttribute("nextViewEnabled", false);
        }
        model.addAttribute("canNextRun", false);
        model.addAttribute("editUrl", null);
        model.addAttribute("pageTitle", "Edit " + web.displayMode(round));
    }

    private Map<String, EloBreakdown> loadEloBreakdowns(String tournament) {
        try {
            return new CsvLoader(web.projectRoot).withConfig(predictionConfig).loadEloBreakdowns(tournament);
        } catch (Exception e) {
            return Map.of();
        }
    }
}

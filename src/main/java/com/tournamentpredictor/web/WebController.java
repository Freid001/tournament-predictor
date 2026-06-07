package com.tournamentpredictor.web;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.service.MatchResolver;
import com.tournamentpredictor.service.simulation.SimulationHandler;
import com.tournamentpredictor.service.util.EloBreakdown;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.HtmlReporter;
import com.tournamentpredictor.web.view.SimulationResultsRenderer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.tournamentpredictor.loader.CsvLoader;
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
import java.util.stream.Stream;

@Controller
public class WebController {
    private static final Set<String> RUN_MODES = Set.of("snapshot-refresh", "start", "groups", "group-simulation", "tournament", "last_32", "last_16", "last_8", "last_4", "final", "simulate");
    private static final Set<String> ROUND_NAMES = Set.of("groups", "groups_match", "last_32", "last_32_match", "last_16", "last_16_match", "last_8", "last_8_match", "last_4", "last_4_match", "final", "final_match");
    private static final Set<String> RESET_STEPS = Set.of("groups", "group-simulation", "last_32", "last_16", "last_8", "last_4", "final", "simulation");
    private static final CSVFormat CSV = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    private final Path projectRoot = Path.of(System.getProperty("user.dir"));
    private final PredictionConfig predictionConfig;

    public WebController(PredictionConfig predictionConfig) {
        this.predictionConfig = predictionConfig;
    }

    @GetMapping("/")
    public String index(Model model) throws IOException {
        model.addAttribute("tournaments", scanTournaments());
        return "index";
    }

    @GetMapping("/tournament/{name}")
    public String tournament(@PathVariable("name") String name, Model model) {
        String tournament = safeTournament(name);
        model.addAttribute("tournament", new TournamentSummary(tournament, describeCurrentStage(tournament), completedStepCount(tournament)));
        model.addAttribute("stages", buildStages(tournament));
        return "tournament";
    }

    @PostMapping("/run/{mode}")
    public String run(@PathVariable("mode") String mode, @RequestParam("tournament") String tournament, Model model, RedirectAttributes redirect) {
        String safeMode = safeMode(mode);
        String safeTournament = safeTournament(tournament);
        HtmlReporter reporter = new HtmlReporter().withConfig(predictionConfig);
        boolean hasError = false;

        try {
            if ("group-simulation".equals(safeMode)) {
                runGroupStagePipeline(safeTournament, reporter);
                redirect.addFlashAttribute("runOutput", reporter.getHtml());
                return redirectAfterSaveRun(safeMode, safeTournament);
            }
            if ("tournament".equals(safeMode)) {
                runTournamentPipeline(safeTournament, reporter);
                redirect.addFlashAttribute("runOutput", reporter.getHtml());
                return redirectAfterSaveRun(safeMode, safeTournament);
            }

            Path lockedPath = outputPathForMode(safeTournament, safeMode);
            boolean lockedBefore = lockedPath != null && Files.exists(lockedPath);
            if (lockedBefore && !"start".equals(safeMode) && !"groups".equals(safeMode)) {
                reporter.appendWarning("Output already exists: " + lockedPath + " — delete or reset to re-run.");
            }

            MatchResolver.forWeb(reporter, predictionConfig).resolveAndWrite(safeMode, safeTournament);
            if ("snapshot-refresh".equals(safeMode)) {
                cascadeDeleteAfterStart(safeTournament);
                reporter.appendInfo("Snapshot updated. Group rankings and tournament results were reset; review Group Setup and run the workflow again.");
            }
            autoRunSimulation(safeMode, safeTournament, reporter);
            appendBrowserOnlyMessages(reporter, safeMode, safeTournament, lockedBefore);
            if (reporter.getHtml().isBlank()) {
                reporter.appendInfo("Completed " + displayMode(safeMode) + ".");
            }
        } catch (Exception e) {
            reporter.appendError(e.getMessage() == null ? "Unexpected error" : e.getMessage());
            hasError = true;
        }

        if (!hasError) {
            redirect.addFlashAttribute("runOutput", reporter.getHtml());
            return redirectAfterSaveRun(safeMode, safeTournament);
        }

        model.addAttribute("mode", displayMode(safeMode));
        model.addAttribute("tournament", safeTournament);
        model.addAttribute("tournamentLabel", displayTournament(safeTournament));
        model.addAttribute("output", reporter.getHtml());
        model.addAttribute("hasNextRound", false);
        model.addAttribute("hasPrevRound", false);
        model.addAttribute("canNextRun", false);
        return "result";
    }

    @GetMapping("/edit/start")
    public String editStart(@RequestParam("tournament") String tournament, Model model) throws IOException {
        String safeTournament = safeTournament(tournament);
        CsvData csvData = readCsv(predictionFile(safeTournament, "start.csv"));
        List<StartRow> rows = new ArrayList<>();
        if (csvData.rows().isEmpty()) {
            for (char group = 'A'; group <= 'L'; group++) {
                for (int i = 0; i < 4; i++) {
                    rows.add(new StartRow(String.valueOf(group), "", false, 0, 0, 0, 0, 0, 0, 0, 0, "", "", "", "", "", ""));
                }
            }
        } else {
            for (Map<String, String> row : csvData.rows()) {
                rows.add(new StartRow(
                        row.getOrDefault("group", ""),
                        row.getOrDefault("team", ""),
                        "yes".equalsIgnoreCase(row.getOrDefault("host", "")),
                        parseInt(row.getOrDefault("injury_impact", "0"), 0),
                        parseInt(row.getOrDefault("heat_impact", "0"), 0),
                        parseInt(row.getOrDefault("squad_dropouts", "0"), 0),
                        parseInt(row.getOrDefault("squad_age_profile", "0"), 0),
                        parseInt(row.getOrDefault("squad_cohesion", "0"), 0),
                        parseInt(row.getOrDefault("squad_depth", "0"), 0),
                        parseInt(row.getOrDefault("attack_quality", row.getOrDefault("squad_quality", "0")), 0),
                        parseInt(row.getOrDefault("defence_quality", row.getOrDefault("squad_quality", "0")), 0),
                        row.getOrDefault("dropout_notes", ""),
                        row.getOrDefault("injury_notes", ""),
                        row.getOrDefault("age_notes", ""),
                        row.getOrDefault("cohesion_notes", ""),
                        row.getOrDefault("depth_notes", ""),
                        row.getOrDefault("quality_notes", "")
                ));
            }
        }
        model.addAttribute("tournament", safeTournament);
        model.addAttribute("tournamentLabel", displayTournament(safeTournament));
        model.addAttribute("pageTitle", "Edit Group Setup");
        model.addAttribute("prevNavUrl", null);
        model.addAttribute("prevNavEnabled", false);
        model.addAttribute("nextNavUrl", "/view/start?tournament=" + safeTournament);
        model.addAttribute("nextNavLabel", "View Setup →");
        model.addAttribute("nextNavEnabled", Files.exists(predictionFile(safeTournament, "start.csv")));
        model.addAttribute("hasPrevRound", false);
        model.addAttribute("prevViewUrl", "#");
        model.addAttribute("prevViewEnabled", false);
        model.addAttribute("hasNextRound", true);
        model.addAttribute("nextViewUrl", "/view/start?tournament=" + safeTournament);
        model.addAttribute("nextViewEnabled", Files.exists(predictionFile(safeTournament, "start.csv")));
        model.addAttribute("canNextRun", false);
        model.addAttribute("editUrl", null);
        model.addAttribute("rows", rows);
        model.addAttribute("allTeams", HtmlReporter.getAllTeamNames());
        model.addAttribute("isoCodesJson", HtmlReporter.getIsoCodesJson());
        return "edit-start";
    }

    @PostMapping("/edit/start")
    public String saveStart(@RequestParam("tournament") String tournament, HttpServletRequest request) throws IOException {
        String safeTournament = safeTournament(tournament);
        CsvData existing = readCsv(predictionFile(safeTournament, "start.csv"));
        int rowCount = parseInt(request.getParameter("rowCount"), 0);
        List<String> headers = new ArrayList<>(List.of(
                "group", "team", "host",
                "squad_age_profile", "age_notes",
                "squad_cohesion", "cohesion_notes",
                "squad_depth", "depth_notes",
                "attack_quality", "defence_quality", "quality_notes",
                "squad_dropouts", "dropout_notes",
                "injury_impact", "injury_notes",
                "heat_impact"));
        for (String header : existing.headers()) {
            if (!headers.contains(header) && !"squad_quality".equals(header)) {
                headers.add(header);
            }
        }

        List<Map<String, String>> rows = new ArrayList<>();
        java.util.Set<String> seenTeams = new java.util.LinkedHashSet<>();
        java.util.Set<String> duplicateTeams = new java.util.LinkedHashSet<>();
        for (int i = 0; i < rowCount; i++) {
            String group = trim(request.getParameter("group" + i));
            String team = trim(request.getParameter("team" + i));
            if (group.isEmpty() && team.isEmpty()) continue;
            if (!team.isEmpty() && !seenTeams.add(team)) duplicateTeams.add(team);
            Map<String, String> row = baseRow(existing.rows(), i, headers);
            row.put("group", group);
            row.put("team", team);
            row.put("host", request.getParameter("host" + i) != null ? "yes" : "no");
            row.put("injury_impact", String.valueOf(parseInt(request.getParameter("injuryImpact" + i), 0)));
            row.put("heat_impact", String.valueOf(parseInt(request.getParameter("heatImpact" + i), 0)));
            row.put("squad_dropouts", String.valueOf(parseInt(request.getParameter("squadDropouts" + i), 0)));
            row.put("squad_age_profile", String.valueOf(parseInt(request.getParameter("squadAgeProfile" + i), 0)));
            row.put("age_notes", sanitiseNote(request.getParameter("ageNotes" + i)));
            row.put("squad_cohesion", String.valueOf(parseInt(request.getParameter("squadCohesion" + i), 0)));
            row.put("cohesion_notes", sanitiseNote(request.getParameter("cohesionNotes" + i)));
            row.put("squad_depth", String.valueOf(parseInt(request.getParameter("squadDepth" + i), 0)));
            row.put("depth_notes", sanitiseNote(request.getParameter("depthNotes" + i)));
            row.remove("squad_quality");
            row.put("attack_quality", String.valueOf(parseInt(request.getParameter("attackQuality" + i), 0)));
            row.put("defence_quality", String.valueOf(parseInt(request.getParameter("defenceQuality" + i), 0)));
            row.put("quality_notes", sanitiseNote(request.getParameter("qualityNotes" + i)));
            row.put("squad_dropouts", String.valueOf(parseInt(request.getParameter("squadDropouts" + i), 0)));
            row.put("dropout_notes", sanitiseNote(request.getParameter("dropoutNotes" + i)));
            row.put("injury_impact", String.valueOf(parseInt(request.getParameter("injuryImpact" + i), 0)));
            row.put("injury_notes", sanitiseNote(request.getParameter("injuryNotes" + i)));
            row.put("heat_impact", String.valueOf(parseInt(request.getParameter("heatImpact" + i), 0)));
            rows.add(row);
        }

        if (!duplicateTeams.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Duplicate teams: " + String.join(", ", duplicateTeams));
        }

        writeCsv(predictionFile(safeTournament, "start.csv"), headers, rows);
        cascadeDeleteAfterStart(safeTournament);
        return redirectToTournament(safeTournament);
    }

    @GetMapping("/edit/{round}")
    public String editRound(@PathVariable("round") String round, @RequestParam("tournament") String tournament, Model model) throws IOException {
        String safeRound = safeRound(round);
        String safeTournament = safeTournament(tournament);
        if (!"groups".equals(safeRound)) {
            return redirectToTournament(safeTournament);
        }
        Path file = predictionFile(safeTournament, safeRound + ".csv");
        CsvData csvData = readCsv(file);
        if (csvData.rows().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, file.getFileName() + " not found");
        }

        model.addAttribute("tournament", safeTournament);
        model.addAttribute("tournamentLabel", displayTournament(safeTournament));
        model.addAttribute("round", safeRound);
        model.addAttribute("roundLabel", displayMode(safeRound));
        model.addAttribute("groupsMode", "groups".equals(safeRound));

        if ("groups".equals(safeRound)) {
            List<Map<String, String>> rawRows = csvData.rows();
            Map<String, EloBreakdown> eloBreakdowns;
            try { eloBreakdowns = new CsvLoader(projectRoot).withConfig(predictionConfig).loadEloBreakdowns(safeTournament); }
            catch (Exception e) { eloBreakdowns = Map.of(); }
            List<GroupPickRow> rows = new ArrayList<>();
            for (int i = 0; i < rawRows.size(); i++) {
                Map<String, String> row = rawRows.get(i);
                String team = row.getOrDefault("team", "");
                rows.add(new GroupPickRow(i,
                        row.getOrDefault("group", ""),
                        team,
                        formatQualBonus(row.getOrDefault("qual_bonus", "")),
                        row.getOrDefault("predicted_position", ""),
                        row.getOrDefault("group_winner", ""),
                        row.getOrDefault("runner_up", ""),
                        row.getOrDefault("3rd_place", ""),
                        parseInt(row.getOrDefault("squad_age_profile", "0"), 0),
                        parseInt(row.getOrDefault("squad_cohesion", "0"), 0),
                        eloBreakdowns.get(team)
                ));
            }
            java.util.LinkedHashMap<String, List<GroupPickRow>> groupedRows = new java.util.LinkedHashMap<>();
            for (GroupPickRow row : rows) {
                groupedRows.computeIfAbsent(row.getGroup(), k -> new ArrayList<>()).add(row);
            }
            model.addAttribute("rows", rows);
            model.addAttribute("groupedRows", groupedRows);
            model.addAttribute("groupSimulationByTeam", groupSimulationByTeam(safeTournament));
            model.addAttribute("groupSimulationReady", Files.exists(simulationFile(safeTournament, "simulation_group_routes.csv")));
        } else {
            // Build lookup from matchup file for the combined prediction (all signals)
            Path matchupPath = matchupFile(safeTournament, safeRound + ".csv");
            Map<String, String> matchupPrediction = new LinkedHashMap<>();
            Map<String, String> matchupModelPrediction = new LinkedHashMap<>();
            for (Map<String, String> r : readCsv(matchupPath).rows()) {
                if ("predicted".equalsIgnoreCase(r.getOrDefault("path", "predicted"))) {
                    String key = r.getOrDefault("match_id", "") + "|" + r.getOrDefault("team1", "") + "|" + r.getOrDefault("team2", "");
                    String pred = trim(r.getOrDefault("prediction", ""));
                    if (!pred.isEmpty()) matchupPrediction.put(key, pred);
                    String modelPrediction = trim(r.getOrDefault("model_prediction", ""));
                    if (!modelPrediction.isEmpty()) matchupModelPrediction.put(key, modelPrediction);
                }
            }
            List<RoundRow> rows = csvData.rows().stream()
                    .map(row -> {
                        String key = row.getOrDefault("match_id", "") + "|" + row.getOrDefault("team1", "") + "|" + row.getOrDefault("team2", "");
                        String selectedWinner = matchupPrediction.getOrDefault(key, preferredWinner(row));
                        boolean disagree = "yes".equalsIgnoreCase(trim(row.getOrDefault("do_you_disagree", "")));
                        String modelWinner = matchupModelPrediction.get(key);
                        if (modelWinner == null || modelWinner.isBlank()) {
                            modelWinner = disagree
                                    ? new EloCalculator().applyDisagreeOverride("yes", selectedWinner,
                                            row.getOrDefault("team1", ""), row.getOrDefault("team2", ""))
                                    : selectedWinner;
                        }
                        return new RoundRow(
                                row.getOrDefault("match_id", ""),
                                row.getOrDefault("team1", ""),
                                row.getOrDefault("team2", ""),
                                modelWinner,
                                disagree
                        );
                    })
                    .collect(Collectors.toList());
            model.addAttribute("rows", rows);
        }

        String editPrevRound = editPrevViewRound(safeRound);
        if (editPrevRound != null) {
            boolean prevExists = Files.exists(roundFileForView(safeTournament, editPrevRound));
            model.addAttribute("hasPrevView", prevExists);
            model.addAttribute("prevNavUrl", prevExists ? "/view/" + editPrevRound + "?tournament=" + safeTournament : null);
            model.addAttribute("prevNavLabel", "← " + displayMode(editPrevRound));
            model.addAttribute("prevNavEnabled", prevExists);
            model.addAttribute("hasPrevRound", true);
            model.addAttribute("prevViewUrl", "/view/" + editPrevRound + "?tournament=" + safeTournament);
            model.addAttribute("prevViewEnabled", prevExists);
        } else {
            model.addAttribute("hasPrevView", false);
            model.addAttribute("prevNavUrl", null);
            model.addAttribute("prevNavEnabled", false);
            model.addAttribute("hasPrevRound", false);
            model.addAttribute("prevViewUrl", "#");
            model.addAttribute("prevViewEnabled", false);
        }

        String currentViewRound = viewRoundForEdit(safeRound);
        if (currentViewRound != null) {
            boolean cvExists = Files.exists(roundFileForView(safeTournament, currentViewRound));
            model.addAttribute("hasCurrentView", cvExists);
            model.addAttribute("nextNavUrl", cvExists ? "/view/" + currentViewRound + "?tournament=" + safeTournament : null);
            model.addAttribute("nextNavLabel", displayViewMode(currentViewRound) + " →");
            model.addAttribute("nextNavEnabled", cvExists);
            model.addAttribute("hasNextRound", true);
            model.addAttribute("nextViewUrl", "/view/" + currentViewRound + "?tournament=" + safeTournament);
            model.addAttribute("nextViewEnabled", cvExists);
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
        model.addAttribute("pageTitle", "Edit " + displayMode(safeRound));

        return "edit-round";
    }

    @PostMapping("/edit/{round}")
    public String saveRound(@PathVariable("round") String round, @RequestParam("tournament") String tournament,
                            HttpServletRequest request, RedirectAttributes redirectAttributes, Model model) throws IOException {
        String safeRound = safeRound(round);
        String safeTournament = safeTournament(tournament);
        if (!"groups".equals(safeRound)) {
            return redirectToTournament(safeTournament);
        }
        Path file = predictionFile(safeTournament, safeRound + ".csv");
        CsvData existing = readCsv(file);
        int rowCount = parseInt(request.getParameter("rowCount"), 0);

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            Map<String, String> row = baseRow(existing.rows(), i, existing.headers());
            if ("groups".equals(safeRound)) {
                row.put("group_winner", trim(request.getParameter("groupWinner" + i)));
                row.put("runner_up", trim(request.getParameter("runnerUp" + i)));
                row.put("3rd_place", trim(request.getParameter("thirdPlace" + i)));
            } else {
                row.put("do_you_disagree", request.getParameter("disagree" + i) != null ? "yes" : "");
            }
            rows.add(row);
        }

        if ("groups".equals(safeRound)) {
            List<String> errors = validateGroupPicks(rows);
            if (!errors.isEmpty()) {
                // Render directly instead of redirecting so submitted values are preserved
                model.addAttribute("validationErrors", errors);
                model.addAttribute("tournament", safeTournament);
                model.addAttribute("tournamentLabel", displayTournament(safeTournament));
                model.addAttribute("round", safeRound);
                model.addAttribute("roundLabel", displayMode(safeRound));
                model.addAttribute("groupsMode", true);
                // Build rows from submitted values merged with CSV data
                Map<String, EloBreakdown> eloBreakdownsSave;
                try { eloBreakdownsSave = new CsvLoader(projectRoot).withConfig(predictionConfig).loadEloBreakdowns(safeTournament); }
                catch (Exception e2) { eloBreakdownsSave = Map.of(); }
                List<GroupPickRow> pickRows = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) {
                    Map<String, String> r = rows.get(i);
                    String team = r.getOrDefault("team", "");
                    pickRows.add(new GroupPickRow(i,
                            r.getOrDefault("group", ""),
                            team,
                            formatQualBonus(r.getOrDefault("qual_bonus", "")),
                            r.getOrDefault("predicted_position", ""),
                            r.getOrDefault("group_winner", ""),
                            r.getOrDefault("runner_up", ""),
                            r.getOrDefault("3rd_place", ""),
                            parseInt(r.getOrDefault("squad_age_profile", "0"), 0),
                            parseInt(r.getOrDefault("squad_cohesion", "0"), 0),
                            eloBreakdownsSave.get(team)
                    ));
                }
                java.util.LinkedHashMap<String, List<GroupPickRow>> groupedRows = new java.util.LinkedHashMap<>();
                for (GroupPickRow row : pickRows) groupedRows.computeIfAbsent(row.getGroup(), k -> new ArrayList<>()).add(row);
                model.addAttribute("rows", pickRows);
                model.addAttribute("groupedRows", groupedRows);
                model.addAttribute("groupSimulationByTeam", groupSimulationByTeam(safeTournament));
                model.addAttribute("groupSimulationReady", Files.exists(simulationFile(safeTournament, "simulation_group_routes.csv")));
                String editPrevRound = editPrevViewRound(safeRound);
                boolean prevExists = editPrevRound != null && Files.exists(roundFileForView(safeTournament, editPrevRound));
                model.addAttribute("prevNavUrl", prevExists ? "/view/" + editPrevRound + "?tournament=" + safeTournament : null);
                model.addAttribute("prevNavLabel", editPrevRound != null ? "← " + displayMode(editPrevRound) : null);
                String currentViewRound = viewRoundForEdit(safeRound);
                boolean cvExists = currentViewRound != null && Files.exists(roundFileForView(safeTournament, currentViewRound));
                model.addAttribute("nextNavUrl", cvExists ? "/view/" + currentViewRound + "?tournament=" + safeTournament : null);
                model.addAttribute("nextNavLabel", currentViewRound != null ? displayViewMode(currentViewRound) + " →" : null);
                model.addAttribute("canNextRun", false);
                model.addAttribute("editUrl", null);
                model.addAttribute("pageTitle", "Edit " + displayMode(safeRound));
                return "edit-round";
            }
        }

        writeCsv(file, existing.headers(), rows);
        if ("groups".equals(safeRound)) {
            cascadeDeleteAfterGroupsEdit(safeTournament);
        } else {
            cascadeDeleteAfterRoundEdit(safeTournament, safeRound);
        }
        try {
            HtmlReporter saveReporter = new HtmlReporter().withConfig(predictionConfig);
            MatchResolver.forWeb(saveReporter, predictionConfig).resolveAndWrite(safeRound, safeTournament);
        } catch (Exception e) {
            return redirectToTournament(safeTournament);
        }
        return redirectAfterSaveRun(safeRound, safeTournament);
    }

    @GetMapping("/view/start")
    public String viewStart(@RequestParam("tournament") String tournament, Model model) throws IOException {
        String safeTournament = safeTournament(tournament);
        CsvData csvData = readCsv(predictionFile(safeTournament, "start.csv"));
        if (csvData.rows().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "start.csv not found");
        }
        List<StartRow> rows = new ArrayList<>();
        for (Map<String, String> row : csvData.rows()) {
            rows.add(new StartRow(
                    row.getOrDefault("group", ""),
                    row.getOrDefault("team", ""),
                    "yes".equalsIgnoreCase(row.getOrDefault("host", "")),
                    parseInt(row.getOrDefault("injury_impact", "0"), 0),
                    parseInt(row.getOrDefault("heat_impact", "0"), 0),
                    parseInt(row.getOrDefault("squad_dropouts", "0"), 0),
                    parseInt(row.getOrDefault("squad_age_profile", "0"), 0),
                    parseInt(row.getOrDefault("squad_cohesion", "0"), 0),
                    parseInt(row.getOrDefault("squad_depth", "0"), 0),
                    parseInt(row.getOrDefault("attack_quality", row.getOrDefault("squad_quality", "0")), 0),
                        parseInt(row.getOrDefault("defence_quality", row.getOrDefault("squad_quality", "0")), 0),
                    row.getOrDefault("dropout_notes", ""),
                    row.getOrDefault("injury_notes", ""),
                    row.getOrDefault("age_notes", ""),
                    row.getOrDefault("cohesion_notes", ""),
                    row.getOrDefault("depth_notes", ""),
                    row.getOrDefault("quality_notes", "")
            ));
        }
        model.addAttribute("tournament", safeTournament);
        model.addAttribute("tournamentLabel", displayTournament(safeTournament));
        model.addAttribute("pageTitle", "Group Setup");
        model.addAttribute("rows", rows);

        String nextRound = "groups";
        boolean nextExists = Files.exists(predictionFile(safeTournament, "groups.csv"));
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
        String safeTournament = safeTournament(tournament);
        String safeRound = safeRound(round);
        int currentIndex = VIEW_ROUND_SEQUENCE.indexOf(safeRound);
        EloCalculator elo = new EloCalculator();

        for (int i = currentIndex - 1; i >= 0; i--) {
            String candidateRound = VIEW_ROUND_SEQUENCE.get(i);
            if (!candidateRound.endsWith("_match")) continue;
            Path candidateFile = roundFileForView(safeTournament, candidateRound);
            if (!Files.exists(candidateFile)) continue;
            for (Map<String, String> row : readCsv(candidateFile).rows()) {
                String team1 = elo.extractTeamName(row.getOrDefault("team1", ""));
                String team2 = elo.extractTeamName(row.getOrDefault("team2", ""));
                boolean correctMatch = sourceMatch == null || sourceMatch.isBlank()
                        || sourceMatch.equalsIgnoreCase(row.getOrDefault("match_id", ""));
                boolean matches = correctMatch && ((team.equalsIgnoreCase(team1) && opponent.equalsIgnoreCase(team2))
                        || (team.equalsIgnoreCase(team2) && opponent.equalsIgnoreCase(team1)));
                if (matches) {
                    String matchId = row.getOrDefault("match_id", "");
                    return "redirect:/view/" + candidateRound
                            + "?tournament=" + webEncode(safeTournament)
                            + "&focusMatch=" + webEncode(matchId)
                            + "&focusTeam=" + webEncode(team)
                            + "&focusOpponent=" + webEncode(opponent);
                }
            }
        }
        return "redirect:/view/" + safeRound + "?tournament=" + webEncode(safeTournament);
    }

    @GetMapping("/view/{round}")
    public String viewRound(@PathVariable("round") String round,
                            @RequestParam("tournament") String tournament,
                            Model model) throws IOException {
        String safeRound = safeRound(round);
        String safeTournament = safeTournament(tournament);
        Path file = roundFileForView(safeTournament, safeRound);
        CsvData csvData = readCsv(file);
        if (csvData.rows().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, file.getFileName() + " not found");
        }

        boolean groupsMode = "groups".equals(safeRound);
        model.addAttribute("tournament", safeTournament);
        model.addAttribute("tournamentLabel", displayTournament(safeTournament));
        model.addAttribute("round", safeRound);
        model.addAttribute("roundLabel", displayViewMode(safeRound));
        model.addAttribute("groupsMode", groupsMode);
        if (groupsMode) {
            // Manual group picks are not part of prediction workflow; future actual results use a separate mode.
            model.addAttribute("editUrl", null);
        }

        String nextRound = nextViewRound(safeRound);
        boolean nextExists = nextRound != null && Files.exists(roundFileForView(safeTournament, nextRound));
        model.addAttribute("nextViewUrl", nextRound != null ? "/view/" + nextRound + "?tournament=" + safeTournament : "#");
        model.addAttribute("nextViewLabel", nextRound != null ? "Go to " + displayViewMode(nextRound) : "Go to Next");
        model.addAttribute("nextViewEnabled", nextExists);
        model.addAttribute("hasNextRound", nextRound != null);
        model.addAttribute("nextNavUrl", nextRound != null ? "/view/" + nextRound + "?tournament=" + safeTournament : null);
        model.addAttribute("nextNavLabel", nextRound != null ? displayViewMode(nextRound) + " →" : null);
        model.addAttribute("nextNavEnabled", nextExists);

        // Offer a direct model run when the next stage has not been generated
        String nextRunMode = nextRunModeForView(safeRound);
        String nextRunPrereq = nextRunPrereqForView(safeRound);
        boolean canNextRun = !nextExists && nextRunMode != null
                && nextRunPrereq != null && Files.exists(predictionFile(safeTournament, nextRunPrereq));
        model.addAttribute("nextRunMode", nextRunMode);
        model.addAttribute("canNextRun", canNextRun);
        model.addAttribute("nextRunLabel", nextRunMode != null ? "Run " + displayMode(nextRunMode) : "");
        if (groupsMode && !bracketHasStage(safeTournament, "LAST_32")
                && bracketHasStage(safeTournament, "LAST_16")) {
            boolean directResults = Files.exists(simulationFile(safeTournament, "simulation_last_16.csv"));
            String directUrl = "/view/last_16_match?tournament=" + safeTournament;
            model.addAttribute("nextViewUrl", directUrl);
            model.addAttribute("nextViewLabel", "Go to Last 16");
            model.addAttribute("nextViewEnabled", directResults);
            model.addAttribute("nextNavUrl", directResults ? directUrl : null);
            model.addAttribute("nextNavLabel", "Last 16 →");
            model.addAttribute("nextNavEnabled", directResults);
            model.addAttribute("nextRunMode", "tournament");
            model.addAttribute("canNextRun", !directResults && Files.exists(
                    simulationFile(safeTournament, "simulation_group_routes.csv")));
            model.addAttribute("nextRunLabel", "Run Knockout Rounds");
        }

        String prevRound = prevViewRound(safeRound);
        if ("last_16_match".equals(safeRound)
                && !bracketHasStage(safeTournament, "LAST_32")
                && bracketHasStage(safeTournament, "LAST_16")) {
            prevRound = "groups";
        }
        boolean prevExists = prevRound != null && Files.exists(roundFileForView(safeTournament, prevRound));
        model.addAttribute("prevViewUrl", prevRound != null ? "/view/" + prevRound + "?tournament=" + safeTournament : "#");
        model.addAttribute("prevViewLabel", prevRound != null ? "Back to " + displayViewMode(prevRound) : "Back to Previous");
        model.addAttribute("prevViewEnabled", prevExists);
        model.addAttribute("hasPrevRound", prevRound != null);
        model.addAttribute("prevNavUrl", prevExists ? "/view/" + prevRound + "?tournament=" + safeTournament : null);
        model.addAttribute("prevNavLabel", prevRound != null ? "← " + displayViewMode(prevRound) : null);
        model.addAttribute("prevNavEnabled", prevExists);
        model.addAttribute("pageTitle", "last_32_match".equals(safeRound) ? "Last 32" : "View " + displayViewMode(safeRound));

        if (groupsMode) {
            CsvLoader csvLoader = new CsvLoader(projectRoot).withConfig(predictionConfig);
            Map<String, EloBreakdown> eloBreakdowns;
            try { eloBreakdowns = csvLoader.loadEloBreakdowns(safeTournament); }
            catch (Exception e) { eloBreakdowns = Map.of(); }


            boolean hasGroupSimulation = Files.exists(simulationFile(safeTournament, "simulation_groups.csv"))
                    && Files.exists(simulationFile(safeTournament, "simulation_group_routes.csv"));
            List<Map<String, String>> groupSimulationRows = hasGroupSimulation
                    ? readCsv(simulationFile(safeTournament, "simulation_groups.csv")).rows()
                    : List.of();
            Map<String, String> groupByTeam = csvData.rows().stream().collect(Collectors.toMap(
                    row -> row.getOrDefault("team", ""), row -> row.getOrDefault("group", ""),
                    (first, ignored) -> first, LinkedHashMap::new));
            groupSimulationRows = groupSimulationRows.stream().map(row -> {
                Map<String, String> enriched = new LinkedHashMap<>(row);
                enriched.put("group", groupByTeam.getOrDefault(row.getOrDefault("team", ""), ""));
                return enriched;
            }).toList();
            model.addAttribute("groupSimulationHtml",
                    SimulationResultsRenderer.renderGroupSimulation(groupSimulationRows, safeTournament));
            List<Map<String, String>> groupScorelineRows = hasGroupSimulation
                    && Files.exists(simulationFile(safeTournament, "simulation_scorelines_groups.csv"))
                    ? readCsv(simulationFile(safeTournament, "simulation_scorelines_groups.csv")).rows()
                    : List.of();
            model.addAttribute("groupMatchesHtml",
                    renderGroupMatches(safeTournament, groupScorelineRows, eloBreakdowns));

            LinkedHashMap<String, List<GroupViewRow>> groupedRows = new LinkedHashMap<>();
            for (Map<String, String> row : csvData.rows()) {
                String group = trim(row.getOrDefault("group", ""));
                if (group.isEmpty()) continue;
                String team = row.getOrDefault("team", "");
                groupedRows.computeIfAbsent(group, ignored -> new ArrayList<>()).add(new GroupViewRow(
                        team,
                        formatQualBonus(row.getOrDefault("qual_bonus", "")),
                        row.getOrDefault("predicted_position", ""),
                        row.getOrDefault("group_winner", ""),
                        row.getOrDefault("runner_up", ""),
                        row.getOrDefault("3rd_place", ""),
                        eloBreakdowns.get(team)
                ));
            }
            model.addAttribute("groupedRows", groupedRows);
            return "view-round";
        } else {
            HtmlReporter reporter = new HtmlReporter().withConfig(predictionConfig)
                    .withPathNavigation(safeTournament, safeRound);
            List<String> lines = java.nio.file.Files.readAllLines(file);
            CsvLoader csvLoader = new CsvLoader(projectRoot).withConfig(predictionConfig);
            String oddsColumn = oddsColumnForRound(safeRound);
            Map<String, String> odds = oddsColumn != null ? csvLoader.loadOdds(safeTournament, oddsColumn) : Map.of();
            Map<String, com.tournamentpredictor.service.util.EloBreakdown> eloBreakdowns;
            try {
                eloBreakdowns = csvLoader.loadEloBreakdowns(safeTournament);
            } catch (Exception e) {
                eloBreakdowns = Map.of();
            }
            String simulationRound = simulationRoundForMatchView(safeRound);
            boolean directLast16 = simulationRound != null
                    && !bracketHasStage(safeTournament, "LAST_32")
                    && bracketHasStage(safeTournament, "LAST_16");
            if (!directLast16) {
                ensureSimulationExists(safeTournament, "last_32");
            }
            List<Map<String, String>> baselineSimulationRows = simulationRound == null
                    ? List.of()
                    : readCsv(simulationFile(safeTournament, directLast16
                            ? "simulation_last_16.csv"
                            : "simulation_last_32.csv")).rows();
            if (directLast16) {
                baselineSimulationRows.forEach(row -> row.put("simulation_origin", "group_stage"));
            }
            List<Map<String, String>> groupProbabilityRows = simulationRound == null
                    ? List.of()
                    : readCsv(predictionFile(safeTournament, "groups.csv")).rows();
            List<Map<String, String>> snapshotRows = directLast16
                    ? baselineSimulationRows
                    : "last_32_match".equals(safeRound)
                            ? routeAverageLast16Rows(csvData.rows(), groupProbabilityRows)
                            : baselineSimulationRows;
            ensureSimulationExists(safeTournament, simulationRound);
            List<Map<String, String>> currentSimulationRows = simulationRound == null
                    ? List.of()
                    : directLast16
                            ? baselineSimulationRows
                            : readCsv(simulationFile(safeTournament, "simulation_" + simulationRound + ".csv")).rows();
            Map<String, String> currentRoundAdvance = simulationAdvanceMap(
                    currentSimulationRows, advanceColumnForRound(simulationRound));
            for (Map<String, String> row : snapshotRows) {
                String marketOdds = odds.getOrDefault(row.getOrDefault("team", ""), "");
                if (!marketOdds.isBlank()) row.put("market_odds", marketOdds);
                String currentRoundPct = currentRoundAdvance.getOrDefault(row.getOrDefault("team", ""), "");
                if (!currentRoundPct.isBlank()) row.put("bet_probability", currentRoundPct);
            }
            if (simulationRound != null) {
                reporter.withSimulationAdvance(simulationAdvanceMap(snapshotRows, advanceColumnForRound(simulationRound)));
                Path tournamentScorelines = simulationFile(safeTournament, directLast16
                        ? "simulation_scorelines_last_16.csv"
                        : "simulation_scorelines_last_32.csv");
                if (Files.exists(tournamentScorelines)) {
                    List<Map<String, String>> scorelineRows = readCsv(tournamentScorelines).rows();
                    reporter.withMatchupLikelihood(directLast16
                            ? simulationMatchupLikelihoodMap(scorelineRows, simulationRound)
                            : routeWeightedMatchupLikelihoods(
                                    safeTournament, simulationRound, csvData.rows(), groupProbabilityRows));
                    reporter.withMatchupSimulationRuns(simulationMatchupRunsMap(scorelineRows, simulationRound));
                }
            }
            reporter.printMatchups(displayViewMode(safeRound), lines, new EloCalculator(), null, Map.of(), eloBreakdowns);
            String outputHtml = reporter.getHtml();
            if (simulationRound != null && !snapshotRows.isEmpty()) {
                outputHtml = SimulationResultsRenderer.renderSnapshot(snapshotRows, safeTournament, simulationRound) + outputHtml;
            }
            model.addAttribute("output", outputHtml);
            model.addAttribute("mode", displayViewMode(safeRound));
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
        String safeTournament = safeTournament(tournament);
        String safeSimulationRound = trim(simulationRound);
        if (!Set.of("last_32", "last_16", "last_8", "last_4", "final").contains(safeSimulationRound)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid simulation round");
        }
        if ("last_16".equals(safeSimulationRound)
                && !bracketHasStage(safeTournament, "LAST_32")
                && bracketHasStage(safeTournament, "LAST_16")
                && Files.exists(matchupFile(safeTournament, "last_16.csv"))) {
            return "redirect:/view/last_16_match?tournament=" + safeTournament;
        }
        List<Map<String, String>> rows = readCsv(simulationFile(safeTournament,
                "simulation_" + safeSimulationRound + ".csv")).rows();
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulation results not found");
        }
        List<Map<String, String>> paths = readCsv(simulationFile(safeTournament,
                "simulation_paths_" + safeSimulationRound + ".csv")).rows();
        List<Map<String, String>> scorelines = readCsv(simulationFile(safeTournament,
                "simulation_scorelines_" + safeSimulationRound + ".csv")).rows();
        model.addAttribute("tournament", safeTournament);
        model.addAttribute("tournamentLabel", displayTournament(safeTournament));
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

    @GetMapping("/reset/{step}")
    public String reset(@PathVariable("step") String step, @RequestParam("tournament") String tournament) throws IOException {
        String safeStep = safeResetStep(step);
        String safeTournament = safeTournament(tournament);
        cascadeDeleteForReset(safeTournament, safeStep);
        return redirectToTournament(safeTournament);
    }

    private List<TournamentSummary> scanTournaments() throws IOException {
        Path predictionsRoot = projectRoot.resolve("data").resolve("predictions");
        if (!Files.exists(predictionsRoot)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(predictionsRoot)) {
            return stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .map(name -> new TournamentSummary(name, describeCurrentStage(name), completedStepCount(name)))
                    .collect(Collectors.toList());
        }
    }

    private List<StageView> buildStages(String tournament) {
        boolean startExists = Files.exists(predictionFile(tournament, "start.csv"));
        boolean snapshotExists = Files.exists(projectRoot.resolve("data").resolve("elo").resolve("snapshots").resolve(tournament).resolve("teams.csv"));
        boolean groupsExists = Files.exists(predictionFile(tournament, "groups.csv"));
        boolean groupSimulationExists = Files.exists(simulationFile(tournament, "simulation_groups.csv"))
                && Files.exists(simulationFile(tournament, "simulation_group_routes.csv"));
        boolean last32MatchExists = Files.exists(matchupFile(tournament, "last_32.csv"));
        boolean last16MatchExists = Files.exists(matchupFile(tournament, "last_16.csv"));
        boolean last8MatchExists = Files.exists(matchupFile(tournament, "last_8.csv"));
        boolean last4MatchExists = Files.exists(matchupFile(tournament, "last_4.csv"));
        boolean finalMatchExists = Files.exists(matchupFile(tournament, "final.csv"));
        boolean startsAtLast16 = !bracketHasStage(tournament, "LAST_32")
                && bracketHasStage(tournament, "LAST_16");
        boolean knockoutSimulationExists = Files.exists(simulationFile(tournament,
                startsAtLast16 ? "simulation_last_16.csv" : "simulation_last_32.csv"));
        boolean knockoutComplete = startsAtLast16 ? knockoutSimulationExists : finalMatchExists;

        List<StageView> stages = new ArrayList<>();
        stages.add(new StageView("Pre-Tournament Snapshot", snapshotDescription(tournament, snapshotExists),
                snapshotExists ? new StageStatus("❄", "Frozen", "info") : status(false, startExists),
                startExists, "snapshot-refresh", "Refresh", "btn-outline-secondary",
                false, null, false, null, false, null, null, false, null));
        stages.add(new StageView("Group Setup", "Edit teams and tournament inputs, then run the group rankings.",
                status(groupsExists, startExists),
                startExists && !groupsExists, "start", "Run Group Rankings", "btn-primary",
                true, "/edit/start?tournament=" + tournament,
                startExists, "/view/start?tournament=" + tournament,
                false, null, null, groupsExists, "/reset/groups?tournament=" + tournament));
        stages.add(new StageView("Group Stage", "Simulate every group fixture and save every possible opening knockout route.",
                status(groupSimulationExists, groupsExists && !groupSimulationExists),
                groupsExists, "group-simulation", groupSimulationExists ? "Run Again" : "Run Group Stage",
                groupSimulationExists ? "btn-outline-primary" : "btn-primary",
                false, null,
                groupSimulationExists, "/view/groups?tournament=" + tournament,
                false, null, null, groupSimulationExists, "/reset/group-simulation?tournament=" + tournament));
        stages.add(new StageView("Knockout Rounds", "Continue the saved group-stage routes through every knockout round and the final.",
                status(knockoutComplete && groupSimulationExists, groupSimulationExists),
                groupSimulationExists, "tournament", knockoutComplete && groupSimulationExists ? "Run Again" : "Run Knockout Rounds",
                knockoutComplete && groupSimulationExists ? "btn-outline-primary" : "btn-primary",
                false, null, false, null, false, null, null,
                startsAtLast16 ? knockoutSimulationExists : (last32MatchExists || finalMatchExists),
                "/reset/" + (startsAtLast16 ? "simulation" : "last_32") + "?tournament=" + tournament));
        if (!startsAtLast16) {
            stages.add(resultStage("Last 32", "Inspect predicted and alternate Last 32 routes.",
                    last32MatchExists, "/view/last_32_match?tournament=" + tournament));
        }
        stages.add(resultStage("Last 16", "Inspect predicted and alternate Last 16 routes.",
                startsAtLast16 ? knockoutSimulationExists : last16MatchExists,
                startsAtLast16 ? "/view/last_16_match?tournament=" + tournament
                        : "/view/last_16_match?tournament=" + tournament));
        stages.add(resultStage("Quarter Finals", "Inspect predicted and alternate quarter-final routes.",
                last8MatchExists, "/view/last_8_match?tournament=" + tournament));
        stages.add(resultStage("Semi Finals", "Inspect predicted and alternate semi-final routes.",
                last4MatchExists, "/view/last_4_match?tournament=" + tournament));
        stages.add(resultStage("Final", "Inspect the final matchup and champion probabilities.",
                finalMatchExists, "/view/final_match?tournament=" + tournament));
        return stages;
    }

    private StageView resultStage(String label, String description, boolean complete, String viewUrl) {
        return new StageView(label, description, status(complete, false),
                false, null, false, null, complete, viewUrl, false, null);
    }

    private String snapshotDescription(String tournament, boolean snapshotExists) {
        String base = "Save the current team ratings and recent results used for this tournament, so future data updates do not change these predictions.";
        if (!snapshotExists) {
            return base;
        }
        Path metadataPath = projectRoot.resolve("data").resolve("elo").resolve("snapshots")
                .resolve(tournament).resolve("metadata.properties");
        if (!Files.exists(metadataPath)) {
            return base;
        }
        try (Reader reader = Files.newBufferedReader(metadataPath)) {
            Properties metadata = new Properties();
            metadata.load(reader);
            String createdAt = metadata.getProperty("created_at", "");
            String teamCount = metadata.getProperty("team_count", "");
            String startDate = metadata.getProperty("tournament_start_date", "");
            if (createdAt.isBlank()) {
                return base;
            }
            String saved = DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(createdAt));
            StringBuilder description = new StringBuilder("Prediction data saved ").append(saved);
            if (!teamCount.isBlank()) {
                description.append(" for ").append(teamCount).append(" teams");
            }
            description.append(".\n");
            if (!startDate.isBlank()) {
                String cutoff = LocalDate.parse(startDate).format(DateTimeFormatter.ofPattern("d MMM yyyy"));
                description.append("Results from ").append(cutoff).append(" onward are excluded.");
            }
            description.append(" Refresh before the tournament to include newly played warm-up matches.");
            return description.toString();
        } catch (Exception e) {
            return base;
        }
    }

    private boolean bracketHasStage(String tournament, String stage) {
        try {
            return new CsvLoader(projectRoot).loadBrackets(tournament).stream()
                    .anyMatch(entry -> stage.equalsIgnoreCase(entry.stage));
        } catch (IOException e) {
            return false;
        }
    }

    private void writeDirectOpeningMatchups(String tournament, String round) throws IOException {
        List<Map<String, String>> scorelines = readCsv(simulationFile(tournament,
                "simulation_scorelines_last_16.csv")).rows();
        Map<String, DirectMatchupSummary> summaries = new LinkedHashMap<>();
        for (Map<String, String> row : scorelines) {
            if (!round.equalsIgnoreCase(row.getOrDefault("stage", ""))) continue;
            String matchId = row.getOrDefault("match_id", "");
            String team1 = row.getOrDefault("team1", "");
            String team2 = row.getOrDefault("team2", "");
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank()) continue;
            String key = matchId + "|" + team1 + "|" + team2;
            DirectMatchupSummary summary = summaries.computeIfAbsent(key,
                    ignored -> new DirectMatchupSummary(matchId, team1, team2));
            summary.matchupRuns = parseInt(row.getOrDefault("matchup_runs", "0"), 0);
            summary.matchupPct = row.getOrDefault("matchup_pct", "");
            summary.winnerCounts.merge(row.getOrDefault("winner", ""),
                    parseInt(row.getOrDefault("count", "0"), 0), Integer::sum);
        }
        Map<String, Integer> primaryRuns = new LinkedHashMap<>();
        summaries.values().forEach(summary -> primaryRuns.merge(
                summary.matchId, summary.matchupRuns, Math::max));
        List<DirectMatchupSummary> ordered = summaries.values().stream()
                .sorted(java.util.Comparator.comparing((DirectMatchupSummary summary) -> summary.matchId)
                        .thenComparing(java.util.Comparator.comparingInt(
                                (DirectMatchupSummary summary) -> summary.matchupRuns).reversed())
                        .thenComparing(summary -> summary.team1)
                        .thenComparing(summary -> summary.team2))
                .toList();
        List<String> headers = List.of("match_id", "team1", "team2", "path", "prediction",
                "team1_path_fatigue", "team2_path_fatigue", "team1_path_opponent",
                "team2_path_opponent", "model_prediction", "selection_source", "matchup_pct", "matchup_runs");
        List<Map<String, String>> rows = new ArrayList<>();
        for (DirectMatchupSummary summary : ordered) {
            Map.Entry<String, Integer> winner = summary.winnerCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).orElse(Map.entry(summary.team1, 0));
            int pct = summary.matchupRuns == 0 ? 0
                    : (int) Math.round(winner.getValue() * 100.0 / summary.matchupRuns);
            String prediction = winner.getKey() + " (" + pct + "%)";
            Map<String, String> row = new LinkedHashMap<>();
            row.put("match_id", summary.matchId);
            row.put("team1", summary.team1);
            row.put("team2", summary.team2);
            row.put("path", summary.matchupRuns == primaryRuns.getOrDefault(summary.matchId, -1)
                    ? "predicted" : "alt");
            row.put("prediction", prediction);
            row.put("team1_path_fatigue", "0");
            row.put("team2_path_fatigue", "0");
            row.put("team1_path_opponent", "Group stage");
            row.put("team2_path_opponent", "Group stage");
            row.put("model_prediction", prediction);
            row.put("selection_source", "simulation");
            row.put("matchup_pct", summary.matchupPct);
            row.put("matchup_runs", String.valueOf(summary.matchupRuns));
            rows.add(row);
        }
        Path output = matchupFile(tournament, round + ".csv");
        Files.createDirectories(output.getParent());
        writeCsv(output, headers, rows);
    }

    private static final class DirectMatchupSummary {
        final String matchId;
        final String team1;
        final String team2;
        final Map<String, Integer> winnerCounts = new LinkedHashMap<>();
        int matchupRuns;
        String matchupPct = "";

        DirectMatchupSummary(String matchId, String team1, String team2) {
            this.matchId = matchId;
            this.team1 = team1;
            this.team2 = team2;
        }
    }

    private StageStatus status(boolean complete, boolean ready) {
        if (complete) return new StageStatus("✅", "Complete", "success");
        if (ready) return new StageStatus("▶", "Ready", "primary");
        return new StageStatus("⬜", "Locked", "secondary");
    }

    private void runGroupStagePipeline(String tournament, HtmlReporter reporter) throws IOException {
        if (!Files.exists(predictionFile(tournament, "groups.csv"))) {
            throw new IOException("Run Group Rankings before simulating the group stage.");
        }
        cascadeDeleteAfterGroupsEdit(tournament);
        MatchResolver resolver = MatchResolver.forWeb(reporter, predictionConfig);
        resolver.resolveAndWriteSimulation(tournament, "groups");
        reporter.appendInfo("Group stage complete. Saved " + SimulationHandler.DEFAULT_RUNS
                + " simulated group tables and their opening knockout routes.");
    }

    private void runTournamentPipeline(String tournament, HtmlReporter reporter) throws IOException {
        boolean startsAtLast16 = !bracketHasStage(tournament, "LAST_32")
                && bracketHasStage(tournament, "LAST_16");
        if (!Files.exists(simulationFile(tournament, "simulation_group_routes.csv"))) {
            throw new IOException("Run Group Stage before running the knockout rounds.");
        }
        if (startsAtLast16) {
            MatchResolver.forWeb(reporter, predictionConfig).resolveAndWriteKnockoutsFromGroups(tournament);
            for (String round : List.of("last_16", "last_8", "last_4", "final")) {
                writeDirectOpeningMatchups(tournament, round);
            }
            reporter.appendInfo("Continued the saved group-stage routes through the Last 16, quarter-finals, semi-finals and final.");
            return;
        }
        MatchResolver resolver = MatchResolver.forWeb(reporter, predictionConfig);
        // groups.csv contains an automatically selected display route. Monte Carlo
        // probabilities never depend on that route; they continue every saved group route.
        if (!Files.exists(predictionFile(tournament, "last_32.csv"))) {
            resolver.resolveAndWrite("groups", tournament);
        }
        cascadeDeleteAfterRoundEdit(tournament, "last_32");
        resolver.resolveAndWriteKnockoutsFromGroups(tournament);
        reporter.appendInfo("Continued the saved group-stage routes through the knockout rounds.");
        for (String round : List.of("last_32", "last_16", "last_8", "last_4", "final")) {
            resolver.resolveAndWrite(round, tournament);
            autoRunSimulation(round, tournament, reporter);
        }
        reporter.appendInfo("Tournament complete. All knockout rounds and simulations are ready.");
    }

    private void autoRunSimulation(String mode, String tournament, HtmlReporter reporter) throws IOException {
        String startRound = switch (mode) {
            case "groups" -> "last_32";
            case "last_32" -> "last_16";
            case "last_16" -> "last_8";
            case "last_8" -> "last_4";
            case "last_4" -> "final";
            default -> null;
        };
        if (startRound == null || !Files.exists(predictionFile(tournament, startRound + ".csv"))) return;
        MatchResolver.forWeb(reporter, predictionConfig).resolveAndWriteSimulation(tournament, startRound);
        reporter.appendInfo("Updated " + displayMode(startRound) + " simulation outputs.");
    }

    private void appendBrowserOnlyMessages(HtmlReporter reporter, String mode, String tournament, boolean lockedBefore) throws IOException {
        if ("start".equals(mode)) {
            if (lockedBefore) {
                reporter.appendWarning("Output already exists: " + predictionFile(tournament, "groups.csv") + " — reset or edit start data to re-run.");
            }
            reporter.appendInfo("Group rankings are ready. Run the Group Stage simulation next.");
            return;
        }
        if ("groups".equals(mode)) {
            if (lockedBefore) {
                reporter.appendWarning("Output already exists: " + predictionFile(tournament, "last_32.csv") + " — reset the knockout rounds to re-run.");
            }
            long rowCount = countDataRows(predictionFile(tournament, "last_32.csv"));
            reporter.appendInfo("Generated " + rowCount + " rows in last_32.csv.");
        }
    }

    private void cascadeDeleteAfterStart(String tournament) throws IOException {
        deletePaths(List.of(
                predictionFile(tournament, "groups.csv"),
                predictionFile(tournament, "last_32.csv"),
                matchupFile(tournament, "last_32.csv"),
                predictionFile(tournament, "last_16.csv"),
                matchupFile(tournament, "last_16.csv"),
                predictionFile(tournament, "last_8.csv"),
                matchupFile(tournament, "last_8.csv"),
                predictionFile(tournament, "last_4.csv"),
                matchupFile(tournament, "last_4.csv"),
                predictionFile(tournament, "final.csv"),
                matchupFile(tournament, "final.csv")
        ));
        deletePaths(simulationFilesFrom(tournament, "groups"));
    }

    private void cascadeDeleteAfterGroupsEdit(String tournament) throws IOException {
        deletePaths(List.of(
                predictionFile(tournament, "last_32.csv"),
                matchupFile(tournament, "last_32.csv"),
                predictionFile(tournament, "last_16.csv"),
                matchupFile(tournament, "last_16.csv"),
                predictionFile(tournament, "last_8.csv"),
                matchupFile(tournament, "last_8.csv"),
                predictionFile(tournament, "last_4.csv"),
                matchupFile(tournament, "last_4.csv"),
                predictionFile(tournament, "final.csv"),
                matchupFile(tournament, "final.csv")
        ));
        deletePaths(simulationFilesFrom(tournament, "last_32"));
    }

    private void cascadeDeleteAfterRoundEdit(String tournament, String round) throws IOException {
        List<Path> paths = new ArrayList<>();
        switch (round) {
            case "last_32" -> paths.addAll(List.of(
                    matchupFile(tournament, "last_32.csv"),
                    predictionFile(tournament, "last_16.csv"),
                    matchupFile(tournament, "last_16.csv"),
                    predictionFile(tournament, "last_8.csv"),
                    matchupFile(tournament, "last_8.csv"),
                    predictionFile(tournament, "last_4.csv"),
                    matchupFile(tournament, "last_4.csv"),
                    predictionFile(tournament, "final.csv"),
                    matchupFile(tournament, "final.csv")
            ));
            case "last_16" -> paths.addAll(List.of(
                    matchupFile(tournament, "last_16.csv"),
                    predictionFile(tournament, "last_8.csv"),
                    matchupFile(tournament, "last_8.csv"),
                    predictionFile(tournament, "last_4.csv"),
                    matchupFile(tournament, "last_4.csv"),
                    predictionFile(tournament, "final.csv"),
                    matchupFile(tournament, "final.csv")
            ));
            case "last_8" -> paths.addAll(List.of(
                    matchupFile(tournament, "last_8.csv"),
                    predictionFile(tournament, "last_4.csv"),
                    matchupFile(tournament, "last_4.csv"),
                    predictionFile(tournament, "final.csv"),
                    matchupFile(tournament, "final.csv")
            ));
            case "last_4" -> paths.addAll(List.of(
                    matchupFile(tournament, "last_4.csv"),
                    predictionFile(tournament, "final.csv"),
                    matchupFile(tournament, "final.csv")
            ));
            case "final" -> paths.add(matchupFile(tournament, "final.csv"));
            default -> {
                return;
            }
        }
        paths.addAll(simulationFilesFrom(tournament, round));
        deletePaths(paths);
    }

    private void cascadeDeleteForReset(String tournament, String step) throws IOException {
        if ("group-simulation".equals(step)) {
            cascadeDeleteAfterGroupsEdit(tournament);
            deletePaths(List.of(
                    simulationFile(tournament, "simulation_groups.csv"),
                    simulationFile(tournament, "simulation_group_routes.csv"),
                    simulationFile(tournament, "simulation_scorelines_groups.csv")
            ));
            return;
        }
        if ("simulation".equals(step)) {
            deletePaths(simulationFilesFrom(tournament, "groups"));
            return;
        }
        if ("groups".equals(step)) {
            deletePaths(List.of(
                    predictionFile(tournament, "groups.csv"),
                    predictionFile(tournament, "last_32.csv"),
                    matchupFile(tournament, "last_32.csv"),
                    predictionFile(tournament, "last_16.csv"),
                    matchupFile(tournament, "last_16.csv"),
                    predictionFile(tournament, "last_8.csv"),
                    matchupFile(tournament, "last_8.csv"),
                    predictionFile(tournament, "last_4.csv"),
                    matchupFile(tournament, "last_4.csv"),
                    predictionFile(tournament, "final.csv"),
                    matchupFile(tournament, "final.csv")
            ));
            deletePaths(simulationFilesFrom(tournament, "groups"));
            return;
        }
        cascadeDeleteAfterRoundEdit(tournament, step);
    }

    private List<Path> simulationFilesFrom(String tournament, String startRound) {
        List<String> rounds = List.of("groups", "last_32", "last_16", "last_8", "last_4", "final");
        int start = rounds.indexOf(startRound);
        if (start < 0) return List.of();
        List<Path> paths = new ArrayList<>();
        for (String round : rounds.subList(start, rounds.size())) {
            paths.add(simulationFile(tournament, "simulation_" + round + ".csv"));
            if ("groups".equals(round)) {
                paths.add(simulationFile(tournament, "simulation_group_routes.csv"));
                paths.add(simulationFile(tournament, "simulation_scorelines_groups.csv"));
            }
            if (!"groups".equals(round)) {
                paths.add(simulationFile(tournament, "simulation_paths_" + round + ".csv"));
                paths.add(simulationFile(tournament, "simulation_scorelines_" + round + ".csv"));
            }
        }
        return paths;
    }

    private void deletePaths(List<Path> paths) throws IOException {
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private long countDataRows(Path path) throws IOException {
        if (!Files.exists(path)) return 0;
        return Files.readAllLines(path).stream().filter(line -> !line.trim().isEmpty()).skip(1).count();
    }

    static String preferredWinner(Map<String, String> row) {
        for (String key : List.of("predicted_winner", "prediction", "elo")) {
            String raw = row.getOrDefault(key, "");
            String value = raw == null ? "" : raw.trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private CsvData readCsv(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new CsvData(new ArrayList<>(), new ArrayList<>());
        }
        try (Reader reader = Files.newBufferedReader(path); CSVParser parser = CSV.parse(reader)) {
            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                for (String header : headers) {
                    row.put(header, record.isMapped(header) ? record.get(header) : "");
                }
                rows.add(row);
            }
            return new CsvData(headers, rows);
        }
    }

    static List<Map<String, String>> routeAverageLast16Rows(List<Map<String, String>> matchupRows) {
        return routeAverageLast16Rows(matchupRows, List.of());
    }

    static List<Map<String, String>> routeAverageLast16Rows(List<Map<String, String>> matchupRows, List<Map<String, String>> groupRows) {
        Map<String, Map<String, Double>> slotProbabilities = groupSlotProbabilities(groupRows);
        boolean weighted = !slotProbabilities.isEmpty();
        EloCalculator eloCalculator = new EloCalculator();
        Map<String, double[]> totals = new LinkedHashMap<>();
        int routeRows = 0;
        for (Map<String, String> row : matchupRows) {
            String team1Display = row.getOrDefault("team1", "");
            String team2Display = row.getOrDefault("team2", "");
            String team1 = eloCalculator.extractTeamName(team1Display);
            String team2 = eloCalculator.extractTeamName(team2Display);
            String prediction = preferredWinner(row);
            String winner = eloCalculator.parseTeamFromPrediction(prediction);
            int winnerPct = eloCalculator.parsePctFromPrediction(prediction);
            if (team1.isBlank() || team2.isBlank() || winner.isBlank() || winnerPct <= 0) {
                continue;
            }
            double routeWeight = weighted
                    ? slotProbability(team1, slotToken(team1Display), slotProbabilities)
                    * slotProbability(team2, slotToken(team2Display), slotProbabilities)
                    : 1.0;
            if (routeWeight <= 0) {
                continue;
            }
            routeRows++;
            addRoutePct(totals, team1, winner.equalsIgnoreCase(team1) ? winnerPct : 100 - winnerPct, routeWeight);
            addRoutePct(totals, team2, winner.equalsIgnoreCase(team2) ? winnerPct : 100 - winnerPct, routeWeight);
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : totals.entrySet()) {
            double[] total = entry.getValue();
            if (total[1] == 0) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("team", entry.getKey());
            row.put("reach_last_16", String.format(java.util.Locale.ROOT, "%.1f", total[0] / total[1]));
            row.put("route_matchups", String.valueOf(routeRows));
            if (weighted) {
                row.put("route_weighted", "true");
            }
            rows.add(row);
        }
        return rows;
    }

    private Map<String, String> routeWeightedMatchupLikelihoods(
            String tournament, String round, List<Map<String, String>> currentRows,
            List<Map<String, String>> groupRows) throws IOException {
        if ("last_32".equals(round)) return matchupLikelihoodMap(currentRows, groupRows);
        List<Map<String, String>> last32Rows = readCsv(matchupFile(tournament, "last_32.csv")).rows();
        List<Map<String, String>> last16Rows = "last_16".equals(round)
                ? currentRows : readCsv(matchupFile(tournament, "last_16.csv")).rows();
        Map<String, String> last16Likelihoods = routeWeightedNextRoundMatchupLikelihoodMap(
                last16Rows, last32Rows, groupRows);
        if ("last_16".equals(round)) return last16Likelihoods;

        List<Map<String, String>> last8Rows = "last_8".equals(round)
                ? currentRows : readCsv(matchupFile(tournament, "last_8.csv")).rows();
        Map<String, String> last8Likelihoods = routeWeightedNextRoundMatchupLikelihoodMap(
                last8Rows, last16Rows, last16Likelihoods);
        if ("last_8".equals(round)) return last8Likelihoods;

        List<Map<String, String>> last4Rows = "last_4".equals(round)
                ? currentRows : readCsv(matchupFile(tournament, "last_4.csv")).rows();
        Map<String, String> last4Likelihoods = routeWeightedNextRoundMatchupLikelihoodMap(
                last4Rows, last8Rows, last8Likelihoods);
        if ("last_4".equals(round)) return last4Likelihoods;
        return routeWeightedNextRoundMatchupLikelihoodMap(currentRows, last4Rows, last4Likelihoods);
    }

    static Map<String, String> routeWeightedNextRoundMatchupLikelihoodMap(
            List<Map<String, String>> nextRoundRows, List<Map<String, String>> feederRows,
            List<Map<String, String>> groupRows) {
        Map<String, Map<String, Double>> slotProbabilities = groupSlotProbabilities(groupRows);
        EloCalculator eloCalculator = new EloCalculator();
        Map<String, Double> routeTotals = new LinkedHashMap<>();
        Map<String, Double> teamWinTotals = new LinkedHashMap<>();

        for (Map<String, String> row : feederRows) {
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1Display = row.getOrDefault("team1", "");
            String team2Display = row.getOrDefault("team2", "");
            String team1 = eloCalculator.extractTeamName(team1Display);
            String team2 = eloCalculator.extractTeamName(team2Display);
            String prediction = preferredWinner(row);
            String winner = eloCalculator.parseTeamFromPrediction(prediction);
            int winnerPct = eloCalculator.parsePctFromPrediction(prediction);
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank() || winner.isBlank() || winnerPct <= 0) continue;

            double routeWeight = slotProbabilities.isEmpty() ? 1.0
                    : slotProbability(team1, slotToken(team1Display), slotProbabilities)
                    * slotProbability(team2, slotToken(team2Display), slotProbabilities);
            if (routeWeight <= 0) continue;
            routeTotals.merge(matchId, routeWeight, Double::sum);
            double team1Win = winner.equalsIgnoreCase(team1) ? winnerPct / 100.0 : (100 - winnerPct) / 100.0;
            teamWinTotals.merge(matchId + "|" + team1, routeWeight * team1Win, Double::sum);
            teamWinTotals.merge(matchId + "|" + team2, routeWeight * (1.0 - team1Win), Double::sum);
        }

        Map<String, String> likelihoods = new LinkedHashMap<>();
        for (Map<String, String> row : nextRoundRows) {
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1Display = row.getOrDefault("team1", "");
            String team2Display = row.getOrDefault("team2", "");
            String team1 = eloCalculator.extractTeamName(team1Display);
            String team2 = eloCalculator.extractTeamName(team2Display);
            String feeder1 = winnerTokenMatchId(team1Display);
            String feeder2 = winnerTokenMatchId(team2Display);
            double total1 = routeTotals.getOrDefault(feeder1, 0.0);
            double total2 = routeTotals.getOrDefault(feeder2, 0.0);
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank() || total1 <= 0 || total2 <= 0) continue;
            double likelihood = teamWinTotals.getOrDefault(feeder1 + "|" + team1, 0.0) / total1
                    * teamWinTotals.getOrDefault(feeder2 + "|" + team2, 0.0) / total2;
            if (likelihood > 0) {
                likelihoods.put(matchupLikelihoodKey(matchId, team1, team2),
                        formatLikelihoodPct(likelihood));
            }
        }
        return likelihoods;
    }

    static Map<String, String> routeWeightedNextRoundMatchupLikelihoodMap(
            List<Map<String, String>> nextRoundRows, List<Map<String, String>> feederRows,
            Map<String, String> feederMatchupLikelihoods) {
        EloCalculator eloCalculator = new EloCalculator();
        Map<String, Double> routeTotals = new LinkedHashMap<>();
        Map<String, Double> teamWinTotals = new LinkedHashMap<>();
        for (Map<String, String> row : feederRows) {
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1Display = row.getOrDefault("team1", "");
            String team2Display = row.getOrDefault("team2", "");
            String team1 = eloCalculator.extractTeamName(team1Display);
            String team2 = eloCalculator.extractTeamName(team2Display);
            String prediction = preferredWinner(row);
            String winner = eloCalculator.parseTeamFromPrediction(prediction);
            int winnerPct = eloCalculator.parsePctFromPrediction(prediction);
            double routeWeight = parseDoubleOrZero(feederMatchupLikelihoods.getOrDefault(
                    matchupLikelihoodKey(matchId, team1, team2), "")) / 100.0;
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank() || winner.isBlank()
                    || winnerPct <= 0 || routeWeight <= 0) continue;
            routeTotals.merge(matchId, routeWeight, Double::sum);
            double team1Win = winner.equalsIgnoreCase(team1) ? winnerPct / 100.0 : (100 - winnerPct) / 100.0;
            teamWinTotals.merge(matchId + "|" + team1, routeWeight * team1Win, Double::sum);
            teamWinTotals.merge(matchId + "|" + team2, routeWeight * (1.0 - team1Win), Double::sum);
        }
        return nextRoundLikelihoods(nextRoundRows, routeTotals, teamWinTotals, eloCalculator);
    }

    private static Map<String, String> nextRoundLikelihoods(
            List<Map<String, String>> nextRoundRows, Map<String, Double> routeTotals,
            Map<String, Double> teamWinTotals, EloCalculator eloCalculator) {
        Map<String, String> likelihoods = new LinkedHashMap<>();
        for (Map<String, String> row : nextRoundRows) {
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1Display = row.getOrDefault("team1", "");
            String team2Display = row.getOrDefault("team2", "");
            String team1 = eloCalculator.extractTeamName(team1Display);
            String team2 = eloCalculator.extractTeamName(team2Display);
            String feeder1 = winnerTokenMatchId(team1Display);
            String feeder2 = winnerTokenMatchId(team2Display);
            double total1 = routeTotals.getOrDefault(feeder1, 0.0);
            double total2 = routeTotals.getOrDefault(feeder2, 0.0);
            if (matchId.isBlank() || team1.isBlank() || team2.isBlank() || total1 <= 0 || total2 <= 0) continue;
            double likelihood = teamWinTotals.getOrDefault(feeder1 + "|" + team1, 0.0) / total1
                    * teamWinTotals.getOrDefault(feeder2 + "|" + team2, 0.0) / total2;
            if (likelihood > 0) likelihoods.put(matchupLikelihoodKey(matchId, team1, team2),
                    formatLikelihoodPct(likelihood));
        }
        return likelihoods;
    }

    private static String formatLikelihoodPct(double probability) {
        double pct = probability * 100.0;
        if (pct >= 0.05) return String.format(java.util.Locale.ROOT, "%.1f", pct + 1e-9);
        return java.math.BigDecimal.valueOf(pct)
                .setScale(12, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static double parseDoubleOrZero(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private static String winnerTokenMatchId(String display) {
        String token = slotToken(display);
        return token.matches("W\\d+") ? "M" + token.substring(1) : "";
    }

    static Map<String, String> matchupLikelihoodMap(List<Map<String, String>> matchupRows, List<Map<String, String>> groupRows) {
        Map<String, Map<String, Double>> slotProbabilities = groupSlotProbabilities(groupRows);
        Map<String, String> likelihoods = new LinkedHashMap<>();
        if (slotProbabilities.isEmpty()) {
            return likelihoods;
        }
        EloCalculator eloCalculator = new EloCalculator();
        for (Map<String, String> row : matchupRows) {
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1Display = row.getOrDefault("team1", "");
            String team2Display = row.getOrDefault("team2", "");
            String team1 = eloCalculator.extractTeamName(team1Display);
            String team2 = eloCalculator.extractTeamName(team2Display);
            double likelihood = slotProbability(team1, slotToken(team1Display), slotProbabilities)
                    * slotProbability(team2, slotToken(team2Display), slotProbabilities);
            if (!matchId.isEmpty() && !team1.isEmpty() && !team2.isEmpty() && likelihood > 0) {
                likelihoods.put(matchupLikelihoodKey(matchId, team1, team2),
                        formatLikelihoodPct(likelihood));
            }
        }
        return likelihoods;
    }

    static String matchupLikelihoodKey(String matchId, String team1, String team2) {
        return matchId + "|" + team1 + "|" + team2;
    }

    private static void addRoutePct(Map<String, double[]> totals, String team, double pct, double weight) {
        double[] total = totals.computeIfAbsent(team, ignored -> new double[2]);
        total[0] += pct * weight;
        total[1] += weight;
    }

    private static Map<String, Map<String, Double>> groupSlotProbabilities(List<Map<String, String>> groupRows) {
        Map<String, Map<String, Double>> probabilities = new LinkedHashMap<>();
        for (Map<String, String> row : groupRows) {
            String team = row.getOrDefault("team", "").trim();
            if (team.isEmpty()) {
                continue;
            }
            Map<String, Double> slots = new LinkedHashMap<>();
            slots.put("1", flagProbability(row.getOrDefault("group_winner", "")));
            slots.put("2", flagProbability(row.getOrDefault("runner_up", "")));
            slots.put("3", flagProbability(row.getOrDefault("3rd_place", "")));

            String predictedPosition = row.getOrDefault("predicted_position", "").trim();
            String position = predictedPosition.isEmpty() ? "" : predictedPosition.substring(0, 1);
            double pct = probabilityFromText(predictedPosition);
            if (("1".equals(position) || "2".equals(position) || "3".equals(position)) && pct > 0) {
                slots.put(position, Math.max(slots.getOrDefault(position, 0.0), pct));
            }
            probabilities.put(team, slots);
        }
        return probabilities;
    }

    private static double slotProbability(String team, String slotToken, Map<String, Map<String, Double>> probabilities) {
        if (probabilities.isEmpty()) {
            return 1.0;
        }
        String slot = slotType(slotToken);
        if (slot.isEmpty()) {
            return 0.0;
        }
        return probabilities.getOrDefault(team, Map.of()).getOrDefault(slot, 0.0);
    }

    private static String slotToken(String display) {
        if (display == null) {
            return "";
        }
        int paren = display.indexOf("(");
        return (paren >= 0 ? display.substring(0, paren) : display).trim();
    }

    private static String slotType(String slotToken) {
        if (slotToken == null || slotToken.isBlank()) {
            return "";
        }
        String last = slotToken.substring(slotToken.length() - 1);
        return "1".equals(last) || "2".equals(last) || "3".equals(last) ? last : "";
    }

    private static double probabilityFromText(String value) {
        int open = value.indexOf("(");
        int pct = value.indexOf("%", open + 1);
        if (open < 0 || pct <= open) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.substring(open + 1, pct).trim()) / 100.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static double flagProbability(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "yes" -> 0.75;
            case "maybe" -> 0.35;
            default -> 0.0;
        };
    }

    private void writeCsv(Path path, List<String> headers, List<Map<String, String>> rows) throws IOException {
        Files.createDirectories(path.getParent());
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build();
        try (Writer writer = Files.newBufferedWriter(path); CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (Map<String, String> row : rows) {
                List<String> values = headers.stream().map(header -> row.getOrDefault(header, "")).collect(Collectors.toList());
                printer.printRecord(values);
            }
        }
    }

    private Map<String, String> baseRow(List<Map<String, String>> existingRows, int index, List<String> headers) {
        Map<String, String> row = new LinkedHashMap<>();
        if (index < existingRows.size()) {
            row.putAll(existingRows.get(index));
        }
        for (String header : headers) {
            row.putIfAbsent(header, "");
        }
        return row;
    }

    private void ensureHeaders(List<String> headers, List<String> requiredHeaders) {
        Set<String> current = new LinkedHashSet<>(headers);
        for (String header : requiredHeaders) {
            if (!current.contains(header)) {
                headers.add(header);
            }
        }
    }

    private static String webEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private Path outputPathForMode(String tournament, String mode) {
        return switch (mode) {
            case "start" -> predictionFile(tournament, "groups.csv");
            case "groups" -> predictionFile(tournament, "last_32.csv");
            case "group-simulation" -> simulationFile(tournament, "simulation_groups.csv");
            case "last_32" -> matchupFile(tournament, "last_32.csv");
            case "last_16" -> matchupFile(tournament, "last_16.csv");
            case "last_8" -> matchupFile(tournament, "last_8.csv");
            case "last_4" -> matchupFile(tournament, "last_4.csv");
            case "final" -> matchupFile(tournament, "final.csv");
            case "simulate" -> simulationFile(tournament, "simulation_last_32.csv");
            default -> null;
        };
    }

    private Path roundFileForView(String tournament, String round) {
        return switch (round) {
            case "start" -> predictionFile(tournament, "start.csv");
            case "groups", "groups_match" -> predictionFile(tournament, "groups.csv");
            case "last_32" -> predictionFile(tournament, "last_32.csv");
            case "last_32_match" -> matchupFile(tournament, "last_32.csv");
            case "last_16" -> predictionFile(tournament, "last_16.csv");
            case "last_16_match" -> matchupFile(tournament, "last_16.csv");
            case "last_8" -> predictionFile(tournament, "last_8.csv");
            case "last_8_match" -> matchupFile(tournament, "last_8.csv");
            case "last_4" -> predictionFile(tournament, "last_4.csv");
            case "last_4_match" -> matchupFile(tournament, "last_4.csv");
            case "final" -> predictionFile(tournament, "final.csv");
            case "final_match" -> matchupFile(tournament, "final.csv");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid round");
        };
    }

    private static final List<String> VIEW_ROUND_SEQUENCE = List.of(
        "start", "groups", "last_32_match", "last_16_match", "last_8_match", "last_4_match", "final_match"
    );

    private String nextViewRound(String round) {
        int idx = VIEW_ROUND_SEQUENCE.indexOf(round);
        return (idx >= 0 && idx + 1 < VIEW_ROUND_SEQUENCE.size()) ? VIEW_ROUND_SEQUENCE.get(idx + 1) : null;
    }

    private String prevViewRound(String round) {
        int idx = VIEW_ROUND_SEQUENCE.indexOf(round);
        return (idx > 0) ? VIEW_ROUND_SEQUENCE.get(idx - 1) : null;
    }

    private String editPrevViewRound(String round) {
        return switch (round) {
            case "groups" -> "start";
            case "last_32" -> "groups";
            case "last_16" -> "last_32_match";
            case "last_8"  -> "last_16_match";
            case "last_4"  -> "last_8_match";
            case "final"   -> "last_4_match";
            default -> null;
        };
    }

    private String viewRoundForEdit(String round) {
        return switch (round) {
            case "groups" -> "groups";
            case "last_32" -> "last_32_match";
            case "last_16" -> "last_16_match";
            case "last_8"  -> "last_8_match";
            case "last_4"  -> "last_4_match";
            case "final"   -> "final_match";
            default -> null;
        };
    }

    private String nextRunPrereqForView(String round) {
        return switch (round) {
            case "groups"        -> "last_32.csv";
            case "last_32_match" -> "last_16.csv";
            case "last_16_match" -> "last_8.csv";
            case "last_8_match"  -> "last_4.csv";
            case "last_4_match"  -> "final.csv";
            default -> null;
        };
    }

    private String nextRunModeForView(String round) {
        return switch (round) {
            case "groups"        -> "tournament";

            default -> null;
        };
    }

    private int completedStepCount(String tournament) {
        int count = 0;
        if (Files.exists(predictionFile(tournament, "start.csv"))) count++;
        if (Files.exists(predictionFile(tournament, "groups.csv"))) count++;
        if (Files.exists(predictionFile(tournament, "last_32.csv"))) count++;
        if (Files.exists(matchupFile(tournament, "final.csv"))) count++;
        return count;
    }

    private String describeCurrentStage(String tournament) {
        if (Files.exists(matchupFile(tournament, "final.csv"))) return "Final complete";
        if (Files.exists(matchupFile(tournament, "last_32.csv"))) return "Tournament run in progress";
        if (Files.exists(predictionFile(tournament, "last_32.csv"))) return "Group Picks complete";
        if (Files.exists(predictionFile(tournament, "groups.csv"))) return "Group Rankings complete";
        if (Files.exists(predictionFile(tournament, "start.csv"))) return "Group Setup complete";
        return "Not started";
    }

    private String safeTournament(String tournament) {
        String value = trim(tournament);
        if (!value.matches("[A-Za-z0-9_-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tournament name");
        }
        return value;
    }

    private String safeMode(String mode) {
        String value = trim(mode);
        if (!RUN_MODES.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid mode");
        }
        return value;
    }

    private String safeRound(String round) {
        String value = trim(round);
        if (!ROUND_NAMES.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid round");
        }
        return value;
    }

    private String safeResetStep(String step) {
        String value = trim(step);
        if (!RESET_STEPS.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset step");
        }
        return value;
    }

    private String displayMode(String mode) {
        return switch (mode) {
            case "snapshot-refresh" -> "Pre-Tournament Snapshot";
            case "start" -> "Group Setup";
            case "groups_match" -> "Group Rankings";
            case "groups" -> "Group Picks";
            case "group-simulation" -> "Group Stage";
            case "last_32", "last_32_match" -> "Last 32";
            case "last_16", "last_16_match" -> "Last 16";
            case "last_8", "last_8_match" -> "Quarter Finals";
            case "last_4", "last_4_match" -> "Semi Finals";
            case "final", "final_match" -> "Final";
            case "simulate", "simulation" -> "Monte Carlo";
            case "tournament" -> "Tournament";
            default -> mode;
        };
    }

    private static String displayTournament(String name) {
        if (name == null || name.isEmpty()) return name;
        return Arrays.stream(name.split("_"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private void ensureSimulationExists(String tournament, String simulationRound) throws IOException {
        if (simulationRound == null) return;
        Path output = simulationFile(tournament, "simulation_" + simulationRound + ".csv");
        Path input = predictionFile(tournament, simulationRound + ".csv");
        if (Files.exists(output) || !Files.exists(input)) return;
        MatchResolver.forWeb(new HtmlReporter().withConfig(predictionConfig), predictionConfig)
                .resolveAndWriteSimulation(tournament, simulationRound);
    }

    private static String simulationRoundForMatchView(String round) {
        return switch (round) {
            case "last_32_match" -> "last_32";
            case "last_16_match" -> "last_16";
            case "last_8_match" -> "last_8";
            case "last_4_match" -> "last_4";
            case "final_match" -> "final";
            default -> null;
        };
    }

    private static String advanceColumnForRound(String round) {
        return switch (round) {
            case "last_32" -> "reach_last_16";
            case "last_16" -> "reach_last_8";
            case "last_8" -> "reach_last_4";
            case "last_4" -> "reach_final";
            case "final" -> "champion";
            default -> "";
        };
    }

    static Map<String, String> simulationAdvanceMap(List<Map<String, String>> rows, String column) {
        Map<String, String> percentages = new LinkedHashMap<>();
        if (column == null || column.isBlank()) return percentages;
        for (Map<String, String> row : rows) {
            String team = row.getOrDefault("team", "").trim();
            String percentage = row.getOrDefault(column, "").trim();
            if (!team.isBlank() && !percentage.isBlank()) percentages.put(team, percentage);
        }
        return percentages;
    }

    static Map<String, String> simulationMatchupLikelihoodMap(List<Map<String, String>> rows, String stage) {
        Map<String, String> percentages = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            if (!stage.equals(row.getOrDefault("stage", ""))) continue;
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1 = row.getOrDefault("team1", "").trim();
            String team2 = row.getOrDefault("team2", "").trim();
            String percentage = row.getOrDefault("matchup_pct", "").trim();
            if (!matchId.isBlank() && !team1.isBlank() && !team2.isBlank() && !percentage.isBlank()) {
                percentages.putIfAbsent(matchupLikelihoodKey(matchId, team1, team2), percentage);
            }
        }
        return percentages;
    }

    static Map<String, String> simulationMatchupRunsMap(List<Map<String, String>> rows, String stage) {
        Map<String, String> runs = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            if (!stage.equals(row.getOrDefault("stage", ""))) continue;
            String matchId = row.getOrDefault("match_id", "").trim();
            String team1 = row.getOrDefault("team1", "").trim();
            String team2 = row.getOrDefault("team2", "").trim();
            String matchupRuns = row.getOrDefault("matchup_runs", "").trim();
            if (!matchId.isBlank() && !team1.isBlank() && !team2.isBlank() && !matchupRuns.isBlank()) {
                runs.putIfAbsent(matchupLikelihoodKey(matchId, team1, team2), matchupRuns);
                runs.putIfAbsent(matchupLikelihoodKey(matchId, team2, team1), matchupRuns);
            }
        }
        return runs;
    }

    private String oddsColumnForRound(String round) {
        return switch (round) {
            case "last_32_match" -> "last_16";
            case "last_16_match" -> "last_8";
            case "last_8_match"  -> "last_4";
            case "last_4_match"  -> "final";
            case "final_match"   -> "final";
            default -> null;
        };
    }

    private String displayViewMode(String round) {
        if ("groups".equals(round) || "groups_match".equals(round)) {
            return "Group Rankings";
        }
        if ("last_32_match".equals(round)) {
            return "Last 32";
        }

        return displayMode(round);
    }

    private String redirectToTournament(String tournament) {
        return "redirect:/tournament/" + tournament;
    }

    private String redirectAfterSaveRun(String round, String tournament) {
        return switch (round) {
            case "start"   -> redirectToTournament(tournament);
            case "groups" -> redirectToTournament(tournament);
            case "group-simulation" -> redirectToTournament(tournament);
            case "last_32" -> "redirect:/view/last_32_match?tournament=" + tournament;
            case "last_16" -> "redirect:/view/last_16_match?tournament=" + tournament;
            case "last_8"  -> "redirect:/view/last_8_match?tournament=" + tournament;
            case "last_4"  -> "redirect:/view/last_4_match?tournament=" + tournament;
            case "final"   -> "redirect:/view/final_match?tournament=" + tournament;
            case "simulate" -> redirectToTournament(tournament);
            case "tournament" -> bracketHasStage(tournament, "LAST_32")
                    ? "redirect:/view/last_32_match?tournament=" + tournament
                    : "redirect:/view/last_16_match?tournament=" + tournament;
            default        -> redirectToTournament(tournament);
        };
    }


    private Path predictionFile(String tournament, String fileName) {
        return projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve(fileName);
    }

    private Path simulationFile(String tournament, String fileName) {
        return projectRoot.resolve("data").resolve("simulations").resolve(tournament).resolve(fileName);
    }

    private Path matchupFile(String tournament, String fileName) {
        String round = fileName.endsWith(".csv")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        return projectRoot.resolve("data").resolve("simulations").resolve(tournament)
                .resolve("matchup_paths_" + round + ".csv");
    }

    private String renderGroupMatches(String tournament, List<Map<String, String>> scorelineRows,
                                      Map<String, EloBreakdown> eloBreakdowns) {
        if (scorelineRows.isEmpty()) return "";
        Map<String, String[]> matches = new java.util.TreeMap<>();
        Map<String, Map<String, Integer>> outcomes = new LinkedHashMap<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Map<String, String> row : scorelineRows) {
            if (!"groups".equals(row.getOrDefault("stage", ""))) continue;
            String matchId = row.getOrDefault("match_id", "");
            String team1 = row.getOrDefault("team1", "");
            String team2 = row.getOrDefault("team2", "");
            String key = matchId + "|" + team1 + "|" + team2;
            int count = parseInt(row.getOrDefault("count", "0"), 0);
            matches.putIfAbsent(key, new String[]{matchId, team1, team2});
            outcomes.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                    .merge(row.getOrDefault("winner", "Draw"), count, Integer::sum);
            totals.merge(key, count, Integer::sum);
        }
        List<String> lines = new ArrayList<>();
        lines.add("match_id,team1,team2,elo");
        for (Map.Entry<String, String[]> entry : matches.entrySet()) {
            String key = entry.getKey();
            String[] match = entry.getValue();
            Map.Entry<String, Integer> likely = outcomes.getOrDefault(key, Map.of()).entrySet().stream()
                    .max(Map.Entry.comparingByValue()).orElse(Map.entry("Draw", 0));
            int pct = totals.getOrDefault(key, 0) == 0 ? 0
                    : (int) Math.round(likely.getValue() * 100.0 / totals.get(key));
            lines.add(String.join(",", match[0], match[1], match[2], likely.getKey() + " (" + pct + "%)"));
        }
        HtmlReporter reporter = new HtmlReporter().withConfig(predictionConfig)
                .withMatchupSimulationRuns(simulationMatchupRunsMap(scorelineRows, "groups"));
        reporter.printMatchups("Group Stage Matches", lines, new EloCalculator(), null, Map.of(), eloBreakdowns);
        return reporter.getHtml();
    }

    private Map<String, Map<String, String>> groupSimulationByTeam(String tournament) throws IOException {
        Path simulation = simulationFile(tournament, "simulation_groups.csv");
        if (!Files.exists(simulation)) return Map.of();
        Map<String, Map<String, String>> byTeam = new LinkedHashMap<>();
        for (Map<String, String> row : readCsv(simulation).rows()) {
            String team = row.getOrDefault("team", "").trim();
            if (!team.isBlank()) byTeam.put(team, row);
        }
        return byTeam;
    }

    private static List<String> validateGroupPicks(List<Map<String, String>> rows) {
        Map<String, List<Map<String, String>>> byGroup = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String group = row.getOrDefault("group", "?");
            byGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(row);
        }
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, String>>> entry : byGroup.entrySet()) {
            String group = entry.getKey();
            List<Map<String, String>> groupRows = entry.getValue();
            long winners = groupRows.stream().filter(r -> "yes".equals(r.get("group_winner"))).count();
            long runnersUp = groupRows.stream().filter(r -> "yes".equals(r.get("runner_up"))).count();
            boolean overlap = groupRows.stream().anyMatch(
                r -> "yes".equals(r.get("group_winner")) && "yes".equals(r.get("runner_up")));
            if (winners != 1) errors.add("Group " + group + ": must have exactly 1 Group Winner (yes) — found " + winners);
            if (runnersUp != 1) errors.add("Group " + group + ": must have exactly 1 Runner-up (yes) — found " + runnersUp);
            if (overlap) errors.add("Group " + group + ": the same team cannot be both Group Winner and Runner-up");
        }
        return errors;
    }

    static String formatQualBonus(String raw) {
        if (raw == null || raw.isBlank()) return "—";
        try {
            int v = Integer.parseInt(raw.trim());
            if (v == 0) return "Host";
            return (v > 0 ? "+" : "") + v;
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String sanitiseNote(String value) {
        if (value == null) return "";
        return value.trim().replace("\r\n", "; ").replace("\r", "; ").replace("\n", "; ");
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(trim(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String extractTeamName(String value) {
        int open = value.lastIndexOf('(');
        int close = open >= 0 ? value.indexOf(')', open) : -1;
        if (open >= 0 && close > open) {
            return value.substring(open + 1, close).trim();
        }
        return value;
    }

    private static String escapeHtml(String input) {
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String extractWinnerName(String value) {
        String trimmed = value == null ? "" : value.trim();
        int percentStart = trimmed.lastIndexOf(" (");
        if (percentStart > 0 && trimmed.endsWith(")")) {
            return trimmed.substring(0, percentStart).trim();
        }
        return extractTeamName(trimmed);
    }

    public static final class TournamentSummary {
        private final String name;
        private final String label;
        private final String stage;
        private final int completedSteps;

        public TournamentSummary(String name, String stage, int completedSteps) {
            this.name = name;
            this.label = displayTournament(name);
            this.stage = stage;
            this.completedSteps = completedSteps;
        }

        public String getName() { return name; }
        public String getLabel() { return label; }
        public String getStage() { return stage; }
        public int getCompletedSteps() { return completedSteps; }
    }

    public static final class StageView {
        private final String label;
        private final String description;
        private final StageStatus status;
        private final boolean canRun;
        private final String runMode;
        private final String runLabel;
        private final String runButtonClass;
        private final boolean canEdit;
        private final String editUrl;
        private final boolean canView;
        private final String viewUrl;
        private final boolean canView2;
        private final String viewUrl2;
        private final String viewLabel2;
        private final boolean canReset;
        private final String resetUrl;

        public StageView(String label, String description, StageStatus status, boolean canRun, String runMode, boolean canEdit, String editUrl, boolean canView, String viewUrl, boolean canReset, String resetUrl) {
            this(label, description, status, canRun, runMode, null, null, canEdit, editUrl, canView, viewUrl, false, null, null, canReset, resetUrl);
        }

        public StageView(String label, String description, StageStatus status, boolean canRun, String runMode, boolean canEdit, String editUrl, boolean canView, String viewUrl, boolean canView2, String viewUrl2, String viewLabel2, boolean canReset, String resetUrl) {
            this(label, description, status, canRun, runMode, null, null, canEdit, editUrl, canView, viewUrl, canView2, viewUrl2, viewLabel2, canReset, resetUrl);
        }

        public StageView(String label, String description, StageStatus status, boolean canRun, String runMode,
                         String runLabel, String runButtonClass, boolean canEdit, String editUrl,
                         boolean canView, String viewUrl, boolean canView2, String viewUrl2,
                         String viewLabel2, boolean canReset, String resetUrl) {
            this.label = label;
            this.description = description;
            this.status = status;
            this.canRun = canRun;
            this.runMode = runMode;
            this.runLabel = runLabel != null ? runLabel : "Review";
            this.runButtonClass = runButtonClass != null ? runButtonClass : "btn-primary";
            this.canEdit = canEdit;
            this.editUrl = editUrl;
            this.canView = canView;
            this.viewUrl = viewUrl;
            this.canView2 = canView2;
            this.viewUrl2 = viewUrl2;
            this.viewLabel2 = viewLabel2 != null ? viewLabel2 : "View";
            this.canReset = canReset;
            this.resetUrl = resetUrl;
        }

        public String getLabel() { return label; }
        public String getDescription() { return description; }
        public StageStatus getStatus() { return status; }
        public boolean isCanRun() { return canRun; }
        public String getRunMode() { return runMode; }
        public String getRunLabel() { return runLabel; }
        public String getRunButtonClass() { return runButtonClass; }
        public boolean isCanEdit() { return canEdit; }
        public String getEditUrl() { return editUrl; }
        public boolean isCanView() { return canView; }
        public String getViewUrl() { return viewUrl; }
        public boolean isCanView2() { return canView2; }
        public String getViewUrl2() { return viewUrl2; }
        public String getViewLabel2() { return viewLabel2; }
        public boolean isCanReset() { return canReset; }
        public String getResetUrl() { return resetUrl; }
    }

    public static final class StageStatus {
        private final String icon;
        private final String text;
        private final String badge;

        public StageStatus(String icon, String text, String badge) {
            this.icon = icon;
            this.text = text;
            this.badge = badge;
        }

        public String getIcon() { return icon; }
        public String getText() { return text; }
        public String getBadge() { return badge; }
    }

    public static final class StartRow {
        private final String group;
        private final String team;
        private final boolean host;
        private final int injuryImpact;
        private final int heatImpact;
        private final int squadDropouts;
        private final int squadAgeProfile;
        private final int squadCohesion;
        private final int squadDepth;
        private final int attackQuality;
        private final int defenceQuality;
        private final String dropoutNotes;
        private final String injuryNotes;
        private final String ageNotes;
        private final String cohesionNotes;
        private final String depthNotes;
        private final String qualityNotes;

        public StartRow(String group, String team, boolean host, int injuryImpact,
                        int heatImpact, int squadDropouts, int squadAgeProfile,
                        int squadCohesion, int squadDepth, int attackQuality, int defenceQuality,
                        String dropoutNotes, String injuryNotes,
                        String ageNotes, String cohesionNotes,
                        String depthNotes, String qualityNotes) {
            this.group = group;
            this.team = team;
            this.host = host;
            this.injuryImpact = injuryImpact;
            this.heatImpact = heatImpact;
            this.squadDropouts = squadDropouts;
            this.squadAgeProfile = squadAgeProfile;
            this.squadCohesion = squadCohesion;
            this.squadDepth = squadDepth;
            this.attackQuality = attackQuality;
            this.defenceQuality = defenceQuality;
            this.dropoutNotes = dropoutNotes;
            this.injuryNotes = injuryNotes;
            this.ageNotes = ageNotes;
            this.cohesionNotes = cohesionNotes;
            this.depthNotes = depthNotes;
            this.qualityNotes = qualityNotes;
        }

        public String getGroup() { return group; }
        public String getTeam() { return team; }
        public String getTeamFlagHtml() { return HtmlReporter.flagHtml(team); }
        public boolean isHost() { return host; }
        public int getInjuryImpact() { return injuryImpact; }
        public int getHeatImpact() { return heatImpact; }
        public int getSquadDropouts() { return squadDropouts; }
        public int getSquadAgeProfile() { return squadAgeProfile; }
        public int getSquadCohesion() { return squadCohesion; }
        public int getSquadDepth() { return squadDepth; }
        public int getAttackQuality() { return attackQuality; }
        public int getDefenceQuality() { return defenceQuality; }
        public String getDropoutNotes() { return dropoutNotes; }
        public String getInjuryNotes() { return injuryNotes; }
        public String getAgeNotes() { return ageNotes; }
        public String getCohesionNotes() { return cohesionNotes; }
        public String getDepthNotes() { return depthNotes; }
        public String getQualityNotes() { return qualityNotes; }
    }

    public static final class GroupPickRow {
        private final int rowIndex;
        private final String group;
        private final String team;
        private final String qualificationForm;
        private final String predictedPosition;
        private final String groupWinner;
        private final String runnerUp;
        private final String thirdPlace;
        private final int squadAgeProfile;
        private final int squadCohesion;
        private final EloBreakdown breakdown;

        public GroupPickRow(int rowIndex, String group, String team, String qualificationForm, String predictedPosition,
                            String groupWinner, String runnerUp, String thirdPlace, int squadAgeProfile, int squadCohesion,
                            EloBreakdown breakdown) {
            this.rowIndex = rowIndex;
            this.group = group;
            this.team = team;
            this.qualificationForm = qualificationForm;
            this.predictedPosition = predictedPosition;
            this.groupWinner = groupWinner;
            this.runnerUp = runnerUp;
            this.thirdPlace = thirdPlace;
            this.squadAgeProfile = squadAgeProfile;
            this.squadCohesion = squadCohesion;
            this.breakdown = breakdown;
        }

        public int getRowIndex() { return rowIndex; }
        public String getGroup() { return group; }
        public String getTeam() { return team; }
        public String getTeamHtml() { return HtmlReporter.flagHtml(team) + escapeHtml(team); }
        public String getQualificationForm() { return qualificationForm; }
        public String getPredictedPosition() { return predictedPosition; }
        public String getGroupWinner() { return groupWinner; }
        public String getRunnerUp() { return runnerUp; }
        public String getThirdPlace() { return thirdPlace; }
        public int getSquadAgeProfile() { return squadAgeProfile; }
        public int getSquadCohesion() { return squadCohesion; }

        public String getBreakdownHtml() {
            return GroupViewRow.buildBreakdownHtml(team, breakdown);
        }
    }

    public static final class RoundRow {
        private final String matchId;
        private final String team1;
        private final String team2;
        private final String predictedWinner;
        private final boolean disagree;

        public RoundRow(String matchId, String team1, String team2, String predictedWinner, boolean disagree) {
            this.matchId = matchId;
            this.team1 = team1;
            this.team2 = team2;
            this.predictedWinner = predictedWinner;
            this.disagree = disagree;
        }

        public String getMatchId() { return matchId; }
        public String getTeam1() { return team1; }
        public String getTeam2() { return team2; }
        public String getPredictedWinner() { return predictedWinner; }
        public boolean isDisagree() { return disagree; }
        public String getTeam1Html() {
            String name = extractTeamName(team1);
            return HtmlReporter.flagHtml(name) + escapeHtml(name);
        }
        public String getTeam2Html() {
            String name = extractTeamName(team2);
            return HtmlReporter.flagHtml(name) + escapeHtml(name);
        }
        public String getPredictedWinnerHtml() {
            String name = extractWinnerName(predictedWinner);
            return HtmlReporter.flagHtml(name) + escapeHtml(predictedWinner);
        }

        public String getDisagreeWinnerHtml() {
            String winnerName = extractWinnerName(predictedWinner);
            String t1 = extractTeamName(team1);
            String t2 = extractTeamName(team2);
            String loserName = winnerName.equalsIgnoreCase(t1) ? t2 : t1;
            int pct = 50;
            int paren = predictedWinner.lastIndexOf(" (");
            if (paren >= 0 && predictedWinner.endsWith(")")) {
                try {
                    String inner = predictedWinner.substring(paren + 2, predictedWinner.length() - 1);
                    if (inner.endsWith("%")) pct = Integer.parseInt(inner.substring(0, inner.length() - 1).trim());
                } catch (NumberFormatException ignored) {}
            }
            String loserDisplay = loserName + " (" + (100 - pct) + "%)";
            return HtmlReporter.flagHtml(loserName) + escapeHtml(loserDisplay);
        }
    }

    public static final class GroupViewRow {
        private final String team;
        private final String qualificationForm;
        private final String predictedPosition;
        private final String groupWinner;
        private final String runnerUp;
        private final String thirdPlace;
        private final EloBreakdown breakdown;

        public GroupViewRow(String team, String qualificationForm, String predictedPosition,
                            String groupWinner, String runnerUp, String thirdPlace, EloBreakdown breakdown) {
            this.team = team;
            this.qualificationForm = qualificationForm;
            this.predictedPosition = predictedPosition;
            this.groupWinner = groupWinner;
            this.runnerUp = runnerUp;
            this.thirdPlace = thirdPlace;
            this.breakdown = breakdown;
        }

        public String getTeamHtml() { return HtmlReporter.flagHtml(team) + escapeHtml(team); }
        public String getQualificationForm() { return qualificationForm; }
        public String getPredictedPosition() { return predictedPosition; }

        public String getBreakdownHtml() {
            return buildBreakdownHtml(team, breakdown);
        }

        static String buildBreakdownHtml(String team, EloBreakdown b) {
            return HtmlReporter.buildTeamBreakdownHtml(team, b);
        }

        public String getGroupWinnerBadgeHtml() {
            return pickBadge(groupWinner, "Group Winner", "Winner Contender", "#FFD700", "#000");
        }

        public String getRunnerUpBadgeHtml() {
            return pickBadge(runnerUp, "Runner-up", "Runner-up Contender", "#C0C0C0", "#000");
        }

        public String getThirdPlaceBadgeHtml() {
            return pickBadge(thirdPlace, "Best 3rd", "3rd-place Contender", "#CD7F32", "#fff");
        }

        public String getPositionBadgeHtml() {
            return getGroupWinnerBadgeHtml() + " " + getRunnerUpBadgeHtml() + " " + getThirdPlaceBadgeHtml();
        }

        private static String pickBadge(String value, String yesLabel, String maybeLabel, String background, String color) {
            if (value == null || value.isBlank() || "no".equalsIgnoreCase(value)) return "";
            boolean maybe = "maybe".equalsIgnoreCase(value);
            String label = maybe ? maybeLabel : yesLabel;
            String style = maybe
                    ? "border:1px solid " + background + ";color:#212529;background-color:transparent"
                    : "background-color:" + background + ";color:" + color;
            String tooltip = maybe ? "Possible group outcome from manual picks." : "Selected group outcome from manual picks.";
            return "<span class=\"badge\" style=\"" + style + "\" title=\"" + tooltip + "\">"
                    + escapeHtml(label) + "</span>";
        }
    }

    public static final class RoundViewRow {
        private final String matchId;
        private final String team1;
        private final String team2;
        private final String predictedWinner;
        private final String prediction;

        public RoundViewRow(String matchId, String team1, String team2, String predictedWinner, String prediction) {
            this.matchId = matchId;
            this.team1 = team1;
            this.team2 = team2;
            this.predictedWinner = predictedWinner;
            this.prediction = prediction;
        }

        public String getMatchId() { return matchId; }
        public String getTeam1Html() { return teamHtml(team1); }
        public String getTeam2Html() { return teamHtml(team2); }
        public String getPredictedWinnerHtml() { return HtmlReporter.flagHtml(extractWinnerName(predictedWinner)) + escapeHtml(predictedWinner); }
        public String getPrediction() { return prediction; }
        public boolean isTeam1Winner() { return isPredictedWinner(team1); }
        public boolean isTeam2Winner() { return isPredictedWinner(team2); }

        private boolean isPredictedWinner(String teamValue) {
            String winner = extractWinnerName(predictedWinner);
            String teamName = extractTeamName(teamValue);
            return !winner.isEmpty() && winner.equals(teamName);
        }

        private String teamHtml(String teamValue) {
            return HtmlReporter.flagHtml(extractTeamName(teamValue)) + escapeHtml(teamValue);
        }
    }

    public record CsvData(List<String> headers, List<Map<String, String>> rows) { }
}

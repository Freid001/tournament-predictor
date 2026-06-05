package com.tournamentpredictor.web;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.service.MatchResolver;
import com.tournamentpredictor.service.util.EloBreakdown;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.HtmlReporter;
import com.tournamentpredictor.web.view.SimulationResultsRenderer;
import jakarta.servlet.http.HttpServletRequest;
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
    private static final Set<String> RUN_MODES = Set.of("snapshot-refresh", "start", "groups", "last_32", "last_16", "last_8", "last_4", "final", "simulate");
    private static final Set<String> ROUND_NAMES = Set.of("groups", "groups_match", "last_32", "last_32_match", "last_16", "last_16_match", "last_8", "last_8_match", "last_4", "last_4_match", "final", "final_match", "simulation");
    private static final Set<String> RESET_STEPS = Set.of("groups", "last_32", "last_16", "last_8", "last_4", "final", "simulation");
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
            Path lockedPath = outputPathForMode(safeTournament, safeMode);
            boolean lockedBefore = lockedPath != null && Files.exists(lockedPath);
            if (lockedBefore && !"start".equals(safeMode) && !"groups".equals(safeMode)) {
                reporter.appendWarning("Output already exists: " + lockedPath + " — delete or reset to re-run.");
            }

            MatchResolver.forWeb(reporter, predictionConfig).resolveAndWrite(safeMode, safeTournament);
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
                    rows.add(new StartRow(String.valueOf(group), "", false, 0, 0, 0, 0, 0, 0, 0, "", "", "", "", "", ""));
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
                        parseInt(row.getOrDefault("squad_quality", "0"), 0),
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
                "squad_quality", "quality_notes",
                "squad_dropouts", "dropout_notes",
                "injury_impact", "injury_notes",
                "heat_impact"));
        for (String header : existing.headers()) {
            if (!headers.contains(header)) {
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
            row.put("squad_quality", String.valueOf(parseInt(request.getParameter("squadQuality" + i), 0)));
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
        try {
            MatchResolver.forWeb(new HtmlReporter().withConfig(predictionConfig), predictionConfig).resolveAndWrite("start", safeTournament);
        } catch (Exception e) {
            return redirectToTournament(safeTournament);
        }
        return "redirect:/edit/groups?tournament=" + safeTournament;
    }

    @GetMapping("/edit/{round}")
    public String editRound(@PathVariable("round") String round, @RequestParam("tournament") String tournament, Model model) throws IOException {
        String safeRound = safeRound(round);
        String safeTournament = safeTournament(tournament);
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
        } else {
            // Build lookup from matchup file for the combined prediction (all signals)
            Path matchupPath = matchupFile(safeTournament, safeRound + ".csv");
            Map<String, String> matchupPrediction = new LinkedHashMap<>();
            for (Map<String, String> r : readCsv(matchupPath).rows()) {
                if ("predicted".equalsIgnoreCase(r.getOrDefault("path", "predicted"))) {
                    String key = r.getOrDefault("match_id", "") + "|" + r.getOrDefault("team1", "") + "|" + r.getOrDefault("team2", "");
                    String pred = trim(r.getOrDefault("prediction", ""));
                    if (!pred.isEmpty()) matchupPrediction.put(key, pred);
                }
            }
            List<RoundRow> rows = csvData.rows().stream()
                    .map(row -> {
                        String key = row.getOrDefault("match_id", "") + "|" + row.getOrDefault("team1", "") + "|" + row.getOrDefault("team2", "");
                        String winner = matchupPrediction.getOrDefault(key, preferredWinner(row));
                        return new RoundRow(
                                row.getOrDefault("match_id", ""),
                                row.getOrDefault("team1", ""),
                                row.getOrDefault("team2", ""),
                                winner,
                                !trim(row.getOrDefault("do_you_disagree", "")).isEmpty()
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
            MatchResolver.forWeb(new HtmlReporter().withConfig(predictionConfig), predictionConfig).resolveAndWrite(safeRound, safeTournament);
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
                    parseInt(row.getOrDefault("squad_quality", "0"), 0),
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

    @GetMapping("/view/{round}")
    public String viewRound(@PathVariable("round") String round, @RequestParam("tournament") String tournament, Model model) throws IOException {
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
            model.addAttribute("editUrl", "/edit/groups?tournament=" + safeTournament);
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

        // Show "Review" button for next step when not yet generated
        String nextRunMode = nextRunModeForView(safeRound);
        String nextRunPrereq = nextRunPrereqForView(safeRound);
        boolean canNextRun = !nextExists && nextRunMode != null
                && nextRunPrereq != null && Files.exists(predictionFile(safeTournament, nextRunPrereq));
        model.addAttribute("nextRunMode", nextRunMode);
        model.addAttribute("canNextRun", canNextRun);
        model.addAttribute("nextRunLabel", nextRunMode != null ? "Review " + displayMode(nextRunMode) : "");

        String prevRound = prevViewRound(safeRound);
        boolean prevExists = prevRound != null && Files.exists(roundFileForView(safeTournament, prevRound));
        model.addAttribute("prevViewUrl", prevRound != null ? "/view/" + prevRound + "?tournament=" + safeTournament : "#");
        model.addAttribute("prevViewLabel", prevRound != null ? "Back to " + displayViewMode(prevRound) : "Back to Previous");
        model.addAttribute("prevViewEnabled", prevExists);
        model.addAttribute("hasPrevRound", prevRound != null);
        model.addAttribute("prevNavUrl", prevExists ? "/view/" + prevRound + "?tournament=" + safeTournament : null);
        model.addAttribute("prevNavLabel", prevRound != null ? "← " + displayViewMode(prevRound) : null);
        model.addAttribute("prevNavEnabled", prevExists);
        model.addAttribute("pageTitle", "View " + displayViewMode(safeRound));

        if (groupsMode) {
            CsvLoader csvLoader = new CsvLoader(projectRoot).withConfig(predictionConfig);
            Map<String, EloBreakdown> eloBreakdowns;
            try { eloBreakdowns = csvLoader.loadEloBreakdowns(safeTournament); }
            catch (Exception e) { eloBreakdowns = Map.of(); }

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
            if ("simulation".equals(safeRound)) {
                model.addAttribute("output", SimulationResultsRenderer.render(csvData.rows(), readCsv(predictionFile(safeTournament, "simulation_paths_last_32.csv")).rows()));
                model.addAttribute("mode", displayViewMode(safeRound));
                return "result";
            }

            HtmlReporter reporter = new HtmlReporter().withConfig(predictionConfig);
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
            String editRound = editRoundForMatchView(safeRound);
            String editUrlStr = editRound != null ? "/edit/" + editRound + "?tournament=" + safeTournament : null;
            reporter.setEditUrl(editUrlStr);
            reporter.printMatchups(displayViewMode(safeRound), lines, new EloCalculator(), null, odds, eloBreakdowns);
            model.addAttribute("output", reporter.getHtml());
            model.addAttribute("mode", displayViewMode(safeRound));
            if (editUrlStr != null) {
                model.addAttribute("editUrl", editUrlStr);
            }
            return "result";
        }
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
        boolean last32PredExists = Files.exists(predictionFile(tournament, "last_32.csv"));
        boolean last32MatchExists = Files.exists(matchupFile(tournament, "last_32.csv"));
        boolean last16PredExists = Files.exists(predictionFile(tournament, "last_16.csv"));
        boolean last16MatchExists = Files.exists(matchupFile(tournament, "last_16.csv"));
        boolean last8PredExists = Files.exists(predictionFile(tournament, "last_8.csv"));
        boolean last8MatchExists = Files.exists(matchupFile(tournament, "last_8.csv"));
        boolean last4PredExists = Files.exists(predictionFile(tournament, "last_4.csv"));
        boolean last4MatchExists = Files.exists(matchupFile(tournament, "last_4.csv"));
        boolean finalPredExists = Files.exists(predictionFile(tournament, "final.csv"));
        boolean finalMatchExists = Files.exists(matchupFile(tournament, "final.csv"));
        boolean simulationExists = Files.exists(predictionFile(tournament, "simulation_last_32.csv"));

        List<StageView> stages = new ArrayList<>();
        stages.add(new StageView("Tournament Snapshot", snapshotDescription(tournament, snapshotExists),
                snapshotExists ? new StageStatus("❄", "Frozen", "info") : status(false, startExists),
                startExists, "snapshot-refresh", "Refresh", "btn-outline-secondary",
                false, null,
                false, null,
                false, null,
                null,
                false, null));
        stages.add(new StageView("Group Setup", "Edit teams, host status, injury and heat levels. Rankings are generated automatically on save.",
                status(groupsExists, startExists),
                false, null,
                true, "/edit/start?tournament=" + tournament,
                startExists, "/view/start?tournament=" + tournament,
                groupsExists, "/reset/groups?tournament=" + tournament));
        stages.add(new StageView("Group Picks", "Edit groups.csv picks, then generate last_32 predictions.",
                status(last32PredExists, groupsExists && !last32PredExists),
                groupsExists && !last32PredExists, "groups",
                groupsExists, "/edit/groups?tournament=" + tournament,
                groupsExists, "/view/groups?tournament=" + tournament,
                groupsExists, "/reset/groups?tournament=" + tournament));
        stages.add(new StageView("Last 32", "Edit last_32.csv picks, then generate last_32 matchups.",
                status(last32MatchExists, last32PredExists && !last32MatchExists),
                last32PredExists && !last32MatchExists, "last_32",
                last32PredExists, "/edit/last_32?tournament=" + tournament,
                last32MatchExists, "/view/last_32_match?tournament=" + tournament,
                last32PredExists || last32MatchExists, "/reset/last_32?tournament=" + tournament));
        stages.add(new StageView("Last 16", "Edit last_16.csv picks, then generate last_16 matchups.",
                status(last16MatchExists, last16PredExists && !last16MatchExists),
                last16PredExists && !last16MatchExists, "last_16",
                last16PredExists, "/edit/last_16?tournament=" + tournament,
                last16MatchExists, "/view/last_16_match?tournament=" + tournament,
                last16PredExists || last16MatchExists, "/reset/last_16?tournament=" + tournament));
        stages.add(new StageView("Quarter Finals", "Edit last_8.csv picks, then generate quarter-final matchups.",
                status(last8MatchExists, last8PredExists && !last8MatchExists),
                last8PredExists && !last8MatchExists, "last_8",
                last8PredExists, "/edit/last_8?tournament=" + tournament,
                last8MatchExists, "/view/last_8_match?tournament=" + tournament,
                last8PredExists || last8MatchExists, "/reset/last_8?tournament=" + tournament));
        stages.add(new StageView("Semi Finals", "Edit last_4.csv picks, then generate semi-final matchups.",
                status(last4MatchExists, last4PredExists && !last4MatchExists),
                last4PredExists && !last4MatchExists, "last_4",
                last4PredExists, "/edit/last_4?tournament=" + tournament,
                last4MatchExists, "/view/last_4_match?tournament=" + tournament,
                last4PredExists || last4MatchExists, "/reset/last_4?tournament=" + tournament));
        stages.add(new StageView("Final", "Edit final.csv picks, then generate the champion output.",
                status(finalMatchExists, finalPredExists && !finalMatchExists),
                finalPredExists && !finalMatchExists, "final",
                finalPredExists, "/edit/final?tournament=" + tournament,
                finalMatchExists, "/view/final_match?tournament=" + tournament,
                finalPredExists || finalMatchExists, "/reset/final?tournament=" + tournament));
        stages.add(new StageView("Monte Carlo", "Simulate Last 32 onward from the current bracket and show reach-round probabilities.",
                status(simulationExists, last32PredExists && !simulationExists),
                last32PredExists && !simulationExists, "simulate", "Run", "btn-primary",
                false, null,
                simulationExists, "/view/simulation?tournament=" + tournament,
                false, null,
                null,
                simulationExists, "/reset/simulation?tournament=" + tournament));
        return stages;
    }

    private String snapshotDescription(String tournament, boolean snapshotExists) {
        String base = "Freeze ELO ratings and recent history for only this tournament's teams.";
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
            String historyCount = metadata.getProperty("history_file_count", "");
            if (createdAt.isBlank()) {
                return base;
            }
            String refreshed = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(createdAt));
            String counts = "";
            if (!teamCount.isBlank() && !historyCount.isBlank()) {
                counts = " (" + teamCount + " teams, " + historyCount + " history files)";
            }
            return base + " Data files last refreshed " + refreshed + counts + ".";
        } catch (Exception e) {
            return base;
        }
    }

    private StageStatus status(boolean complete, boolean ready) {
        if (complete) return new StageStatus("✅", "Complete", "success");
        if (ready) return new StageStatus("▶", "Ready", "primary");
        return new StageStatus("⬜", "Locked", "secondary");
    }

    private void appendBrowserOnlyMessages(HtmlReporter reporter, String mode, String tournament, boolean lockedBefore) throws IOException {
        if ("start".equals(mode)) {
            if (lockedBefore) {
                reporter.appendWarning("Output already exists: " + predictionFile(tournament, "groups.csv") + " — reset or edit start data to re-run.");
            }
            reporter.appendInfo("groups.csv is ready. Review Group Picks before running the next stage.");
            return;
        }
        if ("groups".equals(mode)) {
            if (lockedBefore) {
                reporter.appendWarning("Output already exists: " + predictionFile(tournament, "last_32.csv") + " — reset or edit Group Picks to re-run.");
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
                matchupFile(tournament, "final.csv"),
                predictionFile(tournament, "simulation_last_32.csv"),
                predictionFile(tournament, "simulation_paths_last_32.csv")
        ));
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
                matchupFile(tournament, "final.csv"),
                predictionFile(tournament, "simulation_last_32.csv"),
                predictionFile(tournament, "simulation_paths_last_32.csv")
        ));
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
        paths.add(predictionFile(tournament, "simulation_last_32.csv"));
        paths.add(predictionFile(tournament, "simulation_paths_last_32.csv"));
        deletePaths(paths);
    }

    private void cascadeDeleteForReset(String tournament, String step) throws IOException {
        if ("simulation".equals(step)) {
            deletePaths(List.of(predictionFile(tournament, "simulation_last_32.csv"),
                predictionFile(tournament, "simulation_paths_last_32.csv")));
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
                    matchupFile(tournament, "final.csv"),
                    predictionFile(tournament, "simulation_last_32.csv"),
                predictionFile(tournament, "simulation_paths_last_32.csv")
            ));
            return;
        }
        cascadeDeleteAfterRoundEdit(tournament, step);
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

    private String preferredWinner(Map<String, String> row) {
        for (String key : List.of("predicted_winner", "prediction", "elo")) {
            String value = trim(row.getOrDefault(key, ""));
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

    private Path outputPathForMode(String tournament, String mode) {
        return switch (mode) {
            case "start" -> predictionFile(tournament, "groups.csv");
            case "groups" -> predictionFile(tournament, "last_32.csv");
            case "last_32" -> matchupFile(tournament, "last_32.csv");
            case "last_16" -> matchupFile(tournament, "last_16.csv");
            case "last_8" -> matchupFile(tournament, "last_8.csv");
            case "last_4" -> matchupFile(tournament, "last_4.csv");
            case "final" -> matchupFile(tournament, "final.csv");
            case "simulate" -> predictionFile(tournament, "simulation_last_32.csv");
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
            case "simulation" -> predictionFile(tournament, "simulation_last_32.csv");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid round");
        };
    }

    private static final List<String> VIEW_ROUND_SEQUENCE = List.of(
        "start", "groups", "last_32_match", "last_16_match", "last_8_match", "last_4_match", "final_match", "simulation"
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
            case "groups"        -> "groups.csv";
            case "last_32_match" -> "last_16.csv";
            case "last_16_match" -> "last_8.csv";
            case "last_8_match"  -> "last_4.csv";
            case "last_4_match"  -> "final.csv";
            default -> null;
        };
    }

    private String nextRunModeForView(String round) {
        return switch (round) {
            case "groups"        -> "last_32";
            case "last_32_match" -> "last_16";
            case "last_16_match" -> "last_8";
            case "last_8_match"  -> "last_4";
            case "last_4_match"  -> "final";
            default -> null;
        };
    }

    private int completedStepCount(String tournament) {
        int count = 0;
        if (Files.exists(predictionFile(tournament, "start.csv"))) count++;
        if (Files.exists(predictionFile(tournament, "groups.csv"))) count++;
        if (Files.exists(predictionFile(tournament, "last_32.csv"))) count++;
        if (Files.exists(matchupFile(tournament, "last_32.csv"))) count++;
        if (Files.exists(matchupFile(tournament, "last_16.csv"))) count++;
        if (Files.exists(matchupFile(tournament, "last_8.csv"))) count++;
        if (Files.exists(matchupFile(tournament, "last_4.csv"))) count++;
        if (Files.exists(matchupFile(tournament, "final.csv"))) count++;
        if (Files.exists(predictionFile(tournament, "simulation_last_32.csv"))) count++;
        return count;
    }

    private String describeCurrentStage(String tournament) {
        if (Files.exists(predictionFile(tournament, "simulation_last_32.csv"))) return "Monte Carlo complete";
        if (Files.exists(matchupFile(tournament, "final.csv"))) return "Final complete";
        if (Files.exists(matchupFile(tournament, "last_4.csv"))) return "Semi-finals complete";
        if (Files.exists(matchupFile(tournament, "last_8.csv"))) return "Last 8 complete";
        if (Files.exists(matchupFile(tournament, "last_16.csv"))) return "Last 16 complete";
        if (Files.exists(matchupFile(tournament, "last_32.csv"))) return "Last 32 complete";
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
            case "snapshot-refresh" -> "Tournament Snapshot";
            case "start" -> "Group Setup";
            case "groups_match" -> "Group Rankings";
            case "groups" -> "Group Picks";
            case "last_32", "last_32_match" -> "Last 32";
            case "last_16", "last_16_match" -> "Last 16";
            case "last_8", "last_8_match" -> "Quarter Finals";
            case "last_4", "last_4_match" -> "Semi Finals";
            case "final", "final_match" -> "Final";
            case "simulate", "simulation" -> "Monte Carlo";
            default -> mode;
        };
    }

    private static String displayTournament(String name) {
        if (name == null || name.isEmpty()) return name;
        return Arrays.stream(name.split("_"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String editRoundForMatchView(String round) {
        return switch (round) {
            case "last_32_match" -> "last_32";
            case "last_16_match" -> "last_16";
            case "last_8_match"  -> "last_8";
            case "last_4_match"  -> "last_4";
            case "final_match"   -> "final";
            default -> null;
        };
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
        return displayMode(round);
    }

    private String redirectToTournament(String tournament) {
        return "redirect:/tournament/" + tournament;
    }

    private String redirectAfterSaveRun(String round, String tournament) {
        return switch (round) {
            case "start"   -> "redirect:/edit/groups?tournament=" + tournament;
            case "groups"  -> "redirect:/edit/last_32?tournament=" + tournament;
            case "last_32" -> "redirect:/edit/last_16?tournament=" + tournament;
            case "last_16" -> "redirect:/edit/last_8?tournament=" + tournament;
            case "last_8"  -> "redirect:/edit/last_4?tournament=" + tournament;
            case "last_4"  -> "redirect:/edit/final?tournament=" + tournament;
            case "final"   -> "redirect:/view/final_match?tournament=" + tournament;
            case "simulate" -> "redirect:/view/simulation?tournament=" + tournament;
            default        -> redirectToTournament(tournament);
        };
    }

    private Path predictionFile(String tournament, String fileName) {
        return projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve(fileName);
    }

    private Path matchupFile(String tournament, String fileName) {
        return projectRoot.resolve("data").resolve("matchups").resolve(tournament).resolve(fileName);
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
        private final int squadQuality;
        private final String dropoutNotes;
        private final String injuryNotes;
        private final String ageNotes;
        private final String cohesionNotes;
        private final String depthNotes;
        private final String qualityNotes;

        public StartRow(String group, String team, boolean host, int injuryImpact,
                        int heatImpact, int squadDropouts, int squadAgeProfile,
                        int squadCohesion, int squadDepth, int squadQuality,
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
            this.squadQuality = squadQuality;
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
        public int getSquadQuality() { return squadQuality; }
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

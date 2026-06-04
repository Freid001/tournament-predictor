package com.tournamentpredictor.web;

import com.tournamentpredictor.service.MatchResolver;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.HtmlReporter;
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
import com.tournamentpredictor.loader.CsvLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
public class WebController {
    private static final Set<String> RUN_MODES = Set.of("start", "groups", "last_32", "last_16", "last_8", "last_4", "final");
    private static final Set<String> ROUND_NAMES = Set.of("groups", "groups_match", "last_32", "last_32_match", "last_16", "last_16_match", "last_8", "last_8_match", "last_4", "last_4_match", "final", "final_match");
    private static final Set<String> RESET_STEPS = Set.of("groups", "last_32", "last_16", "last_8", "last_4", "final");
    private static final CSVFormat CSV = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    private final Path projectRoot = Path.of(System.getProperty("user.dir"));

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
        HtmlReporter reporter = new HtmlReporter();
        boolean hasError = false;

        try {
            Path lockedPath = outputPathForMode(safeTournament, safeMode);
            boolean lockedBefore = lockedPath != null && Files.exists(lockedPath);
            if (lockedBefore && !"start".equals(safeMode) && !"groups".equals(safeMode)) {
                reporter.appendWarning("Output already exists: " + lockedPath + " — delete or reset to re-run.");
            }

            MatchResolver.forWeb(reporter).resolveAndWrite(safeMode, safeTournament);
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
        model.addAttribute("output", reporter.getHtml());
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
                    rows.add(new StartRow(String.valueOf(group), "", false, 0));
                }
            }
        } else {
            for (Map<String, String> row : csvData.rows()) {
                rows.add(new StartRow(
                        row.getOrDefault("group", ""),
                        row.getOrDefault("team", ""),
                        "yes".equalsIgnoreCase(row.getOrDefault("host", "")),
                        parseInt(row.getOrDefault("injury_impact", "0"), 0)
                ));
            }
        }
        model.addAttribute("tournament", safeTournament);
        model.addAttribute("rows", rows);
        model.addAttribute("allTeams", HtmlReporter.getAllTeamNames());
        return "edit-start";
    }

    @PostMapping("/edit/start")
    public String saveStart(@RequestParam("tournament") String tournament, HttpServletRequest request) throws IOException {
        String safeTournament = safeTournament(tournament);
        CsvData existing = readCsv(predictionFile(safeTournament, "start.csv"));
        int rowCount = parseInt(request.getParameter("rowCount"), 0);
        List<String> headers = existing.headers().isEmpty()
                ? new ArrayList<>(List.of("group", "team", "host", "injury_impact"))
                : new ArrayList<>(existing.headers());
        ensureHeaders(headers, List.of("group", "team", "host", "injury_impact"));

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
            rows.add(row);
        }

        if (!duplicateTeams.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Duplicate teams: " + String.join(", ", duplicateTeams));
        }

        writeCsv(predictionFile(safeTournament, "start.csv"), headers, rows);
        cascadeDeleteAfterStart(safeTournament);
        try {
            MatchResolver.forWeb(new HtmlReporter()).resolveAndWrite("start", safeTournament);
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
        model.addAttribute("round", safeRound);
        model.addAttribute("roundLabel", displayMode(safeRound));
        model.addAttribute("groupsMode", "groups".equals(safeRound));

        if ("groups".equals(safeRound)) {
            List<Map<String, String>> rawRows = csvData.rows();
            List<GroupPickRow> rows = new ArrayList<>();
            for (int i = 0; i < rawRows.size(); i++) {
                Map<String, String> row = rawRows.get(i);
                rows.add(new GroupPickRow(i,
                        row.getOrDefault("group", ""),
                        row.getOrDefault("team", ""),
                        row.getOrDefault("predicted_position", ""),
                        row.getOrDefault("group_winner", ""),
                        row.getOrDefault("runner_up", ""),
                        row.getOrDefault("3rd_place", "")
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
                if ("primary".equalsIgnoreCase(r.getOrDefault("path", "primary"))) {
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
            model.addAttribute("prevViewUrl", "/view/" + editPrevRound + "?tournament=" + safeTournament);
            model.addAttribute("prevViewLabel", "View " + displayMode(editPrevRound));
        } else {
            model.addAttribute("hasPrevView", false);
        }

        return "edit-round";
    }

    @PostMapping("/edit/{round}")
    public String saveRound(@PathVariable("round") String round, @RequestParam("tournament") String tournament, HttpServletRequest request) throws IOException {
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
        writeCsv(file, existing.headers(), rows);
        if ("groups".equals(safeRound)) {
            cascadeDeleteAfterGroupsEdit(safeTournament);
        } else {
            cascadeDeleteAfterRoundEdit(safeTournament, safeRound);
        }
        try {
            MatchResolver.forWeb(new HtmlReporter()).resolveAndWrite(safeRound, safeTournament);
        } catch (Exception e) {
            return redirectToTournament(safeTournament);
        }
        return redirectAfterSaveRun(safeRound, safeTournament);
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

        String prevRound = prevViewRound(safeRound);
        boolean prevExists = prevRound != null && Files.exists(roundFileForView(safeTournament, prevRound));
        model.addAttribute("prevViewUrl", prevRound != null ? "/view/" + prevRound + "?tournament=" + safeTournament : "#");
        model.addAttribute("prevViewLabel", prevRound != null ? "Back to " + displayViewMode(prevRound) : "Back to Previous");
        model.addAttribute("prevViewEnabled", prevExists);
        model.addAttribute("hasPrevRound", prevRound != null);

        if (groupsMode) {
            LinkedHashMap<String, List<GroupViewRow>> groupedRows = new LinkedHashMap<>();
            for (Map<String, String> row : csvData.rows()) {
                String group = trim(row.getOrDefault("group", ""));
                if (group.isEmpty()) {
                    continue;
                }
                groupedRows.computeIfAbsent(group, ignored -> new ArrayList<>()).add(new GroupViewRow(
                        row.getOrDefault("team", ""),
                        row.getOrDefault("predicted_position", ""),
                        row.getOrDefault("group_winner", ""),
                        row.getOrDefault("runner_up", ""),
                        row.getOrDefault("3rd_place", "")
                ));
            }
            model.addAttribute("groupedRows", groupedRows);
            return "view-round";
        } else {
            HtmlReporter reporter = new HtmlReporter();
            List<String> lines = java.nio.file.Files.readAllLines(file);
            String oddsColumn = oddsColumnForRound(safeRound);
            Map<String, String> odds = oddsColumn != null ? new CsvLoader(projectRoot).loadOdds(safeTournament, oddsColumn) : Map.of();
            reporter.printMatchups(displayViewMode(safeRound), lines, new EloCalculator(), null, odds);
            model.addAttribute("output", reporter.getHtml());
            model.addAttribute("mode", displayViewMode(safeRound));
            String editRound = editRoundForMatchView(safeRound);
            if (editRound != null) {
                model.addAttribute("editUrl", "/edit/" + editRound + "?tournament=" + safeTournament);
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

        List<StageView> stages = new ArrayList<>();
        stages.add(new StageView("Group Setup", "Edit start.csv teams, host status, and injury levels.",
                status(startExists, !startExists),
                false, null,
                true, "/edit/start?tournament=" + tournament,
                false, null,
                false, null));
        stages.add(new StageView("Group Rankings", "Run start mode to generate groups.csv.",
                status(groupsExists, startExists && !groupsExists),
                startExists && !groupsExists, "start",
                false, null,
                groupsExists, "/view/groups?tournament=" + tournament,
                groupsExists, "/reset/groups?tournament=" + tournament));
        stages.add(new StageView("Group Picks", "Edit groups.csv picks, then generate last_32 predictions.",
                status(last32PredExists, groupsExists && !last32PredExists),
                groupsExists && !last32PredExists, "groups",
                groupsExists, "/edit/groups?tournament=" + tournament,
                last32PredExists, "/view/last_32?tournament=" + tournament,
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
        return stages;
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
                matchupFile(tournament, "final.csv")
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
                matchupFile(tournament, "final.csv")
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
        deletePaths(paths);
    }

    private void cascadeDeleteForReset(String tournament, String step) throws IOException {
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
            default -> null;
        };
    }

    private Path roundFileForView(String tournament, String round) {
        return switch (round) {
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
        "groups", "last_32_match", "last_16_match", "last_8_match", "last_4_match", "final_match"
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
            case "groups" -> "groups";
            case "last_32" -> "groups";
            case "last_16" -> "last_32_match";
            case "last_8"  -> "last_16_match";
            case "last_4"  -> "last_8_match";
            case "final"   -> "last_4_match";
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
        return count;
    }

    private String describeCurrentStage(String tournament) {
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
            case "start", "groups_match" -> "Group Rankings";
            case "groups" -> "Group Picks";
            case "last_32", "last_32_match" -> "Last 32";
            case "last_16", "last_16_match" -> "Last 16";
            case "last_8", "last_8_match" -> "Quarter Finals";
            case "last_4", "last_4_match" -> "Semi Finals";
            case "final", "final_match" -> "Final";
            default -> mode;
        };
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
            case "last_32_match" -> "last_32";
            case "last_16_match" -> "last_16";
            case "last_8_match"  -> "last_8";
            case "last_4_match"  -> "last_4";
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
            case "groups"  -> "redirect:/edit/last_32?tournament=" + tournament;
            case "last_32" -> "redirect:/edit/last_16?tournament=" + tournament;
            case "last_16" -> "redirect:/edit/last_8?tournament=" + tournament;
            case "last_8"  -> "redirect:/edit/last_4?tournament=" + tournament;
            case "last_4"  -> "redirect:/edit/final?tournament=" + tournament;
            case "final"   -> "redirect:/view/final_match?tournament=" + tournament;
            default        -> redirectToTournament(tournament);
        };
    }

    private Path predictionFile(String tournament, String fileName) {
        return projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve(fileName);
    }

    private Path matchupFile(String tournament, String fileName) {
        return projectRoot.resolve("data").resolve("matchups").resolve(tournament).resolve(fileName);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
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
        private final String stage;
        private final int completedSteps;

        public TournamentSummary(String name, String stage, int completedSteps) {
            this.name = name;
            this.stage = stage;
            this.completedSteps = completedSteps;
        }

        public String getName() { return name; }
        public String getStage() { return stage; }
        public int getCompletedSteps() { return completedSteps; }
    }

    public static final class StageView {
        private final String label;
        private final String description;
        private final StageStatus status;
        private final boolean canRun;
        private final String runMode;
        private final boolean canEdit;
        private final String editUrl;
        private final boolean canView;
        private final String viewUrl;
        private final boolean canReset;
        private final String resetUrl;

        public StageView(String label, String description, StageStatus status, boolean canRun, String runMode, boolean canEdit, String editUrl, boolean canView, String viewUrl, boolean canReset, String resetUrl) {
            this.label = label;
            this.description = description;
            this.status = status;
            this.canRun = canRun;
            this.runMode = runMode;
            this.canEdit = canEdit;
            this.editUrl = editUrl;
            this.canView = canView;
            this.viewUrl = viewUrl;
            this.canReset = canReset;
            this.resetUrl = resetUrl;
        }

        public String getLabel() { return label; }
        public String getDescription() { return description; }
        public StageStatus getStatus() { return status; }
        public boolean isCanRun() { return canRun; }
        public String getRunMode() { return runMode; }
        public boolean isCanEdit() { return canEdit; }
        public String getEditUrl() { return editUrl; }
        public boolean isCanView() { return canView; }
        public String getViewUrl() { return viewUrl; }
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

        public StartRow(String group, String team, boolean host, int injuryImpact) {
            this.group = group;
            this.team = team;
            this.host = host;
            this.injuryImpact = injuryImpact;
        }

        public String getGroup() { return group; }
        public String getTeam() { return team; }
        public String getTeamFlagHtml() { return HtmlReporter.flagHtml(team); }
        public boolean isHost() { return host; }
        public int getInjuryImpact() { return injuryImpact; }
    }

    public static final class GroupPickRow {
        private final int rowIndex;
        private final String group;
        private final String team;
        private final String predictedPosition;
        private final String groupWinner;
        private final String runnerUp;
        private final String thirdPlace;

        public GroupPickRow(int rowIndex, String group, String team, String predictedPosition, String groupWinner, String runnerUp, String thirdPlace) {
            this.rowIndex = rowIndex;
            this.group = group;
            this.team = team;
            this.predictedPosition = predictedPosition;
            this.groupWinner = groupWinner;
            this.runnerUp = runnerUp;
            this.thirdPlace = thirdPlace;
        }

        public int getRowIndex() { return rowIndex; }
        public String getGroup() { return group; }
        public String getTeam() { return team; }
        public String getTeamHtml() { return HtmlReporter.flagHtml(team) + escapeHtml(team); }
        public String getPredictedPosition() { return predictedPosition; }
        public String getGroupWinner() { return groupWinner; }
        public String getRunnerUp() { return runnerUp; }
        public String getThirdPlace() { return thirdPlace; }
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
    }

    public static final class GroupViewRow {
        private final String team;
        private final String predictedPosition;
        private final String groupWinner;
        private final String runnerUp;
        private final String thirdPlace;

        public GroupViewRow(String team, String predictedPosition, String groupWinner, String runnerUp, String thirdPlace) {
            this.team = team;
            this.predictedPosition = predictedPosition;
            this.groupWinner = groupWinner;
            this.runnerUp = runnerUp;
            this.thirdPlace = thirdPlace;
        }

        public String getTeamHtml() { return HtmlReporter.flagHtml(team) + escapeHtml(team); }
        public String getPredictedPosition() { return predictedPosition; }
        public String getGroupWinner() { return groupWinner; }
        public String getRunnerUp() { return runnerUp; }
        public String getThirdPlace() { return thirdPlace; }

        public String getPositionBadgeHtml() {
            int pos = 1;
            if (predictedPosition != null && !predictedPosition.isBlank()) {
                try { pos = Integer.parseInt(predictedPosition.trim().split("[^0-9]")[0]); } catch (NumberFormatException ignored) {}
            }
            String label = switch (pos) {
                case 1 -> "1st — Topped Group";
                case 2 -> "2nd — Runner-up";
                case 3 -> "3rd — Best 3rd";
                default -> pos + "th";
            };
            String colour = switch (pos) {
                case 1 -> "text-bg-success";
                case 2 -> "text-bg-primary";
                case 3 -> "text-bg-warning";
                default -> "text-bg-secondary";
            };
            String tooltip = "Predicted group stage finishing position. 1st = topped group and enters as group winner, 2nd = runner-up, 3rd = may qualify as one of the best third-place finishers.";
            return "<span class=\"badge " + colour + "\" title=\"" + tooltip + "\">" + escapeHtml(label) + "</span>";
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

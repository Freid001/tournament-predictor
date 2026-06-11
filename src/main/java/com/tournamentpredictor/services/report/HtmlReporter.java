package com.tournamentpredictor.services.report;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.ExpectedGoalsCalculator;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.web.PredictionLabelService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HtmlReporter extends ConsoleReporter {
    private static final Map<String, String> ISO_CODES = new HashMap<>();
    private PathFatigueCalculator pathFatigueCalculator = new PathFatigueCalculator();

    static {
        ISO_CODES.put("Afghanistan", "af");
        ISO_CODES.put("Albania", "al");
        ISO_CODES.put("Algeria", "dz");
        ISO_CODES.put("American Samoa", "as");
        ISO_CODES.put("Andorra", "ad");
        ISO_CODES.put("Angola", "ao");
        ISO_CODES.put("Antigua and Barbuda", "ag");
        ISO_CODES.put("Argentina", "ar");
        ISO_CODES.put("Armenia", "am");
        ISO_CODES.put("Aruba", "aw");
        ISO_CODES.put("Australia", "au");
        ISO_CODES.put("Austria", "at");
        ISO_CODES.put("Azerbaijan", "az");
        ISO_CODES.put("Bahamas", "bs");
        ISO_CODES.put("Bahrain", "bh");
        ISO_CODES.put("Bangladesh", "bd");
        ISO_CODES.put("Barbados", "bb");
        ISO_CODES.put("Belarus", "by");
        ISO_CODES.put("Belgium", "be");
        ISO_CODES.put("Belize", "bz");
        ISO_CODES.put("Benin", "bj");
        ISO_CODES.put("Bermuda", "bm");
        ISO_CODES.put("Bhutan", "bt");
        ISO_CODES.put("Bolivia", "bo");
        ISO_CODES.put("Bosnia & Herzegovina", "ba");
        ISO_CODES.put("Bosnia and Herzegovina", "ba");
        ISO_CODES.put("Botswana", "bw");
        ISO_CODES.put("Brazil", "br");
        ISO_CODES.put("British Virgin Islands", "vg");
        ISO_CODES.put("Brunei", "bn");
        ISO_CODES.put("Bulgaria", "bg");
        ISO_CODES.put("Burkina Faso", "bf");
        ISO_CODES.put("Burundi", "bi");
        ISO_CODES.put("Cambodia", "kh");
        ISO_CODES.put("Cameroon", "cm");
        ISO_CODES.put("Canada", "ca");
        ISO_CODES.put("Cape Verde", "cv");
        ISO_CODES.put("Cayman Islands", "ky");
        ISO_CODES.put("Central African Republic", "cf");
        ISO_CODES.put("Chad", "td");
        ISO_CODES.put("Chile", "cl");
        ISO_CODES.put("China", "cn");
        ISO_CODES.put("Chinese Taipei", "tw");
        ISO_CODES.put("Colombia", "co");
        ISO_CODES.put("Comoros", "km");
        ISO_CODES.put("Congo", "cg");
        ISO_CODES.put("Congo DR", "cd");
        ISO_CODES.put("Cook Islands", "ck");
        ISO_CODES.put("Costa Rica", "cr");
        ISO_CODES.put("Croatia", "hr");
        ISO_CODES.put("Cuba", "cu");
        ISO_CODES.put("Curaçao", "cw");
        ISO_CODES.put("Curacao", "cw");
        ISO_CODES.put("Cyprus", "cy");
        ISO_CODES.put("Czech Republic", "cz");
        ISO_CODES.put("Czechia", "cz");
        ISO_CODES.put("Denmark", "dk");
        ISO_CODES.put("Djibouti", "dj");
        ISO_CODES.put("Dominica", "dm");
        ISO_CODES.put("Dominican Republic", "do");
        ISO_CODES.put("DR Congo", "cd");
        ISO_CODES.put("Ecuador", "ec");
        ISO_CODES.put("Egypt", "eg");
        ISO_CODES.put("El Salvador", "sv");
        ISO_CODES.put("England", "gb-eng");
        ISO_CODES.put("Equatorial Guinea", "gq");
        ISO_CODES.put("Eritrea", "er");
        ISO_CODES.put("Estonia", "ee");
        ISO_CODES.put("Eswatini", "sz");
        ISO_CODES.put("Ethiopia", "et");
        ISO_CODES.put("Faroe Islands", "fo");
        ISO_CODES.put("Fiji", "fj");
        ISO_CODES.put("Finland", "fi");
        ISO_CODES.put("France", "fr");
        ISO_CODES.put("Gabon", "ga");
        ISO_CODES.put("Gambia", "gm");
        ISO_CODES.put("Georgia", "ge");
        ISO_CODES.put("Germany", "de");
        ISO_CODES.put("Ghana", "gh");
        ISO_CODES.put("Gibraltar", "gi");
        ISO_CODES.put("Greece", "gr");
        ISO_CODES.put("Grenada", "gd");
        ISO_CODES.put("Guam", "gu");
        ISO_CODES.put("Guatemala", "gt");
        ISO_CODES.put("Guinea", "gn");
        ISO_CODES.put("Guinea-Bissau", "gw");
        ISO_CODES.put("Guyana", "gy");
        ISO_CODES.put("Haiti", "ht");
        ISO_CODES.put("Hong Kong", "hk");
        ISO_CODES.put("Honduras", "hn");
        ISO_CODES.put("Hungary", "hu");
        ISO_CODES.put("Iceland", "is");
        ISO_CODES.put("India", "in");
        ISO_CODES.put("Indonesia", "id");
        ISO_CODES.put("Iran", "ir");
        ISO_CODES.put("Iraq", "iq");
        ISO_CODES.put("Israel", "il");
        ISO_CODES.put("Italy", "it");
        ISO_CODES.put("Ivory Coast", "ci");
        ISO_CODES.put("Jamaica", "jm");
        ISO_CODES.put("Japan", "jp");
        ISO_CODES.put("Jordan", "jo");
        ISO_CODES.put("Kazakhstan", "kz");
        ISO_CODES.put("Kenya", "ke");
        ISO_CODES.put("Kosovo", "xk");
        ISO_CODES.put("Kuwait", "kw");
        ISO_CODES.put("Kyrgyzstan", "kg");
        ISO_CODES.put("Laos", "la");
        ISO_CODES.put("Latvia", "lv");
        ISO_CODES.put("Lebanon", "lb");
        ISO_CODES.put("Lesotho", "ls");
        ISO_CODES.put("Liberia", "lr");
        ISO_CODES.put("Libya", "ly");
        ISO_CODES.put("Liechtenstein", "li");
        ISO_CODES.put("Lithuania", "lt");
        ISO_CODES.put("Luxembourg", "lu");
        ISO_CODES.put("Macau", "mo");
        ISO_CODES.put("Madagascar", "mg");
        ISO_CODES.put("Malawi", "mw");
        ISO_CODES.put("Malaysia", "my");
        ISO_CODES.put("Maldives", "mv");
        ISO_CODES.put("Mali", "ml");
        ISO_CODES.put("Malta", "mt");
        ISO_CODES.put("Mauritania", "mr");
        ISO_CODES.put("Mauritius", "mu");
        ISO_CODES.put("Mexico", "mx");
        ISO_CODES.put("Moldova", "md");
        ISO_CODES.put("Monaco", "mc");
        ISO_CODES.put("Mongolia", "mn");
        ISO_CODES.put("Montenegro", "me");
        ISO_CODES.put("Montserrat", "ms");
        ISO_CODES.put("Morocco", "ma");
        ISO_CODES.put("Mozambique", "mz");
        ISO_CODES.put("Myanmar", "mm");
        ISO_CODES.put("Namibia", "na");
        ISO_CODES.put("Nepal", "np");
        ISO_CODES.put("Netherlands", "nl");
        ISO_CODES.put("New Caledonia", "nc");
        ISO_CODES.put("New Zealand", "nz");
        ISO_CODES.put("Nicaragua", "ni");
        ISO_CODES.put("Niger", "ne");
        ISO_CODES.put("Nigeria", "ng");
        ISO_CODES.put("North Korea", "kp");
        ISO_CODES.put("North Macedonia", "mk");
        ISO_CODES.put("Northern Ireland", "gb-nir");
        ISO_CODES.put("Norway", "no");
        ISO_CODES.put("Oman", "om");
        ISO_CODES.put("Pakistan", "pk");
        ISO_CODES.put("Palestine", "ps");
        ISO_CODES.put("Panama", "pa");
        ISO_CODES.put("Papua New Guinea", "pg");
        ISO_CODES.put("Paraguay", "py");
        ISO_CODES.put("Peru", "pe");
        ISO_CODES.put("Philippines", "ph");
        ISO_CODES.put("Poland", "pl");
        ISO_CODES.put("Portugal", "pt");
        ISO_CODES.put("Puerto Rico", "pr");
        ISO_CODES.put("Qatar", "qa");
        ISO_CODES.put("Republic of Ireland", "ie");
        ISO_CODES.put("Romania", "ro");
        ISO_CODES.put("Russia", "ru");
        ISO_CODES.put("Rwanda", "rw");
        ISO_CODES.put("Saint Kitts and Nevis", "kn");
        ISO_CODES.put("Saint Lucia", "lc");
        ISO_CODES.put("Saint Vincent and the Grenadines", "vc");
        ISO_CODES.put("Samoa", "ws");
        ISO_CODES.put("San Marino", "sm");
        ISO_CODES.put("São Tomé and Príncipe", "st");
        ISO_CODES.put("Saudi Arabia", "sa");
        ISO_CODES.put("Scotland", "gb-sct");
        ISO_CODES.put("Senegal", "sn");
        ISO_CODES.put("Serbia", "rs");
        ISO_CODES.put("Seychelles", "sc");
        ISO_CODES.put("Sierra Leone", "sl");
        ISO_CODES.put("Singapore", "sg");
        ISO_CODES.put("Slovakia", "sk");
        ISO_CODES.put("Slovenia", "si");
        ISO_CODES.put("Solomon Islands", "sb");
        ISO_CODES.put("Somalia", "so");
        ISO_CODES.put("South Africa", "za");
        ISO_CODES.put("South Korea", "kr");
        ISO_CODES.put("South Sudan", "ss");
        ISO_CODES.put("Spain", "es");
        ISO_CODES.put("Sri Lanka", "lk");
        ISO_CODES.put("Sudan", "sd");
        ISO_CODES.put("Suriname", "sr");
        ISO_CODES.put("Sweden", "se");
        ISO_CODES.put("Switzerland", "ch");
        ISO_CODES.put("Syria", "sy");
        ISO_CODES.put("Tahiti", "pf");
        ISO_CODES.put("Tajikistan", "tj");
        ISO_CODES.put("Tanzania", "tz");
        ISO_CODES.put("Thailand", "th");
        ISO_CODES.put("Timor-Leste", "tl");
        ISO_CODES.put("Togo", "tg");
        ISO_CODES.put("Tonga", "to");
        ISO_CODES.put("Trinidad and Tobago", "tt");
        ISO_CODES.put("Tunisia", "tn");
        ISO_CODES.put("Turkey", "tr");
        ISO_CODES.put("Turkmenistan", "tm");
        ISO_CODES.put("Turks and Caicos Islands", "tc");
        ISO_CODES.put("UAE", "ae");
        ISO_CODES.put("Uganda", "ug");
        ISO_CODES.put("Ukraine", "ua");
        ISO_CODES.put("United Arab Emirates", "ae");
        ISO_CODES.put("United States", "us");
        ISO_CODES.put("Uruguay", "uy");
        ISO_CODES.put("USA", "us");
        ISO_CODES.put("US Virgin Islands", "vi");
        ISO_CODES.put("Uzbekistan", "uz");
        ISO_CODES.put("Vanuatu", "vu");
        ISO_CODES.put("Venezuela", "ve");
        ISO_CODES.put("Vietnam", "vn");
        ISO_CODES.put("Wales", "gb-wls");
        ISO_CODES.put("Yemen", "ye");
        ISO_CODES.put("Zambia", "zm");
        ISO_CODES.put("Zimbabwe", "zw");
    }

    public static List<String> getAllTeamNames() {
        return ISO_CODES.keySet().stream().sorted().collect(java.util.stream.Collectors.toList());
    }

    public static String getIsoCodesJson() {
        StringBuilder sb = new StringBuilder("{");
        ISO_CODES.forEach((team, code) ->
            sb.append("\"").append(team.replace("\"", "\\\"")).append("\":\"").append(code).append("\","));
        if (sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }

    private final StringBuilder html = new StringBuilder();
    private boolean pathFilterScriptAdded = false;
    private String editUrl = null;
    private String pathTournament = "";
    private String pathRound = "";
    private boolean serverPaginationEnabled = false;
    private boolean actualModeEnabled = false;
    private String serverPaginationBaseUrl = "";
    private int serverPaginationPage = 1;
    private int serverPaginationPageCount = 1;
    private int serverPaginationPageSize = 50;
    private String activePathFilter = "all";
    private String activeTeamFilter = "";
    private List<String> availableTeamNames = List.of();
    private Map<String, String> simulationAdvancePct = Map.of();
    private Map<String, String> matchupLikelihoodPct = Map.of();
    private Map<String, String> matchupSimulationRuns = Map.of();
    private Map<String, String> actualScoreLookup = Map.of();
    private Map<String, String> actualResultLookup = Map.of();

    public HtmlReporter() {
        setShowFlags(true);
    }

    public HtmlReporter withConfig(PredictionConfig config) {
        this.pathFatigueCalculator = new PathFatigueCalculator().withConfig(config);
        return this;
    }

    public void setEditUrl(String editUrl) {
        this.editUrl = editUrl;
    }

    public HtmlReporter withPathNavigation(String tournament, String round) {
        this.pathTournament = tournament == null ? "" : tournament;
        this.pathRound = round == null ? "" : round;
        return this;
    }

    public HtmlReporter withServerPagination(String baseUrl, int page, int pageCount, int pageSize) {
        this.serverPaginationEnabled = baseUrl != null && !baseUrl.isBlank();
        this.serverPaginationBaseUrl = baseUrl == null ? "" : baseUrl;
        this.serverPaginationPage = Math.max(1, page);
        this.serverPaginationPageCount = Math.max(1, pageCount);
        this.serverPaginationPageSize = Math.max(1, pageSize);
        return this;
    }

    public HtmlReporter withActualMode(boolean actualModeEnabled) {
        this.actualModeEnabled = actualModeEnabled;
        return this;
    }

    public HtmlReporter withActiveFilters(String pathFilter, String teamFilter) {
        this.activePathFilter = normalizePathFilter(pathFilter);
        this.activeTeamFilter = teamFilter == null ? "" : teamFilter.trim();
        return this;
    }

    public HtmlReporter withTeamNames(List<String> teamNames) {
        this.availableTeamNames = teamNames == null ? List.of() : teamNames;
        return this;
    }

    public HtmlReporter withActualResultScores(Map<String, String> actualScoreLookup) {
        this.actualScoreLookup = actualScoreLookup == null ? Map.of() : actualScoreLookup;
        return this;
    }

    public HtmlReporter withActualResultLabels(Map<String, String> actualResultLookup) {
        this.actualResultLookup = actualResultLookup == null ? Map.of() : actualResultLookup;
        return this;
    }

    public HtmlReporter withSimulationAdvance(Map<String, String> simulationAdvancePct) {
        this.simulationAdvancePct = simulationAdvancePct == null ? Map.of() : simulationAdvancePct;
        return this;
    }

    public HtmlReporter withMatchupLikelihood(Map<String, String> matchupLikelihoodPct) {
        this.matchupLikelihoodPct = matchupLikelihoodPct == null ? Map.of() : matchupLikelihoodPct;
        return this;
    }

    public HtmlReporter withMatchupSimulationRuns(Map<String, String> matchupSimulationRuns) {
        this.matchupSimulationRuns = matchupSimulationRuns == null ? Map.of() : matchupSimulationRuns;
        return this;
    }

    private String normalizePathFilter(String pathFilter) {
        String normalized = pathFilter == null ? "all" : pathFilter.trim().toLowerCase();
        if (normalized.isEmpty() || "both".equals(normalized)) {
            return "all";
        }
        if ("predicted".equals(normalized)) return "prediction";
        if ("actual".equals(normalized)) return "results";
        if ("upset".equals(normalized)) return "alt";
        return Set.of("all", "results", "alt", "prediction", "live").contains(normalized) ? normalized : "all";
    }

    private List<PathFilterButton> pathFilterButtons(String label, List<String[]> tableRows) {
        if (!isGroupTable(label)) {
            return List.of(
                    new PathFilterButton("all", "All"),
                    new PathFilterButton("results", resultsFilterLabel(tableRows)),
                    new PathFilterButton("prediction", "Predicted Matchups"),
                    new PathFilterButton("alt", "Alternative Matchups"));
        }
        boolean hasResults = tableRows.stream().anyMatch(row -> isResultsFilterPath(valueAt(row, 7)));
        boolean hasPrediction = tableRows.stream().anyMatch(row -> isPredictionFilterPath(valueAt(row, 7)));
        List<PathFilterButton> buttons = new ArrayList<>();
        if (hasResults || hasPrediction) {
            buttons.add(new PathFilterButton("all", "All"));
        }
        if (hasResults) {
            buttons.add(new PathFilterButton("results", resultsFilterLabel(tableRows)));
        }
        if (hasPrediction) {
            buttons.add(new PathFilterButton("prediction", "Predicted Matchups"));
        }
        return buttons;
    }

    private boolean isGroupTable(String label) {
        return label != null && label.toLowerCase(java.util.Locale.ROOT).contains("group");
    }

    private String resultsFilterLabel(List<String[]> tableRows) {
        boolean hasFixture = tableRows.stream().anyMatch(row -> "fixture".equalsIgnoreCase(valueAt(row, 7)));
        boolean hasResult = tableRows.stream().anyMatch(row -> {
            String path = valueAt(row, 7);
            return "results".equalsIgnoreCase(path) || "actual".equalsIgnoreCase(path) || "result_upset".equalsIgnoreCase(path);
        });
        if (hasResult) {
            return "Results";
        }
        return hasFixture ? "Fixtures" : "Results";
    }

    private boolean isResultsFilterPath(String path) {
        return "results".equalsIgnoreCase(path)
                || "fixture".equalsIgnoreCase(path)
                || "actual".equalsIgnoreCase(path)
                || "result_upset".equalsIgnoreCase(path);
    }

    private boolean isPredictionFilterPath(String path) {
        return "predicted".equalsIgnoreCase(path)
                || "prediction".equalsIgnoreCase(path)
                || "live".equalsIgnoreCase(path);
    }

    private String pathButtonHtml(String path, String label) {
        String normalizedPath = normalizePathFilter(path);
        boolean active = normalizedPath.equals(activePathFilter);
        boolean actual = "all".equals(normalizedPath) || "results".equals(normalizedPath);
        return new StringBuilder()
                .append("<button type=\"button\" class=\"btn btn-outline-secondary path-btn")
                .append(active ? " active" : "")
                .append("\" data-path=\"").append(normalizedPath)
                .append("\" data-actual=\"").append(actual ? "true" : "false")
                .append("\" onclick=\"filterPath(this)\">")
                .append(label)
                .append("</button>")
                .toString();
    }

    public void appendInfo(String message) {
        appendAlert("info", message);
    }

    public void appendWarning(String message) {
        appendAlert("warning", message);
    }

    public void appendError(String message) {
        appendAlert("danger", message);
    }

    public void appendPre(String text) {
        if (text == null || text.isBlank()) return;
        html.append("<div class=\"output-section mb-4\"><pre class=\"bg-light p-3 rounded\">")
                .append(escapeHtml(text))
                .append("</pre></div>");
    }

    private void appendAlert(String type, String message) {
        if (message == null || message.isBlank()) return;
        html.append("<div class=\"output-section mb-3\"><div class=\"alert alert-")
                .append(type)
                .append(" mb-0\">")
                .append(escapeHtml(message))
                .append("</div></div>");
    }

    @Override
    public void printMatchups(String label, List<String> csvLines, EloCalculator eloCalculator, Path predictionsFile, Map<String, String> teamOdds) {
        printMatchups(label, csvLines, eloCalculator, predictionsFile, teamOdds, Map.of());
    }

    public void printMatchups(String label, List<String> csvLines, EloCalculator eloCalculator, Path predictionsFile, Map<String, String> teamOdds, Map<String, EloBreakdown> eloBreakdowns) {
        if (csvLines == null || csvLines.isEmpty()) {
            return;
        }

        String[] headers = csvLines.get(0).split(",", -1);
        int matchIdIdx = indexOf(headers, "match_id");
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int pathIdx = indexOf(headers, "path");
        int predIdx = indexOf(headers, "prediction");
        int team1PathDiffIdx = indexOf(headers, "team1_path_fatigue");
        int team2PathDiffIdx = indexOf(headers, "team2_path_fatigue");
        int team1PathOppIdx = indexOf(headers, "team1_path_opponent");
        int team2PathOppIdx = indexOf(headers, "team2_path_opponent");
        int modelPredictionIdx = indexOf(headers, "model_prediction");
        int selectionSourceIdx = indexOf(headers, "selection_source");
        int homeScoreIdx = indexOf(headers, "home_score");
        int awayScoreIdx = indexOf(headers, "away_score");
        if (homeScoreIdx < 0) homeScoreIdx = indexOf(headers, "team1_score");
        if (awayScoreIdx < 0) awayScoreIdx = indexOf(headers, "team2_score");
        if (predIdx < 0) predIdx = indexOf(headers, "predicted_winner");
        if (predIdx < 0) predIdx = indexOf(headers, "elo");

        if (team1Idx < 0 || team2Idx < 0 || predIdx < 0) {
            appendPre(String.join(System.lineSeparator(), csvLines));
            return;
        }

        Map<String, Integer> primaryMatchIdCount = new HashMap<>();
        for (int i = 1; i < csvLines.size(); i++) {
            String[] cols = csvLines.get(i).split(",", -1);
            String rowPath = valueAt(cols, pathIdx);
            boolean isPrimary = pathIdx < 0 || "predicted".equalsIgnoreCase(rowPath);
            if (!isPrimary) continue;
            String matchId = valueAt(cols, matchIdIdx);
            if (!matchId.isEmpty()) primaryMatchIdCount.merge(matchId, 1, Integer::sum);
        }

        List<String[]> rowsToPrint = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 1; i < csvLines.size(); i++) {
            String line = csvLines.get(i);
            if (line.trim().isEmpty()) continue;
            String[] cols = line.split(",", -1);
            String rowPath = valueAt(cols, pathIdx);
            if (rowPath.isBlank() && cols.length > 7) {
                rowPath = valueAt(cols, 7);
            }
            boolean isPrimary = pathIdx < 0 || "predicted".equalsIgnoreCase(rowPath);

            String team1 = eloCalculator.extractTeamName(valueAt(cols, team1Idx));
            String team2 = eloCalculator.extractTeamName(valueAt(cols, team2Idx));
            String filter = getTeamFilter();
            if (filter != null && !filter.isEmpty()) {
                String f = filter.toLowerCase();
                if (!team1.toLowerCase().contains(f) && !team2.toLowerCase().contains(f)) continue;
            }

            String matchId = valueAt(cols, matchIdIdx);
            String key = matchId + "|" + team1 + "|" + team2 + "|" + rowPath;
            if (seen.contains(key)) continue;
            seen.add(key);
            rowsToPrint.add(cols);
        }

        html.append("<div class=\"output-section mb-4\" data-server-paging=\"")
                .append(serverPaginationEnabled ? "true" : "false")
                .append("\" data-actual-mode=\"")
                .append(actualModeEnabled ? "true" : "false")
                .append("\">");
        appendSummary(primaryMatchIdCount.size());
        appendPathFilterScript();

        boolean hasOdds = false;
        String stage = stageFromLabel(label);
        List<String[]> tableRows = new ArrayList<>();
        for (String[] cols : rowsToPrint) {
            String matchId = valueAt(cols, matchIdIdx);
            String rawTeam1 = valueAt(cols, team1Idx);
            String rawTeam2 = valueAt(cols, team2Idx);
            String team1 = eloCalculator.extractTeamName(rawTeam1);
            String team2 = eloCalculator.extractTeamName(rawTeam2);
            String pred = valueAt(cols, predIdx);
            String winner = eloCalculator.parseTeamFromPrediction(pred);
            int pct = eloCalculator.parsePctFromPrediction(pred);
            String modelPrediction = valueAt(cols, modelPredictionIdx);
            String modelWinner = eloCalculator.parseTeamFromPrediction(modelPrediction);
            String modelPct = modelPrediction.isBlank() ? "" : String.valueOf(eloCalculator.parsePctFromPrediction(modelPrediction));
            String selectionSource = valueAt(cols, selectionSourceIdx);
            String homeScore = valueAt(cols, homeScoreIdx);
            String awayScore = valueAt(cols, awayScoreIdx);
            String rowPath = valueAt(cols, pathIdx);
            if (rowPath.isBlank() && cols.length > 7) {
                rowPath = valueAt(cols, 7);
            }
            boolean isPrimary = pathIdx < 0 || "predicted".equalsIgnoreCase(rowPath);
            boolean isActual = "results".equalsIgnoreCase(rowPath)
                    || "actual".equalsIgnoreCase(rowPath)
                    || "fixture".equalsIgnoreCase(rowPath);
            boolean isObservedResult = isActual || "result_upset".equalsIgnoreCase(rowPath);
            boolean isLive = "live".equalsIgnoreCase(rowPath);
            boolean isAlt = !isPrimary && !isLive;
            String displayPath = isActual ? ("fixture".equalsIgnoreCase(rowPath) ? "fixture" : "results")
                    : isLive ? "live"
                    : isPrimary ? "predicted"
                    : "result_upset".equalsIgnoreCase(rowPath) ? "result_upset"
                    : "upset".equalsIgnoreCase(rowPath) ? "upset" : "alt";
            String suffix = isAlt ? "Alt" : "";
            String team1PathDiff = valueAt(cols, team1PathDiffIdx);
            String team2PathDiff = valueAt(cols, team2PathDiffIdx);
            String team1PathOpp = valueAt(cols, team1PathOppIdx);
            String team2PathOpp = valueAt(cols, team2PathOppIdx);
            RowAdvanceProjection rowProjection = rowAdvanceProjection(team1, team2, team1PathDiff, team2PathDiff,
                    team1PathOpp, team2PathOpp, eloBreakdowns, pathFatigueCalculator);
            String oddsCell = "";
            String netWinningsCell = "";
            String team1SimPct = simulationAdvancePct.getOrDefault(team1, "");
            String team2SimPct = simulationAdvancePct.getOrDefault(team2, "");
            String simWinner = "";
            String simWinnerPct = "";
            String matchupKey = matchId + "|" + team1 + "|" + team2;
            String matchupLikelihood = matchupLikelihoodPct.getOrDefault(matchupKey, "");
            if (actualModeEnabled && isObservedResult) {
                matchupLikelihood = "100.0";
            }
            String matchupRuns = matchupSimulationRuns.getOrDefault(matchupKey,
                    matchupSimulationRuns.getOrDefault(matchId + "|" + team2 + "|" + team1, ""));
            if (!team1SimPct.isBlank() || !team2SimPct.isBlank()) {
                double team1Sim = parseDoubleOrZero(team1SimPct);
                double team2Sim = parseDoubleOrZero(team2SimPct);
                simWinner = team1Sim >= team2Sim ? team1 : team2;
                simWinnerPct = team1Sim >= team2Sim ? team1SimPct : team2SimPct;
            }
            if (teamOdds != null && !teamOdds.isEmpty()) {
                String oddsTeam = !simWinner.isBlank() ? simWinner : (!rowProjection.winner().isBlank() ? rowProjection.winner() : winner);
                String odds = teamOdds.get(oddsTeam);
                if (odds != null && !odds.isEmpty()) {
                    oddsCell = odds;
                    netWinningsCell = calcNetWinnings(odds, 10.0);
                    hasOdds = true;
                }
            }
            tableRows.add(new String[]{matchId, team1, team2, suffix, winner, String.valueOf(pct), oddsCell, displayPath, netWinningsCell,
                    positionTag(rawTeam1), positionTag(rawTeam2),
                    team1PathDiff, team2PathDiff,
                    team1PathOpp, team2PathOpp,
                    originSlot(rawTeam1), originSlot(rawTeam2),
                    simWinner, simWinnerPct, team1SimPct, team2SimPct,
                    rowProjection.winner(), rowProjection.winnerPct(), rowProjection.team1Pct(), rowProjection.team2Pct(),
                    matchupLikelihood, selectionSource, modelWinner, modelPct, matchupRuns, homeScore, awayScore});
        }

        long altCount = tableRows.stream().filter(r -> "alt".equals(r[7]) || "upset".equals(r[7])).count();
        long actualCount = tableRows.stream().filter(r -> "results".equals(r[7]) || "fixture".equals(r[7])).count();
        List<PathFilterButton> pathButtons = pathFilterButtons(label, tableRows);
        boolean showToggle = pathButtons.size() > 1;
        boolean roundOf32 = label != null && (label.contains("Last 32") || label.contains("Round of 32"));
        boolean hasSimulationAdvance = tableRows.stream().anyMatch(r -> (r.length > 17 && !r[17].isBlank()) || (r.length > 21 && !r[21].isBlank()));
        boolean showWinnerColumn = !hasSimulationAdvance;
        boolean showOdds = false;
        boolean hasMatchupLikelihood = tableRows.stream().anyMatch(r -> r.length > 25 && !r[25].isBlank());
        boolean hasActualRows = tableRows.stream().anyMatch(r -> r.length > 7 && ("results".equalsIgnoreCase(r[7]) || "fixture".equalsIgnoreCase(r[7]) || "actual".equalsIgnoreCase(r[7]) || "result_upset".equalsIgnoreCase(r[7])));
        if (hasMatchupLikelihood) {
            java.util.Comparator<String[]> likelihoodSort = java.util.Comparator
                    .comparingDouble((String[] row) -> parseDoubleOrZero(row[25]))
                    .reversed();
            java.util.Comparator<String[]> pathSort = java.util.Comparator.comparingInt((String[] row) -> {
                String path = row.length > 7 ? row[7] : "";
                if (actualModeEnabled && hasActualRows) {
                    return ("results".equalsIgnoreCase(path) || "fixture".equalsIgnoreCase(path) || "actual".equalsIgnoreCase(path) || "result_upset".equalsIgnoreCase(path)) ? 0
                            : "live".equalsIgnoreCase(path) ? 1
                            : "predicted".equalsIgnoreCase(path) ? 2
                            : ("alt".equalsIgnoreCase(path) || "upset".equalsIgnoreCase(path)) ? 3 : 4;
                }
                return "live".equalsIgnoreCase(path) ? 0
                        : "predicted".equalsIgnoreCase(path) ? 1
                        : ("alt".equalsIgnoreCase(path) || "upset".equalsIgnoreCase(path)) ? 2 : 3;
            });
            tableRows.sort(pathSort.thenComparing(likelihoodSort));
        }
        if (serverPaginationEnabled && tableRows.size() > serverPaginationPageSize) {
            int start = Math.max(0, (serverPaginationPage - 1) * serverPaginationPageSize);
            int end = Math.min(tableRows.size(), start + serverPaginationPageSize);
            tableRows = new ArrayList<>(tableRows.subList(Math.min(start, tableRows.size()), end));
        }

        List<String> teamNames = availableTeamNames.isEmpty()
                ? rowsToPrint.stream()
                        .map(cols -> {
                            String team1 = eloCalculator.extractTeamName(valueAt(cols, team1Idx));
                            String team2 = eloCalculator.extractTeamName(valueAt(cols, team2Idx));
                            return java.util.stream.Stream.of(team1, team2);
                        })
                        .flatMap(java.util.function.Function.identity())
                        .filter(t -> t != null && !t.isEmpty())
                        .distinct()
                        .sorted()
                        .collect(java.util.stream.Collectors.toList())
                : availableTeamNames;

        if (showToggle || !teamNames.isEmpty() || editUrl != null) {
            html.append("<div class=\"d-flex gap-2 align-items-center mb-2 flex-wrap\">");
            if (showToggle) {
                html.append("<div class=\"btn-group btn-group-sm\" role=\"group\">");
                for (PathFilterButton button : pathButtons) {
                    html.append(pathButtonHtml(button.path(), button.label()));
                }
                html.append("</div>");
            }
            if (!teamNames.isEmpty()) {
                html.append("<select class=\"form-select form-select-sm\" style=\"max-width:180px\" onchange=\"filterTeam(this)\">")
                        .append("<option value=\"\"")
                        .append(activeTeamFilter.isEmpty() ? " selected" : "")
                        .append(">All teams</option>");
                for (String t : teamNames) {
                    html.append("<option value=\"").append(escapeHtml(t)).append("\"")
                            .append(t.equals(activeTeamFilter) ? " selected" : "")
                            .append(">").append(escapeHtml(t)).append("</option>");
                }
                html.append("</select>");
            }
            if (editUrl != null) {
                html.append("<a class=\"btn btn-sm btn-outline-secondary ms-auto\" href=\"")
                        .append(escapeHtml(editUrl)).append("\">Edit</a>");
            }
            html.append("</div>");
        }
        html.append("<div class=\"table-responsive\"><table class=\"table table-striped table-hover table-sm align-middle\">");
        html.append("<thead class=\"table-dark\"><tr><th style=\"width:28px\" aria-label=\"Details\"></th><th>Match</th><th>Team 1</th><th>Team 2</th>");
        if (showWinnerColumn) html.append("<th>Winner</th>");
        if (hasSimulationAdvance) html.append("<th>").append(hasActualRows ? "Winner / Result" : "Most Likely Winner").append("</th>");
        if (hasMatchupLikelihood) html.append("<th>Match Likelihood</th>");
        html.append("<th>Path</th>");
        if (showOdds) html.append("<th>").append(escapeHtml(oddsHeaderFor(label))).append("</th><th>Net Winnings (Bet=£10)</th>");
        html.append("</tr></thead><tbody>");

        Map<String, String> actualWinnerByMatch = actualWinnerByMatch(tableRows);
        Map<String, String> actualMatchupByMatch = actualMatchupByMatch(tableRows);
        for (String[] row : tableRows) {
            boolean userPick = row.length > 26 && "user".equalsIgnoreCase(row[26]);
            boolean actualPath = "results".equalsIgnoreCase(row[7]) || "actual".equalsIgnoreCase(row[7]);
            boolean fixturePath = "fixture".equalsIgnoreCase(row[7]);
            boolean resultUpsetPath = actualModeEnabled && "result_upset".equalsIgnoreCase(row[7]);
            boolean resultPath = actualPath || resultUpsetPath;
            boolean livePath = "live".equalsIgnoreCase(row[7]);
            boolean predictedPath = "predicted".equalsIgnoreCase(row[7]);
            String exactResultWinner = resolveActualResult(row[1], row[2], "");
            String resultWinner = !exactResultWinner.isBlank() ? exactResultWinner : actualWinnerByMatch.getOrDefault(row[0], "");
            boolean fixtureWithResult = actualModeEnabled && fixturePath && !exactResultWinner.isBlank() && !"Draw".equalsIgnoreCase(exactResultWinner);
            if (fixtureWithResult) {
                resultPath = true;
            }
            boolean predictionSettled = actualModeEnabled && predictedPath && !resultWinner.isBlank() && !"Draw".equalsIgnoreCase(resultWinner);
            boolean predictionWrong = predictionSettled
                    && (!actualScoreKey(row[1], row[2]).equals(actualMatchupByMatch.getOrDefault(row[0], actualScoreKey(row[1], row[2])))
                    || !resultWinner.equalsIgnoreCase(predictedTeamName(row[4])));
            String rowClass = (resultPath || fixturePath) ? "table-warning"
                    : livePath ? "table-info"
                    : predictedPath ? (predictionSettled ? (predictionWrong ? "table-danger" : "table-success") : "table-primary")
                    : "";
            html.append("<tr class=\"").append(rowClass).append("\" data-path=\"").append(row[7]).append("\"")
                    .append(" data-match=\"").append(escapeHtml(row[0])).append("\"")
                    .append(" data-team1=\"").append(escapeHtml(row[1])).append("\"")
                    .append(" data-team2=\"").append(escapeHtml(row[2])).append("\"")
                    .append(" style=\"cursor:pointer\"")
                    .append(" aria-expanded=\"false\"")
                    .append(" onclick=\"toggleDetail(this)\"")
                    .append(">");
            html.append("<td class=\"text-center text-muted\"><span class=\"expand-icon\" aria-hidden=\"true\"></span></td>");
            html.append("<td>").append(escapeHtml(row[0])).append("</td>");
            html.append("<td>").append(teamCell(row[1]));
            html.append("</td>");
            html.append("<td>").append(teamCell(row[2]));
            html.append("</td>");
            if (showWinnerColumn) {
                html.append("<td>");
                if (resultPath) {
                    appendActualResultCell(html, row[1], row[2], resolveActualResult(row[1], row[2], row[4]), resolveActualScore(row[1], row[2], actualHomeScore(row), actualAwayScore(row)));
                } else {
                    if (userPick) html.append("<span class=\"badge text-bg-warning me-1\">User Pick</span>");
                    html.append("<span class=\"")
                            .append(Integer.parseInt(row[5]) >= 60 ? "fw-semibold text-success" : "fw-semibold text-warning-emphasis")
                            .append("\">")
                            .append(teamCell(row[4]))
                            .append(" (")
                            .append(escapeHtml(row[5]))
                            .append("%)</span>");
                }
                html.append("</td>");
            }
            if (hasSimulationAdvance) {
                html.append("<td>");
                // Match rows must show exactly one direct-match selection. Tournament-wide
                // progression probabilities belong in the summary cards, never in this cell.
                if (resultPath) {
                    appendActualResultCell(html, row[1], row[2], resolveActualResult(row[1], row[2], row[4]), resolveActualScore(row[1], row[2], actualHomeScore(row), actualAwayScore(row)));
                } else {
                    String displayedWinner = userPick ? row[4]
                            : row.length > 21 && !row[21].isBlank() ? row[21]
                            : !row[4].isBlank() ? row[4]
                            : row.length > 17 ? row[17] : "";
                    String displayedPct = userPick ? row[5]
                            : row.length > 22 && !row[22].isBlank() ? row[22]
                            : row[5];
                    if (displayedWinner.isBlank()) {
                        html.append("<span class=\"text-muted small\">No prediction</span>");
                    } else {
                        html.append("<span class=\"fw-semibold\">").append(teamCell(displayedWinner));
                        if (!displayedPct.isBlank()) html.append(" (").append(escapeHtml(displayedPct)).append("%)");
                        html.append("</span>");
                        if (userPick) html.append(" <span class=\"badge text-bg-warning\">User Pick</span>");
                    }
                }
                html.append("</td>");
            }
            if (hasMatchupLikelihood) {
                html.append("<td class=\"fw-semibold\">");
                if (row[25].isBlank()) {
                    html.append("<span class=\"text-muted\">Not observed</span>");
                } else {
                    double likelihood = parseDoubleOrZero(row[25]);
                    if (likelihood > 0 && likelihood < 0.1) {
                        html.append("&lt;0.1%");
                    } else {
                        html.append(escapeHtml(row[25])).append("%");
                    }
                }
                html.append("</td>");
            }
            String pathBadgeClass = (resultPath || fixturePath) ? "text-bg-warning"
                    : livePath ? "text-bg-info"
                    : predictedPath ? (predictionSettled ? (predictionWrong ? "text-bg-danger" : "text-bg-success") : "text-bg-primary")
                    : "text-bg-secondary";
            String pathLabel = PredictionLabelService.combinedLabel(row[7], row[4], resultWinner);
            html.append("<td><span class=\"badge ")
                    .append(pathBadgeClass)
                    .append("\">")
                    .append(pathLabel)
                    .append("</span></td>");
            if (showOdds) {
                html.append("<td>").append(escapeHtml(row[6])).append("</td>");
                int betPct = hasSimulationAdvance && row.length > 18 && !row[18].isBlank()
                        ? (int) Math.round(parseDoubleOrZero(row[18]))
                        : (row.length > 22 && !row[22].isBlank() ? (int) Math.round(parseDoubleOrZero(row[22])) : Integer.parseInt(row[5]));
                String betTag = betCandidateTag(betPct, row[8]);
                html.append("<td class=\"fw-semibold\"><div class=\"d-flex justify-content-between align-items-center gap-2\">")
                    .append("<span>").append(escapeHtml(row[8])).append("</span>");
                if (!betTag.isEmpty()) html.append(betCandidateBadgeHtml(betTag));
                html.append("</div></td>");
            }
            html.append("</tr>");
            // Detail (expandable) row with signal breakdown
            int colSpan = 4 + (showWinnerColumn ? 1 : 0) + (hasSimulationAdvance ? 1 : 0)
                    + (hasMatchupLikelihood ? 1 : 0) + 1 + (showOdds ? 2 : 0);
            appendEloDetailRow(html, row, colSpan, eloCalculator, eloBreakdowns, stage, pathFatigueCalculator);
        }

        html.append("</tbody></table></div>");
        html.append("<div class=\"table-no-results alert alert-light border text-muted py-2\" style=\"display:")
                .append(tableRows.isEmpty() ? "block" : "none")
                .append("\">No results.</div>");
        if (serverPaginationEnabled) {
            String prevUrl = pageUrl(serverPaginationPage - 1);
            String nextUrl = pageUrl(serverPaginationPage + 1);
            html.append("<div class=\"table-pagination d-flex justify-content-center align-items-center gap-2 mt-2\">")
                    .append("<a class=\"btn btn-sm btn-outline-secondary page-prev")
                    .append(serverPaginationPage <= 1 ? " disabled" : "")
                    .append("\" href=\"").append(prevUrl).append("\">Previous</a>")
                    .append("<span class=\"small text-muted page-status\">Page ")
                    .append(serverPaginationPage).append(" of ").append(serverPaginationPageCount)
                    .append("</span>")
                    .append("<a class=\"btn btn-sm btn-outline-secondary page-next")
                    .append(serverPaginationPage >= serverPaginationPageCount ? " disabled" : "")
                    .append("\" href=\"").append(nextUrl).append("\">Next</a>")
                    .append("</div></div>");
        } else {
            html.append("<div class=\"table-pagination d-flex justify-content-center align-items-center gap-2 mt-2\" style=\"display:none\">")
                    .append("<button type=\"button\" class=\"btn btn-sm btn-outline-secondary page-prev\" onclick=\"changeTablePage(this,-1)\">Previous</button>")
                    .append("<span class=\"small text-muted page-status\"></span>")
                    .append("<button type=\"button\" class=\"btn btn-sm btn-outline-secondary page-next\" onclick=\"changeTablePage(this,1)\">Next</button>")
                    .append("</div></div>");
        }
    }

    private Map<String, String> actualWinnerByMatch(List<String[]> tableRows) {
        Map<String, String> winners = new LinkedHashMap<>();
        for (String[] row : tableRows) {
            if (row.length <= 7) continue;
            String path = row[7];
            boolean actualRow = "results".equalsIgnoreCase(path)
                    || "actual".equalsIgnoreCase(path)
                    || "fixture".equalsIgnoreCase(path)
                    || "result_upset".equalsIgnoreCase(path);
            if (!actualModeEnabled || !actualRow) continue;
            String winner = resolveActualResult(row[1], row[2], "");
            if (!winner.isBlank() && !"Draw".equalsIgnoreCase(winner)) {
                winners.putIfAbsent(row[0], winner);
            }
        }
        return winners;
    }

    private Map<String, String> actualMatchupByMatch(List<String[]> tableRows) {
        Map<String, String> matchups = new LinkedHashMap<>();
        for (String[] row : tableRows) {
            if (row.length <= 7) continue;
            String path = row[7];
            boolean actualRow = "results".equalsIgnoreCase(path)
                    || "actual".equalsIgnoreCase(path)
                    || "fixture".equalsIgnoreCase(path)
                    || "result_upset".equalsIgnoreCase(path);
            if (!actualModeEnabled || !actualRow) continue;
            String winner = resolveActualResult(row[1], row[2], "");
            if (!winner.isBlank() && !"Draw".equalsIgnoreCase(winner)) {
                matchups.putIfAbsent(row[0], actualScoreKey(row[1], row[2]));
            }
        }
        return matchups;
    }

    private String pageUrl(int page) {
        if (serverPaginationBaseUrl.isBlank()) return "#";
        String separator = serverPaginationBaseUrl.contains("?") ? "&" : "?";
        return serverPaginationBaseUrl + separator + "page=" + Math.max(1, page);
    }

    public String getHtml() {
        return html.toString();
    }

    public static String flagHtml(String teamName) {
        String code = ISO_CODES.get(teamName);
        if (code == null || code.isBlank()) {
            return "";
        }
        return "<span class=\"fi fi-" + escapeHtml(code) + "\"></span> ";
    }

    private static void appendSignalRow(StringBuilder sb, String signalName, String value, EloCalculator elo) {
        if (value == null || value.isBlank()) return;
        String team = elo.parseTeamFromPrediction(value);
        if (team.isBlank()) return;
        int pct = elo.parsePctFromPrediction(value);
        sb.append("<tr><td class=\"text-muted\">").append(escapeHtml(signalName)).append("</td>")
          .append("<td>").append(flagHtml(team)).append(escapeHtml(team)).append("</td>")
          .append("<td><span class=\"")
          .append(pct >= 60 ? "text-success fw-semibold" : "text-warning-emphasis fw-semibold")
          .append("\">").append(pct).append("%</span></td></tr>");
    }

    private void appendEloDetailRow(StringBuilder html, String[] row, int colSpan,
                                            EloCalculator eloCalculator,
                                            Map<String, EloBreakdown> eloBreakdowns,
                                            String stage, PathFatigueCalculator pathFatigueCalculator) {
        String rowPath = row[7];
        String team1 = row[1];
        String team2 = row[2];
        EloBreakdown b1 = eloBreakdowns.get(team1);
        EloBreakdown b2 = eloBreakdowns.get(team2);
        int team1PathDiffScore = row.length > 11 ? parseIntOrZero(row[11]) : 0;
        int team2PathDiffScore = row.length > 12 ? parseIntOrZero(row[12]) : 0;
        String team1PathOpp = row.length > 13 ? row[13] : "";
        String team2PathOpp = row.length > 14 ? row[14] : "";
        if (b1 != null && (team1PathDiffScore != 0 || !team1PathOpp.isEmpty())) {
            b1 = b1.withPathFatigue(
                    pathFatigueCalculator.eloAdjustmentFromWeighted(team1PathDiffScore),
                    pathFatigueCalculator.label(team1PathDiffScore),
                    team1PathOpp);
        }
        if (b2 != null && (team2PathDiffScore != 0 || !team2PathOpp.isEmpty())) {
            b2 = b2.withPathFatigue(
                    pathFatigueCalculator.eloAdjustmentFromWeighted(team2PathDiffScore),
                    pathFatigueCalculator.label(team2PathDiffScore),
                    team2PathOpp);
        }

        html.append("<tr class=\"detail-row\" data-path=\"").append(rowPath).append("\"")
                .append(" data-team1=\"").append(escapeHtml(team1)).append("\"")
                .append(" data-team2=\"").append(escapeHtml(team2)).append("\"")
                .append(" style=\"display:none\" data-expanded=\"false\">");
        html.append("<td colspan=\"").append(colSpan).append("\" class=\"p-0\">");
        html.append("<div class=\"px-3 py-2\" style=\"background:#f8f9fa;border-bottom:1px solid #dee2e6\">");

        if (b1 != null || b2 != null) {
            String lookupResult = actualResultLookup.getOrDefault(actualScoreKey(team1, team2), "");
            boolean fixtureWithResult = "fixture".equalsIgnoreCase(rowPath)
                    && !lookupResult.isBlank()
                    && !"Draw".equalsIgnoreCase(lookupResult);
            boolean actualResultMode = actualModeEnabled && ("results".equalsIgnoreCase(rowPath)
                    || "actual".equalsIgnoreCase(rowPath)
                    || "result_upset".equalsIgnoreCase(rowPath)
                    || fixtureWithResult);
            String actualResult = lookupResult.isBlank() ? (row.length > 4 ? row[4] : "") : lookupResult;
            String resolvedActualScore = resolveActualScore(team1, team2, actualHomeScore(row), actualAwayScore(row));
            String resolvedHomeScore = actualHomeScore(row);
            String resolvedAwayScore = actualAwayScore(row);
            if ((resolvedHomeScore == null || resolvedHomeScore.isBlank() || resolvedAwayScore == null || resolvedAwayScore.isBlank())
                    && !resolvedActualScore.isBlank() && resolvedActualScore.contains(" - ")) {
                String[] resolvedScoreParts = resolvedActualScore.split(" - ", 2);
                if (resolvedScoreParts.length == 2) {
                    resolvedHomeScore = resolvedScoreParts[0];
                    resolvedAwayScore = resolvedScoreParts[1];
                }
            }
            appendExpectedGoalsPanel(html, team1, b1, row.length > 15 ? row[15] : "", team2, b2, row.length > 16 ? row[16] : "",
                    row.length > 29 ? row[29] : "", actualResultMode, actualResult, resolvedHomeScore, resolvedAwayScore);
            html.append("<div class=\"row g-3 mb-2 w-100 align-items-start\">");
            appendTeamEloBreakdown(html, team1, b1, row.length > 15 ? row[15] : "");
            appendTeamEloBreakdown(html, team2, b2, row.length > 16 ? row[16] : "");
            html.append("</div>");

            boolean hasQual = false;
            boolean hasH2h  = false;
            if (hasQual || hasH2h) {
                html.append("<small class=\"text-muted fw-semibold d-block mb-1\">Other signals:</small>");
                html.append("<table class=\"table table-sm table-bordered mb-0\" style=\"max-width:520px;background:#fff\">");
                html.append("<thead class=\"table-secondary\"><tr><th>Signal</th><th>Favours</th><th>Confidence</th></tr></thead><tbody>");
                html.append("</tbody></table>");
            }
        }

        html.append("</div></td></tr>");
    }

    private static String actualHomeScore(String[] row) {
        return row.length > 30 ? row[30] : "";
    }

    private static String actualAwayScore(String[] row) {
        return row.length > 31 ? row[31] : "";
    }

    private static void appendActualResultCell(StringBuilder html, String team1, String team2, String result, String scoreText) {
        if (result == null || result.isBlank()) {
            html.append("<span class=\"text-muted small\">Actual result unavailable</span>");
            return;
        }
        String score = scoreText == null ? "" : scoreText.trim();
        boolean draw = "Draw".equalsIgnoreCase(result);
        html.append("<span class=\"fw-semibold\">");
        if (draw) {
            html.append(flagHtml(team1)).append(escapeHtml(team1))
                    .append(" / ")
                    .append(flagHtml(team2)).append(escapeHtml(team2));
        } else {
            html.append(flagHtml(result)).append(escapeHtml(result));
        }
        if (!score.isBlank()) {
            html.append(" (").append(escapeHtml(score.replace(" - ", "-"))).append(")");
        }
        html.append("</span>");
    }

    private void appendExpectedGoalsPanel(StringBuilder html, String team1, EloBreakdown b1, String originSlot1,
                                                 String team2, EloBreakdown b2, String originSlot2, String matchupRuns,
                                                 boolean actualResultMode, String actualResult, String homeScore, String awayScore) {
        if (b1 == null || b2 == null) {
            return;
        }
        ExpectedGoalsCalculator.Projection projection = new ExpectedGoalsCalculator()
                .project(team1, team2, b1.totalElo, b2.totalElo,
                        b1.attackQuality, b1.defenceQuality,
                        b2.attackQuality, b2.defenceQuality);
        html.append("<div class=\"border rounded bg-white mb-2 p-2\">");
        html.append("<div class=\"text-muted small fw-semibold mb-2\">Score model</div>");
        html.append("<div class=\"row g-2 align-items-stretch text-center\">");
        appendScoreModelTeamColumn(html, team1, b1, originSlot1, projection.team1ExpectedGoals(),
                projection.team1WinPct(), projection.team1AdvancePct());
        html.append("<div class=\"col-12 col-lg-4 d-flex align-items-stretch\">");
        html.append("<div class=\"w-100 border rounded-3 bg-light px-2 py-3 text-center d-flex flex-column justify-content-center\">");
        if (actualResultMode) {
            String actualScore = formatActualScore(homeScore, awayScore);
            boolean isDraw = isDrawScore(homeScore, awayScore);
            boolean decidedOnPenalties = isDraw && actualResult != null && !actualResult.isBlank() && !"Draw".equalsIgnoreCase(actualResult);
            html.append("<div class=\"text-uppercase text-muted fw-semibold\" style=\"font-size:0.65rem;letter-spacing:.06em\">Actual result</div>");
            html.append("<div class=\"display-6 fw-bold lh-1 my-2\">")
                    .append(escapeHtml(actualScore.isBlank() ? "Score unavailable" : actualScore))
                    .append("</div>");
            if (!actualResult.isBlank() && !"Draw".equalsIgnoreCase(actualResult)) {
                html.append("<div class=\"text-muted mb-1\" style=\"font-size:0.68rem\">")
                        .append("Winner: ").append(flagHtml(actualResult)).append(escapeHtml(actualResult)).append("</div>");
                if (decidedOnPenalties) {
                    html.append("<div class=\"text-muted mb-3\" style=\"font-size:0.68rem\">(Penalties)</div>");
                } else {
                    html.append("<div class=\"mb-3\"></div>");
                }
            } else if (isDraw) {
                html.append("<div class=\"text-muted mb-3\" style=\"font-size:0.68rem\">Draw after 90 min</div>");
            }
            html.append("<div class=\"text-muted mb-0\" style=\"font-size:0.68rem\">Played result from the tournament.</div>");
        } else {
            html.append("<div class=\"text-uppercase text-muted fw-semibold\" style=\"font-size:0.65rem;letter-spacing:.06em\">Most likely score</div>");
            html.append("<div class=\"display-6 fw-bold lh-1 my-2\">")
                    .append(escapeHtml(projection.mostLikelyScoreText().replace("-", " - ")))
                    .append("</div>");
            html.append("<div class=\"text-muted mb-3\" style=\"font-size:0.72rem\">")
                    .append(projection.mostLikelyScorePct()).append("% exact-score likelihood</div>");
            if (!matchupRuns.isBlank()) {
                html.append("<div class=\"text-muted mb-3\" style=\"font-size:0.68rem\">")
                        .append(String.format(java.util.Locale.ROOT, "%,d", parseIntOrZero(matchupRuns)))
                        .append(" simulations for this matchup</div>");
            }
            html.append("<div class=\"d-grid gap-2\">");
            html.append("<div class=\"border rounded-2 bg-white px-2 py-1\">")
                    .append("<div class=\"text-muted\" style=\"font-size:0.65rem\">Draw after 90 min</div>")
                    .append("<div class=\"fw-semibold\">").append(projection.drawPct()).append("%</div></div>");
            html.append("<div class=\"border rounded-2 bg-white px-2 py-1\">")
                    .append("<div class=\"text-muted\" style=\"font-size:0.65rem\">")
                    .append(flagHtml(projection.pick())).append(escapeHtml(projection.pick())).append(" advances</div>")
                    .append("<div class=\"fw-semibold\">").append(projection.pickAdvancePct()).append("%</div></div>");
            html.append("</div>");
        }
        html.append("</div>");
        html.append("</div>");
        appendScoreModelTeamColumn(html, team2, b2, originSlot2, projection.team2ExpectedGoals(),
                projection.team2WinPct(), projection.team2AdvancePct());
        html.append("</div>");
        if (!actualResultMode) {
            html.append("<div class=\"text-muted mt-2\" style=\"font-size:0.72rem\">")
                    .append("Exact-score probabilities are individually low; this is the single most likely score, not a confident result forecast. Simulations sample 90-minute goals; level games are resolved by the Elo-based ET/pens tiebreak.")
                    .append("</div>");
        }
        html.append("</div>");
    }

    private static String actualScoreKey(String team1, String team2) {
        team1 = normalizeActualTeamName(team1);
        team2 = normalizeActualTeamName(team2);
        return team1.compareToIgnoreCase(team2) <= 0 ? team1 + "|" + team2 : team2 + "|" + team1;
    }

    private static String normalizeActualTeamName(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        while (trimmed.matches("^[A-Z0-9]+\\(.+\\)$")) {
            trimmed = trimmed.substring(trimmed.indexOf('(') + 1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static String predictedTeamName(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        int pctStart = trimmed.lastIndexOf(" (");
        if (pctStart > 0 && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(0, pctStart).trim();
        }
        return normalizeActualTeamName(trimmed);
    }

    private static String formatActualScore(String homeScore, String awayScore) {
        if (homeScore == null || awayScore == null) {
            return "";
        }
        String home = homeScore.trim();
        String away = awayScore.trim();
        if (home.isBlank() || away.isBlank()) {
            return "";
        }
        return home + " - " + away;
    }

    private String resolveActualResult(String team1, String team2, String fallbackResult) {
        String actualResult = actualResultLookup.getOrDefault(actualScoreKey(team1, team2), "");
        return actualResult.isBlank() ? fallbackResult : actualResult;
    }

    private String resolveActualScore(String team1, String team2, String homeScore, String awayScore) {
        String direct = formatActualScore(homeScore, awayScore);
        if (!direct.isBlank()) {
            return direct;
        }
        return actualScoreLookup.getOrDefault(actualScoreKey(team1, team2), "");
    }

    private static boolean isDrawScore(String homeScore, String awayScore) {
        if (homeScore == null || awayScore == null) {
            return false;
        }
        String home = homeScore.trim();
        String away = awayScore.trim();
        if (home.isBlank() || away.isBlank()) {
            return false;
        }
        return home.equals(away);
    }

    private static String penaltyLevelClass(int level, int maxLevel) {
        if (level >= maxLevel) return "quality-level-neg-2";
        if (level <= 1) return "quality-level-neg-1";
        return "quality-level-neg-mid";
    }

    private static String benefitLevelClass(int level, int maxLevel) {
        if (level >= maxLevel) return "quality-level-pos-2";
        if (level <= 1) return "quality-level-pos-1";
        return "quality-level-pos-mid";
    }

    private static void appendGoalQualityCard(StringBuilder html, String label, int level) {
        String levelClass = switch (level) {
            case -2 -> "quality-level-neg-2";
            case -1 -> "quality-level-neg-1";
            case 1 -> "quality-level-pos-1";
            case 2 -> "quality-level-pos-2";
            default -> "quality-level-0";
        };
        String value = (level > 0 ? "+" : "") + level;
        String description = switch (level) {
            case -2 -> "Very weak";
            case -1 -> "Weak";
            case 1 -> "Strong";
            case 2 -> "Elite";
            default -> "Average";
        };
        html.append("<div class=\"col-6 p-2 ").append(levelClass).append("\">")
                .append("<div style=\"font-size:0.68rem;opacity:.8\">").append(label).append("</div>")
                .append("<div class=\"fw-bold\">").append(value).append("</div>")
                .append("<div style=\"font-size:0.68rem;opacity:.8\">").append(description).append("</div>")
                .append("</div>");
    }

    private void appendScoreModelTeamColumn(StringBuilder html, String team, EloBreakdown breakdown, String originSlot,
                                                   double expectedGoals, int winPct, int advancePct) {
        html.append("<div class=\"col-12 col-lg-4\">");
        html.append("<div class=\"h-100 border rounded bg-light-subtle p-2\">");
        html.append("<div class=\"fs-4 mb-1\">").append(flagHtml(team)).append("</div>");
        html.append("<div class=\"fw-semibold mb-2\">").append(escapeHtml(team)).append("</div>");
        html.append("<div class=\"row g-1 small\">");
        appendScoreModelStat(html, "xG", String.format(java.util.Locale.ROOT, "%.2f", expectedGoals));
        appendScoreModelStat(html, "90-min W", winPct + "%");
        appendScoreModelStat(html, "Advance", advancePct + "%");
        html.append("</div>");
        appendScoreModelTournamentPath(html, team, breakdown, originSlot);
        html.append("</div>");
        html.append("</div>");
    }

    private void appendScoreModelTournamentPath(StringBuilder html, String team, EloBreakdown b, String originSlot) {
        if (b == null || (b.pathFatigueLabel.isEmpty() && b.pathOpponent.isEmpty()
                && (originSlot == null || originSlot.isBlank()))) {
            return;
        }
        String badgeClass = switch (b.pathFatigueLabel) {
            case "Very Easy", "Easy" -> "bg-success";
            case "Hard", "Very Hard" -> "bg-danger";
            default -> "bg-secondary";
        };
        html.append("<div class=\"border-top mt-2 pt-2\">");
        html.append("<div class=\"text-muted\" style=\"font-size:0.68rem\">Tournament Path</div>");
        if (!b.pathOpponent.isEmpty() || (originSlot != null && !originSlot.isBlank())) {
            String[] segments = b.pathOpponent.isEmpty() ? new String[0] : b.pathOpponent.split(" > ");
            html.append("<div class=\"small\">");
            boolean hasPathItem = false;
            if (originSlot != null && !originSlot.isBlank()) {
                html.append("<span class='text-muted fw-semibold'>").append(escapeHtml(originSlot)).append("</span>");
                hasPathItem = true;
            }
            for (String segment : segments) {
                if ("G".equals(pathFatigueSegmentStage(segment))) continue;
                String name = pathFatigueSegmentName(segment);
                if (name.isEmpty() || "Group stage".equalsIgnoreCase(name)) continue;
                if (hasPathItem) html.append(" <span class='text-muted'>›</span> ");
                if (!pathTournament.isBlank() && !pathRound.isBlank()) {
                    String href = "/view/path-game?tournament=" + urlEncode(pathTournament)
                            + "&round=" + urlEncode(pathRound)
                            + "&team=" + urlEncode(team)
                            + "&opponent=" + urlEncode(name)
                            + "&match=" + urlEncode(pathFatigueSegmentMatchId(segment));
                    html.append("<a class='text-decoration-none path-opponent-link' target='_blank' rel='noopener' href='")
                            .append(escapeHtml(href))
                            .append("' title='Open the earlier ").append(escapeHtml(team)).append(" vs ")
                            .append(escapeHtml(name)).append(" match'>")
                            .append(flagHtml(name)).append(escapeHtml(name))
                            .append(pathFatigueSegmentUpset(segment) ? " (Upset)" : "").append("</a>");
                } else {
                    html.append(flagHtml(name)).append(escapeHtml(name));
                    if (pathFatigueSegmentUpset(segment)) html.append(" (Upset)");
                }
                hasPathItem = true;
            }
            html.append("</div>");
        }
        if (!b.pathFatigueLabel.isEmpty()) {
            html.append("<span class=\"badge ").append(badgeClass).append(" fw-normal mt-1\" style=\"font-size:0.7rem\">")
                    .append(escapeHtml(b.pathFatigueLabel)).append("</span>");
        }
        html.append("</div>");
    }

    private static void appendScoreModelStat(StringBuilder html, String label, String value) {
        html.append("<div class=\"col-4\">")
                .append("<div class=\"text-muted\" style=\"font-size:0.68rem\">")
                .append(escapeHtml(label))
                .append("</div>")
                .append("<div class=\"fw-semibold\">")
                .append(escapeHtml(value))
                .append("</div>")
                .append("</div>");
    }

    private static void appendFormValueWithCircles(StringBuilder html, String value, List<String[]> results, String teamName) {
        html.append("<span class=\"d-inline-flex align-items-center justify-content-end gap-2 flex-wrap\">");
        if (!results.isEmpty()) {
            html.append("<span class='d-inline-flex align-items-center gap-1'>");
            appendFormCircles(html, results, teamName);
            html.append("</span>");
        }
        html.append("<span>").append(escapeHtml(value)).append("</span>");
        html.append("</span>");
    }

    private static void appendFormCircles(StringBuilder html, List<String[]> results, String teamName) {
        for (String[] entry : results) {
            String result   = entry[0];
            String opponent = entry.length > 1 ? entry[1] : "";
            String score    = entry.length > 2 ? entry[2] : "";
            String contribution = entry.length > 3 ? entry[3] : "";
            String date = entry.length > 4 ? entry[4] : "";
            String color = switch (result) {
                case "W" -> "#198754";
                case "D" -> "#adb5bd";
                default  -> "#dc3545";
            };
            String tooltipHtml = flagHtml(teamName) + escapeHtml(teamName)
                    + " vs " + flagHtml(opponent) + escapeHtml(opponent)
                    + "<br>" + escapeHtml(score);
            if (!contribution.isBlank()) {
                tooltipHtml += "<br>ELO " + signedEloText(parseIntOrZero(contribution));
            }
            if (!date.isBlank()) {
                tooltipHtml += "<br>" + escapeHtml(date);
            }
            html.append("<span data-bs-toggle='tooltip' data-bs-html='true' data-bs-title='")
                .append(tooltipHtml.replace("'", "&#39;"))
                .append("' style=\"display:inline-block;width:13px;height:13px;border-radius:50%;background:")
                .append(color).append(";margin:1px;cursor:default\"></span>");
        }
    }

    private static void appendTeamEloBreakdown(StringBuilder html, String teamName, EloBreakdown b, String originSlot) {
        html.append("<div class=\"col\">")
            .append(buildTeamBreakdownHtml(teamName, b, originSlot, false, false))
            .append("</div>");
    }

    /**
     * Builds the inner HTML for a single team's ELO breakdown panel.
     * Used by both the group stage expandable rows (WebController) and the
     * vs matchup detail rows (HtmlReporter). Single source of truth for
     * signal labels, emojis, notes, and table structure.
     */
    public static String buildTeamBreakdownHtml(String teamName, EloBreakdown b) {
        return buildTeamBreakdownHtml(teamName, b, "");
    }

    public static String buildTeamBreakdownHtml(String teamName, EloBreakdown b, String originSlot) {
        return buildTeamBreakdownHtml(teamName, b, originSlot, true, true);
    }

    private static String buildTeamBreakdownHtml(String teamName, EloBreakdown b, String originSlot,
                                                 boolean showHeader, boolean showTournamentPath) {
        StringBuilder sb = new StringBuilder();
        if (showHeader) {
            sb.append("<div class=\"text-center mb-2 fs-6\">")
              .append(flagHtml(teamName))
              .append("<strong>").append(escapeHtml(teamName)).append("</strong>")
              .append("</div>");
        }
        // Tournament path — shown ABOVE qualifiers, only for knockout rounds
        if (showTournamentPath && b != null && !b.pathFatigueLabel.isEmpty()) {
            String badgeClass = switch (b.pathFatigueLabel) {
                case "Very Easy", "Easy" -> "bg-success";
                case "Hard", "Very Hard" -> "bg-danger";
                default -> "bg-secondary";
            };
            sb.append("<div class=\"text-center mb-1\">");
            sb.append("<span class=\"text-muted\" style=\"font-size:0.65rem\">Tournament Path</span><br>");
            if (!b.pathOpponent.isEmpty() || (originSlot != null && !originSlot.isBlank())) {
                String[] segments = b.pathOpponent.isEmpty() ? new String[0] : b.pathOpponent.split(" > ");
                sb.append("<span style=\"font-size:0.75rem\">");
                boolean hasPathItem = false;
                if (originSlot != null && !originSlot.isBlank()) {
                    sb.append("<span class='text-muted fw-semibold'>").append(escapeHtml(originSlot)).append("</span>");
                    hasPathItem = true;
                }
                for (String segment : segments) {
                    if ("G".equals(pathFatigueSegmentStage(segment))) continue;
                    String name = pathFatigueSegmentName(segment);
                    if (name.isEmpty() || "Group stage".equalsIgnoreCase(name)) continue;
                    if (hasPathItem) sb.append(" <span class='text-muted'>›</span> ");
                    sb.append(flagHtml(name)).append(escapeHtml(name));
                    hasPathItem = true;
                }
                sb.append("</span><br>");
            }
            sb.append("<span class=\"badge ").append(badgeClass).append(" fw-normal\" style=\"font-size:0.7rem\">")
              .append(escapeHtml(b.pathFatigueLabel)).append(" ")
              .append(signedEloSpan(b.pathFatigueAdjustment)).append("</span>");
            sb.append("</div>");
        } else if (showTournamentPath && b != null) {
            sb.append("<div class=\"mb-1\"></div>");
        }
        if (b != null) {

            sb.append("<div class=\"border rounded bg-white shadow-sm mb-2\">");
            sb.append("<div class=\"px-2 py-1 border-bottom bg-light text-muted small fw-semibold\">Goal model inputs</div>");
            sb.append("<div class=\"row g-0 text-center\">");
            appendGoalQualityCard(sb, "Attack", b.attackQuality);
            appendGoalQualityCard(sb, "Defence", b.defenceQuality);
            sb.append("</div>");
            sb.append("<div class=\"border-top px-2 py-1 text-muted\" style=\"font-size:0.68rem\">Affects xG, scorelines and match probabilities. Excluded from Adjusted ELO.</div>");
            sb.append("</div>");
            sb.append("<div class=\"border rounded-2 bg-white overflow-hidden shadow-sm elo-calc-card\">");
            sb.append("<div class=\"d-flex justify-content-between align-items-center px-2 py-2 elo-calc-header\">");
            sb.append("<span class=\"small fw-semibold text-uppercase text-muted\" style=\"letter-spacing:.04em\">ELO calculation</span>");
            sb.append("<span class=\"small text-muted\">Base <span class=\"elo-base-pill fw-semibold\">").append(b.baseElo).append("</span></span>");
            sb.append("</div>");
            sb.append("<table class=\"table table-sm mb-0 align-middle elo-calc-table\">");
            sb.append("<tbody>");
            // Path difficulty first
            if (b.pathFatigueAdjustment != 0 || !b.pathOpponent.isEmpty()) {
                String[] pathSegs = b.pathOpponent.isEmpty() ? new String[0] : b.pathOpponent.split(" > ");
                StringBuilder groupHtml = new StringBuilder();
                StringBuilder knockoutHtml = new StringBuilder();
                for (String seg : pathSegs) {
                    String segmentHtml = pathFatigueSegmentHtml(seg);
                    if (segmentHtml.isEmpty()) continue;
                    StringBuilder target = "G".equals(pathFatigueSegmentStage(seg)) ? groupHtml : knockoutHtml;
                    if (target.length() > 0) target.append(", ");
                    target.append(segmentHtml);
                }
                StringBuilder breakdownHtml = new StringBuilder();
                if (groupHtml.length() > 0) {
                    breakdownHtml.append("<span class='fw-semibold'>G:</span> ").append(groupHtml);
                }
                if (knockoutHtml.length() > 0) {
                    if (breakdownHtml.length() > 0) breakdownHtml.append("<br>");
                    breakdownHtml.append("<span class='fw-semibold'>KO:</span> ").append(knockoutHtml);
                }
                sb.append("<tr class=\"").append(b.pathFatigueAdjustment > 0 ? "table-success elo-positive" : "table-danger elo-negative").append("\">");
                sb.append("<td class=\"border-end-0\" style=\"width:58%\"><div class=\"elo-signal-label\">Path Fatigue</div></td>");
                sb.append("<td class=\"text-end border-start-0 elo-value\" style=\"width:42%\">");
                if (breakdownHtml.length() > 0) {
                    sb.append("<span class=\"d-inline-flex align-items-center justify-content-end gap-2 flex-wrap\">");
                    appendPathFatigueChips(sb, pathSegs);
                    sb.append("<span>").append(signedEloText(b.pathFatigueAdjustment)).append("</span>");
                    sb.append("</span>");
                } else {
                    sb.append(signedEloText(b.pathFatigueAdjustment));
                }
                sb.append("</td></tr>");
            }
            // Qualification form is intentionally excluded: base ELO already includes qualifiers.
            // Pre-tournament form uses only the most recent pre-tournament friendlies.
            if (b.preTournamentBonus != 0) {
                sb.append("<tr class=\"").append(b.preTournamentBonus > 0 ? "table-success" : "table-danger").append("\">");
                sb.append("<td class=\"small text-nowrap border-end-0\" style=\"width:48%\">📈 Pre-tournament Form</td>");
                sb.append("<td class=\"text-end fw-bold border-start-0 ").append(b.preTournamentBonus > 0 ? "text-success" : "text-danger").append("\" style=\"width:52%\">");
                appendFormValueWithCircles(sb, signedEloText(b.preTournamentBonus), b.friendlyResults, teamName);
                sb.append("</td></tr>");
            }
            // Rest of squad/situational signals
            if (b.homeBonus != 0)
                sb.append("<tr class=\"quality-level-pos-2\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">🏠 Home Advantage</td><td class=\"text-end fw-bold border-start-0 text-success\" style=\"width:52%\">+").append(b.homeBonus).append("</td></tr>");
            if (b.squadAgePenalty != 0)
                sb.append("<tr class=\"").append(b.squadAgeLevel == 1 ? "quality-level-neg-mid" : "quality-level-neg-1").append("\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">👶 Squad Age</td><td class=\"text-end fw-bold border-start-0 text-warning\" style=\"width:52%\">−").append(b.squadAgePenalty)
                  .append(b.ageNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.ageNotes) + "</span>")
                  .append("</td></tr>");
            if (b.squadCohesionPenalty != 0)
                sb.append("<tr class=\"").append(penaltyLevelClass(b.squadCohesionLevel, 3)).append("\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">🤝 Squad Cohesion</td><td class=\"text-end fw-bold border-start-0 text-danger\" style=\"width:52%\">−").append(b.squadCohesionPenalty)
                  .append(b.cohesionNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.cohesionNotes) + "</span>")
                  .append("</td></tr>");
            if (b.squadDepthPenalty != 0) {
                boolean excellentDepth = b.squadDepthLevel == -1;
                sb.append("<tr class=\"")
                  .append(excellentDepth ? "quality-level-pos-2" : penaltyLevelClass(b.squadDepthLevel, 2))
                  .append("\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">⬇️ Bench Depth</td><td class=\"text-end fw-bold border-start-0\" style=\"width:52%\">")
                  .append(excellentDepth ? "+" + Math.abs(b.squadDepthPenalty) : "−" + b.squadDepthPenalty)
                  .append(b.depthNotes.isEmpty() ? "" : "<br><span class='fw-normal' style='font-size:0.75rem;opacity:.8'>" + escapeHtml(b.depthNotes) + "</span>")
                  .append("</td></tr>");
            }
            if (b.dropoutPenalty != 0)
                sb.append("<tr class=\"").append(penaltyLevelClass(b.dropoutLevel, 3)).append("\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">👤 Squad Omissions</td><td class=\"text-end fw-bold border-start-0 text-danger\" style=\"width:52%\">−").append(b.dropoutPenalty)
                  .append(b.dropoutNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.dropoutNotes) + "</span>")
                  .append("</td></tr>");
            if (b.injuryPenalty != 0)
                sb.append("<tr class=\"").append(penaltyLevelClass(b.injuryLevel, 3)).append("\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">🤕 Injuries</td><td class=\"text-end fw-bold border-start-0 text-danger\" style=\"width:52%\">−").append(b.injuryPenalty)
                  .append(b.injuryNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.injuryNotes) + "</span>")
                  .append("</td></tr>");
            if (b.heatBonus != 0)
                sb.append("<tr class=\"").append(benefitLevelClass(b.heatLevel, 3)).append("\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">🔥 Heat Advantage</td><td class=\"text-end fw-bold border-start-0\" style=\"width:52%\">+").append(b.heatBonus).append("</td></tr>");
            sb.append("<tr class=\"border-top table-light\"><td class=\"fw-bold text-nowrap border-end-0\" style=\"width:48%\">Adjusted ELO</td><td class=\"text-end fw-bold border-start-0 fs-6\" style=\"width:52%\">").append(b.totalElo).append("</td></tr>");
            sb.append("</tbody></table></div>");
        } else {
            sb.append("<div class=\"text-muted small fst-italic\">No breakdown data available.</div>");
        }
        return sb.toString();
    }

    private static void appendPathFatigueChips(StringBuilder html, String[] segments) {
        html.append("<span class='d-inline-flex align-items-center gap-1 flex-wrap justify-content-end'>");
        for (String segment : segments) {
            String chip = pathFatigueChipHtml(segment);
            if (!chip.isEmpty()) html.append(chip);
        }
        html.append("</span>");
    }

    private static String pathFatigueChipHtml(String segment) {
        String name = pathFatigueSegmentName(segment);
        if (name.isEmpty() || "Group stage".equalsIgnoreCase(name)) return "";
        String value = pathFatigueSegmentValue(segment);
        String stage = pathFatigueSegmentStage(segment);
        String tooltip = escapeHtml(name) + (pathFatigueSegmentUpset(segment) ? " (Upset)" : "");
        if (!value.isEmpty()) {
            tooltip += "<br>" + ("G".equals(stage) ? "Group" : "Knockout") + " ELO " + signedEloText(parseIntOrZero(value));
        }
        String label = flagHtml(name);
        if (label.isEmpty()) label = escapeHtml(name);
        return "<span data-bs-toggle='tooltip' data-bs-html='true' data-bs-title='"
                + tooltip.replace("'", "&#39;")
                + "' class='d-inline-flex align-items-center justify-content-center border rounded px-1 bg-white'"
                + " style='min-width:24px;height:18px;font-size:0.72rem;cursor:default'>"
                + label + "</span>";
    }

    private static String pathFatigueSegmentValue(String segment) {
        String raw = segment == null ? "" : segment.trim();
        int marker = raw.indexOf('|');
        if (marker > 0) raw = raw.substring(marker + 1).trim();
        int colon = raw.lastIndexOf(':');
        return colon > 0 && colon < raw.length() - 1 ? raw.substring(colon + 1).trim() : "";
    }

    private static String pathFatigueSegmentStage(String segment) {
        String value = segment == null ? "" : segment.trim();
        int marker = value.indexOf('|');
        if (marker <= 0) return "KO";
        String prefix = value.substring(0, marker).trim();
        return prefix.equalsIgnoreCase("G") ? "G" : "KO";
    }

    private static boolean pathFatigueSegmentUpset(String segment) {
        String value = segment == null ? "" : segment.trim();
        int marker = value.indexOf('|');
        return marker > 0 && value.substring(0, marker).trim().toUpperCase(java.util.Locale.ROOT).startsWith("U@");
    }

    private static String pathFatigueSegmentMatchId(String segment) {
        String value = segment == null ? "" : segment.trim();
        int marker = value.indexOf('|');
        if (marker <= 0) return "";
        String prefix = value.substring(0, marker).trim();
        int at = prefix.indexOf('@');
        return at >= 0 && at < prefix.length() - 1 ? prefix.substring(at + 1).trim() : "";
    }

    private static String pathFatigueSegmentName(String segment) {
        String value = segment == null ? "" : segment.trim();
        int marker = value.indexOf('|');
        if (marker > 0) value = value.substring(marker + 1).trim();
        int colon = value.lastIndexOf(':');
        return (colon > 0 ? value.substring(0, colon) : value).trim();
    }

    private static String pathFatigueSegmentHtml(String segment) {
        String name = pathFatigueSegmentName(segment);
        if (name.isEmpty() || "Group stage".equalsIgnoreCase(name)) return "";
        StringBuilder html = new StringBuilder();
        String flag = flagHtml(name);
        html.append(flag);
        if (flag.isEmpty()) {
            html.append(escapeHtml(name)).append(" ");
        }
        String value = "";
        String raw = segment == null ? "" : segment.trim();
        int marker = raw.indexOf('|');
        if (marker > 0) raw = raw.substring(marker + 1).trim();
        int colon = raw.lastIndexOf(':');
        if (colon > 0 && colon < raw.length() - 1) {
            value = raw.substring(colon + 1).trim();
        }
        if (!value.isEmpty()) {
            try {
                html.append(signedEloSpan(Integer.parseInt(value)));
            } catch (NumberFormatException ignored) {
                html.append("<span style=\"white-space:nowrap\">").append(escapeHtml(value)).append("</span>");
            }
        }
        return html.toString();
    }

    private static String signedEloText(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private static String signedEloSpan(int value) {
        return "<span style=\"white-space:nowrap\">" + signedEloText(value) + "</span>";
    }

    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static RowAdvanceProjection rowAdvanceProjection(String team1, String team2,
                                                             String team1PathDiff, String team2PathDiff,
                                                             String team1PathOpp, String team2PathOpp,
                                                             Map<String, EloBreakdown> eloBreakdowns,
                                                             PathFatigueCalculator pathFatigueCalculator) {
        EloBreakdown b1 = eloBreakdowns.get(team1);
        EloBreakdown b2 = eloBreakdowns.get(team2);
        if (b1 == null || b2 == null) {
            return RowAdvanceProjection.empty();
        }
        int team1PathDiffScore = parseIntOrZero(team1PathDiff);
        int team2PathDiffScore = parseIntOrZero(team2PathDiff);
        if (team1PathDiffScore != 0 || (team1PathOpp != null && !team1PathOpp.isBlank())) {
            b1 = b1.withPathFatigue(
                    pathFatigueCalculator.eloAdjustmentFromWeighted(team1PathDiffScore),
                    pathFatigueCalculator.label(team1PathDiffScore),
                    team1PathOpp == null ? "" : team1PathOpp);
        }
        if (team2PathDiffScore != 0 || (team2PathOpp != null && !team2PathOpp.isBlank())) {
            b2 = b2.withPathFatigue(
                    pathFatigueCalculator.eloAdjustmentFromWeighted(team2PathDiffScore),
                    pathFatigueCalculator.label(team2PathDiffScore),
                    team2PathOpp == null ? "" : team2PathOpp);
        }
        ExpectedGoalsCalculator.Projection projection = new ExpectedGoalsCalculator()
                .project(team1, team2, b1.totalElo, b2.totalElo,
                        b1.attackQuality, b1.defenceQuality,
                        b2.attackQuality, b2.defenceQuality);
        return new RowAdvanceProjection(
                projection.pick(),
                String.valueOf(projection.pickAdvancePct()),
                String.valueOf(projection.team1AdvancePct()),
                String.valueOf(projection.team2AdvancePct()));
    }

    private record RowAdvanceProjection(String winner, String winnerPct, String team1Pct, String team2Pct) {
        static RowAdvanceProjection empty() {
            return new RowAdvanceProjection("", "", "", "");
        }
    }

    private static double parseDoubleOrZero(String value) {
        try {
            return Double.parseDouble(value == null ? "0" : value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String stageFromLabel(String label) {
        if (label == null) {
            return "last_16";
        }
        String normalized = label.toLowerCase();
        if (normalized.contains("group")) {
            return "groups";
        }
        if (normalized.contains("last 16") || normalized.contains("16")) {
            return "last_16";
        }
        if (normalized.contains("quarter")) {
            return "last_8";
        }
        if (normalized.contains("semi")) {
            return "last_4";
        }
        if (normalized.contains("final") || normalized.contains("champion")) {
            return "final";
        }
        return "last_16";
    }

    private void appendSummary(int matchupCount) {
    }

    private void appendPathFilterScript() {
        if (pathFilterScriptAdded) {
            return;
        }
        html.append("<style>.path-focus-row{outline:3px solid #fd7e14;outline-offset:-2px}.expand-icon{display:inline-block;width:0;height:0;border-top:4px solid transparent;border-bottom:4px solid transparent;border-left:6px solid currentColor;opacity:.55;vertical-align:middle;transform-origin:35% 50%;transition:transform .15s ease}.expand-icon.expanded{transform:rotate(90deg)}</style><script>")
                .append("function applyFilters(section){")
                .append("const serverPaging=section.dataset.serverPaging==='true';const pathBtn=section.querySelector('.path-btn.active');const path=pathBtn?pathBtn.dataset.path:'all';const rowPath=path==='prediction'?'predicted':path;const teamSel=section.querySelector('select');const team=teamSel?teamSel.value:'';const rows=Array.from(section.querySelectorAll('tbody tr[data-path]')).filter(row=>!row.classList.contains('detail-row'));const matches=rows.filter(row=>{const rowType=row.dataset.path;const pathMatch=path==='all'?true:path==='results'?(rowType==='results'||rowType==='fixture'||rowType==='actual'||rowType==='result_upset'):(path==='prediction'?(rowType==='predicted'||rowType==='live'):(path==='alt'?(rowType==='alt'||rowType==='upset'):rowType===rowPath));return pathMatch&&(!team||row.dataset.team1===team||row.dataset.team2===team);});")
                .append("if(serverPaging){rows.forEach(row=>{row.style.display='';const detail=row.nextElementSibling;if(detail&&detail.classList.contains('detail-row')){detail.style.display=detail.dataset.expanded==='true'?'table-row':'none';if(detail.dataset.expanded!=='true'){row.setAttribute('aria-expanded','false');const icon=row.querySelector('.expand-icon');if(icon)icon.classList.remove('expanded');}}});const empty=section.querySelector('.table-no-results');if(empty)empty.style.display=rows.length?'none':'block';return;}")
                .append("const pageSize=50;const pageCount=Math.max(1,Math.ceil(matches.length/pageSize));let page=Math.min(Math.max(1,Number(section.dataset.tablePage||1)),pageCount);section.dataset.tablePage=String(page);const start=(page-1)*pageSize;const pageRows=new Set(matches.slice(start,start+pageSize));")
                .append("rows.forEach(row=>{const show=pageRows.has(row);row.style.display=show?'':'none';const detail=row.nextElementSibling;if(detail&&detail.classList.contains('detail-row')){detail.style.display=(show&&detail.dataset.expanded==='true')?'table-row':'none';if(!show){detail.dataset.expanded='false';row.setAttribute('aria-expanded','false');const icon=row.querySelector('.expand-icon');if(icon)icon.classList.remove('expanded');}}});")
                .append("const empty=section.querySelector('.table-no-results');if(empty)empty.style.display=matches.length?'none':'';const pager=section.querySelector('.table-pagination');if(pager){pager.style.display=pageCount>1?'flex':'none';const status=pager.querySelector('.page-status');if(status)status.textContent='Page '+page+' of '+pageCount+' · '+matches.length+' results';const prev=pager.querySelector('.page-prev');const next=pager.querySelector('.page-next');if(prev)prev.disabled=page<=1;if(next)next.disabled=page>=pageCount;}")
                .append("}")
                .append("function changeTablePage(btn,delta){const section=btn.closest('.output-section');section.dataset.tablePage=String(Number(section.dataset.tablePage||1)+delta);applyFilters(section);section.querySelector('.table-responsive').scrollIntoView({behavior:'smooth',block:'start'});}")
                .append("function writeFilterState(section,overrides,replaceOnly){const url=new URL(window.location.href);const activePath=overrides.path!==undefined?overrides.path:(section.querySelector('.path-btn.active')?.dataset.path||'all');const teamSel=section.querySelector('select');const activeTeam=overrides.team!==undefined?overrides.team:(teamSel?teamSel.value:'');const actualOverride=overrides.actual!==undefined?overrides.actual:null;const actualValue=actualOverride===null?(activePath==='all'||activePath==='results'):(actualOverride==='true'||actualOverride===true);url.searchParams.set('path',activePath);if(activeTeam){url.searchParams.set('team',activeTeam);}else{url.searchParams.delete('team');}url.searchParams.set('results',actualValue?'true':'false');url.searchParams.delete('actual');url.searchParams.set('page',String(overrides.page||1));const next=url.pathname+'?'+url.searchParams.toString();if(replaceOnly){window.history.replaceState({},'',next);}else{window.location.href=next;}}")
                .append("function navigateWithFilters(section,overrides){writeFilterState(section,overrides,false);}")
                .append("function syncFilterUrl(section,overrides){writeFilterState(section,overrides,true);}")
                .append("function filterPath(btn){const section=btn.closest('.output-section');if(section.dataset.serverPaging==='true'){navigateWithFilters(section,{path:btn.dataset.path,page:1,actual:btn.dataset.actual});return;}section.dataset.tablePage='1';btn.closest('.btn-group').querySelectorAll('.btn').forEach(b=>b.classList.remove('active'));btn.classList.add('active');localStorage.setItem('predictor_path_v2',btn.dataset.path);localStorage.removeItem('predictor_team');const teamSel=section.querySelector('select');if(teamSel)teamSel.value='';document.querySelectorAll('.sim-snapshot-team').forEach(tile=>{tile.classList.remove('border-primary','bg-primary-subtle');tile.setAttribute('aria-pressed','false');});applyFilters(section);syncFilterUrl(section,{path:btn.dataset.path,team:'',page:1,actual:btn.dataset.actual});}")
                .append("function filterTeam(sel){const section=sel.closest('.output-section');if(section.dataset.serverPaging==='true'){navigateWithFilters(section,{team:sel.value,page:1});return;}section.dataset.tablePage='1';localStorage.setItem('predictor_team',sel.value);document.querySelectorAll('.sim-snapshot-team').forEach(tile=>{tile.classList.remove('border-primary','bg-primary-subtle');tile.setAttribute('aria-pressed','false');});applyFilters(section);syncFilterUrl(section,{team:sel.value,page:1});}function filterTeamValue(team){const section=document.querySelector('.output-section');if(!section)return;const sel=section.querySelector('select');const active=sel&&sel.value===team;const next=active?'':team;if(section.dataset.serverPaging==='true'){navigateWithFilters(section,{team:next,page:1});return;}section.dataset.tablePage='1';localStorage.setItem('predictor_team',next);if(sel){const opt=Array.from(sel.options).find(o=>o.value===next);if(opt)sel.value=next;else sel.value='';}document.querySelectorAll('.sim-snapshot-team').forEach(tile=>{const selected=!active&&tile.dataset.team===team;tile.classList.toggle('border-primary',selected);tile.classList.toggle('bg-primary-subtle',selected);tile.setAttribute('aria-pressed',selected?'true':'false');});applyFilters(section);syncFilterUrl(section,{team:next,page:1});section.scrollIntoView({behavior:'smooth',block:'start'});}")
                .append("function toggleDetail(row){")
                .append("const detail=row.nextElementSibling;")
                .append("if(!detail||!detail.classList.contains('detail-row'))return;")
                .append("const isHidden=detail.style.display==='none';")
                .append("detail.style.display=isHidden?'table-row':'none';")
                .append("detail.dataset.expanded=isHidden?'true':'false';")
                .append("row.setAttribute('aria-expanded',isHidden?'true':'false');")
                .append("const icon=row.querySelector('.expand-icon');")
                .append("if(icon)icon.classList.toggle('expanded',isHidden);")
                .append("}")
                .append("document.addEventListener('DOMContentLoaded',function(){")
                .append("const savedPath=localStorage.getItem('predictor_path_v2');")
                .append("const savedTeam=localStorage.getItem('predictor_team');")
                .append("document.querySelectorAll('.output-section').forEach(function(section){")
                .append("const serverPaging=section.dataset.serverPaging==='true';if(!serverPaging&&savedPath){const btn=section.querySelector('.path-btn[data-path=\"'+savedPath+'\"]');if(btn){section.querySelectorAll('.path-btn').forEach(b=>b.classList.remove('active'));btn.classList.add('active');}}")
                .append("const sel=section.querySelector('select');if(sel&&!serverPaging&&savedTeam){const opt=Array.from(sel.options).find(o=>o.value===savedTeam);if(opt)sel.value=savedTeam;}")
                .append("});")
                .append("const params=new URLSearchParams(window.location.search);const focusMatch=params.get('focusMatch');const focusTeam=params.get('focusTeam');const focusOpponent=params.get('focusOpponent');if(focusMatch&&focusTeam&&focusOpponent){const section=document.querySelector('.output-section');if(section){const allBtn=section.querySelector('.path-btn[data-path=\"all\"]');if(allBtn){section.querySelectorAll('.path-btn').forEach(b=>b.classList.remove('active'));allBtn.classList.add('active');}const sel=section.querySelector('select');if(sel)sel.value='';localStorage.removeItem('predictor_team');applyFilters(section);const rows=Array.from(section.querySelectorAll('tbody tr[data-match]'));const row=rows.find(r=>r.dataset.match===focusMatch&&((r.dataset.team1===focusTeam&&r.dataset.team2===focusOpponent)||(r.dataset.team1===focusOpponent&&r.dataset.team2===focusTeam)));if(row){const mainRows=Array.from(section.querySelectorAll('tbody tr[data-path]')).filter(r=>!r.classList.contains('detail-row'));section.dataset.tablePage=String(Math.floor(mainRows.indexOf(row)/50)+1);applyFilters(section);row.style.display='';row.classList.add('path-focus-row');toggleDetail(row);setTimeout(()=>row.scrollIntoView({behavior:'smooth',block:'center'}),50);setTimeout(()=>row.classList.remove('path-focus-row'),3000);}}}")
.append("document.querySelectorAll('[data-bs-toggle=\"tooltip\"]').forEach(function(el){new bootstrap.Tooltip(el);});")
                .append("});")
                .append("</script>");
        pathFilterScriptAdded = true;
    }

    private String teamCell(String team) {
        return flagHtml(team) + escapeHtml(team);
    }

    private int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static String valueAt(String[] cols, int idx) {
        return idx >= 0 && idx < cols.length ? cols[idx].trim() : "";
    }

    private static String calcNetWinnings(String odds, double stake) {
        if (odds == null || odds.isBlank()) return "";
        odds = odds.trim();
        // Fractional: "9/4", "5/1", "11/10"
        if (odds.contains("/")) {
            String[] parts = odds.split("/", 2);
            try {
                double num = Double.parseDouble(parts[0].trim());
                double den = Double.parseDouble(parts[1].trim());
                if (den == 0) return "";
                return String.format("£%.2f", (num / den) * stake);
            } catch (NumberFormatException ignored) { }
        }
        // Decimal: "3.25", "6.0"
        try {
            double decimal = Double.parseDouble(odds);
            return String.format("£%.2f", (decimal - 1.0) * stake);
        } catch (NumberFormatException ignored) { }
        return "";
    }

    private static String oddsHeaderFor(String label) {
        if (label == null) return "Odds";
        if (label.contains("Last 32"))       return "Odds to Reach Last 16";
        if (label.contains("Last 16"))       return "Odds to Reach QF";
        if (label.contains("Quarter"))       return "Odds to Reach SF";
        if (label.contains("Semi"))          return "Odds to Reach Final";
        if (label.contains("Final"))         return "Odds to Win";
        return "Odds";
    }

    private static String betCandidateTag(int pct, String netWinnings) {
        if (netWinnings == null || netWinnings.isBlank()) return "";
        double profit;
        try {
            profit = Double.parseDouble(netWinnings.replace("£", "").trim());
        } catch (NumberFormatException e) {
            return "";
        }
        if (pct >= 55 && profit >= 10) return "strong";
        if (pct >= 40 && profit >= 10) return "candidate";
        if (pct >= 40 && profit > 5)   return "weak";
        if (pct >= 30 && profit >= 10) return "risky";
        if (pct < 30  && profit >= 20) return "moonshot";
        return "";
    }

    private static String betCandidateBadgeHtml(String tag) {
        return switch (tag) {
            case "strong"    -> "<span class=\"badge\" style=\"background-color:#198754;color:#fff\">Strong Candidate</span>";
            case "candidate" -> "<span class=\"badge\" style=\"background-color:#0d6efd;color:#fff\">Candidate</span>";
            case "weak"      -> "<span class=\"badge\" style=\"background-color:#0dcaf0;color:#fff\">Weak Candidate</span>";
            case "risky"     -> "<span class=\"badge\" style=\"background-color:#fd7e14;color:#fff\">Risky Candidate</span>";
            case "moonshot"  -> "<span class=\"badge\" style=\"background-color:#6f42c1;color:#fff\">Moonshot</span>";
            default -> "";
        };
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String originSlot(String rawDisplay) {
        if (rawDisplay == null || rawDisplay.isBlank()) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([A-L]{1,3}[123])\\(")
                .matcher(rawDisplay);
        String origin = "";
        while (matcher.find()) {
            origin = matcher.group(1);
        }
        return origin;
    }

    private static String positionTag(String rawDisplay) {
        if (rawDisplay == null) return "";
        // Unwrap outer match-winner wrappers (e.g. W97(W89(W77(I1(France))))) stopping at position tokens
        String token = rawDisplay;
        while (token.matches("^[A-Z]+[0-9]+\\(.*\\)$")) {
            if (token.matches("^[A-L]1\\(.*\\)$")) return "winner";
            if (token.matches("^[A-L]2\\(.*\\)$")) return "runner_up";
            if (token.matches("^[A-L]+3\\(.*\\)$")) return "third";
            String inner = token.substring(token.indexOf('(') + 1, token.lastIndexOf(')'));
            if (inner.equals(token)) break;
            token = inner;
        }
        return "";
    }

    private static String positionBadgeHtml(String tag) {
        return switch (tag) {
            case "winner"    -> "<span class=\"badge\" style=\"background-color:#FFD700;color:#000\">Group Winner</span>";
            case "runner_up" -> "<span class=\"badge\" style=\"background-color:#C0C0C0;color:#000\">Runner-up</span>";
            case "third"     -> "<span class=\"badge\" style=\"background-color:#CD7F32;color:#fff\">Best 3rd</span>";
            default -> "";
        };
    }
    private record PathFilterButton(String path, String label) {}

}

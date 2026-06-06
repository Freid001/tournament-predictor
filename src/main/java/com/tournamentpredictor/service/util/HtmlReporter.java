package com.tournamentpredictor.service.util;

import com.tournamentpredictor.config.PredictionConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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
    private Map<String, String> simulationAdvancePct = Map.of();
    private Map<String, String> matchupLikelihoodPct = Map.of();

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

    public HtmlReporter withSimulationAdvance(Map<String, String> simulationAdvancePct) {
        this.simulationAdvancePct = simulationAdvancePct == null ? Map.of() : simulationAdvancePct;
        return this;
    }

    public HtmlReporter withMatchupLikelihood(Map<String, String> matchupLikelihoodPct) {
        this.matchupLikelihoodPct = matchupLikelihoodPct == null ? Map.of() : matchupLikelihoodPct;
        return this;
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
            boolean isPrimary = pathIdx < 0 || "predicted".equalsIgnoreCase(rowPath);

            String team1 = eloCalculator.extractTeamName(valueAt(cols, team1Idx));
            String team2 = eloCalculator.extractTeamName(valueAt(cols, team2Idx));
            String filter = getTeamFilter();
            if (filter != null && !filter.isEmpty()) {
                String f = filter.toLowerCase();
                if (!team1.toLowerCase().contains(f) && !team2.toLowerCase().contains(f)) continue;
            }

            String matchId = valueAt(cols, matchIdIdx);
            String key = matchId + "|" + team1 + "|" + team2;
            if (seen.contains(key)) continue;
            seen.add(key);
            rowsToPrint.add(cols);
        }

        html.append("<div class=\"output-section mb-4\">");
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
            String rowPath = valueAt(cols, pathIdx);
            boolean isPrimary = pathIdx < 0 || "predicted".equalsIgnoreCase(rowPath);
            boolean isAlt = !isPrimary;
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
            String matchupLikelihood = matchupLikelihoodPct.getOrDefault(matchId + "|" + team1 + "|" + team2, "");
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
            tableRows.add(new String[]{matchId, team1, team2, suffix, winner, String.valueOf(pct), oddsCell, isPrimary ? "predicted" : "alt", netWinningsCell,
                    positionTag(rawTeam1), positionTag(rawTeam2),
                    team1PathDiff, team2PathDiff,
                    team1PathOpp, team2PathOpp,
                    originSlot(rawTeam1), originSlot(rawTeam2),
                    simWinner, simWinnerPct, team1SimPct, team2SimPct,
                    rowProjection.winner(), rowProjection.winnerPct(), rowProjection.team1Pct(), rowProjection.team2Pct(),
                    matchupLikelihood});
        }

        if (tableRows.isEmpty()) {
            html.append("<div class=\"alert alert-warning\">No rows available for this view.</div></div>");
            return;
        }

        long altCount = tableRows.stream().filter(r -> "alt".equals(r[7])).count();
        boolean showToggle = altCount > 0;
        boolean roundOf32 = label != null && (label.contains("Last 32") || label.contains("Round of 32"));
        boolean showPositionOnPrimary = roundOf32;
        boolean hasSimulationAdvance = tableRows.stream().anyMatch(r -> (r.length > 17 && !r[17].isBlank()) || (r.length > 21 && !r[21].isBlank()));
        boolean showWinnerColumn = !hasSimulationAdvance;
        boolean showOdds = hasOdds && !roundOf32;
        boolean hasMatchupLikelihood = tableRows.stream().anyMatch(r -> r.length > 25 && !r[25].isBlank());
        if (hasMatchupLikelihood) {
            tableRows.sort(java.util.Comparator.comparingDouble((String[] row) -> parseDoubleOrZero(row[25])).reversed());
        }

        List<String> teamNames = tableRows.stream()
                .flatMap(r -> java.util.stream.Stream.of(r[1], r[2]))
                .filter(t -> t != null && !t.isEmpty())
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList());

        if (showToggle || !teamNames.isEmpty() || editUrl != null) {
            html.append("<div class=\"d-flex gap-2 align-items-center mb-2 flex-wrap\">");
            if (showToggle) {
                html.append("<div class=\"btn-group btn-group-sm\" role=\"group\">")
                        .append("<button type=\"button\" class=\"btn btn-outline-secondary active path-btn\" data-path=\"both\" onclick=\"filterPath(this)\">All</button>")
                        .append("<button type=\"button\" class=\"btn btn-outline-secondary path-btn\" data-path=\"alt\" onclick=\"filterPath(this)\">Alternative</button>")
                        .append("<button type=\"button\" class=\"btn btn-outline-secondary path-btn\" data-path=\"predicted\" onclick=\"filterPath(this)\">Predicted</button>")
                        .append("</div>");
            }
            if (!teamNames.isEmpty()) {
                html.append("<select class=\"form-select form-select-sm\" style=\"max-width:180px\" onchange=\"filterTeam(this)\">")
                        .append("<option value=\"\">All teams</option>");
                for (String t : teamNames) {
                    html.append("<option value=\"").append(escapeHtml(t)).append("\">").append(escapeHtml(t)).append("</option>");
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
        html.append("<thead class=\"table-dark\"><tr><th>Match</th><th>Team 1</th><th>Team 2</th>");
        if (showWinnerColumn) html.append("<th>Winner</th>");
        if (hasSimulationAdvance) html.append("<th>Most Likely Winner</th>");
        if (hasMatchupLikelihood) html.append("<th>Match Likelihood</th>");
        if (roundOf32) html.append("<th>Path</th>");
        if (showOdds) html.append("<th>").append(escapeHtml(oddsHeaderFor(label))).append("</th><th>Net Winnings (Bet=£10)</th>");
        html.append("</tr></thead><tbody>");

        for (String[] row : tableRows) {
            html.append("<tr class=\"").append("predicted".equals(row[7]) ? "table-primary" : "").append("\" data-path=\"").append(row[7]).append("\"")
                    .append(" data-team1=\"").append(escapeHtml(row[1])).append("\"")
                    .append(" data-team2=\"").append(escapeHtml(row[2])).append("\"")
                    .append(" style=\"cursor:pointer\"")
                    .append(" aria-expanded=\"false\"")
                    .append(" onclick=\"toggleDetail(this)\"")
                    .append(">");
            html.append("<td><span class=\"expand-icon me-1\" aria-hidden=\"true\"></span>").append(escapeHtml(row[0])).append("</td>");
            html.append("<td>").append(teamCell(row[1]));
            if (!roundOf32 && row.length > 9 && !row[9].isEmpty() && ("alt".equals(row[7]) || showPositionOnPrimary)) {
                html.append(" ").append(positionBadgeHtml(row[9]));
            }
            html.append("</td>");
            html.append("<td>").append(teamCell(row[2]));
            if (!roundOf32 && !row[3].isEmpty()) {
                html.append(" <span class=\"badge text-bg-secondary\">").append(escapeHtml(row[3])).append("</span>");
            }
            if (!roundOf32 && row.length > 10 && !row[10].isEmpty() && ("alt".equals(row[7]) || showPositionOnPrimary)) {
                html.append(" ").append(positionBadgeHtml(row[10]));
            }
            html.append("</td>");
            if (showWinnerColumn) {
                html.append("<td><span class=\"")
                        .append(Integer.parseInt(row[5]) >= 60 ? "fw-semibold text-success" : "fw-semibold text-warning-emphasis")
                        .append("\">")
                        .append(teamCell(row[4]))
                        .append(" (")
                        .append(escapeHtml(row[5]))
                        .append("%)</span></td>");
            }
            if (hasSimulationAdvance) {
                html.append("<td>");
                if (row.length > 17 && !row[17].isBlank()) {
                    html.append("<div class=\"fw-semibold\">")
                            .append(teamCell(row[17]))
                            .append(" (").append(escapeHtml(row[18])).append("%)</div>")
                            .append("<div class=\"text-muted small\">")
                            .append("all routes · ")
                            .append(escapeHtml(row[1])).append(" ").append(escapeHtml(row[19])).append("%")
                            .append(" · ")
                            .append(escapeHtml(row[2])).append(" ").append(escapeHtml(row[20])).append("%")
                            .append("</div>");
                } else if (row.length > 21 && !row[21].isBlank()) {
                    html.append("<div class=\"fw-semibold\">")
                            .append(teamCell(row[21]))
                            .append(" (").append(escapeHtml(row[22])).append("%)</div>");
                } else {
                    html.append("<span class=\"text-muted small\">No simulation data</span>");
                }
                html.append("</td>");
            }
            if (hasMatchupLikelihood) {
                html.append("<td class=\"fw-semibold\">")
                        .append(escapeHtml(row[25])).append("%</td>");
            }
            if (roundOf32) {
                boolean predictedPath = "predicted".equals(row[7]);
                html.append("<td><span class=\"badge ")
                        .append(predictedPath ? "text-bg-primary" : "text-bg-secondary")
                        .append("\">")
                        .append(predictedPath ? "Predicted" : "Alternative")
                        .append("</span></td>");
            }
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
            int colSpan = 3 + (showWinnerColumn ? 1 : 0) + (hasSimulationAdvance ? 1 : 0)
                    + (hasMatchupLikelihood ? 1 : 0) + (roundOf32 ? 1 : 0) + (showOdds ? 2 : 0);
            appendEloDetailRow(html, row, colSpan, eloCalculator, eloBreakdowns, stage, pathFatigueCalculator);
        }

        html.append("</tbody></table></div></div>");
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

    private static void appendEloDetailRow(StringBuilder html, String[] row, int colSpan,
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
            appendExpectedGoalsPanel(html, team1, b1, row.length > 15 ? row[15] : "", team2, b2, row.length > 16 ? row[16] : "");
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

    private static void appendExpectedGoalsPanel(StringBuilder html, String team1, EloBreakdown b1, String originSlot1,
                                                 String team2, EloBreakdown b2, String originSlot2) {
        if (b1 == null || b2 == null) {
            return;
        }
        ExpectedGoalsCalculator.Projection projection = new ExpectedGoalsCalculator()
                .project(team1, team2, b1.totalElo, b2.totalElo);
        html.append("<div class=\"border rounded bg-white mb-2 p-2\">");
        html.append("<div class=\"text-muted small fw-semibold mb-2\">Score model</div>");
        html.append("<div class=\"row g-2 align-items-stretch text-center\">");
        appendScoreModelTeamColumn(html, team1, b1, originSlot1, projection.team1ExpectedGoals(),
                projection.team1WinPct(), projection.team1AdvancePct());
        html.append("<div class=\"col-12 col-lg-2 d-flex flex-lg-column justify-content-center align-items-center gap-2 small\">");
        html.append("<span class=\"badge text-bg-light border fs-6\">")
                .append(escapeHtml(projection.mostLikelyScoreText()))
                .append("</span>");
        html.append("<span class=\"text-muted\">90-min draw ").append(projection.drawPct()).append("%</span>");
        html.append("<span class=\"badge text-bg-light border\">")
                .append(flagHtml(projection.pick())).append(escapeHtml(projection.pick()))
                .append(" after ET/pens ").append(projection.pickAdvancePct()).append("%")
                .append("</span>");
        html.append("</div>");
        appendScoreModelTeamColumn(html, team2, b2, originSlot2, projection.team2ExpectedGoals(),
                projection.team2WinPct(), projection.team2AdvancePct());
        html.append("</div>");
        html.append("<div class=\"text-muted mt-2\" style=\"font-size:0.72rem\">")
                .append("Score model preview. Simulations sample 90-minute goals; level games are resolved by the Elo-based ET/pens tiebreak.")
                .append("</div>");
        html.append("</div>");
    }

    private static void appendScoreModelTeamColumn(StringBuilder html, String team, EloBreakdown breakdown, String originSlot,
                                                   double expectedGoals, int winPct, int advancePct) {
        html.append("<div class=\"col-12 col-lg-5\">");
        html.append("<div class=\"h-100 border rounded bg-light-subtle p-2\">");
        html.append("<div class=\"fs-4 mb-1\">").append(flagHtml(team)).append("</div>");
        html.append("<div class=\"fw-semibold mb-2\">").append(escapeHtml(team)).append("</div>");
        html.append("<div class=\"row g-1 small\">");
        appendScoreModelStat(html, "xG", String.format(java.util.Locale.ROOT, "%.2f", expectedGoals));
        appendScoreModelStat(html, "90-min W", winPct + "%");
        appendScoreModelStat(html, "Advance", advancePct + "%");
        html.append("</div>");
        appendScoreModelTournamentPath(html, breakdown, originSlot);
        html.append("</div>");
        html.append("</div>");
    }

    private static void appendScoreModelTournamentPath(StringBuilder html, EloBreakdown b, String originSlot) {
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
                if (name.isEmpty()) continue;
                if (hasPathItem) html.append(" <span class='text-muted'>›</span> ");
                html.append(flagHtml(name)).append(escapeHtml(name));
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
                    if (name.isEmpty()) continue;
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
            sb.append("<div class=\"border rounded bg-white overflow-hidden shadow-sm\">");
            sb.append("<div class=\"d-flex justify-content-between align-items-center px-2 py-1 border-bottom bg-light\">");
            sb.append("<span class=\"text-muted small fw-semibold\">ELO calculation</span>");
            sb.append("<span class=\"small text-muted\">Base <span class=\"fw-semibold text-body\">").append(b.baseElo).append("</span></span>");
            sb.append("</div>");
            sb.append("<table class=\"table table-sm mb-0 align-middle\">");
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
                sb.append("<tr class=\"").append(b.pathFatigueAdjustment > 0 ? "table-success" : "table-warning").append("\">");
                sb.append("<td class=\"small text-nowrap border-end-0\" style=\"width:48%\">😴 Path Fatigue</td>");
                sb.append("<td class=\"text-end fw-bold border-start-0 ").append(b.pathFatigueAdjustment > 0 ? "text-success" : "text-warning").append("\">");
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
            // Qual Form second
            if (b.qualBonus != 0) {
                sb.append("<tr class=\"").append(b.qualBonus > 0 ? "table-success" : "table-danger").append("\">");
                sb.append("<td class=\"small text-nowrap border-end-0\" style=\"width:48%\">⚔️ Qualification Form</td>");
                sb.append("<td class=\"text-end fw-bold border-start-0 ").append(b.qualBonus > 0 ? "text-success" : "text-danger").append("\" style=\"width:52%\">");
                appendFormValueWithCircles(sb, signedEloText(b.qualBonus), b.qualResults, teamName);
                sb.append("</td></tr>");
            }
            // Friendly Form third
            if (b.preTournamentBonus != 0) {
                sb.append("<tr class=\"").append(b.preTournamentBonus > 0 ? "table-success" : "table-danger").append("\">");
                sb.append("<td class=\"small text-nowrap border-end-0\" style=\"width:48%\">📈 Friendly Form</td>");
                sb.append("<td class=\"text-end fw-bold border-start-0 ").append(b.preTournamentBonus > 0 ? "text-success" : "text-danger").append("\" style=\"width:52%\">");
                appendFormValueWithCircles(sb, signedEloText(b.preTournamentBonus), b.friendlyResults, teamName);
                sb.append("</td></tr>");
            }
            // Rest of squad/situational signals
            if (b.homeBonus != 0)
                sb.append("<tr class=\"table-success\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">🏠 Home Advantage</td><td class=\"text-end fw-bold border-start-0 text-success\" style=\"width:52%\">+").append(b.homeBonus).append("</td></tr>");
            if (b.squadAgePenalty != 0)
                sb.append("<tr class=\"table-warning\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">👶 Squad Age</td><td class=\"text-end fw-bold border-start-0 text-warning\" style=\"width:52%\">−").append(b.squadAgePenalty)
                  .append(b.ageNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.ageNotes) + "</span>")
                  .append("</td></tr>");
            if (b.squadCohesionPenalty != 0)
                sb.append("<tr class=\"table-danger\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">🤝 Squad Cohesion</td><td class=\"text-end fw-bold border-start-0 text-danger\" style=\"width:52%\">−").append(b.squadCohesionPenalty)
                  .append(b.cohesionNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.cohesionNotes) + "</span>")
                  .append("</td></tr>");
            if (b.squadDepthPenalty != 0)
                sb.append("<tr class=\"table-danger\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">⬇️ Bench Depth</td><td class=\"text-end fw-bold border-start-0 text-danger\" style=\"width:52%\">−").append(b.squadDepthPenalty)
                  .append(b.depthNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.depthNotes) + "</span>")
                  .append("</td></tr>");
            if (b.squadQualityBonus != 0)
                sb.append("<tr class=\"table-success\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">⭐ Squad Quality</td><td class=\"text-end fw-bold border-start-0 text-success\" style=\"width:52%\">+").append(b.squadQualityBonus)
                  .append(b.qualityNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.qualityNotes) + "</span>")
                  .append("</td></tr>");
            if (b.dropoutPenalty != 0)
                sb.append("<tr class=\"table-danger\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">👤 Squad Omissions</td><td class=\"text-end fw-bold border-start-0 text-danger\" style=\"width:52%\">−").append(b.dropoutPenalty)
                  .append(b.dropoutNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.dropoutNotes) + "</span>")
                  .append("</td></tr>");
            if (b.injuryPenalty != 0)
                sb.append("<tr class=\"table-danger\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">🤕 Injuries</td><td class=\"text-end fw-bold border-start-0 text-danger\" style=\"width:52%\">−").append(b.injuryPenalty)
                  .append(b.injuryNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.injuryNotes) + "</span>")
                  .append("</td></tr>");
            if (b.heatBonus != 0)
                sb.append("<tr class=\"table-warning\"><td class=\"small text-nowrap border-end-0\" style=\"width:48%\">🔥 Heat Advantage</td><td class=\"text-end fw-bold border-start-0\" style=\"width:52%\">+").append(b.heatBonus).append("</td></tr>");
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
        if (name.isEmpty()) return "";
        String value = pathFatigueSegmentValue(segment);
        String stage = pathFatigueSegmentStage(segment);
        String tooltip = escapeHtml(name);
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

    private static String pathFatigueSegmentName(String segment) {
        String value = segment == null ? "" : segment.trim();
        int marker = value.indexOf('|');
        if (marker > 0) value = value.substring(marker + 1).trim();
        int colon = value.lastIndexOf(':');
        return (colon > 0 ? value.substring(0, colon) : value).trim();
    }

    private static String pathFatigueSegmentHtml(String segment) {
        String name = pathFatigueSegmentName(segment);
        if (name.isEmpty()) return "";
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
                .project(team1, team2, b1.totalElo, b2.totalElo);
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
        html.append("<style>.expand-icon{display:inline-block;width:0;height:0;border-top:4px solid transparent;border-bottom:4px solid transparent;border-left:6px solid currentColor;opacity:.55;vertical-align:middle;transform-origin:35% 50%;transition:transform .15s ease}.expand-icon.expanded{transform:rotate(90deg)}</style><script>")
                .append("function applyFilters(section){")
                .append("const pathBtn=section.querySelector('.path-btn.active');")
                .append("const path=pathBtn?pathBtn.dataset.path:'both';")
                .append("const teamSel=section.querySelector('select');")
                .append("const team=teamSel?teamSel.value:'';")
                .append("section.querySelectorAll('tbody tr[data-path]').forEach(row=>{")
                .append("if(row.classList.contains('detail-row'))return;")
                .append("const rp=row.dataset.path;")
                .append("const pathOk=path==='both'||rp===path;")
                .append("const teamOk=!team||row.dataset.team1===team||row.dataset.team2===team;")
                .append("const show=pathOk&&teamOk;")
                .append("row.style.display=show?'':'none';")
                .append("const detail=row.nextElementSibling;")
                .append("if(detail&&detail.classList.contains('detail-row')){")
                .append("detail.style.display=(show&&detail.dataset.expanded==='true')?'table-row':'none';")
                .append("if(!show){detail.dataset.expanded='false';row.setAttribute('aria-expanded','false');const icon=row.querySelector('.expand-icon');if(icon)icon.classList.remove('expanded');}")
                .append("}")
                .append("});}")
                .append("function filterPath(btn){btn.closest('.btn-group').querySelectorAll('.btn').forEach(b=>b.classList.remove('active'));btn.classList.add('active');localStorage.setItem('predictor_path_v2',btn.dataset.path);applyFilters(btn.closest('.output-section'));}")
                .append("function filterTeam(sel){localStorage.setItem('predictor_team',sel.value);applyFilters(sel.closest('.output-section'));}function filterTeamValue(team){localStorage.setItem('predictor_team',team);const section=document.querySelector('.output-section');if(!section)return;const sel=section.querySelector('select');if(sel){const opt=Array.from(sel.options).find(o=>o.value===team);if(opt)sel.value=team;}applyFilters(section);section.scrollIntoView({behavior:'smooth',block:'start'});}")
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
                .append("if(savedPath){const btn=section.querySelector('.path-btn[data-path=\"'+savedPath+'\"]');if(btn){section.querySelectorAll('.path-btn').forEach(b=>b.classList.remove('active'));btn.classList.add('active');}}")
                .append("const sel=section.querySelector('select');if(sel&&savedTeam){const opt=Array.from(sel.options).find(o=>o.value===savedTeam);if(opt)sel.value=savedTeam;}")
                .append("applyFilters(section);")
                .append("});")
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
}

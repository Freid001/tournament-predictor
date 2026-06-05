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
            String oddsCell = "";
            String netWinningsCell = "";
            if (teamOdds != null && !teamOdds.isEmpty()) {
                String odds = teamOdds.get(winner);
                if (odds != null && !odds.isEmpty()) {
                    oddsCell = odds;
                    netWinningsCell = calcNetWinnings(odds, 10.0);
                    hasOdds = true;
                }
            }
            tableRows.add(new String[]{matchId, team1, team2, suffix, winner, String.valueOf(pct), oddsCell, isPrimary ? "predicted" : "alt", netWinningsCell,
                    positionTag(rawTeam1), positionTag(rawTeam2),
                    valueAt(cols, team1PathDiffIdx), valueAt(cols, team2PathDiffIdx),
                    valueAt(cols, team1PathOppIdx), valueAt(cols, team2PathOppIdx)});
        }

        if (tableRows.isEmpty()) {
            html.append("<div class=\"alert alert-warning\">No rows available for this view.</div></div>");
            return;
        }

        long altCount = tableRows.stream().filter(r -> "alt".equals(r[7])).count();
        boolean showToggle = altCount > 0;
        boolean showPositionOnPrimary = label != null && label.contains("Last 32");

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
                        .append("<button type=\"button\" class=\"btn btn-outline-secondary active path-btn\" data-path=\"predicted\" onclick=\"filterPath(this)\">Predicted</button>")
                        .append("<button type=\"button\" class=\"btn btn-outline-secondary path-btn\" data-path=\"alt\" onclick=\"filterPath(this)\">Alternative</button>")
                        .append("<button type=\"button\" class=\"btn btn-outline-secondary path-btn\" data-path=\"both\" onclick=\"filterPath(this)\">Both</button>")
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
        html.append("<thead class=\"table-dark\"><tr><th>Match</th><th>Team 1</th><th>Team 2</th><th>Winner</th>");
        if (hasOdds) html.append("<th>").append(escapeHtml(oddsHeaderFor(label))).append("</th><th>Net Winnings (Bet=£10)</th>");
        html.append("</tr></thead><tbody>");

        for (String[] row : tableRows) {
            html.append("<tr data-path=\"").append(row[7]).append("\"")
                    .append(" data-team1=\"").append(escapeHtml(row[1])).append("\"")
                    .append(" data-team2=\"").append(escapeHtml(row[2])).append("\"")
                    .append(" style=\"cursor:pointer").append(!"predicted".equals(row[7]) ? ";display:none" : "").append("\"")
                    .append(" onclick=\"toggleDetail(this)\"")
                    .append(">");
            html.append("<td><span class=\"expand-icon me-1\" style=\"font-size:.75em;opacity:.5\">▶</span>").append(escapeHtml(row[0])).append("</td>");
            html.append("<td>").append(teamCell(row[1]));
            if (row.length > 9 && !row[9].isEmpty() && ("alt".equals(row[7]) || showPositionOnPrimary)) {
                html.append(" ").append(positionBadgeHtml(row[9]));
            }
            html.append("</td>");
            html.append("<td>").append(teamCell(row[2]));
            if (!row[3].isEmpty()) {
                html.append(" <span class=\"badge text-bg-secondary\">").append(escapeHtml(row[3])).append("</span>");
            }
            if (row.length > 10 && !row[10].isEmpty() && ("alt".equals(row[7]) || showPositionOnPrimary)) {
                html.append(" ").append(positionBadgeHtml(row[10]));
            }
            html.append("</td>");
            html.append("<td><span class=\"")
                    .append(Integer.parseInt(row[5]) >= 60 ? "fw-semibold text-success" : "fw-semibold text-warning-emphasis")
                    .append("\">")
                    .append(teamCell(row[4]))
                    .append(" (")
                    .append(escapeHtml(row[5]))
                    .append("%)</span></td>");
            if (hasOdds) {
                html.append("<td>").append(escapeHtml(row[6])).append("</td>");
                String betTag = betCandidateTag(Integer.parseInt(row[5]), row[8]);
                html.append("<td class=\"fw-semibold\"><div class=\"d-flex justify-content-between align-items-center gap-2\">")
                    .append("<span>").append(escapeHtml(row[8])).append("</span>");
                if (!betTag.isEmpty()) html.append(betCandidateBadgeHtml(betTag));
                html.append("</div></td>");
            }
            html.append("</tr>");
            // Detail (expandable) row with signal breakdown
            int colSpan = hasOdds ? 6 : 4;
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
            html.append("<div class=\"row g-3 mb-2 w-100 align-items-start\">");
            appendTeamEloBreakdown(html, team1, b1);
            html.append("<div class=\"col-auto d-flex align-items-center justify-content-center px-1\">")
                .append("<span class=\"fw-bold text-muted small\">vs</span>")
                .append("</div>");
            appendTeamEloBreakdown(html, team2, b2);
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

    private static void appendFormCircles(StringBuilder html, List<String[]> results, String teamName) {
        for (String[] entry : results) {
            String result   = entry[0];
            String opponent = entry.length > 1 ? entry[1] : "";
            String score    = entry.length > 2 ? entry[2] : "";
            String color = switch (result) {
                case "W" -> "#198754";
                case "D" -> "#adb5bd";
                default  -> "#dc3545";
            };
            String tooltipHtml = flagHtml(teamName) + escapeHtml(teamName)
                    + " vs " + flagHtml(opponent) + escapeHtml(opponent)
                    + "<br>" + escapeHtml(score);
            html.append("<span data-bs-toggle='tooltip' data-bs-html='true' data-bs-title='")
                .append(tooltipHtml.replace("'", "&#39;"))
                .append("' style=\"display:inline-block;width:13px;height:13px;border-radius:50%;background:")
                .append(color).append(";margin:1px;cursor:default\"></span>");
        }
    }

    private static void appendTeamEloBreakdown(StringBuilder html, String teamName, EloBreakdown b) {
        html.append("<div class=\"col\">")
            .append(buildTeamBreakdownHtml(teamName, b))
            .append("</div>");
    }

    /**
     * Builds the inner HTML for a single team's ELO breakdown panel.
     * Used by both the group stage expandable rows (WebController) and the
     * vs matchup detail rows (HtmlReporter). Single source of truth for
     * signal labels, emojis, notes, and table structure.
     */
    public static String buildTeamBreakdownHtml(String teamName, EloBreakdown b) {
        StringBuilder sb = new StringBuilder();
        // Flag + name header
        sb.append("<div class=\"text-center mb-2 fs-6\">")
          .append(flagHtml(teamName))
          .append("<strong>").append(escapeHtml(teamName)).append("</strong>")
          .append("</div>");
        // Tournament path — shown ABOVE qualifiers, only for knockout rounds
        if (b != null && !b.pathFatigueLabel.isEmpty()) {
            String badgeClass = switch (b.pathFatigueLabel) {
                case "Very Easy", "Easy" -> "bg-success";
                case "Hard", "Very Hard" -> "bg-danger";
                default -> "bg-secondary";
            };
            sb.append("<div class=\"text-center mb-1\">");
            sb.append("<span class=\"text-muted\" style=\"font-size:0.65rem\">tournament path</span><br>");
            if (!b.pathOpponent.isEmpty()) {
                String[] segments = b.pathOpponent.split(" > ");
                sb.append("<span style=\"font-size:0.75rem\">");
                for (int i = 0; i < segments.length; i++) {
                    if (i > 0) sb.append(" <span class='text-muted'>›</span> ");
                    String seg = segments[i].trim();
                    int colon = seg.lastIndexOf(':');
                    String name = colon > 0 ? seg.substring(0, colon) : seg;
                    sb.append(flagHtml(name)).append(escapeHtml(name));
                }
                sb.append("</span><br>");
            }
            sb.append("<span class=\"badge ").append(badgeClass).append(" fw-normal\" style=\"font-size:0.7rem\">")
              .append(escapeHtml(b.pathFatigueLabel)).append("</span>");
            sb.append("</div>");
        } else if (b != null) {
            sb.append("<div class=\"mb-1\"></div>");
        }
        // Qual form circles — always 2 divs so ELO table aligns consistently
        if (b != null && !b.qualResults.isEmpty()) {
            sb.append("<div class=\"text-center mb-1 text-muted\" style=\"font-size:0.65rem\">qualifiers</div>");
            sb.append("<div class=\"text-center mb-1\">");
            appendFormCircles(sb, b.qualResults, teamName);
            sb.append("</div>");
        } else if (b != null && b.isHost) {
            sb.append("<div class=\"text-center mb-1 text-muted\" style=\"font-size:0.65rem\">qualifiers</div>");
            sb.append("<div class=\"text-center mb-1 text-muted small fst-italic\">Host — no qualifiers</div>");
        } else if (b != null) {
            sb.append("<div class=\"mb-1\"></div><div class=\"mb-1\"></div>");
        }
        // Friendly form circles
        if (b != null && !b.friendlyResults.isEmpty()) {
            sb.append("<div class=\"text-center text-muted mb-1\" style=\"font-size:0.65rem\">friendlies</div>");
            sb.append("<div class=\"text-center mb-2\">");
            appendFormCircles(sb, b.friendlyResults, teamName);
            sb.append("</div>");
        } else if (b != null) {
            sb.append("<div class=\"mb-2\"></div>");
        }
        if (b != null) {
            sb.append("<table class=\"table table-sm table-bordered mb-0\" style=\"background:#fff\">");
            sb.append("<tbody>");
            sb.append("<tr><td class=\"text-muted small\">Base ELO</td><td class=\"text-end fw-bold\">").append(b.baseElo).append("</td></tr>");
            // Path difficulty first
            if (b.pathFatigueAdjustment != 0 || !b.pathOpponent.isEmpty()) {
                String[] pathSegs = b.pathOpponent.isEmpty() ? new String[0] : b.pathOpponent.split(" > ");
                StringBuilder breakdownHtml = new StringBuilder();
                for (String seg : pathSegs) {
                    String name = seg.trim();
                    int colon = name.lastIndexOf(':');
                    if (colon > 0) name = name.substring(0, colon);
                    if (!name.isEmpty()) {
                        if (breakdownHtml.length() > 0) breakdownHtml.append(", ");
                        breakdownHtml.append(flagHtml(name));
                    }
                }
                sb.append("<tr class=\"").append(b.pathFatigueAdjustment > 0 ? "table-success" : "table-warning").append("\">");
                sb.append("<td class=\"small\">😴 Path Fatigue</td>");
                sb.append("<td class=\"text-end fw-bold ").append(b.pathFatigueAdjustment > 0 ? "text-success" : "text-warning").append("\">")
                  .append(b.pathFatigueAdjustment > 0 ? "+" : "").append(b.pathFatigueAdjustment);
                if (pathSegs.length > 0)
                    sb.append("<br><span class='fw-normal text-muted' style='font-size:0.72rem'>").append(breakdownHtml).append("</span>");
                sb.append("</td></tr>");
            }
            // Qual Form second
            if (b.qualBonus != 0) {
                sb.append("<tr class=\"").append(b.qualBonus > 0 ? "table-success" : "table-danger").append("\">");
                sb.append("<td class=\"small\">⚔️ Qualification Form</td>");
                sb.append("<td class=\"text-end fw-bold ").append(b.qualBonus > 0 ? "text-success" : "text-danger").append("\">")
                  .append(b.qualBonus > 0 ? "+" : "").append(b.qualBonus);
                if (!b.qualResults.isEmpty()) {
                    sb.append("<br><span class='fw-normal text-muted' style='font-size:0.72rem'>");
                    for (int i = 0; i < b.qualResults.size(); i++) {
                        if (i > 0) sb.append(", ");
                        String opp = b.qualResults.get(i)[1];
                        int cv = Integer.parseInt(b.qualResults.get(i)[3]);
                        sb.append(flagHtml(opp)).append(cv >= 0 ? "+" : "").append(cv);
                    }
                    sb.append("</span>");
                }
                sb.append("</td></tr>");
            }
            // Friendly Form third
            if (b.preTournamentBonus != 0) {
                sb.append("<tr class=\"").append(b.preTournamentBonus > 0 ? "table-success" : "table-danger").append("\">");
                sb.append("<td class=\"small\">📈 Friendly Form</td>");
                sb.append("<td class=\"text-end fw-bold ").append(b.preTournamentBonus > 0 ? "text-success" : "text-danger").append("\">")
                  .append(b.preTournamentBonus > 0 ? "+" : "").append(b.preTournamentBonus);
                if (!b.friendlyResults.isEmpty()) {
                    sb.append("<br><span class='fw-normal text-muted' style='font-size:0.72rem'>");
                    for (int i = 0; i < b.friendlyResults.size(); i++) {
                        if (i > 0) sb.append(", ");
                        String opp = b.friendlyResults.get(i)[1];
                        int cv = Integer.parseInt(b.friendlyResults.get(i)[3]);
                        sb.append(flagHtml(opp)).append(cv >= 0 ? "+" : "").append(cv);
                    }
                    sb.append("</span>");
                }
                sb.append("</td></tr>");
            }
            // Rest of squad/situational signals
            if (b.homeBonus != 0)
                sb.append("<tr class=\"table-success\"><td class=\"small\">🏠 Home Advantage</td><td class=\"text-end fw-bold text-success\">+").append(b.homeBonus).append("</td></tr>");
            if (b.squadAgePenalty != 0)
                sb.append("<tr class=\"table-warning\"><td class=\"small\">👶 Squad Age</td><td class=\"text-end fw-bold text-warning\">−").append(b.squadAgePenalty)
                  .append(b.ageNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.ageNotes) + "</span>")
                  .append("</td></tr>");
            if (b.squadCohesionPenalty != 0)
                sb.append("<tr class=\"table-danger\"><td class=\"small\">🤝 Squad Cohesion</td><td class=\"text-end fw-bold text-danger\">−").append(b.squadCohesionPenalty)
                  .append(b.cohesionNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.cohesionNotes) + "</span>")
                  .append("</td></tr>");
            if (b.squadDepthPenalty != 0)
                sb.append("<tr class=\"table-danger\"><td class=\"small\">⬇️ Bench Depth</td><td class=\"text-end fw-bold text-danger\">−").append(b.squadDepthPenalty)
                  .append(b.depthNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.depthNotes) + "</span>")
                  .append("</td></tr>");
            if (b.squadQualityBonus != 0)
                sb.append("<tr class=\"table-success\"><td class=\"small\">⭐ Squad Quality</td><td class=\"text-end fw-bold text-success\">+").append(b.squadQualityBonus)
                  .append(b.qualityNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.qualityNotes) + "</span>")
                  .append("</td></tr>");
            if (b.dropoutPenalty != 0)
                sb.append("<tr class=\"table-danger\"><td class=\"small\">👤 Squad Dropouts</td><td class=\"text-end fw-bold text-danger\">−").append(b.dropoutPenalty)
                  .append(b.dropoutNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.dropoutNotes) + "</span>")
                  .append("</td></tr>");
            if (b.injuryPenalty != 0)
                sb.append("<tr class=\"table-danger\"><td class=\"small\">🤕 Injuries</td><td class=\"text-end fw-bold text-danger\">−").append(b.injuryPenalty)
                  .append(b.injuryNotes.isEmpty() ? "" : "<br><span class='fw-normal text-muted' style='font-size:0.75rem'>" + escapeHtml(b.injuryNotes) + "</span>")
                  .append("</td></tr>");
            if (b.heatBonus != 0)
                sb.append("<tr class=\"table-warning\"><td class=\"small\">🔥 Heat Advantage</td><td class=\"text-end fw-bold\">+").append(b.heatBonus).append("</td></tr>");
            sb.append("<tr class=\"table-primary\"><td class=\"fw-bold\">Adjusted ELO</td><td class=\"text-end fw-bold\">").append(b.totalElo).append("</td></tr>");
            sb.append("</tbody></table>");
        } else {
            sb.append("<div class=\"text-muted small fst-italic\">No breakdown data available.</div>");
        }
        return sb.toString();
    }

    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (NumberFormatException e) {
            return 0;
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
        html.append("<script>")
                .append("function applyFilters(section){")
                .append("const pathBtn=section.querySelector('.path-btn.active');")
                .append("const path=pathBtn?pathBtn.dataset.path:'predicted';")
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
                .append("if(!show){detail.dataset.expanded='false';const icon=row.querySelector('.expand-icon');if(icon)icon.textContent='▶';}")
                .append("}")
                .append("});}")
                .append("function filterPath(btn){btn.closest('.btn-group').querySelectorAll('.btn').forEach(b=>b.classList.remove('active'));btn.classList.add('active');localStorage.setItem('predictor_path',btn.dataset.path);applyFilters(btn.closest('.output-section'));}")
                .append("function filterTeam(sel){localStorage.setItem('predictor_team',sel.value);applyFilters(sel.closest('.output-section'));}")
                .append("function toggleDetail(row){")
                .append("const detail=row.nextElementSibling;")
                .append("if(!detail||!detail.classList.contains('detail-row'))return;")
                .append("const isHidden=detail.style.display==='none';")
                .append("detail.style.display=isHidden?'table-row':'none';")
                .append("detail.dataset.expanded=isHidden?'true':'false';")
                .append("const icon=row.querySelector('.expand-icon');")
                .append("if(icon)icon.textContent=isHidden?'▼':'▶';")
                .append("}")
                .append("document.addEventListener('DOMContentLoaded',function(){")
                .append("const savedPath=localStorage.getItem('predictor_path');")
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
        if (label.contains("Last 32"))       return "Odds to reach Last 16";
        if (label.contains("Last 16"))       return "Odds to reach QF";
        if (label.contains("Quarter"))       return "Odds to reach SF";
        if (label.contains("Semi"))          return "Odds to reach Final";
        if (label.contains("Final"))         return "Odds to win";
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

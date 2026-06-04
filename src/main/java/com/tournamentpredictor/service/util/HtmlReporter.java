package com.tournamentpredictor.service.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HtmlReporter extends ConsoleReporter {
    private static final Map<String, String> ISO_CODES = new HashMap<>();

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

    private final StringBuilder html = new StringBuilder();
    private boolean pathFilterScriptAdded = false;

    public HtmlReporter() {
        setShowFlags(true);
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
        if (csvLines == null || csvLines.isEmpty()) {
            return;
        }

        String[] headers = csvLines.get(0).split(",", -1);
        int matchIdIdx = indexOf(headers, "match_id");
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int pathIdx = indexOf(headers, "path");
        int predIdx = indexOf(headers, "prediction");
        if (predIdx < 0) predIdx = indexOf(headers, "predicted_winner");
        if (predIdx < 0) predIdx = indexOf(headers, "elo");
        int eloIdx = indexOf(headers, "elo");

        if (team1Idx < 0 || team2Idx < 0 || predIdx < 0) {
            appendPre(String.join(System.lineSeparator(), csvLines));
            return;
        }

        Map<String, Integer> primaryMatchIdCount = new HashMap<>();
        for (int i = 1; i < csvLines.size(); i++) {
            String[] cols = csvLines.get(i).split(",", -1);
            String rowPath = valueAt(cols, pathIdx);
            boolean isPrimary = pathIdx < 0 || "primary".equalsIgnoreCase(rowPath);
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
            boolean isPrimary = pathIdx < 0 || "primary".equalsIgnoreCase(rowPath);

            String team1 = eloCalculator.extractTeamName(valueAt(cols, team1Idx));
            String team2 = eloCalculator.extractTeamName(valueAt(cols, team2Idx));
            String filter = getTeamFilter();
            if (filter != null && !filter.isEmpty()) {
                String f = filter.toLowerCase();
                if (!team1.toLowerCase().contains(f) && !team2.toLowerCase().contains(f)) continue;
            }

            String matchId = valueAt(cols, matchIdIdx);
            if (isPrimary) {
                if (seen.contains(matchId)) {
                    // Extra primary scenario — include as alt so it shows in "Both" view
                    String[] altCols = cols.clone();
                    if (pathIdx >= 0 && pathIdx < altCols.length) altCols[pathIdx] = "alt";
                    String key = matchId + "|" + team1 + "|" + team2;
                    if (!seen.contains(key)) { seen.add(key); rowsToPrint.add(altCols); }
                } else {
                    seen.add(matchId);
                    rowsToPrint.add(cols);
                }
            } else {
                String key = matchId + "|" + team1 + "|" + team2;
                if (seen.contains(key)) continue;
                seen.add(key);
                rowsToPrint.add(cols);
            }
        }

        html.append("<div class=\"output-section mb-4\">");
        appendSummary(primaryMatchIdCount.size());
        appendPathFilterScript();

        boolean hasOdds = false;
        List<String[]> tableRows = new ArrayList<>();
        for (String[] cols : rowsToPrint) {
            String matchId = valueAt(cols, matchIdIdx);
            String team1 = eloCalculator.extractTeamName(valueAt(cols, team1Idx));
            String team2 = eloCalculator.extractTeamName(valueAt(cols, team2Idx));
            String pred = valueAt(cols, predIdx);
            String winner = eloCalculator.parseTeamFromPrediction(pred);
            int pct = eloCalculator.parsePctFromPrediction(pred);
            String rowPath = valueAt(cols, pathIdx);
            boolean isPrimary = pathIdx < 0 || "primary".equalsIgnoreCase(rowPath);
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
            tableRows.add(new String[]{matchId, team1, team2, suffix, winner, String.valueOf(pct), oddsCell, isPrimary ? "primary" : "alt", netWinningsCell});
        }

        if (tableRows.isEmpty()) {
            html.append("<div class=\"alert alert-warning\">No rows available for this view.</div></div>");
            return;
        }

        long altCount = tableRows.stream().filter(r -> "alt".equals(r[7])).count();
        boolean showToggle = altCount > 0;

        if (showToggle) {
            html.append("<div class=\"d-flex gap-2 mb-2\"><div class=\"btn-group btn-group-sm\" role=\"group\">")
                    .append("<button type=\"button\" class=\"btn btn-outline-secondary active path-btn\" data-path=\"primary\" onclick=\"filterPath(this)\">Primary</button>")
                    .append("<button type=\"button\" class=\"btn btn-outline-secondary path-btn\" data-path=\"alt\" onclick=\"filterPath(this)\">Alternative</button>")
                    .append("<button type=\"button\" class=\"btn btn-outline-secondary path-btn\" data-path=\"both\" onclick=\"filterPath(this)\">Both</button>")
                    .append("</div></div>");
        }
        html.append("<div class=\"table-responsive\"><table class=\"table table-striped table-hover table-sm align-middle\">");
        html.append("<thead class=\"table-dark\"><tr><th>Match</th><th>Team 1</th><th>Team 2</th><th>Winner</th>");
        if (hasOdds) html.append("<th>Odds</th><th>Net Winnings (Bet=£10)</th>");
        html.append("</tr></thead><tbody>");

        for (String[] row : tableRows) {
            html.append("<tr data-path=\"").append(row[7]).append("\"");
            if (!"primary".equals(row[7])) {
                html.append(" style=\"display:none\"");
            }
            html.append(">");
            html.append("<td>").append(escapeHtml(row[0])).append("</td>");
            html.append("<td>").append(teamCell(row[1])).append("</td>");
            html.append("<td>").append(teamCell(row[2]));
            if (!row[3].isEmpty()) {
                html.append(" <span class=\"badge text-bg-secondary\">").append(escapeHtml(row[3])).append("</span>");
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
                html.append("<td class=\"fw-semibold\">").append(escapeHtml(row[8])).append("</td>");
            }
            html.append("</tr>");
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

    private void appendSummary(int matchupCount) {
    }

    private void appendPathFilterScript() {
        if (pathFilterScriptAdded) {
            return;
        }
        html.append("<script>function filterPath(btn){const section=btn.closest('.output-section');const path=btn.dataset.path;section.querySelectorAll('tbody tr').forEach(row=>{const rp=row.dataset.path;row.style.display=(path==='both'||rp===path)?'':'none';});btn.closest('.btn-group').querySelectorAll('.btn').forEach(b=>b.classList.remove('active'));btn.classList.add('active');}</script>");
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

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

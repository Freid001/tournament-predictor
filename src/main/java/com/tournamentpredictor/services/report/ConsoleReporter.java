package com.tournamentpredictor.services.report;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.services.calculation.EloCalculator;
import org.fusesource.jansi.Ansi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ConsoleReporter {

    // ANSI enabled when connected to a real terminal and NO_COLOR is not set
    public static final boolean ANSI_ENABLED =
            System.getenv("NO_COLOR") == null && System.console() != null;

    private String teamFilter = null;   // --filter=x (null = no filter)
    private String pathFilter = "both"; // --path=primary|alt|both (default: both)
    private int    pageNumber = 1;      // --page=N (default: 1)
    private boolean showFlags = false;  // --flags (default: off)

    private double bettingStrongCandidateMinProfit = 20.0;
    private double bettingCandidateMinProfit       = 10.0;
    private double bettingWeakCandidateMinProfit   = 5.0;
    private int    bettingCandidateMinPct          = 40;
    private int    bettingRiskyMinPct              = 30;
    private double bettingMoonshotMinProfit        = 20.0;

    @Autowired(required = false)
    public void setPredictionConfig(PredictionConfig config) {
        this.bettingStrongCandidateMinProfit = config.getBettingStrongCandidateMinProfit();
        this.bettingCandidateMinProfit       = config.getBettingCandidateMinProfit();
        this.bettingWeakCandidateMinProfit   = config.getBettingWeakCandidateMinProfit();
        this.bettingCandidateMinPct          = config.getBettingCandidateMinPct();
        this.bettingRiskyMinPct              = config.getBettingRiskyMinPct();
        this.bettingMoonshotMinProfit        = config.getBettingMoonshotMinProfit();
    }

    public void setTeamFilter(String teamFilter) { this.teamFilter = teamFilter; }
    public void setPathFilter(String pathFilter) { this.pathFilter = pathFilter; }
    public void setPageNumber(int pageNumber)    { this.pageNumber = pageNumber; }
    public void setShowFlags(boolean showFlags)  { this.showFlags = showFlags; }
    public String getTeamFilter() { return teamFilter; }
    public String getPathFilter() { return pathFilter; }
    public int    getPageNumber() { return pageNumber; }
    public boolean isShowFlags()  { return showFlags; }

    private static final Map<String, String> FLAGS = new HashMap<>();
    private static final String FLAG_PLACEHOLDER = "🏳️";

    static {
        FLAGS.put("Algeria", "🇩🇿");
        FLAGS.put("Argentina", "🇦🇷");
        FLAGS.put("Australia", "🇦🇺");
        FLAGS.put("Austria", "🇦🇹");
        FLAGS.put("Belgium", "🇧🇪");
        FLAGS.put("Bolivia", "🇧🇴");
        FLAGS.put("Bosnia and Herzegovina", "🇧🇦");
        FLAGS.put("Brazil", "🇧🇷");
        FLAGS.put("Cameroon", "🇨🇲");
        FLAGS.put("Cape Verde", "🇨🇻");
        FLAGS.put("Chile", "🇨🇱");
        FLAGS.put("China", "🇨🇳");
        FLAGS.put("Colombia", "🇨🇴");
        FLAGS.put("Costa Rica", "🇨🇷");
        FLAGS.put("Croatia", "🇭🇷");
        FLAGS.put("Czech Republic", "🇨🇿");
        FLAGS.put("Czechia", "🇨🇿");
        FLAGS.put("Denmark", "🇩🇰");
        FLAGS.put("Ecuador", "🇪🇨");
        FLAGS.put("Egypt", "🇪🇬");
        FLAGS.put("England", "🇬🇧");
        FLAGS.put("France", "🇫🇷");
        FLAGS.put("Germany", "🇩🇪");
        FLAGS.put("Ghana", "🇬🇭");
        FLAGS.put("Honduras", "🇭🇳");
        FLAGS.put("Hungary", "🇭🇺");
        FLAGS.put("Indonesia", "🇮🇩");
        FLAGS.put("Iran", "🇮🇷");
        FLAGS.put("Iraq", "🇮🇶");
        FLAGS.put("Italy", "🇮🇹");
        FLAGS.put("Ivory Coast", "🇨🇮");
        FLAGS.put("Jamaica", "🇯🇲");
        FLAGS.put("Japan", "🇯🇵");
        FLAGS.put("Kenya", "🇰🇪");
        FLAGS.put("Mexico", "🇲🇽");
        FLAGS.put("Morocco", "🇲🇦");
        FLAGS.put("Netherlands", "🇳🇱");
        FLAGS.put("New Zealand", "🇳🇿");
        FLAGS.put("Nigeria", "🇳🇬");
        FLAGS.put("Norway", "🇳🇴");
        FLAGS.put("Panama", "🇵🇦");
        FLAGS.put("Paraguay", "🇵🇾");
        FLAGS.put("Peru", "🇵🇪");
        FLAGS.put("Poland", "🇵🇱");
        FLAGS.put("Portugal", "🇵🇹");
        FLAGS.put("Qatar", "🇶🇦");
        FLAGS.put("Romania", "🇷🇴");
        FLAGS.put("Saudi Arabia", "🇸🇦");
        FLAGS.put("Scotland", "🇬🇧");
        FLAGS.put("Senegal", "🇸🇳");
        FLAGS.put("Serbia", "🇷🇸");
        FLAGS.put("Slovakia", "🇸🇰");
        FLAGS.put("South Africa", "🇿🇦");
        FLAGS.put("South Korea", "🇰🇷");
        FLAGS.put("Spain", "🇪🇸");
        FLAGS.put("Sweden", "🇸🇪");
        FLAGS.put("Switzerland", "🇨🇭");
        FLAGS.put("Tunisia", "🇹🇳");
        FLAGS.put("Turkey", "🇹🇷");
        FLAGS.put("Ukraine", "🇺🇦");
        FLAGS.put("United States", "🇺🇸");
        FLAGS.put("Uruguay", "🇺🇾");
        FLAGS.put("Uzbekistan", "🇺🇿");
        FLAGS.put("Venezuela", "🇻🇪");
        FLAGS.put("Wales", "🇬🇧");
    }

    private static final int COL1_WIDTH = 26;
    private static final int COL2_WIDTH = 47; // COL1_WIDTH + len(" (possible 3rd place)") = 26 + 21
    private static final int WINNER_COL_WIDTH = 24; // flag + name + (pct%) — covers "United States (68%)"

    /** Wraps a pre-padded winner cell in colour based on win %. Colour is applied AFTER padding. */
    private static String colorWinner(String paddedCell, int pct) {
        if (!ANSI_ENABLED) return paddedCell;
        return Ansi.ansi().bold()
                .fg(pct >= 60 ? Ansi.Color.GREEN : Ansi.Color.YELLOW)
                .a(paddedCell).boldOff().reset().toString();
    }

    /** Wraps a pre-padded header cell in bold. */
    public static String boldHeader(String paddedCell) {
        if (!ANSI_ENABLED) return paddedCell;
        return Ansi.ansi().bold().a(paddedCell).boldOff().reset().toString();
    }

    /** Colours a border line (┌─┬─┐ etc.) in cyan. */
    public static String colorBorder(String border) {
        if (!ANSI_ENABLED) return border;
        return Ansi.ansi().fgCyan().a(border).reset().toString();
    }

    /** Colours a match-step cell based on the selected team's win probability. Applied AFTER padding. */
    public static String colorMatchStep(String paddedCell, int teamWinPct) {
        if (!ANSI_ENABLED) return paddedCell;
        Ansi.Color color = teamWinPct >= 60 ? Ansi.Color.GREEN
                         : teamWinPct >= 50 ? Ansi.Color.YELLOW
                         : Ansi.Color.RED;
        return Ansi.ansi().fg(color).a(paddedCell).reset().toString();
    }

    /** Colours a result cell: green + trophy if won, red if lost. Applied AFTER padding. */
    public static String colorResult(String paddedCell, boolean won) {
        if (!ANSI_ENABLED) return paddedCell;
        return won ? Ansi.ansi().bold().fgGreen().a(paddedCell).reset().toString()
                   : Ansi.ansi().fgRed().a(paddedCell).reset().toString();
    }

    /** Colours a difficulty cell: easy=green, medium=yellow, hard=red. Applied AFTER padding. */
    public static String colorDifficulty(String paddedCell, String label) {
        if (!ANSI_ENABLED) return paddedCell;
        Ansi.Color color = "easy".equalsIgnoreCase(label) ? Ansi.Color.GREEN
                         : "medium".equalsIgnoreCase(label) ? Ansi.Color.YELLOW
                         : Ansi.Color.RED;
        return Ansi.ansi().fg(color).a(paddedCell).reset().toString();
    }

    /** Bolds a section header like "=== England — tournament paths ===". */
    public static String colorSectionHeader(String text) {
        if (!ANSI_ENABLED) return text;
        return Ansi.ansi().bold().fgCyan().a(text).reset().toString();
    }

    /** Extracts the win % integer from a rendered winner cell like "🇩🇪 Germany (67%)". */
    private static int extractPctFromCell(String cell) {
        int open = cell.lastIndexOf('(');
        int close = cell.lastIndexOf('%');
        if (open < 0 || close <= open) return -1;
        try { return Integer.parseInt(cell.substring(open + 1, close).trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    public static void printGeneratedFile(Path generatedFile, int matchupCount, String nextMode) {
        System.out.println();
        System.out.println("  Generated " + matchupCount + " matchups");
        System.out.println("  Model-selected path written to: " + generatedFile.toAbsolutePath());
        System.out.println("  Run --mode=" + nextMode + " to continue");
        System.out.println();
    }

    private static final int PAGE_SIZE = 50;

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    private static String padRight(String s, int w) {
        int pad = w - s.length();
        return pad > 0 ? s + repeat(' ', pad) : s;
    }

    private static String boxBorder(char left, char mid, char right, int... widths) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) sb.append(mid);
            sb.append(repeat('─', widths[i] + 2));
        }
        sb.append(right);
        return sb.toString();
    }

    public void printMatchups(String label, List<String> csvLines, EloCalculator eloCalculator,
                                     Path predictionsFile, Map<String, String> teamOdds) {
        if (csvLines.isEmpty()) return;

        String[] headers = csvLines.get(0).split(",", -1);
        int matchIdIdx = indexOf(headers, "match_id");
        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int pathIdx = indexOf(headers, "path");
        int predIdx = indexOf(headers, "prediction");
        if (predIdx < 0) predIdx = indexOf(headers, "predicted_winner");
        if (predIdx < 0) predIdx = indexOf(headers, "elo");

        if (team1Idx < 0 || team2Idx < 0 || predIdx < 0) return;

        // Always count unique primary matchups for the summary line
        Map<String, Integer> primaryMatchIdCount = new HashMap<>();
        for (int i = 1; i < csvLines.size(); i++) {
            String[] cols = csvLines.get(i).split(",", -1);
            if (pathIdx >= 0 && !"predicted".equalsIgnoreCase(valueAt(cols, pathIdx))) continue;
            String matchId = valueAt(cols, matchIdIdx);
            if (!matchId.isEmpty()) primaryMatchIdCount.merge(matchId, 1, Integer::sum);
        }

        // Collect rows to display based on path + team filters
        boolean showPrimary = "predicted".equalsIgnoreCase(pathFilter) || "both".equalsIgnoreCase(pathFilter);
        boolean showAlt = "alt".equalsIgnoreCase(pathFilter) || "both".equalsIgnoreCase(pathFilter);

        // For primary rows: track 3rd-place matches (same matchId appears twice in primary)
        Map<String, Integer> displayMatchIdCount = showPrimary ? primaryMatchIdCount : new HashMap<>();

        List<String[]> rowsToPrint = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 1; i < csvLines.size(); i++) {
            String line = csvLines.get(i);
            if (line.trim().isEmpty()) continue;
            String[] cols = line.split(",", -1);
            String rowPath = valueAt(cols, pathIdx);
            boolean isPrimary = "predicted".equalsIgnoreCase(rowPath);

            if (isPrimary && !showPrimary) continue;
            if (!isPrimary && !showAlt) continue;

            String team1 = eloCalculator.extractTeamName(valueAt(cols, team1Idx));
            String team2 = eloCalculator.extractTeamName(valueAt(cols, team2Idx));

            if (teamFilter != null && !teamFilter.isEmpty()) {
                String f = teamFilter.toLowerCase();
                if (!team1.toLowerCase().contains(f) && !team2.toLowerCase().contains(f)) continue;
            }

            String matchId = valueAt(cols, matchIdIdx);
            if (isPrimary) {
                boolean isThirdPlace = displayMatchIdCount.getOrDefault(matchId, 1) > 1;
                if (!isThirdPlace) {
                    if (seen.contains(matchId)) continue;
                    seen.add(matchId);
                }
            } else {
                // alt rows: dedupe by matchId+team1+team2
                String key = matchId + "|" + team1 + "|" + team2;
                if (seen.contains(key)) continue;
                seen.add(key);
            }

            rowsToPrint.add(cols);
        }

        System.out.println();
        System.out.println("  Generated " + primaryMatchIdCount.size() + " matchups");
        if (teamFilter != null || !"both".equalsIgnoreCase(pathFilter)) {
            System.out.println("  Showing " + rowsToPrint.size() + " rows"
                    + (teamFilter != null ? "  (--filter=" + teamFilter + ")" : "")
                    + (!"both".equalsIgnoreCase(pathFilter) ? "  (--path=" + pathFilter + ")" : ""));
        }
        if (predictionsFile != null) {
            String nextMode = predictionsFile.getFileName().toString().replace(".csv", "");
            System.out.println("  Model-selected path written to: " + predictionsFile.toAbsolutePath());
            System.out.println("  Run --mode=" + nextMode + " to continue");
        }
        System.out.println();
        System.out.println(colorSectionHeader("=== " + label + " ==="));
        System.out.println();

        // --- Build row data for table ---
        boolean hasOdds = false;
        List<String[]> tableRows = new ArrayList<>(); // [homeCell, awayCell, winnerCell, oddsCell]
        for (String[] cols : rowsToPrint) {
            String matchId = valueAt(cols, matchIdIdx);
            String team1 = eloCalculator.extractTeamName(valueAt(cols, team1Idx));
            String team2 = eloCalculator.extractTeamName(valueAt(cols, team2Idx));
            String flag1 = showFlags ? FLAGS.getOrDefault(team1, FLAG_PLACEHOLDER) + " " : "";
            String flag2 = showFlags ? FLAGS.getOrDefault(team2, FLAG_PLACEHOLDER) + " " : "";
            String pred = valueAt(cols, predIdx);
            String winner = eloCalculator.parseTeamFromPrediction(pred);
            int pct = eloCalculator.parsePctFromPrediction(pred);
            boolean isThirdPlace = "predicted".equalsIgnoreCase(valueAt(cols, pathIdx))
                    && displayMatchIdCount.getOrDefault(matchId, 1) > 1;
            boolean isAlt = "alt".equalsIgnoreCase(valueAt(cols, pathIdx));
            String suffix = isThirdPlace ? " (3rd place)" : isAlt ? " (alt)" : "";
            String flagWinner = showFlags ? FLAGS.getOrDefault(winner, FLAG_PLACEHOLDER) + " " : "";
            String homeCell   = flag1 + team1;
            String awayCell   = flag2 + team2 + suffix;
            String winnerCell = flagWinner + winner + " (" + pct + "%)";
            String oddsCell   = "";
            if (teamOdds != null && !teamOdds.isEmpty()) {
                String odds = teamOdds.get(winner);
                if (odds != null && !odds.isEmpty()) {
                    double netVal = calcNetWinningsDouble(odds, 10.0);
                    String net = netVal >= 0 ? String.format("%.2f", netVal) : "";
                    String bettingLabel = netVal >= 0 ? getBettingLabel(pct, netVal) : "";
                    if (!bettingLabel.isEmpty() && isAlt) bettingLabel = "Alt " + bettingLabel;
                    oddsCell = odds + (net.isEmpty() ? "" : "  ·  £" + net)
                            + (bettingLabel.isEmpty() ? "" : "  ·  " + bettingLabel);
                    hasOdds = true;
                }
            }
            tableRows.add(new String[]{homeCell, awayCell, winnerCell, oddsCell});
        }

        // --- Pagination ---
        int page = Math.max(1, pageNumber);
        int totalPages = Math.max(1, (int) Math.ceil((double) tableRows.size() / PAGE_SIZE));
        page = Math.min(page, totalPages);
        int fromIdx = (page - 1) * PAGE_SIZE;
        int toIdx   = Math.min(fromIdx + PAGE_SIZE, tableRows.size());
        List<String[]> pageRows = tableRows.subList(fromIdx, toIdx);

        // --- Compute column widths ---
        int homeW   = "TEAM 1".length();
        int awayW   = "TEAM 2".length();
        int winnerW = "WINNER (win %)".length();
        int oddsW   = "ODDS".length();
        for (String[] row : pageRows) {
            homeW   = Math.max(homeW,   displayWidth(row[0]) + 2);
            awayW   = Math.max(awayW,   displayWidth(row[1]) + 2);
            winnerW = Math.max(winnerW, displayWidth(row[2]) + 2);
            if (hasOdds) oddsW = Math.max(oddsW, row[3].length());
        }

        // --- Print table ---
        if (hasOdds) {
            System.out.println(colorBorder(boxBorder('┌', '┬', '┐', homeW, awayW, winnerW, oddsW)));
            System.out.println("│ " + boldHeader(padRight("TEAM 1", homeW)) + " │ " + boldHeader(padRight("TEAM 2", awayW))
                + " │ " + boldHeader(padRight("WINNER (win %)", winnerW)) + " │ " + boldHeader(padRight("ODDS", oddsW)) + " │");
            System.out.println(colorBorder(boxBorder('├', '┼', '┤', homeW, awayW, winnerW, oddsW)));
            for (String[] row : pageRows) {
                String winnerPadded = padToDisplayWidth(row[2], winnerW);
                String winnerColored = colorWinner(winnerPadded, extractPctFromCell(row[2]));
                System.out.println("│ " + padToDisplayWidth(row[0], homeW) + " │ " + padToDisplayWidth(row[1], awayW)
                    + " │ " + winnerColored + " │ " + padRight(row[3], oddsW) + " │");
            }
            System.out.println(colorBorder(boxBorder('└', '┴', '┘', homeW, awayW, winnerW, oddsW)));
        } else {
            System.out.println(colorBorder(boxBorder('┌', '┬', '┐', homeW, awayW, winnerW)));
            System.out.println("│ " + boldHeader(padRight("TEAM 1", homeW)) + " │ " + boldHeader(padRight("TEAM 2", awayW))
                + " │ " + boldHeader(padRight("WINNER (win %)", winnerW)) + " │");
            System.out.println(colorBorder(boxBorder('├', '┼', '┤', homeW, awayW, winnerW)));
            for (String[] row : pageRows) {
                String winnerPadded = padToDisplayWidth(row[2], winnerW);
                String winnerColored = colorWinner(winnerPadded, extractPctFromCell(row[2]));
                System.out.println("│ " + padToDisplayWidth(row[0], homeW) + " │ " + padToDisplayWidth(row[1], awayW)
                    + " │ " + winnerColored + " │");
            }
            System.out.println(colorBorder(boxBorder('└', '┴', '┘', homeW, awayW, winnerW)));
        }
        if (totalPages > 1) {
            System.out.println("  Page " + page + " of " + totalPages + " (" + tableRows.size() + " total matchups)");
            if (page < totalPages) System.out.println("  Next page: add --page=" + (page + 1) + " to your command");
        }
        System.out.println();
    }

    public static String padToDisplayWidth(String s, int targetWidth) {
        return s + " ".repeat(Math.max(0, targetWidth - displayWidth(s)));
    }

    public static int displayWidth(String s) {
        int width = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0xE0000 && cp <= 0xE007F) continue; // tag chars (subdivision flags) — zero width
            if (cp == 0x200D || (cp >= 0xFE00 && cp <= 0xFE0F)) continue; // ZWJ / variation selectors
            if (cp >= 0x1F1E6 && cp <= 0x1F1FF) { width += 1; continue; } // regional indicators: pair = 2 cols
            width += cp > 0xFFFF ? 2 : 1; // emoji = 2, ASCII/BMP = 1
        }
        return width;
    }

    private static int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private String getBettingLabel(int eloPct, double net) {
        if (eloPct >= bettingCandidateMinPct && net >= bettingStrongCandidateMinProfit) return "Strong Candidate";
        if (eloPct >= bettingCandidateMinPct && net >= bettingCandidateMinProfit) return "Candidate";
        if (eloPct >= bettingCandidateMinPct && net > bettingWeakCandidateMinProfit) return "Weak Candidate";
        if (eloPct >= bettingRiskyMinPct && eloPct < bettingCandidateMinPct && net >= bettingCandidateMinProfit) return "Risky Candidate";
        if (eloPct < bettingRiskyMinPct && net >= bettingMoonshotMinProfit) return "Moonshot";
        return "";
    }

    private static double calcNetWinningsDouble(String fractionalOdds, double stake) {
        if (fractionalOdds == null || fractionalOdds.isEmpty()) return -1;
        int slash = fractionalOdds.indexOf('/');
        if (slash < 0) return -1;
        try {
            double num = Double.parseDouble(fractionalOdds.substring(0, slash).trim());
            double den = Double.parseDouble(fractionalOdds.substring(slash + 1).trim());
            if (den == 0) return -1;
            return (num / den) * stake;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String calcNetWinnings(String fractionalOdds, double stake) {
        double net = calcNetWinningsDouble(fractionalOdds, stake);
        return net >= 0 ? String.format("%.2f", net) : "";
    }

    public static String getFlag(String team) {
        return FLAGS.getOrDefault(team, FLAG_PLACEHOLDER);
    }

    public String flagFor(String team) {
        return showFlags ? FLAGS.getOrDefault(team, FLAG_PLACEHOLDER) + " " : "";
    }

    private static String valueAt(String[] cols, int idx) {
        return idx >= 0 && idx < cols.length ? cols[idx].trim() : "";
    }
}

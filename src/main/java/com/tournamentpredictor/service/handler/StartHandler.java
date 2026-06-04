package com.tournamentpredictor.service.handler;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.CsvHelper;
import com.tournamentpredictor.service.util.HeadToHeadCalculator;
import com.tournamentpredictor.service.util.QualificationFormCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

public class StartHandler {
    private static final Logger log = LoggerFactory.getLogger(StartHandler.class);

    final int HOME_ADVANTAGE;
    final int[] INJURY_PENALTIES;
    final int[] HEAT_BONUSES;
    final int[] SQUAD_DROPOUT_PENALTIES;

    private final int CONFIDENCE_GAP;
    private static final List<String> REQUIRED_HEADERS = Arrays.asList("group", "team", "host", "injury_impact");
    private static final Set<String> VALID_HOST_VALUES = Set.of("yes", "no");

    private final CsvLoader loader;
    private final Path projectRoot;
    private final CsvHelper csvHelper;
    private final HeadToHeadCalculator headToHeadCalculator;
    private final double eloBaseWeight;
    private final double qualFormWeight;

    public StartHandler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                        HeadToHeadCalculator headToHeadCalculator) {
        this(loader, projectRoot, csvHelper, headToHeadCalculator,
                100, new int[]{0, 25, 50, 100}, new int[]{0, 8, 15, 25}, new int[]{0, 20, 40, 75}, 25, 0.85, 0.15);
    }

    public StartHandler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                        HeadToHeadCalculator headToHeadCalculator, PredictionConfig config) {
        this(loader, projectRoot, csvHelper, headToHeadCalculator,
                config.getHomeAdvantageElo(), config.getInjuryPenalties(), config.getHeatBonuses(),
                config.getSquadDropoutPenalties(), config.getConfidenceGap(),
                config.getEloWeight(), config.getQualFormWeight());
    }

    private StartHandler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                         HeadToHeadCalculator headToHeadCalculator,
                         int homeAdvantageElo, int[] injuryPenalties, int[] heatBonuses,
                         int[] squadDropoutPenalties, int confidenceGap,
                         double eloBaseWeight, double qualFormWeight) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.csvHelper = csvHelper;
        this.headToHeadCalculator = headToHeadCalculator;
        this.HOME_ADVANTAGE = homeAdvantageElo;
        this.INJURY_PENALTIES = injuryPenalties;
        this.HEAT_BONUSES = heatBonuses;
        this.SQUAD_DROPOUT_PENALTIES = squadDropoutPenalties;
        this.CONFIDENCE_GAP = confidenceGap;
        this.eloBaseWeight = eloBaseWeight;
        this.qualFormWeight = qualFormWeight;
    }

    public void handle(String tournament) throws IOException {
        Path predictionDir = projectRoot.resolve("data").resolve("predictions").resolve(tournament);
        Path groupsFile = predictionDir.resolve("groups.csv");
        if (csvHelper.isLocked(groupsFile)) {
            System.out.println("  🔒 Output already exists: " + groupsFile + " — delete to re-run");
            System.out.println("  Run --mode=groups to continue");
            return;
        }

        Path startFile = predictionDir.resolve("start.csv");
        if (!Files.exists(startFile)) {
            throw new IOException("start.csv not found: " + startFile + ". Create data/predictions/" + tournament + "/start.csv first.");
        }

        List<String> startLines = Files.readAllLines(startFile);
        Map<String, Integer> startIndexes = validateStartCsv(startLines, startFile);
        int groupIdx = startIndexes.get("group");
        int teamIdx = startIndexes.get("team");
        int hostIdx = startIndexes.get("host");
        int injuryIdx = startIndexes.get("injury_impact");
        int heatIdx = startIndexes.getOrDefault("heat_impact", -1);
        int squadDropoutIdx = startIndexes.getOrDefault("squad_dropouts", -1);

        Map<String, Integer> eloRatings = loader.loadElo();
        QualificationFormCalculator qualCalc = new QualificationFormCalculator(
                projectRoot.resolve("data").resolve("elo").resolve("history"), 2023);

        Map<String, List<String[]>> groups = new LinkedHashMap<>();
        for (int i = 1; i < startLines.size(); i++) {
            String line = startLines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split(",", -1);
            String group = cols[groupIdx].trim().toUpperCase();
            String team = cols[teamIdx].trim();
            boolean isHost = cols[hostIdx].trim().equalsIgnoreCase("yes");
            int injuryLevel = Integer.parseInt(cols[injuryIdx].trim());
            int heatLevel = (heatIdx >= 0 && cols.length > heatIdx && !cols[heatIdx].trim().isEmpty())
                    ? Integer.parseInt(cols[heatIdx].trim()) : 0;
            int squadDropoutLevel = (squadDropoutIdx >= 0 && cols.length > squadDropoutIdx && !cols[squadDropoutIdx].trim().isEmpty())
                    ? Integer.parseInt(cols[squadDropoutIdx].trim()) : 0;
            int elo = eloRatings.getOrDefault(team, 0);
            if (elo == 0) {
                log.warn("Team '{}' not found in world.csv, ELO defaulting to 0", team);
            }
            if (elo > 0) {
                if (isHost) {
                    elo += HOME_ADVANTAGE;
                }
                elo -= INJURY_PENALTIES[injuryLevel];
                elo += HEAT_BONUSES[heatLevel];
                elo -= SQUAD_DROPOUT_PENALTIES[squadDropoutLevel];
            }
            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(new String[]{team, String.valueOf(elo)});
        }

        List<String> output = new ArrayList<>();
        output.add("group,team,elo_ranking,qualification_form,h2h,predicted_position,group_winner,runner_up,3rd_place");
        boolean first = true;
        for (Map.Entry<String, List<String[]>> entry : groups.entrySet()) {
            if (!first) {
                output.add("");
            }
            first = false;
            String group = entry.getKey();
            List<String[]> teams = entry.getValue();
            Map<String, String[]> scores = computeGroupScores(teams, qualCalc);
            teams.sort(Comparator.comparingInt(t -> Integer.parseInt(scores.get(t[0])[2].replaceAll("\\s*\\(.*", ""))));

            int[] pcts = teams.stream()
                    .mapToInt(t -> parseCombinedPct(scores.get(t[0])[2]))
                    .toArray();
            int gap12 = pcts.length > 1 ? pcts[0] - pcts[1] : 100;
            int gap23 = pcts.length > 2 ? pcts[1] - pcts[2] : 100;
            int gap34 = pcts.length > 3 ? pcts[2] - pcts[3] : 100;

            for (int i = 0; i < teams.size(); i++) {
                String name = teams.get(i)[0];
                String[] s = scores.get(name);
                String[] pred = autoFillPredictions(i, gap12, gap23, gap34, CONFIDENCE_GAP);
                output.add(group + "," + name + "," + teams.get(i)[1] + ","
                        + s[1] + "," + s[0] + "," + s[2] + ","
                        + pred[0] + "," + pred[1] + "," + pred[2]);
            }
        }

        Files.createDirectories(predictionDir);
        Files.write(groupsFile, output);
        System.out.println("Generated groups.csv for " + groups.size() + " groups. Fill in group_winner, runner_up, and 3rd_place columns before running mode=groups.");
    }

    private Map<String, String[]> computeGroupScores(List<String[]> teams, QualificationFormCalculator qualCalc) {
        Map<String, String> h2hDisplay = new LinkedHashMap<>();
        Map<String, String> qualDisplay = new LinkedHashMap<>();
        Map<String, Integer> combinedPcts = new LinkedHashMap<>();

        for (String[] team : teams) {
            String name = team[0];
            int elo = Integer.parseInt(team[1]);
            double eloTotal = 0.0;
            double h2hTotal = 0.0;
            int h2hCount = 0;
            int oppCount = 0;

            for (String[] opp : teams) {
                if (opp[0].equals(name)) continue;
                int oppElo = Integer.parseInt(opp[1]);
                eloTotal += 1.0 / (1.0 + Math.pow(10.0, (oppElo - elo) / 400.0));
                OptionalDouble combined = headToHeadCalculator.computeRawCombinedWinRate(name, opp[0]);
                h2hTotal += combined.orElse(0.5);
                if (combined.isPresent()) h2hCount++;
                oppCount++;
            }

            double eloScore = oppCount > 0 ? eloTotal / oppCount : 0.5;
            boolean hasQual = qualCalc.hasData(name);
            double qualFormScore = qualCalc.getFormScore(name);
            boolean hasH2h = h2hCount > 0;
            double h2hScore = oppCount > 0 ? h2hTotal / oppCount : 0.5;

            h2hDisplay.put(name, hasH2h ? ((int) Math.round(h2hScore * 100)) + "%" : "N/A");
            qualDisplay.put(name, hasQual ? ((int) Math.round(qualFormScore * 100)) + "%" : "N/A");

            double effectiveQualWeight = hasQual ? qualFormWeight : 0.0;
            double effectiveEloWeight = eloBaseWeight + (hasQual ? 0.0 : qualFormWeight);
            double combined = effectiveEloWeight * eloScore + effectiveQualWeight * qualFormScore;
            combinedPcts.put(name, (int) Math.round(combined * 100));
        }

        List<String> names = teams.stream().map(t -> t[0]).collect(Collectors.toList());
        Map<String, Integer> positions = denseRankDesc(names, combinedPcts);

        Map<String, String[]> result = new LinkedHashMap<>();
        for (String[] team : teams) {
            String name = team[0];
            int pos = positions.get(name);
            int pct = combinedPcts.get(name);
            // indices: 0=h2h, 1=qualForm, 2=position
            result.put(name, new String[]{h2hDisplay.get(name), qualDisplay.get(name), pos + " (" + pct + "%)"});
        }
        return result;
    }

    private static Map<String, Integer> denseRankDesc(List<String> names, Map<String, Integer> scores) {
        List<Integer> sortedDistinct = scores.values().stream()
                .distinct().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        Map<String, Integer> ranks = new LinkedHashMap<>();
        for (String name : names) {
            ranks.put(name, sortedDistinct.indexOf(scores.get(name)) + 1);
        }
        return ranks;
    }

    private static int parseCombinedPct(String predictedPos) {
        int start = predictedPos.indexOf('(');
        int end = predictedPos.indexOf('%');
        if (start >= 0 && end > start) {
            try {
                return Integer.parseInt(predictedPos.substring(start + 1, end).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 50;
    }

    private static String[] autoFillPredictions(int pos, int gap12, int gap23, int gap34, int confidenceGap) {
        String gw;
        String ru;
        String tp;

        if (pos == 0) {
            gw = "yes";
        } else if (pos == 1) {
            gw = gap12 <= confidenceGap ? "maybe" : "no";
        } else {
            gw = "no";
        }

        if (pos == 0) {
            ru = gap12 <= confidenceGap ? "maybe" : "no";
        } else if (pos == 1) {
            ru = "yes";
        } else if (pos == 2) {
            ru = gap23 <= confidenceGap ? "maybe" : "no";
        } else {
            ru = "no";
        }

        if (pos == 0) {
            tp = "no";
        } else if (pos == 1) {
            tp = gap23 <= confidenceGap ? "maybe" : "no";
        } else if (pos == 2) {
            tp = "yes";
        } else {
            tp = gap34 <= confidenceGap ? "maybe" : "no";
        }

        return new String[]{gw, ru, tp};
    }

    private Map<String, Integer> validateStartCsv(List<String> lines, Path startFile) throws IOException {
        if (lines.isEmpty()) {
            throw new IOException("start.csv is empty: " + startFile);
        }

        String[] headers = lines.get(0).split(",", -1);
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            indexes.put(headers[i].trim(), i);
        }
        for (String required : REQUIRED_HEADERS) {
            if (!indexes.containsKey(required)) {
                throw new IOException("start.csv missing required header: " + required);
            }
        }

        int teamIdx = indexes.get("team");
        int hostIdx = indexes.get("host");
        int injuryIdx = indexes.get("injury_impact");
        int heatIdx = indexes.getOrDefault("heat_impact", -1);
        int squadDropoutIdx = indexes.getOrDefault("squad_dropouts", -1);

        List<String> errors = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split(",", -1);
            if (cols.length <= Math.max(teamIdx, Math.max(hostIdx, injuryIdx))) {
                errors.add("Row " + (i + 1) + ": missing required columns");
                continue;
            }
            String host = cols[hostIdx].trim().toLowerCase();
            if (!VALID_HOST_VALUES.contains(host)) {
                errors.add("Row " + (i + 1) + " ('" + cols[teamIdx].trim() + "'): host must be 'yes' or 'no', got '" + host + "'");
            }
            validateLevel(cols, i, teamIdx, injuryIdx, "injury_impact", INJURY_PENALTIES.length, errors);
            if (heatIdx >= 0 && cols.length > heatIdx && !cols[heatIdx].trim().isEmpty()) {
                validateLevel(cols, i, teamIdx, heatIdx, "heat_impact", HEAT_BONUSES.length, errors);
            }
            if (squadDropoutIdx >= 0 && cols.length > squadDropoutIdx && !cols[squadDropoutIdx].trim().isEmpty()) {
                validateLevel(cols, i, teamIdx, squadDropoutIdx, "squad_dropouts", SQUAD_DROPOUT_PENALTIES.length, errors);
            }
        }

        if (!errors.isEmpty()) {
            throw new IOException("start.csv validation failed:\n  " + String.join("\n  ", errors));
        }
        return indexes;
    }

    private void validateLevel(String[] cols, int rowNum, int teamIdx, int colIdx,
                                String colName, int maxLevels, List<String> errors) {
        String val = cols[colIdx].trim();
        try {
            int level = Integer.parseInt(val);
            if (level < 0 || level >= maxLevels) {
                errors.add("Row " + (rowNum + 1) + " ('" + cols[teamIdx].trim() + "'): " + colName
                        + " must be 0-" + (maxLevels - 1) + ", got " + level);
            }
        } catch (NumberFormatException e) {
            errors.add("Row " + (rowNum + 1) + " ('" + cols[teamIdx].trim() + "'): " + colName
                    + " must be a number (0-" + (maxLevels - 1) + "), got '" + val + "'");
        }
    }
}

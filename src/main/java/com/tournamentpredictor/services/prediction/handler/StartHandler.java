package com.tournamentpredictor.services.prediction.handler;

import com.tournamentpredictor.config.PredictionConfig;
import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.io.CsvHelper;
import com.tournamentpredictor.services.calculation.QualificationFormCalculator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tournamentpredictor.services.storage.GeneratedDataStore;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StartHandler {
    private static final Logger log = LoggerFactory.getLogger(StartHandler.class);

    final int HOME_ADVANTAGE;
    final int[] INJURY_PENALTIES;
    final int[] HEAT_ADVANTAGES;
    final int[] SQUAD_DROPOUT_PENALTIES;

    private final int CONFIDENCE_GAP;
    private final int qualFormSinceYear;
    private final int qualFormUntilYear;
    private final int qualFormEloMax;
    private final int preTournamentFormSinceYear;
    private final int preTournamentFormUntilYear;
    private final int preTournamentFormEloMax;
    private final int preTournamentFormMaxGames;
    private final int squadAgeYoungPenalty;
    private final int squadAgeAgingPenalty;
    private final int squadCohesionUnsettledPenalty;
    private final int squadCohesionDisruptedPenalty;
    private final int squadCohesionFracturedPenalty;
    private final int squadDepthExcellentBonus;
    private final int squadDepthLimitedPenalty;
    private final int squadDepthThinPenalty;
    private static final List<String> REQUIRED_HEADERS = Arrays.asList("group", "team", "host", "injury_impact");
    private static final Set<String> VALID_HOST_VALUES = Set.of("yes", "no");

    private final CsvLoader loader;
    private final Path projectRoot;
    private final CsvHelper csvHelper;
    private final GeneratedDataStore generatedDataStore;

    public StartHandler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper) {
        this(loader, projectRoot, csvHelper,
                100, new int[]{0, 25, 50, 100}, new int[]{0, 8, 15, 25}, new int[]{0, 20, 40, 75}, 25,
                2023, 2026, 0,
                2026, 2026, 25, 3,
                10, 8,
                15, 30, 45,
                10, 10, 20);
    }

    public StartHandler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper, PredictionConfig config) {
        this(loader, projectRoot, csvHelper,
                config.getHomeAdvantageElo(), config.getInjuryPenalties(), config.getHeatAdvantages(),
                config.getSquadDropoutPenalties(), config.getConfidenceGap(),
                config.getQualFormSinceYear(), config.getQualFormUntilYear(), config.getQualFormEloMax(),
                config.getPreTournamentFormSinceYear(), config.getPreTournamentFormUntilYear(), config.getPreTournamentFormEloMax(), config.getPreTournamentFormMaxGames(),
                config.getSquadAgeYoungPenalty(), config.getSquadAgeAgingPenalty(),
                config.getSquadCohesionUnsettledPenalty(), config.getSquadCohesionDisruptedPenalty(), config.getSquadCohesionFracturedPenalty(),
                config.getSquadDepthExcellentBonus(), config.getSquadDepthLimitedPenalty(), config.getSquadDepthThinPenalty());
    }

    private StartHandler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                         int homeAdvantageElo, int[] injuryPenalties, int[] heatAdvantages,
                         int[] squadDropoutPenalties, int confidenceGap,
                         int qualFormSinceYear, int qualFormUntilYear, int qualFormEloMax,
                         int preTournamentFormSinceYear, int preTournamentFormUntilYear, int preTournamentFormEloMax, int preTournamentFormMaxGames,
                         int squadAgeYoungPenalty, int squadAgeAgingPenalty,
                         int squadCohesionUnsettledPenalty, int squadCohesionDisruptedPenalty, int squadCohesionFracturedPenalty) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.csvHelper = csvHelper;
        this.generatedDataStore = new GeneratedDataStore(projectRoot);
        this.HOME_ADVANTAGE = homeAdvantageElo;
        this.INJURY_PENALTIES = injuryPenalties;
        this.HEAT_ADVANTAGES = heatAdvantages;
        this.SQUAD_DROPOUT_PENALTIES = squadDropoutPenalties;
        this.CONFIDENCE_GAP = confidenceGap;
        this.qualFormSinceYear = qualFormSinceYear;
        this.qualFormUntilYear = qualFormUntilYear;
        this.qualFormEloMax = qualFormEloMax;
        this.preTournamentFormSinceYear = preTournamentFormSinceYear;
        this.preTournamentFormUntilYear = preTournamentFormUntilYear;
        this.preTournamentFormEloMax = preTournamentFormEloMax;
        this.preTournamentFormMaxGames = preTournamentFormMaxGames;
        this.squadAgeYoungPenalty = squadAgeYoungPenalty;
        this.squadAgeAgingPenalty = squadAgeAgingPenalty;
        this.squadCohesionUnsettledPenalty = squadCohesionUnsettledPenalty;
        this.squadCohesionDisruptedPenalty = squadCohesionDisruptedPenalty;
        this.squadCohesionFracturedPenalty = squadCohesionFracturedPenalty;
        this.squadDepthExcellentBonus = 10;
        this.squadDepthLimitedPenalty = 10;
        this.squadDepthThinPenalty = 20;
    }

    private StartHandler(CsvLoader loader, Path projectRoot, CsvHelper csvHelper,
                         int homeAdvantageElo, int[] injuryPenalties, int[] heatAdvantages,
                         int[] squadDropoutPenalties, int confidenceGap,
                         int qualFormSinceYear, int qualFormUntilYear, int qualFormEloMax,
                         int preTournamentFormSinceYear, int preTournamentFormUntilYear, int preTournamentFormEloMax, int preTournamentFormMaxGames,
                         int squadAgeYoungPenalty, int squadAgeAgingPenalty,
                         int squadCohesionUnsettledPenalty, int squadCohesionDisruptedPenalty, int squadCohesionFracturedPenalty,
                         int squadDepthExcellentBonus, int squadDepthLimitedPenalty, int squadDepthThinPenalty) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.csvHelper = csvHelper;
        this.generatedDataStore = new GeneratedDataStore(projectRoot);
        this.HOME_ADVANTAGE = homeAdvantageElo;
        this.INJURY_PENALTIES = injuryPenalties;
        this.HEAT_ADVANTAGES = heatAdvantages;
        this.SQUAD_DROPOUT_PENALTIES = squadDropoutPenalties;
        this.CONFIDENCE_GAP = confidenceGap;
        this.qualFormSinceYear = qualFormSinceYear;
        this.qualFormUntilYear = qualFormUntilYear;
        this.qualFormEloMax = qualFormEloMax;
        this.preTournamentFormSinceYear = preTournamentFormSinceYear;
        this.preTournamentFormUntilYear = preTournamentFormUntilYear;
        this.preTournamentFormEloMax = preTournamentFormEloMax;
        this.preTournamentFormMaxGames = preTournamentFormMaxGames;
        this.squadAgeYoungPenalty = squadAgeYoungPenalty;
        this.squadAgeAgingPenalty = squadAgeAgingPenalty;
        this.squadCohesionUnsettledPenalty = squadCohesionUnsettledPenalty;
        this.squadCohesionDisruptedPenalty = squadCohesionDisruptedPenalty;
        this.squadCohesionFracturedPenalty = squadCohesionFracturedPenalty;
        this.squadDepthExcellentBonus = squadDepthExcellentBonus;
        this.squadDepthLimitedPenalty = squadDepthLimitedPenalty;
        this.squadDepthThinPenalty = squadDepthThinPenalty;
    }

    public void handle(String tournament) throws IOException {
        Path predictionDir = projectRoot.resolve("data").resolve("predictions").resolve(tournament);
        Path groupsFile = predictionDir.resolve("groups.csv");
        if (generatedDataStore.exists(groupsFile)) {
            System.out.println("  🔒 Output already exists: " + groupsFile + " — delete to re-run");
            System.out.println("  Run --mode=groups to continue");
            return;
        }

        Path startFile = predictionDir.resolve("start.csv");
        if (!generatedDataStore.exists(startFile)) {
            throw new IOException("start.csv not found: " + startFile + ". Create data/predictions/" + tournament + "/start.csv first.");
        }

        List<String> startLines = generatedDataStore.readLines(startFile);
        Map<String, Integer> startIndexes = validateStartCsv(startLines, startFile);
        int groupIdx = startIndexes.get("group");
        int teamIdx = startIndexes.get("team");
        int hostIdx = startIndexes.get("host");
        int injuryIdx = startIndexes.get("injury_impact");
        int heatIdx = startIndexes.getOrDefault("heat_impact", -1);
        int squadDropoutIdx = startIndexes.getOrDefault("squad_dropouts", -1);
        int squadAgeIdx = startIndexes.getOrDefault("squad_age_profile", -1);
        int squadCohesionIdx = startIndexes.getOrDefault("squad_cohesion", -1);
        int squadDepthIdx = startIndexes.getOrDefault("squad_depth", -1);
        int squadQualityIdx = startIndexes.getOrDefault("squad_quality", -1);
        int attackQualityIdx = startIndexes.getOrDefault("attack_quality", -1);
        int defenceQualityIdx = startIndexes.getOrDefault("defence_quality", -1);
        int confederationIdx = startIndexes.getOrDefault("confederation", -1);

        Map<String, Integer> eloRatings = loader.loadEloForTournament(tournament);
        Path historyDir = loader.historyDirForTournament(tournament);
        int effectiveQualSinceYear = loader.resolveSnapshotBackedSetting(tournament,
                "qual.form.since.year", "qual_form_since", qualFormSinceYear);
        int effectiveQualUntilYear = loader.resolveSnapshotBackedSetting(tournament,
                "qual.form.until.year", "qual_form_until", qualFormUntilYear);
        int effectivePreTournamentSinceYear = loader.resolveSnapshotBackedSetting(tournament,
                "pre.tournament.form.since.year", "pre_tournament_form_since", preTournamentFormSinceYear);
        int effectivePreTournamentUntilYear = loader.resolveSnapshotBackedSetting(tournament,
                "pre.tournament.form.until.year", "pre_tournament_form_until", preTournamentFormUntilYear);
        java.time.LocalDate tournamentStartDate = loader.resolveSnapshotBackedDate(tournament,
                "tournament.start.date", "tournament_start_date");
        Set<String> qualifierTypes = loader.resolveTournamentMatchTypes(tournament,
                "qual.form.match.types", Set.of("WQ", "WQS", "FQ"));

        QualificationFormCalculator qualCalc = new QualificationFormCalculator(
                historyDir,
                effectiveQualSinceYear, effectiveQualUntilYear, qualFormEloMax,
                qualifierTypes, 0, tournamentStartDate);
        QualificationFormCalculator friendlyCalc = new QualificationFormCalculator(
                historyDir,
                effectivePreTournamentSinceYear, effectivePreTournamentUntilYear,
                preTournamentFormEloMax, Set.of("F"), preTournamentFormMaxGames, tournamentStartDate);

        Map<String, List<String[]>> groups = new LinkedHashMap<>();
        for (int i = 1; i < startLines.size(); i++) {
            String line = startLines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = parseCsvLine(line);
            String group = cols[groupIdx].trim().toUpperCase();
            String team = cols[teamIdx].trim();
            boolean isHost = cols[hostIdx].trim().equalsIgnoreCase("yes");
            int injuryLevel = Integer.parseInt(cols[injuryIdx].trim());
            int heatLevel = (heatIdx >= 0 && cols.length > heatIdx && !cols[heatIdx].trim().isEmpty())
                    ? Integer.parseInt(cols[heatIdx].trim()) : 0;
            int squadDropoutLevel = (squadDropoutIdx >= 0 && cols.length > squadDropoutIdx && !cols[squadDropoutIdx].trim().isEmpty())
                    ? Integer.parseInt(cols[squadDropoutIdx].trim()) : 0;
            int ageLevel = (squadAgeIdx >= 0 && cols.length > squadAgeIdx && !cols[squadAgeIdx].trim().isEmpty())
                    ? Integer.parseInt(cols[squadAgeIdx].trim()) : 0;
            int cohesionLevel = (squadCohesionIdx >= 0 && cols.length > squadCohesionIdx && !cols[squadCohesionIdx].trim().isEmpty())
                    ? Integer.parseInt(cols[squadCohesionIdx].trim()) : 0;
            int depthLevel = (squadDepthIdx >= 0 && cols.length > squadDepthIdx && !cols[squadDepthIdx].trim().isEmpty())
                    ? Integer.parseInt(cols[squadDepthIdx].trim()) : 0;
            int legacyQuality = (squadQualityIdx >= 0 && cols.length > squadQualityIdx && !cols[squadQualityIdx].trim().isEmpty())
                    ? Integer.parseInt(cols[squadQualityIdx].trim()) : 0;
            int attackQuality = (attackQualityIdx >= 0 && cols.length > attackQualityIdx && !cols[attackQualityIdx].trim().isEmpty())
                    ? Integer.parseInt(cols[attackQualityIdx].trim()) : legacyQuality;
            int defenceQuality = (defenceQualityIdx >= 0 && cols.length > defenceQualityIdx && !cols[defenceQualityIdx].trim().isEmpty())
                    ? Integer.parseInt(cols[defenceQualityIdx].trim()) : legacyQuality;
            String confederation = (confederationIdx >= 0 && cols.length > confederationIdx) ? cols[confederationIdx].trim() : "";
            int leagueAdjustment = confederationAdjustment(confederation);
            int[] SQUAD_AGE_PENALTIES = {0, squadAgeYoungPenalty, squadAgeAgingPenalty};
            int[] COHESION_PENALTIES = {0, squadCohesionUnsettledPenalty, squadCohesionDisruptedPenalty, squadCohesionFracturedPenalty};
            int[] DEPTH_PENALTIES = {0, squadDepthLimitedPenalty, squadDepthThinPenalty};
            int depthPenalty = depthLevel == -1
                    ? -squadDepthExcellentBonus
                    : DEPTH_PENALTIES[Math.min(depthLevel, DEPTH_PENALTIES.length - 1)];
            int baseElo = eloRatings.getOrDefault(team, 0);
            if (baseElo == 0) {
                log.warn("Team '{}' not found in world.csv, ELO defaulting to 0", team);
            }
            int homeBonus = isHost ? HOME_ADVANTAGE : 0;
            int qualBonus = qualCalc.getQualBonus(team);
            int preTournamentBonus = friendlyCalc.getQualBonus(team);
            int adjustedElo = baseElo + homeBonus - INJURY_PENALTIES[injuryLevel]
                    + HEAT_ADVANTAGES[heatLevel] - SQUAD_DROPOUT_PENALTIES[squadDropoutLevel] + qualBonus
                    + preTournamentBonus + leagueAdjustment - SQUAD_AGE_PENALTIES[Math.min(ageLevel, SQUAD_AGE_PENALTIES.length - 1)]
                    - COHESION_PENALTIES[Math.min(cohesionLevel, COHESION_PENALTIES.length - 1)]
                    - depthPenalty;
            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(new String[]{
                    team,
                    String.valueOf(baseElo),
                    String.valueOf(qualBonus),
                    String.valueOf(adjustedElo),
                    String.valueOf(preTournamentBonus),
                    String.valueOf(ageLevel),
                    String.valueOf(cohesionLevel),
                    String.valueOf(depthLevel),
                    String.valueOf(attackQuality),
                    String.valueOf(defenceQuality),
                    String.valueOf(leagueAdjustment)
            });
        }

        List<String> output = new ArrayList<>();
        output.add("group,team,base_elo,qual_bonus,pre_tournament_bonus,confederation_adjustment,squad_age_profile,squad_cohesion,squad_depth,attack_quality,defence_quality,elo_ranking,predicted_position,group_winner,runner_up,3rd_place");
        boolean first = true;
        for (Map.Entry<String, List<String[]>> entry : groups.entrySet()) {
            if (!first) {
                output.add("");
            }
            first = false;
            String group = entry.getKey();
            List<String[]> teams = entry.getValue();
            Map<String, String[]> scores = computeGroupScores(teams);
            teams.sort(Comparator.comparingInt(t -> Integer.parseInt(scores.get(t[0])[0].replaceAll("\\s*\\(.*", ""))));

            int[] pcts = teams.stream()
                    .mapToInt(t -> parseCombinedPct(scores.get(t[0])[0]))
                    .toArray();
            int gap12 = pcts.length > 1 ? pcts[0] - pcts[1] : 100;
            int gap23 = pcts.length > 2 ? pcts[1] - pcts[2] : 100;
            int gap34 = pcts.length > 3 ? pcts[2] - pcts[3] : 100;

            for (int i = 0; i < teams.size(); i++) {
                String[] team = teams.get(i);
                String name = team[0];
                int baseElo = Integer.parseInt(team[1]);
                int qualBonus = Integer.parseInt(team[2]);
                int adjustedElo = Integer.parseInt(team[3]);
                int preTournamentBonus = Integer.parseInt(team[4]);
                int leagueAdjustment = Integer.parseInt(team[5]);
                int ageLevel = Integer.parseInt(team[6]);
                int cohesionLevel = Integer.parseInt(team[7]);
                int depthLevel = Integer.parseInt(team[8]);
                int attackQuality = Integer.parseInt(team[9]);
                int defenceQuality = Integer.parseInt(team[10]);
                String[] s = scores.get(name);
                String[] pred = autoFillPredictions(i, gap12, gap23, gap34, CONFIDENCE_GAP);
                output.add(group + "," + name + "," + baseElo + "," + qualBonus + "," + preTournamentBonus + "," + leagueAdjustment + ","
                        + ageLevel + "," + cohesionLevel + "," + depthLevel + "," + attackQuality + "," + defenceQuality + ","
                        + adjustedElo + "," + s[0] + ","
                        + pred[0] + "," + pred[1] + "," + pred[2]);
            }
        }

        Files.createDirectories(predictionDir);
        generatedDataStore.writeLines(groupsFile, output);
        System.out.println("Generated groups.csv for " + groups.size() + " groups. Model-selected display positions are ready for group simulation.");
    }

    private int confederationAdjustment(String confederation) {
        if (confederation == null) return 0;
        return switch (confederation.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "UEFA" -> 0;
            case "CONMEBOL" -> 0;
            case "CONCACAF" -> -25;
            case "CAF" -> -10;
            case "AFC" -> -25;
            case "OFC" -> -50;
            default -> 0;
        };
    }

    private Map<String, String[]> computeGroupScores(List<String[]> teams) {
        Map<String, Integer> combinedPcts = new LinkedHashMap<>();

        for (String[] team : teams) {
            String name = team[0];
            int elo = Integer.parseInt(team[3]);
            double eloTotal = 0.0;
            int oppCount = 0;

            for (String[] opp : teams) {
                if (opp[0].equals(name)) continue;
                int oppElo = Integer.parseInt(opp[3]);
                eloTotal += 1.0 / (1.0 + Math.pow(10.0, (oppElo - elo) / 400.0));
                oppCount++;
            }

            double eloScore = oppCount > 0 ? eloTotal / oppCount : 0.5;
            combinedPcts.put(name, (int) Math.round(eloScore * 100));
        }

        List<String> names = teams.stream().map(t -> t[0]).collect(Collectors.toList());
        Map<String, Integer> positions = denseRankDesc(names, combinedPcts);

        Map<String, String[]> result = new LinkedHashMap<>();
        for (String[] team : teams) {
            String name = team[0];
            int pos = positions.get(name);
            int pct = combinedPcts.get(name);
            result.put(name, new String[]{pos + " (" + pct + "%)"});
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

    private static String[] parseCsvLine(String line) {
        try {
            List<CSVRecord> records = CSVFormat.DEFAULT.parse(new StringReader(line)).getRecords();
            if (records.isEmpty()) return new String[0];
            CSVRecord record = records.get(0);
            String[] cols = new String[record.size()];
            for (int i = 0; i < record.size(); i++) cols[i] = record.get(i);
            return cols;
        } catch (IOException e) {
            return line.split(",", -1);
        }
    }

    private Map<String, Integer> validateStartCsv(List<String> lines, Path startFile) throws IOException {
        if (lines.isEmpty()) {
            throw new IOException("start.csv is empty: " + startFile);
        }

        String[] headers = parseCsvLine(lines.get(0));
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
            String[] cols = parseCsvLine(line);
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
                validateLevel(cols, i, teamIdx, heatIdx, "heat_impact", HEAT_ADVANTAGES.length, errors);
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

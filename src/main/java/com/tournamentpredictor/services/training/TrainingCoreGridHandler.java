package com.tournamentpredictor.services.training;

import com.tournamentpredictor.services.storage.GeneratedDataStore;
import com.tournamentpredictor.services.calculation.ExpectedGoalsCalculator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Calibrates clean historical signals and a recent-weighted joint core model. */
public class TrainingCoreGridHandler {
    private static final int SAVED_HOME_ADVANTAGE = 100;
    private static final double BASELINE_ELO_SEPARATION = 1.20;
    private static final double BASELINE_XG_MULTIPLIER = 0.93;
    private static final double BASELINE_QUALITY_STEP = 0.05;
    private static final int[] HOME_ADVANTAGES = {0, 25, 50, 75, 100, 125, 150, 175, 200, 225, 250};
    private static final int[] JOINT_HOME_ADVANTAGES = {50, 75, 100, 125, 150};
    private static final double[] QUALITY_STEPS = {0.00, 0.05, 0.10, 0.15, 0.20, 0.25};
    private static final double[] JOINT_ELO_SEPARATION = {1.20, 1.40, 1.60};
    private static final double[] JOINT_XG_MULTIPLIER = {0.93, 0.95};

    private final Path root;
    private final double eloScaleDivisor;
    private final GeneratedDataStore generatedDataStore;

    public TrainingCoreGridHandler(Path root, double eloScaleDivisor) {
        this.root = root;
        this.eloScaleDivisor = eloScaleDivisor;
        this.generatedDataStore = new GeneratedDataStore(root);
    }

    public void handle() throws IOException {
        List<TournamentData> tournaments = loadTournaments();
        List<Params> homeParams = new ArrayList<>();
        for (int home : HOME_ADVANTAGES) {
            homeParams.add(new Params(BASELINE_ELO_SEPARATION, BASELINE_XG_MULTIPLIER, home, BASELINE_QUALITY_STEP));
        }
        List<Params> qualityParams = new ArrayList<>();
        for (double quality : QUALITY_STEPS) {
            qualityParams.add(new Params(BASELINE_ELO_SEPARATION, BASELINE_XG_MULTIPLIER, SAVED_HOME_ADVANTAGE, quality));
        }
        List<Params> jointParams = new ArrayList<>();
        for (double elo : JOINT_ELO_SEPARATION) {
            for (double xg : JOINT_XG_MULTIPLIER) {
                for (int home : JOINT_HOME_ADVANTAGES) {
                    for (double quality : QUALITY_STEPS) jointParams.add(new Params(elo, xg, home, quality));
                }
            }
        }

        Path output = root.resolve("data/backtests");
        GridRun home = runGrid(tournaments, homeParams, baseline(), false);
        GridRun quality = runGrid(tournaments, qualityParams, baseline(), false);
        GridRun joint = runGrid(tournaments, jointParams, baseline(), true);
        writeGrid(output.resolve("training_home_grid.csv"), home.aggregates);
        writeHoldouts(output.resolve("training_home_grid_holdouts.csv"), home.holdouts);
        writeGrid(output.resolve("training_quality_grid.csv"), quality.aggregates);
        writeHoldouts(output.resolve("training_quality_grid_holdouts.csv"), quality.holdouts);
        writeGrid(output.resolve("training_joint_grid.csv"), joint.aggregates);
        writeHoldouts(output.resolve("training_joint_grid_holdouts.csv"), joint.holdouts);
        print("Home advantage", home);
        print("Attack/defence xG step", quality);
        print("Recent-weighted joint core", joint);
    }

    private GridRun runGrid(List<TournamentData> tournaments, List<Params> candidates,
                            Params baseline, boolean recentWeighted) {
        List<Result> results = new ArrayList<>();
        for (Params params : candidates) {
            for (TournamentData tournament : tournaments) results.add(evaluate(tournament, params));
        }
        List<Aggregate> aggregates = candidates.stream()
                .map(params -> aggregate(params, results.stream().filter(r -> r.params.equals(params)).toList(), recentWeighted))
                .sorted(Comparator.comparingDouble(Aggregate::objective)).toList();
        List<Holdout> holdouts = new ArrayList<>();
        for (TournamentData holdout : tournaments) {
            Aggregate selected = candidates.stream()
                    .map(params -> aggregate(params, results.stream()
                            .filter(r -> r.params.equals(params) && !r.tournament.equals(holdout.name)).toList(), recentWeighted))
                    .min(Comparator.comparingDouble(Aggregate::objective)).orElseThrow();
            holdouts.add(new Holdout(holdout.name, selected,
                    find(results, holdout.name, selected.params), find(results, holdout.name, baseline)));
        }
        return new GridRun(aggregates, holdouts);
    }

    private Result evaluate(TournamentData tournament, Params params) {
        ExpectedGoalsCalculator calculator = new ExpectedGoalsCalculator(
                eloScaleDivisor, 2.60, params.eloSeparation, params.xgMultiplier, params.qualityStep);
        double brier = 0, logLoss = 0, predictedGoals = 0, actualGoals = 0, predictedUpsets = 0;
        int actualUpsets = 0;
        for (Match match : tournament.matches) {
            Profile p1 = tournament.profiles.get(match.team1);
            Profile p2 = tournament.profiles.get(match.team2);
            int elo1 = adjustedElo(p1, params.homeAdvantage);
            int elo2 = adjustedElo(p2, params.homeAdvantage);
            var projection = calculator.project(match.team1, match.team2, elo1, elo2,
                    p1.attackQuality, p1.defenceQuality, p2.attackQuality, p2.defenceQuality);
            double[] probabilities = {projection.exactTeam1WinProbability(), projection.exactDrawProbability(),
                    projection.exactTeam2WinProbability()};
            int outcome = match.goals1 > match.goals2 ? 0 : match.goals2 > match.goals1 ? 2 : 1;
            for (int i = 0; i < probabilities.length; i++) {
                brier += Math.pow(probabilities[i] - (i == outcome ? 1 : 0), 2);
            }
            logLoss -= Math.log(Math.max(probabilities[outcome], 1e-12));
            predictedGoals += projection.team1ExpectedGoals() + projection.team2ExpectedGoals();
            actualGoals += match.goals1 + match.goals2;
            int baselineElo1 = adjustedElo(p1, SAVED_HOME_ADVANTAGE);
            int baselineElo2 = adjustedElo(p2, SAVED_HOME_ADVANTAGE);
            int favourite = baselineElo1 >= baselineElo2 ? 0 : 2;
            predictedUpsets += probabilities[favourite == 0 ? 2 : 0];
            if (outcome != favourite && outcome != 1) actualUpsets++;
        }
        return new Result(tournament.name, params, tournament.matches.size(), tournament.weight,
                brier, logLoss, predictedGoals, actualGoals, predictedUpsets, actualUpsets,
                tournament.hostMatchCount, tournament.qualityMatchCount);
    }

    private static int adjustedElo(Profile profile, int homeAdvantage) {
        return profile.adjustedElo - (profile.host ? SAVED_HOME_ADVANTAGE : 0)
                + (profile.host ? homeAdvantage : 0);
    }

    private static Aggregate aggregate(Params params, List<Result> rows, boolean recentWeighted) {
        double denominator = rows.stream().mapToDouble(r -> recentWeighted ? r.weight * r.matches : r.matches).sum();
        double brier = rows.stream().mapToDouble(r -> r.brierSum * (recentWeighted ? r.weight : 1)).sum() / denominator;
        double logLoss = rows.stream().mapToDouble(r -> r.logLossSum * (recentWeighted ? r.weight : 1)).sum() / denominator;
        double predictedGoals = rows.stream().mapToDouble(r -> r.predictedGoals * (recentWeighted ? r.weight : 1)).sum() / denominator;
        double actualGoals = rows.stream().mapToDouble(r -> r.actualGoals * (recentWeighted ? r.weight : 1)).sum() / denominator;
        double predictedUpsets = rows.stream().mapToDouble(r -> r.predictedUpsets * (recentWeighted ? r.weight : 1)).sum() / denominator;
        double actualUpsets = rows.stream().mapToDouble(r -> r.actualUpsets * (recentWeighted ? r.weight : 1)).sum() / denominator;
        double objective = brier + 0.20 * logLoss + 0.05 * Math.abs(predictedGoals - actualGoals)
                + 0.10 * Math.abs(predictedUpsets - actualUpsets);
        return new Aggregate(params, rows.stream().mapToInt(Result::matches).sum(),
                rows.stream().mapToInt(Result::hostMatches).sum(), rows.stream().mapToInt(Result::qualityMatches).sum(),
                brier, logLoss, predictedGoals, actualGoals, predictedUpsets, actualUpsets, objective);
    }

    private List<TournamentData> loadTournaments() throws IOException {
        List<TournamentData> tournaments = new ArrayList<>();
        Path backtests = root.resolve("data/backtests");
        try (var paths = Files.list(backtests)) {
            for (Path path : paths.filter(Files::isDirectory).sorted().toList()) {
                String name = path.getFileName().toString();
                Path actual = path.resolve("actual_results.csv");
                Path groups = root.resolve("data/predictions").resolve(name).resolve("groups.csv");
                Path start = root.resolve("data/predictions").resolve(name).resolve("start.csv");
                if (!Files.exists(actual) || !generatedExists(groups) || !generatedExists(start)) continue;
                Map<String, Boolean> hosts = loadHosts(start);
                Map<String, Profile> profiles = loadProfiles(groups, hosts);
                List<Match> matches = loadMatches(actual, profiles);
                int hostMatches = (int) matches.stream().filter(m -> profiles.get(m.team1).host || profiles.get(m.team2).host).count();
                int qualityMatches = (int) matches.stream().filter(m -> profiles.get(m.team1).hasQuality()
                        || profiles.get(m.team2).hasQuality()).count();
                tournaments.add(new TournamentData(name, profiles, matches, recentWeight(name), hostMatches, qualityMatches));
            }
        }
        return tournaments;
    }

    private Map<String, Boolean> loadHosts(Path path) throws IOException {
        Map<String, Boolean> hosts = new HashMap<>();
        try (CSVParser parser = generatedParser(path)) {
            for (CSVRecord row : parser) hosts.put(row.get("team").trim(), "yes".equalsIgnoreCase(row.get("host").trim()));
        }
        return hosts;
    }

    private Map<String, Profile> loadProfiles(Path path, Map<String, Boolean> hosts) throws IOException {
        Map<String, Profile> profiles = new HashMap<>();
        try (CSVParser parser = generatedParser(path)) {
            for (CSVRecord row : parser) {
                String team = row.get("team").trim();
                profiles.put(team, new Profile(i(row, "elo_ranking"), hosts.getOrDefault(team, false),
                        i(row, "attack_quality"), i(row, "defence_quality")));
            }
        }
        return profiles;
    }


    private CSVParser generatedParser(Path path) throws IOException {
        List<String> lines = generatedDataStore.readLines(path);
        if (lines.isEmpty()) {
            throw new IOException("Generated data not found: " + path);
        }
        return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).build().parse(new StringReader(String.join("\n", lines)));
    }

    private boolean generatedExists(Path path) {
        try {
            return generatedDataStore.exists(path);
        } catch (IOException e) {
            return Files.exists(path);
        }
    }

    private static List<Match> loadMatches(Path path, Map<String, Profile> profiles) throws IOException {
        List<Match> matches = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            for (CSVRecord row : parser) {
                String team1 = row.get("team1"), team2 = row.get("team2");
                if (!profiles.containsKey(team1) || !profiles.containsKey(team2)) {
                    throw new IOException("Missing profile for " + team1 + " or " + team2 + " in " + path);
                }
                matches.add(new Match(team1, team2, i(row, "team1_goals"), i(row, "team2_goals")));
            }
        }
        return matches;
    }

    private static double recentWeight(String tournament) {
        int year = Integer.parseInt(tournament.replaceAll(".*_", ""));
        if (year >= 2020) return 2.0;
        if (year >= 2010) return 1.0;
        return 0.5;
    }

    private static Params baseline() {
        return new Params(BASELINE_ELO_SEPARATION, BASELINE_XG_MULTIPLIER,
                SAVED_HOME_ADVANTAGE, BASELINE_QUALITY_STEP);
    }

    private static Result find(List<Result> rows, String tournament, Params params) {
        return rows.stream().filter(r -> r.tournament.equals(tournament) && r.params.equals(params))
                .findFirst().orElseThrow();
    }

    private static void writeGrid(Path path, List<Aggregate> rows) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path); CSVPrinter csv = CSVFormat.DEFAULT.print(writer)) {
            csv.printRecord("rank", "elo_goal_separation", "xg_multiplier", "home_advantage_elo",
                    "quality_xg_per_level", "matches", "host_matches", "quality_signal_matches", "brier",
                    "log_loss", "predicted_goals", "actual_goals", "predicted_upset_rate",
                    "actual_upset_rate", "objective");
            int rank = 1;
            for (Aggregate a : rows) csv.printRecord(rank++, f(a.params.eloSeparation), f(a.params.xgMultiplier),
                    a.params.homeAdvantage, f(a.params.qualityStep), a.matches, a.hostMatches, a.qualityMatches,
                    f(a.brier), f(a.logLoss), f(a.predictedGoals), f(a.actualGoals), f(a.predictedUpsets),
                    f(a.actualUpsets), f(a.objective));
        }
    }

    private static void writeHoldouts(Path path, List<Holdout> rows) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path); CSVPrinter csv = CSVFormat.DEFAULT.print(writer)) {
            csv.printRecord("holdout", "selected_elo_goal_separation", "selected_xg_multiplier",
                    "selected_home_advantage_elo", "selected_quality_xg_per_level", "training_objective",
                    "holdout_brier", "baseline_brier", "brier_delta", "holdout_log_loss", "baseline_log_loss",
                    "log_loss_delta", "holdout_predicted_upset_rate", "holdout_actual_upset_rate");
            for (Holdout h : rows) {
                Result v = h.validation, b = h.baseline;
                csv.printRecord(h.tournament, f(h.selected.params.eloSeparation), f(h.selected.params.xgMultiplier),
                        h.selected.params.homeAdvantage, f(h.selected.params.qualityStep), f(h.selected.objective),
                        f(v.brierSum / v.matches), f(b.brierSum / b.matches),
                        f(v.brierSum / v.matches - b.brierSum / b.matches),
                        f(v.logLossSum / v.matches), f(b.logLossSum / b.matches),
                        f(v.logLossSum / v.matches - b.logLossSum / b.matches),
                        f(v.predictedUpsets / v.matches), f(v.actualUpsets / (double) v.matches));
            }
        }
    }

    private static void print(String label, GridRun run) {
        Aggregate best = run.aggregates.getFirst();
        long brierWins = run.holdouts.stream().filter(h -> h.validation.brierSum / h.validation.matches
                < h.baseline.brierSum / h.baseline.matches).count();
        long logLossWins = run.holdouts.stream().filter(h -> h.validation.logLossSum / h.validation.matches
                < h.baseline.logLossSum / h.baseline.matches).count();
        Map<String, Long> selections = new LinkedHashMap<>();
        run.holdouts.forEach(h -> selections.merge(h.selected.params.label(), 1L, Long::sum));
        System.out.printf(Locale.ROOT, "%s best: %s, Brier %.4f, log loss %.4f, objective %.4f%n",
                label, best.params.label(), best.brier, best.logLoss, best.objective);
        System.out.println("Holdout selections: " + selections);
        System.out.println("Beats current baseline on " + brierWins + "/" + run.holdouts.size()
                + " Brier and " + logLossWins + "/" + run.holdouts.size() + " log-loss holdouts.");
    }

    private static int i(CSVRecord row, String column) { return Integer.parseInt(row.get(column).trim()); }
    private static String f(double value) { return String.format(Locale.ROOT, "%.4f", value); }

    private record Params(double eloSeparation, double xgMultiplier, int homeAdvantage, double qualityStep) {
        String label() { return String.format(Locale.ROOT, "ELO %.2f / xG %.2f / home %d / quality %.2f",
                eloSeparation, xgMultiplier, homeAdvantage, qualityStep); }
    }
    private record Profile(int adjustedElo, boolean host, int attackQuality, int defenceQuality) {
        boolean hasQuality() { return attackQuality != 0 || defenceQuality != 0; }
    }
    private record Match(String team1, String team2, int goals1, int goals2) {}
    private record TournamentData(String name, Map<String, Profile> profiles, List<Match> matches, double weight,
                                  int hostMatchCount, int qualityMatchCount) {}
    private record Result(String tournament, Params params, int matches, double weight, double brierSum,
                          double logLossSum, double predictedGoals, double actualGoals, double predictedUpsets,
                          int actualUpsets, int hostMatches, int qualityMatches) {}
    private record Aggregate(Params params, int matches, int hostMatches, int qualityMatches, double brier,
                             double logLoss, double predictedGoals, double actualGoals, double predictedUpsets,
                             double actualUpsets, double objective) {}
    private record Holdout(String tournament, Aggregate selected, Result validation, Result baseline) {}
    private record GridRun(List<Aggregate> aggregates, List<Holdout> holdouts) {}
}

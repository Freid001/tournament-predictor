package com.tournamentpredictor.services.training;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.calculation.ExpectedGoalsCalculator;
import com.tournamentpredictor.services.calculation.TeamEloSnapshot;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Deterministic parameter-grid evaluation with leave-one-tournament-out validation. */
public class TrainingGridHandler {
    private static final double[] ELO_SEPARATION = {1.05, 1.10, 1.15, 1.20, 1.25, 1.30, 1.35, 1.40, 1.45, 1.50, 1.60, 1.70, 1.80, 1.90, 2.00};
    private static final double[] XG_MULTIPLIER = {0.91, 0.93, 0.95, 0.97, 0.99};
    private static final double BASELINE_ELO_SEPARATION = 1.20;
    private static final double BASELINE_XG_MULTIPLIER = 0.93;

    private final CsvLoader loader;
    private final Path root;

    public TrainingGridHandler(CsvLoader loader, Path root) {
        this.loader = loader;
        this.root = root;
    }

    public void handle() throws IOException {
        List<String> tournaments = discoverTournaments();
        List<GridResult> results = new ArrayList<>();
        for (double eloSeparation : ELO_SEPARATION) {
            for (double xgMultiplier : XG_MULTIPLIER) {
                for (String tournament : tournaments) {
                    results.add(evaluate(tournament, eloSeparation, xgMultiplier));
                }
            }
        }

        List<Aggregate> aggregates = new ArrayList<>();
        for (double eloSeparation : ELO_SEPARATION) {
            for (double xgMultiplier : XG_MULTIPLIER) {
                aggregates.add(aggregate(results.stream()
                        .filter(r -> r.eloSeparation == eloSeparation && r.xgMultiplier == xgMultiplier).toList()));
            }
        }
        aggregates.sort(Comparator.comparingDouble(Aggregate::objective));

        List<Holdout> holdouts = new ArrayList<>();
        for (String holdout : tournaments) {
            Aggregate selected = null;
            for (double eloSeparation : ELO_SEPARATION) {
                for (double xgMultiplier : XG_MULTIPLIER) {
                    Aggregate training = aggregate(results.stream()
                            .filter(r -> !holdout.equals(r.tournament)
                                    && r.eloSeparation == eloSeparation && r.xgMultiplier == xgMultiplier).toList());
                    if (selected == null || training.objective < selected.objective) selected = training;
                }
            }
            GridResult validation = find(results, holdout, selected.eloSeparation, selected.xgMultiplier);
            GridResult baseline = find(results, holdout, BASELINE_ELO_SEPARATION, BASELINE_XG_MULTIPLIER);
            holdouts.add(new Holdout(holdout, selected, validation, baseline));
        }

        Path output = root.resolve("data/backtests");
        writeGrid(output.resolve("training_grid.csv"), aggregates);
        writeHoldouts(output.resolve("training_grid_holdouts.csv"), holdouts);
        print(aggregates, holdouts);
    }

    private List<String> discoverTournaments() throws IOException {
        Path backtests = root.resolve("data/backtests");
        if (!Files.isDirectory(backtests)) return List.of();
        try (var paths = Files.list(backtests)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> Files.exists(path.resolve("actual_results.csv")))
                    .map(path -> path.getFileName().toString())
                    .filter(loader::hasTournamentSnapshot)
                    .sorted()
                    .toList();
        }
    }

    private GridResult evaluate(String tournament, double eloSeparation, double xgMultiplier) throws IOException {
        Map<String, Integer> elo = loader.loadTournamentElo(tournament);
        Map<String, TeamEloSnapshot> profiles = loader.loadTeamSnapshots(tournament);
        ExpectedGoalsCalculator calculator = new ExpectedGoalsCalculator(400.0, 2.60, eloSeparation, xgMultiplier);
        List<Map<String, String>> matches = read(root.resolve("data/backtests").resolve(tournament).resolve("actual_results.csv"));
        double brier = 0, logLoss = 0, predictedGoals = 0, actualGoals = 0, predictedUpsets = 0;
        int actualUpsets = 0;
        for (Map<String, String> match : matches) {
            String team1 = match.get("team1"), team2 = match.get("team2");
            TeamEloSnapshot p1 = profiles.getOrDefault(team1, new TeamEloSnapshot(elo.getOrDefault(team1, 0), 0, elo.getOrDefault(team1, 0)));
            TeamEloSnapshot p2 = profiles.getOrDefault(team2, new TeamEloSnapshot(elo.getOrDefault(team2, 0), 0, elo.getOrDefault(team2, 0)));
            var projection = calculator.project(team1, team2, elo.getOrDefault(team1, 0), elo.getOrDefault(team2, 0),
                    p1.attackQuality(), p1.defenceQuality(), p2.attackQuality(), p2.defenceQuality());
            double[] probabilities = {projection.exactTeam1WinProbability(), projection.exactDrawProbability(), projection.exactTeam2WinProbability()};
            int goals1 = Integer.parseInt(match.get("team1_goals"));
            int goals2 = Integer.parseInt(match.get("team2_goals"));
            int outcome = goals1 > goals2 ? 0 : goals2 > goals1 ? 2 : 1;
            for (int i = 0; i < probabilities.length; i++) brier += Math.pow(probabilities[i] - (i == outcome ? 1 : 0), 2);
            logLoss -= Math.log(Math.max(probabilities[outcome], 1e-12));
            predictedGoals += projection.team1ExpectedGoals() + projection.team2ExpectedGoals();
            actualGoals += goals1 + goals2;
            int favourite = probabilities[0] >= probabilities[2] ? 0 : 2;
            predictedUpsets += 1.0 - probabilities[favourite] - probabilities[1];
            if (outcome != favourite && outcome != 1) actualUpsets++;
        }
        int count = matches.size();
        return new GridResult(tournament, eloSeparation, xgMultiplier, count, brier, logLoss,
                predictedGoals, actualGoals, predictedUpsets, actualUpsets);
    }

    private static Aggregate aggregate(List<GridResult> rows) {
        int matches = rows.stream().mapToInt(GridResult::matches).sum();
        double brier = rows.stream().mapToDouble(GridResult::brierSum).sum() / matches;
        double logLoss = rows.stream().mapToDouble(GridResult::logLossSum).sum() / matches;
        double predictedGoals = rows.stream().mapToDouble(GridResult::predictedGoalsSum).sum() / matches;
        double actualGoals = rows.stream().mapToDouble(GridResult::actualGoalsSum).sum() / matches;
        double predictedUpsetRate = rows.stream().mapToDouble(GridResult::predictedUpsetsSum).sum() / matches;
        double actualUpsetRate = rows.stream().mapToInt(GridResult::actualUpsets).sum() / (double) matches;
        double objective = brier + 0.20 * logLoss + 0.05 * Math.abs(predictedGoals - actualGoals)
                + 0.10 * Math.abs(predictedUpsetRate - actualUpsetRate);
        GridResult first = rows.getFirst();
        return new Aggregate(first.eloSeparation, first.xgMultiplier, matches, brier, logLoss,
                predictedGoals, actualGoals, predictedUpsetRate, actualUpsetRate, objective);
    }

    private static GridResult find(List<GridResult> rows, String tournament, double eloSeparation, double xgMultiplier) {
        return rows.stream().filter(r -> tournament.equals(r.tournament) && r.eloSeparation == eloSeparation
                && r.xgMultiplier == xgMultiplier).findFirst().orElseThrow();
    }

    private void writeGrid(Path path, List<Aggregate> rows) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path); CSVPrinter csv = CSVFormat.DEFAULT.print(writer)) {
            csv.printRecord("rank", "elo_goal_separation", "xg_multiplier", "matches", "brier", "log_loss",
                    "predicted_goals", "actual_goals", "predicted_upset_rate", "actual_upset_rate", "objective");
            int rank = 1;
            for (Aggregate r : rows) csv.printRecord(rank++, f(r.eloSeparation), f(r.xgMultiplier), r.matches,
                    f(r.brier), f(r.logLoss), f(r.predictedGoals), f(r.actualGoals), f(r.predictedUpsetRate),
                    f(r.actualUpsetRate), f(r.objective));
        }
    }

    private void writeHoldouts(Path path, List<Holdout> rows) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path); CSVPrinter csv = CSVFormat.DEFAULT.print(writer)) {
            csv.printRecord("holdout", "selected_elo_goal_separation", "selected_xg_multiplier", "training_objective",
                    "holdout_brier", "holdout_log_loss", "holdout_predicted_goals", "holdout_actual_goals",
                    "holdout_predicted_upset_rate", "holdout_actual_upset_rate", "baseline_brier", "brier_delta",
                    "baseline_log_loss", "log_loss_delta");
            for (Holdout h : rows) {
                GridResult v = h.validation;
                csv.printRecord(h.tournament, f(h.selected.eloSeparation), f(h.selected.xgMultiplier), f(h.selected.objective),
                        f(v.brierSum / v.matches), f(v.logLossSum / v.matches), f(v.predictedGoalsSum / v.matches),
                        f(v.actualGoalsSum / v.matches), f(v.predictedUpsetsSum / v.matches), f(v.actualUpsets / (double) v.matches),
                        f(h.baseline.brierSum / h.baseline.matches), f(v.brierSum / v.matches - h.baseline.brierSum / h.baseline.matches),
                        f(h.baseline.logLossSum / h.baseline.matches), f(v.logLossSum / v.matches - h.baseline.logLossSum / h.baseline.matches));
            }
        }
    }

    private static void print(List<Aggregate> aggregates, List<Holdout> holdouts) {
        System.out.println("Top deterministic calibration combinations:");
        System.out.printf("%-5s %-8s %-8s %-8s %-8s %-8s %-8s%n", "Rank", "ELO", "xG", "Brier", "LogLoss", "Goals", "Objective");
        for (int i = 0; i < Math.min(5, aggregates.size()); i++) {
            Aggregate r = aggregates.get(i);
            System.out.printf(Locale.ROOT, "%-5d %-8.2f %-8.2f %-8.4f %-8.4f %.2f/%-5.2f %.4f%n",
                    i + 1, r.eloSeparation, r.xgMultiplier, r.brier, r.logLoss, r.predictedGoals, r.actualGoals, r.objective);
        }
        Map<String, Long> selections = new LinkedHashMap<>();
        holdouts.forEach(h -> selections.merge(String.format(Locale.ROOT, "%.2f / %.2f", h.selected.eloSeparation, h.selected.xgMultiplier), 1L, Long::sum));
        long brierWins = holdouts.stream().filter(h -> h.validation.brierSum / h.validation.matches
                < h.baseline.brierSum / h.baseline.matches).count();
        long logLossWins = holdouts.stream().filter(h -> h.validation.logLossSum / h.validation.matches
                < h.baseline.logLossSum / h.baseline.matches).count();
        System.out.println("Leave-one-tournament-out selections: " + selections);
        System.out.println("Selected parameters beat the current baseline on " + brierWins
                + "/" + holdouts.size() + " Brier holdouts and " + logLossWins + "/" + holdouts.size()
                + " log-loss holdouts.");
        System.out.println("Reports written to data/backtests/training_grid*.csv.");
    }

    private static List<Map<String, String>> read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                parser.getHeaderNames().forEach(header -> row.put(header, record.get(header)));
                rows.add(row);
            }
            return rows;
        }
    }

    private static String f(double value) { return String.format(Locale.ROOT, "%.4f", value); }

    private record GridResult(String tournament, double eloSeparation, double xgMultiplier, int matches,
                              double brierSum, double logLossSum, double predictedGoalsSum, double actualGoalsSum,
                              double predictedUpsetsSum, int actualUpsets) {}
    private record Aggregate(double eloSeparation, double xgMultiplier, int matches, double brier, double logLoss,
                             double predictedGoals, double actualGoals, double predictedUpsetRate,
                             double actualUpsetRate, double objective) {}
    private record Holdout(String tournament, Aggregate selected, GridResult validation, GridResult baseline) {}
}

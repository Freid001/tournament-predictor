package com.tournamentpredictor.service.handler;

import com.tournamentpredictor.service.util.ExpectedGoalsCalculator;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Independently tests multipliers for historical contextual ELO signals. */
public class TrainingContextGridHandler {
    private static final double[] MULTIPLIERS = {0.0, 0.5, 1.0, 1.5, 2.0};
    private static final List<Signal> SIGNALS = List.of(
            new Signal("age", "squad_age_profile", new int[]{0, -12, -8}),
            new Signal("cohesion", "squad_cohesion", new int[]{0, -11, -22, -45}),
            new Signal("depth", "squad_depth", new int[]{0, -10, -20}),
            new Signal("dropouts", "squad_dropouts", new int[]{0, -18, -35, -70}),
            new Signal("injuries", "injury_impact", new int[]{0, -22, -45, -90}),
            new Signal("heat", "heat_impact", new int[]{0, 9, 18, 35})
    );

    private final Path root;
    private final double eloScaleDivisor;

    public TrainingContextGridHandler(Path root, double eloScaleDivisor) {
        this.root = root;
        this.eloScaleDivisor = eloScaleDivisor;
    }

    public void handle() throws IOException {
        List<TournamentData> tournaments = loadTournaments();
        List<Aggregate> allAggregates = new ArrayList<>();
        List<Holdout> allHoldouts = new ArrayList<>();
        for (Signal signal : SIGNALS) {
            GridRun run = run(tournaments, signal);
            allAggregates.addAll(run.aggregates);
            allHoldouts.addAll(run.holdouts);
            print(signal, run);
        }
        Path output = root.resolve("data/backtests");
        writeGrid(output.resolve("training_context_grid.csv"), allAggregates);
        writeHoldouts(output.resolve("training_context_grid_holdouts.csv"), allHoldouts);
        System.out.println("Context reports written to data/backtests/training_context_grid*.csv.");
    }

    private GridRun run(List<TournamentData> tournaments, Signal signal) {
        List<Result> results = new ArrayList<>();
        for (double multiplier : MULTIPLIERS) {
            for (TournamentData tournament : tournaments) results.add(evaluate(tournament, signal, multiplier));
        }
        List<Aggregate> aggregates = new ArrayList<>();
        for (double multiplier : MULTIPLIERS) {
            aggregates.add(aggregate(signal, multiplier, results.stream().filter(r -> r.multiplier == multiplier).toList()));
        }
        aggregates.sort(Comparator.comparingDouble(Aggregate::objective));
        List<Holdout> holdouts = new ArrayList<>();
        for (TournamentData holdout : tournaments) {
            Aggregate selected = java.util.Arrays.stream(MULTIPLIERS)
                    .mapToObj(multiplier -> aggregate(signal, multiplier, results.stream()
                            .filter(r -> r.multiplier == multiplier && !r.tournament.equals(holdout.name)).toList()))
                    .min(Comparator.comparingDouble(Aggregate::objective)).orElseThrow();
            holdouts.add(new Holdout(signal.name, holdout.name, selected,
                    find(results, holdout.name, selected.multiplier), find(results, holdout.name, 1.0)));
        }
        return new GridRun(aggregates, holdouts);
    }

    private Result evaluate(TournamentData tournament, Signal signal, double multiplier) {
        ExpectedGoalsCalculator calculator = new ExpectedGoalsCalculator(eloScaleDivisor, 2.60, 1.20, 0.93, 0.05);
        double brier = 0, logLoss = 0, predictedGoals = 0, actualGoals = 0;
        for (Match match : tournament.matches) {
            Profile p1 = tournament.profiles.get(match.team1), p2 = tournament.profiles.get(match.team2);
            int elo1 = p1.adjustedElo + signal.adjustment(p1.levels.getOrDefault(signal.name, 0), multiplier);
            int elo2 = p2.adjustedElo + signal.adjustment(p2.levels.getOrDefault(signal.name, 0), multiplier);
            var p = calculator.project(match.team1, match.team2, elo1, elo2,
                    p1.attack, p1.defence, p2.attack, p2.defence);
            double[] probabilities = {p.exactTeam1WinProbability(), p.exactDrawProbability(), p.exactTeam2WinProbability()};
            int outcome = match.goals1 > match.goals2 ? 0 : match.goals2 > match.goals1 ? 2 : 1;
            for (int i = 0; i < 3; i++) brier += Math.pow(probabilities[i] - (i == outcome ? 1 : 0), 2);
            logLoss -= Math.log(Math.max(probabilities[outcome], 1e-12));
            predictedGoals += p.team1ExpectedGoals() + p.team2ExpectedGoals();
            actualGoals += match.goals1 + match.goals2;
        }
        int signalMatches = (int) tournament.matches.stream().filter(match ->
                tournament.profiles.get(match.team1).levels.getOrDefault(signal.name, 0) != 0
                        || tournament.profiles.get(match.team2).levels.getOrDefault(signal.name, 0) != 0).count();
        return new Result(tournament.name, multiplier, tournament.matches.size(), signalMatches,
                brier, logLoss, predictedGoals, actualGoals);
    }

    private static Aggregate aggregate(Signal signal, double multiplier, List<Result> rows) {
        int matches = rows.stream().mapToInt(Result::matches).sum();
        int signalMatches = rows.stream().mapToInt(Result::signalMatches).sum();
        double brier = rows.stream().mapToDouble(Result::brier).sum() / matches;
        double logLoss = rows.stream().mapToDouble(Result::logLoss).sum() / matches;
        double predictedGoals = rows.stream().mapToDouble(Result::predictedGoals).sum() / matches;
        double actualGoals = rows.stream().mapToDouble(Result::actualGoals).sum() / matches;
        double objective = brier + 0.20 * logLoss + 0.05 * Math.abs(predictedGoals - actualGoals);
        return new Aggregate(signal.name, multiplier, matches, signalMatches, brier, logLoss,
                predictedGoals, actualGoals, objective);
    }

    private List<TournamentData> loadTournaments() throws IOException {
        List<TournamentData> tournaments = new ArrayList<>();
        try (var paths = Files.list(root.resolve("data/backtests"))) {
            for (Path path : paths.filter(Files::isDirectory).sorted().toList()) {
                String name = path.getFileName().toString();
                Path actual = path.resolve("actual_results.csv");
                Path prediction = root.resolve("data/predictions").resolve(name);
                if (!Files.exists(actual) || !Files.exists(prediction.resolve("groups.csv"))) continue;
                Map<String, Map<String, Integer>> levels = loadLevels(prediction.resolve("start.csv"));
                Map<String, Profile> profiles = loadProfiles(prediction.resolve("groups.csv"), levels);
                tournaments.add(new TournamentData(name, profiles, loadMatches(actual, profiles)));
            }
        }
        return tournaments;
    }

    private static Map<String, Map<String, Integer>> loadLevels(Path path) throws IOException {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(path); CSVParser csv = format().parse(reader)) {
            for (CSVRecord row : csv) {
                Map<String, Integer> levels = new HashMap<>();
                for (Signal signal : SIGNALS) levels.put(signal.name, integer(row, signal.column));
                result.put(row.get("team").trim(), levels);
            }
        }
        return result;
    }

    private static Map<String, Profile> loadProfiles(Path path, Map<String, Map<String, Integer>> levels) throws IOException {
        Map<String, Profile> result = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(path); CSVParser csv = format().parse(reader)) {
            for (CSVRecord row : csv) {
                String team = row.get("team").trim();
                result.put(team, new Profile(integer(row, "elo_ranking"), integer(row, "attack_quality"),
                        integer(row, "defence_quality"), levels.getOrDefault(team, Map.of())));
            }
        }
        return result;
    }

    private static List<Match> loadMatches(Path path, Map<String, Profile> profiles) throws IOException {
        List<Match> matches = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path); CSVParser csv = format().parse(reader)) {
            for (CSVRecord row : csv) {
                String team1 = row.get("team1"), team2 = row.get("team2");
                if (!profiles.containsKey(team1) || !profiles.containsKey(team2))
                    throw new IOException("Missing profile for " + team1 + " or " + team2 + " in " + path);
                matches.add(new Match(team1, team2, integer(row, "team1_goals"), integer(row, "team2_goals")));
            }
        }
        return matches;
    }

    private static CSVFormat format() {
        return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build();
    }

    private static Result find(List<Result> rows, String tournament, double multiplier) {
        return rows.stream().filter(r -> r.tournament.equals(tournament) && r.multiplier == multiplier).findFirst().orElseThrow();
    }

    private static void writeGrid(Path path, List<Aggregate> rows) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path); CSVPrinter csv = CSVFormat.DEFAULT.print(writer)) {
            csv.printRecord("signal", "rank", "production_weight_multiplier", "matches", "signal_matches",
                    "brier", "log_loss", "predicted_goals", "actual_goals", "objective");
            Map<String, Integer> ranks = new HashMap<>();
            for (Aggregate row : rows) csv.printRecord(row.signal, ranks.merge(row.signal, 1, Integer::sum),
                    f(row.multiplier), row.matches, row.signalMatches, f(row.brier), f(row.logLoss),
                    f(row.predictedGoals), f(row.actualGoals), f(row.objective));
        }
    }

    private static void writeHoldouts(Path path, List<Holdout> rows) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path); CSVPrinter csv = CSVFormat.DEFAULT.print(writer)) {
            csv.printRecord("signal", "holdout", "selected_multiplier", "training_objective", "signal_matches",
                    "holdout_brier", "baseline_brier", "brier_delta", "holdout_log_loss", "baseline_log_loss", "log_loss_delta");
            for (Holdout row : rows) {
                Result v = row.validation, b = row.baseline;
                csv.printRecord(row.signal, row.tournament, f(row.selected.multiplier), f(row.selected.objective),
                        v.signalMatches, f(v.brier / v.matches), f(b.brier / b.matches),
                        f(v.brier / v.matches - b.brier / b.matches), f(v.logLoss / v.matches),
                        f(b.logLoss / b.matches), f(v.logLoss / v.matches - b.logLoss / b.matches));
            }
        }
    }

    private static void print(Signal signal, GridRun run) {
        Aggregate best = run.aggregates.getFirst();
        long brierWins = run.holdouts.stream().filter(h -> h.validation.brier / h.validation.matches
                < h.baseline.brier / h.baseline.matches).count();
        long logWins = run.holdouts.stream().filter(h -> h.validation.logLoss / h.validation.matches
                < h.baseline.logLoss / h.baseline.matches).count();
        Map<String, Long> selections = new LinkedHashMap<>();
        run.holdouts.forEach(h -> selections.merge(f(h.selected.multiplier), 1L, Long::sum));
        System.out.printf(Locale.ROOT, "Context %-9s best multiplier %.2f over %d signal matches; Brier %.4f, log loss %.4f%n",
                signal.name, best.multiplier, best.signalMatches, best.brier, best.logLoss);
        System.out.println("  holdout selections " + selections + "; beats baseline " + brierWins + "/"
                + run.holdouts.size() + " Brier and " + logWins + "/" + run.holdouts.size() + " log loss.");
    }

    private static int integer(CSVRecord row, String column) {
        if (!row.isMapped(column) || row.get(column).isBlank()) return 0;
        return Integer.parseInt(row.get(column).trim());
    }
    private static String f(double value) { return String.format(Locale.ROOT, "%.4f", value); }

    private record Signal(String name, String column, int[] contributions) {
        int production(int level) {
            if ("depth".equals(name) && level == -1) return 10;
            return contributions[Math.max(0, Math.min(level, contributions.length - 1))];
        }
        int adjustment(int level, double multiplier) { return (int) Math.round(production(level) * (multiplier - 1.0)); }
    }
    private record Profile(int adjustedElo, int attack, int defence, Map<String, Integer> levels) {}
    private record Match(String team1, String team2, int goals1, int goals2) {}
    private record TournamentData(String name, Map<String, Profile> profiles, List<Match> matches) {}
    private record Result(String tournament, double multiplier, int matches, int signalMatches, double brier,
                          double logLoss, double predictedGoals, double actualGoals) {}
    private record Aggregate(String signal, double multiplier, int matches, int signalMatches, double brier,
                             double logLoss, double predictedGoals, double actualGoals, double objective) {}
    private record Holdout(String signal, String tournament, Aggregate selected, Result validation, Result baseline) {}
    private record GridRun(List<Aggregate> aggregates, List<Holdout> holdouts) {}
}

package com.tournamentpredictor.services.training;

import com.tournamentpredictor.services.calculation.ExpectedGoalsCalculator;
import com.tournamentpredictor.services.calculation.QualificationFormCalculator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Tests pre-tournament friendly ELO caps while preserving every other saved team adjustment. */
public class TrainingWarmupGridHandler {
    private static final int[] CAPS = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 120, 140, 160, 180, 200};
    private static final int BASELINE_CAP = 50;
    private static final int MAX_GAMES = 3;

    private final Path root;
    private final double eloScaleDivisor;
    private final double goalDiffPer400Elo;
    private final double xgMultiplier;

    public TrainingWarmupGridHandler(Path root, double eloScaleDivisor,
                                     double goalDiffPer400Elo, double xgMultiplier) {
        this.root = root;
        this.eloScaleDivisor = eloScaleDivisor;
        this.goalDiffPer400Elo = goalDiffPer400Elo;
        this.xgMultiplier = xgMultiplier;
    }

    public void handle() throws IOException {
        List<String> tournaments = discoverTournaments();
        List<Result> results = new ArrayList<>();
        for (int cap : CAPS) {
            for (String tournament : tournaments) results.add(evaluate(tournament, cap));
        }

        List<Aggregate> aggregates = new ArrayList<>();
        for (int cap : CAPS) aggregates.add(aggregate(cap, results.stream().filter(r -> r.cap == cap).toList()));
        aggregates.sort(Comparator.comparingDouble(Aggregate::objective));

        List<Holdout> holdouts = new ArrayList<>();
        for (String holdout : tournaments) {
            Aggregate selected = null;
            for (int cap : CAPS) {
                Aggregate training = aggregate(cap, results.stream()
                        .filter(r -> r.cap == cap && !holdout.equals(r.tournament)).toList());
                if (selected == null || training.objective < selected.objective) selected = training;
            }
            holdouts.add(new Holdout(holdout, selected,
                    find(results, holdout, selected.cap), find(results, holdout, BASELINE_CAP)));
        }

        Path output = root.resolve("data/backtests");
        writeGrid(output.resolve("training_warmup_grid.csv"), aggregates);
        writeHoldouts(output.resolve("training_warmup_grid_holdouts.csv"), holdouts);
        print(aggregates, holdouts);
    }

    private Result evaluate(String tournament, int cap) throws IOException {
        Map<String, Profile> profiles = loadProfiles(tournament);
        Properties metadata = loadProperties(root.resolve("data/elo/snapshots").resolve(tournament)
                .resolve("metadata.properties"));
        int since = Integer.parseInt(metadata.getProperty("pre_tournament_form_since"));
        int until = Integer.parseInt(metadata.getProperty("pre_tournament_form_until"));
        LocalDate cutoff = LocalDate.parse(metadata.getProperty("tournament_start_date"));
        QualificationFormCalculator form = new QualificationFormCalculator(
                root.resolve("data/elo/snapshots").resolve(tournament).resolve("history"),
                since, until, cap, Set.of("F"), MAX_GAMES, cutoff);
        ExpectedGoalsCalculator calculator = new ExpectedGoalsCalculator(
                eloScaleDivisor, 2.60, goalDiffPer400Elo, xgMultiplier);

        double brier = 0, logLoss = 0, predictedUpsets = 0;
        int actualUpsets = 0;
        Set<String> teamsWithForm = new java.util.HashSet<>();
        List<Map<String, String>> matches = read(root.resolve("data/backtests").resolve(tournament)
                .resolve("actual_results.csv"));
        for (Map<String, String> match : matches) {
            String team1 = match.get("team1"), team2 = match.get("team2");
            Profile p1 = required(profiles, team1, tournament);
            Profile p2 = required(profiles, team2, tournament);
            if (form.hasData(team1)) teamsWithForm.add(team1);
            if (form.hasData(team2)) teamsWithForm.add(team2);
            int elo1 = p1.adjustedElo - p1.savedWarmupBonus + form.getQualBonus(team1);
            int elo2 = p2.adjustedElo - p2.savedWarmupBonus + form.getQualBonus(team2);
            var projection = calculator.project(team1, team2, elo1, elo2,
                    p1.attackQuality, p1.defenceQuality, p2.attackQuality, p2.defenceQuality);
            double[] probabilities = {projection.exactTeam1WinProbability(), projection.exactDrawProbability(),
                    projection.exactTeam2WinProbability()};
            int goals1 = Integer.parseInt(match.get("team1_goals"));
            int goals2 = Integer.parseInt(match.get("team2_goals"));
            int outcome = goals1 > goals2 ? 0 : goals2 > goals1 ? 2 : 1;
            for (int i = 0; i < probabilities.length; i++) {
                brier += Math.pow(probabilities[i] - (i == outcome ? 1 : 0), 2);
            }
            logLoss -= Math.log(Math.max(probabilities[outcome], 1e-12));
            int noWarmupElo1 = p1.adjustedElo - p1.savedWarmupBonus;
            int noWarmupElo2 = p2.adjustedElo - p2.savedWarmupBonus;
            int favourite = noWarmupElo1 >= noWarmupElo2 ? 0 : 2;
            predictedUpsets += probabilities[favourite == 0 ? 2 : 0];
            if (outcome != favourite && outcome != 1) actualUpsets++;
        }
        return new Result(tournament, cap, matches.size(), teamsWithForm.size(), brier, logLoss,
                predictedUpsets, actualUpsets);
    }

    private Map<String, Profile> loadProfiles(String tournament) throws IOException {
        Path path = root.resolve("data/predictions").resolve(tournament).resolve("groups.csv");
        Map<String, Profile> profiles = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(path);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true).build().parse(reader)) {
            for (CSVRecord row : parser) {
                String team = row.get("team").trim();
                profiles.put(team, new Profile(i(row, "elo_ranking"), i(row, "pre_tournament_bonus"),
                        i(row, "attack_quality"), i(row, "defence_quality")));
            }
        }
        return profiles;
    }

    private List<String> discoverTournaments() throws IOException {
        Path backtests = root.resolve("data/backtests");
        try (var paths = Files.list(backtests)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> Files.exists(path.resolve("actual_results.csv")))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> Files.exists(root.resolve("data/predictions").resolve(name).resolve("groups.csv")))
                    .filter(name -> Files.exists(root.resolve("data/elo/snapshots").resolve(name)
                            .resolve("metadata.properties")))
                    .sorted().toList();
        }
    }

    private static Aggregate aggregate(int cap, List<Result> rows) {
        int matches = rows.stream().mapToInt(Result::matches).sum();
        int teamsWithForm = rows.stream().mapToInt(Result::teamsWithForm).sum();
        double brier = rows.stream().mapToDouble(Result::brierSum).sum() / matches;
        double logLoss = rows.stream().mapToDouble(Result::logLossSum).sum() / matches;
        double predictedUpsetRate = rows.stream().mapToDouble(Result::predictedUpsets).sum() / matches;
        double actualUpsetRate = rows.stream().mapToInt(Result::actualUpsets).sum() / (double) matches;
        double objective = brier + 0.20 * logLoss + 0.10 * Math.abs(predictedUpsetRate - actualUpsetRate);
        return new Aggregate(cap, matches, teamsWithForm, brier, logLoss, predictedUpsetRate,
                actualUpsetRate, objective);
    }

    private static Result find(List<Result> rows, String tournament, int cap) {
        return rows.stream().filter(r -> r.cap == cap && tournament.equals(r.tournament))
                .findFirst().orElseThrow();
    }

    private static Profile required(Map<String, Profile> profiles, String team, String tournament) throws IOException {
        Profile profile = profiles.get(team);
        if (profile == null) throw new IOException("Missing group profile for " + team + " in " + tournament);
        return profile;
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { properties.load(reader); }
        return properties;
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

    private static void writeGrid(Path path, List<Aggregate> rows) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path); CSVPrinter csv = CSVFormat.DEFAULT.print(writer)) {
            csv.printRecord("rank", "warmup_elo_cap", "matches", "teams_with_warmup_data", "brier",
                    "log_loss", "predicted_upset_rate", "actual_upset_rate", "objective");
            int rank = 1;
            for (Aggregate row : rows) csv.printRecord(rank++, row.cap, row.matches, row.teamsWithForm,
                    f(row.brier), f(row.logLoss), f(row.predictedUpsetRate), f(row.actualUpsetRate), f(row.objective));
        }
    }

    private static void writeHoldouts(Path path, List<Holdout> rows) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path); CSVPrinter csv = CSVFormat.DEFAULT.print(writer)) {
            csv.printRecord("holdout", "selected_warmup_elo_cap", "training_objective", "teams_with_warmup_data",
                    "holdout_brier", "baseline_brier", "brier_delta", "holdout_log_loss", "baseline_log_loss",
                    "log_loss_delta", "holdout_predicted_upset_rate", "holdout_actual_upset_rate");
            for (Holdout h : rows) {
                Result v = h.validation, b = h.baseline;
                csv.printRecord(h.tournament, h.selected.cap, f(h.selected.objective), v.teamsWithForm,
                        f(v.brierSum / v.matches), f(b.brierSum / b.matches),
                        f(v.brierSum / v.matches - b.brierSum / b.matches),
                        f(v.logLossSum / v.matches), f(b.logLossSum / b.matches),
                        f(v.logLossSum / v.matches - b.logLossSum / b.matches),
                        f(v.predictedUpsets / v.matches), f(v.actualUpsets / (double) v.matches));
            }
        }
    }

    private static void print(List<Aggregate> aggregates, List<Holdout> holdouts) {
        System.out.println("Warm-up friendly ELO cap calibration:");
        System.out.printf("%-5s %-6s %-8s %-8s %-8s %-13s %-9s%n",
                "Rank", "Cap", "Brier", "LogLoss", "FormTeams", "Upsets P/A", "Objective");
        for (int i = 0; i < aggregates.size(); i++) {
            Aggregate a = aggregates.get(i);
            System.out.printf(Locale.ROOT, "%-5d %-6d %-8.4f %-8.4f %-8d %.3f/%.3f   %.4f%n",
                    i + 1, a.cap, a.brier, a.logLoss, a.teamsWithForm,
                    a.predictedUpsetRate, a.actualUpsetRate, a.objective);
        }
        Map<Integer, Long> selections = new LinkedHashMap<>();
        holdouts.forEach(h -> selections.merge(h.selected.cap, 1L, Long::sum));
        long brierWins = holdouts.stream().filter(h -> metric(h.validation.brierSum, h.validation.matches)
                < metric(h.baseline.brierSum, h.baseline.matches)).count();
        long logLossWins = holdouts.stream().filter(h -> metric(h.validation.logLossSum, h.validation.matches)
                < metric(h.baseline.logLossSum, h.baseline.matches)).count();
        System.out.println("Leave-one-tournament-out selections: " + selections);
        System.out.println("Selected caps beat the current 50-ELO cap on " + brierWins + "/" + holdouts.size()
                + " Brier holdouts and " + logLossWins + "/" + holdouts.size() + " log-loss holdouts.");
        System.out.println("Reports written to data/backtests/training_warmup_grid*.csv.");
    }

    private static double metric(double sum, int matches) { return sum / matches; }
    private static int i(CSVRecord row, String column) { return Integer.parseInt(row.get(column).trim()); }
    private static String f(double value) { return String.format(Locale.ROOT, "%.4f", value); }

    private record Profile(int adjustedElo, int savedWarmupBonus, int attackQuality, int defenceQuality) {}
    private record Result(String tournament, int cap, int matches, int teamsWithForm, double brierSum,
                          double logLossSum, double predictedUpsets, int actualUpsets) {}
    private record Aggregate(int cap, int matches, int teamsWithForm, double brier, double logLoss,
                             double predictedUpsetRate, double actualUpsetRate, double objective) {}
    private record Holdout(String tournament, Aggregate selected, Result validation, Result baseline) {}
}

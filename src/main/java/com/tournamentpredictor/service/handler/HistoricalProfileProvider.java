package com.tournamentpredictor.service.handler;

import com.tournamentpredictor.service.util.ExpectedGoalsCalculator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Builds reproducible historical context from dated squad and match data plus sparse cited overrides. */
class HistoricalProfileProvider {
    static final String WORLD_CUP_REVISION = "f41e9437a007498bdbf3751305818101f96cb6fb";
    static final int EURO_DATASET_VERSION = 13;
    private static final String WORLD_BASE = "https://raw.githubusercontent.com/jfjelstul/worldcup/"
            + WORLD_CUP_REVISION + "/data-csv/";
    private static final String EURO_ZIP = "https://www.kaggle.com/api/v1/datasets/download/"
            + "piterfm/football-soccer-uefa-euro-1960-2024?datasetVersionNumber=" + EURO_DATASET_VERSION;
    private static final String WORLD_SOURCE = "Fjelstul World Cup Database v1.2.0 squad and player tables";
    private static final String EURO_SOURCE = "Petro Ivaniuk EURO dataset v13 match-sheet players";
    private static final ExpectedGoalsCalculator QUALITY_MODEL =
            new ExpectedGoalsCalculator(400.0, 2.60, 1.20, 0.93, 0.0);

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("USA", "United States"), Map.entry("United States of America", "United States"),
            Map.entry("Czech Republic", "Czechia"), Map.entry("Serbia and Montenegro", "Serbia"),
            Map.entry("Yugoslavia", "Serbia"), Map.entry("FR Yugoslavia", "Serbia"),
            Map.entry("Côte d'Ivoire", "Ivory Coast"), Map.entry("Bosnia-Herzegovina", "Bosnia and Herzegovina"),
            Map.entry("Bosnia & Herzegovina", "Bosnia and Herzegovina"), Map.entry("Türkiye", "Turkey"),
            Map.entry("CIS", "Russia"), Map.entry("Korea Republic", "South Korea")
    );

    private static final Map<String, Override> OVERRIDES = overrides();
    private static final Set<String> TROPICAL = Set.of("Brazil", "Colombia", "Ecuador", "Costa Rica", "Mexico",
            "Nigeria", "Cameroon", "Ghana", "Senegal", "Ivory Coast", "Morocco", "Tunisia", "Algeria",
            "Saudi Arabia", "Qatar", "Iran", "Japan", "South Korea", "Australia", "South Africa");
    private static final Set<String> VERY_HOT = Set.of("Mexico", "Saudi Arabia", "Qatar", "Morocco", "Tunisia",
            "Algeria", "Nigeria", "Cameroon", "Ghana", "Senegal", "Ivory Coast", "South Africa");

    private final Path root;
    private final HttpClient http;
    private final Map<String, List<LocalDate>> ages = new HashMap<>();
    private boolean loaded;

    HistoricalProfileProvider(Path root, HttpClient http) {
        this.root = root;
        this.http = http;
    }

    Profile profile(String tournament, LocalDate startDate, boolean euro, String team) throws IOException {
        ensureLoaded();
        String key = key(tournament, team);
        List<LocalDate> births = ages.getOrDefault(key, List.of());
        Age age = age(births, startDate);
        Quality quality = quality(team, startDate);
        Override override = OVERRIDES.getOrDefault(key, Override.NONE);
        int heat = heat(tournament, team);
        String ageNotes = births.isEmpty() ? "No complete squad-age source; neutral."
                : String.format(Locale.ROOT, "Average age %.1f from %d selected players; %s.",
                age.average, births.size(), euro ? EURO_SOURCE : WORLD_SOURCE);
        String qualityNotes = quality.games == 0 ? "No usable pre-tournament competitive history; neutral."
                : String.format(Locale.ROOT,
                "Last %d competitive matches before kickoff: attack residual %+.2f and defence residual %+.2f xG/game; derived from frozen ELO history.",
                quality.games, quality.attackResidual, quality.defenceResidual);
        String heatNotes = heat == 0 ? "No material static climate-adaptation assumption."
                : "Climate-adaptation proxy for tournament location; regional assumption, not observed player physiology.";
        return new Profile(age.level, ageNotes, override.cohesion, override.cohesionNotes,
                override.depth, override.depthNotes, quality.attackLevel, quality.defenceLevel, qualityNotes,
                override.dropouts, override.dropoutNotes, override.injuries, override.injuryNotes, heat, heatNotes);
    }

    private void ensureLoaded() throws IOException {
        if (loaded) return;
        loadWorldCupAges();
        loadEuroAges();
        loaded = true;
    }

    private void loadWorldCupAges() throws IOException {
        Map<String, LocalDate> players = new HashMap<>();
        try (CSVParser csv = parse(download(WORLD_BASE + "players.csv"))) {
            for (CSVRecord row : csv) {
                String birth = row.get("birth_date").trim();
                if (birth.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    players.put(row.get("player_id"), LocalDate.parse(birth));
                }
            }
        }
        try (CSVParser csv = parse(download(WORLD_BASE + "squads.csv"))) {
            for (CSVRecord row : csv) {
                String tournament = row.get("tournament_id");
                if (!tournament.matches("WC-(1994|1998|2002|2006|2010|2014|2018|2022)")) continue;
                LocalDate birth = players.get(row.get("player_id"));
                if (birth == null) continue;
                String year = tournament.substring(3);
                ages.computeIfAbsent(key("world_cup_" + year, canonical(row.get("team_name"))), ignored -> new ArrayList<>())
                        .add(birth);
            }
        }
    }

    private void loadEuroAges() throws IOException {
        byte[] zip = download(EURO_ZIP);
        byte[] coaches = zipEntry(zip, "euro_coaches.csv");
        byte[] lineups = zipEntry(zip, "euro_lineups.csv");
        Map<String, String> countries = new HashMap<>();
        try (CSVParser csv = parse(coaches)) {
            for (CSVRecord row : csv) countries.put(row.get("year") + "|" + row.get("country_code"),
                    canonical(row.get("country")));
        }
        Map<String, Set<String>> seen = new HashMap<>();
        try (CSVParser csv = parse(lineups)) {
            for (CSVRecord row : csv) {
                String year = row.get("year").trim();
                if (!year.matches("1992|1996|2000|2004|2008|2012|2016|2020|2024")) continue;
                String team = countries.get(year + "|" + row.get("country_code"));
                String birth = row.get("birth_date").trim();
                if (team == null || birth.isEmpty()) continue;
                String tournament = "euros_" + year;
                String key = key(tournament, team);
                String player = row.get("id_player").trim();
                if (player.isEmpty()) player = row.get("name").trim() + "|" + birth;
                if (seen.computeIfAbsent(key, ignored -> new HashSet<>()).add(player)) {
                    ages.computeIfAbsent(key, ignored -> new ArrayList<>()).add(LocalDate.parse(birth));
                }
            }
        }
    }

    private Quality quality(String team, LocalDate start) throws IOException {
        Path history = root.resolve("data/elo/current/history").resolve(team + ".tsv");
        if (!Files.exists(history)) return Quality.NONE;
        List<HistoryMatch> matches = new ArrayList<>();
        List<String> lines = Files.readAllLines(history);
        for (int n = 1; n < lines.size(); n++) {
            String[] c = lines.get(n).split("\\t", -1);
            if (c.length < 12 || "F".equals(c[7])) continue;
            LocalDate date;
            try { date = LocalDate.of(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])); }
            catch (RuntimeException ignored) { continue; }
            if (!date.isBefore(start) || date.isBefore(start.minusYears(2))) continue;
            boolean home = canonical(c[3]).equals(team), away = canonical(c[4]).equals(team);
            if (!home && !away) continue;
            try {
                int homeGoals = Integer.parseInt(c[5]), awayGoals = Integer.parseInt(c[6]);
                int homeElo = Integer.parseInt(c[10]), awayElo = Integer.parseInt(c[11]);
                var p = QUALITY_MODEL.project(c[3], c[4], homeElo, awayElo);
                double actualFor = home ? homeGoals : awayGoals;
                double actualAgainst = home ? awayGoals : homeGoals;
                double expectedFor = home ? p.team1ExpectedGoals() : p.team2ExpectedGoals();
                double expectedAgainst = home ? p.team2ExpectedGoals() : p.team1ExpectedGoals();
                matches.add(new HistoryMatch(date, actualFor - expectedFor, expectedAgainst - actualAgainst));
            } catch (NumberFormatException ignored) { }
        }
        matches.sort(Comparator.comparing(HistoryMatch::date).reversed());
        List<HistoryMatch> sample = matches.stream().limit(10).toList();
        if (sample.size() < 4) return Quality.NONE;
        double attack = sample.stream().mapToDouble(HistoryMatch::attackResidual).average().orElse(0);
        double defence = sample.stream().mapToDouble(HistoryMatch::defenceResidual).average().orElse(0);
        return new Quality(level(attack), level(defence), attack, defence, sample.size());
    }

    static Age age(List<LocalDate> births, LocalDate start) {
        if (births.size() < 15) return Age.NONE;
        List<Integer> values = births.stream().map(b -> Period.between(b, start).getYears()).toList();
        double average = values.stream().mapToInt(Integer::intValue).average().orElse(0);
        long under23 = values.stream().filter(v -> v < 23).count();
        long over32 = values.stream().filter(v -> v >= 33).count();
        int level = average <= 25.0 || under23 >= 7 ? 1 : average >= 29.0 || over32 >= 6 ? 2 : 0;
        return new Age(level, average);
    }

    static int level(double residual) {
        if (residual >= 0.45) return 2;
        if (residual >= 0.20) return 1;
        if (residual <= -0.45) return -2;
        if (residual <= -0.20) return -1;
        return 0;
    }

    static int heat(String tournament, String team) {
        if (tournament.startsWith("euros_")) return 0;
        int year = Integer.parseInt(tournament.substring(tournament.length() - 4));
        if (!Set.of(1994, 2002, 2014, 2022).contains(year)) return 0;
        if (VERY_HOT.contains(team)) return 3;
        return TROPICAL.contains(team) ? 2 : 0;
    }

    private byte[] download(String url) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "tournament-predictor/1.0").GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IOException("Historical profile source returned HTTP " + response.statusCode() + ": " + url);
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted downloading " + url, e);
        }
    }

    private static CSVParser parse(byte[] bytes) throws IOException {
        return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build()
                .parse(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
    }

    private static byte[] zipEntry(byte[] zip, String name) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                if (name.equals(entry.getName())) return input.readAllBytes();
            }
        }
        throw new IOException("Missing " + name + " in EURO dataset archive");
    }

    private static String canonical(String name) { return NAMES.getOrDefault(name.trim(), name.trim()); }
    private static String key(String tournament, String team) { return tournament + "|" + canonical(team); }

    private static Map<String, Override> overrides() {
        Map<String, Override> map = new LinkedHashMap<>();
        put(map, "euros_1992", "Denmark", 2, "Late invitation after Yugoslavia's exclusion left minimal preparation; UEFA EURO 1992 squad record.", 0, "", 0, "", 0, "");
        put(map, "world_cup_2002", "Ireland", 3, "Roy Keane left the camp after a public dispute before the tournament.", 0, "", 2, "Roy Keane was removed from the selected squad; FIFA World Cup 2002 squad record.", 0, "");
        put(map, "world_cup_2002", "France", 0, "", 0, "", 0, "", 2, "Zinedine Zidane entered the tournament with a thigh injury and missed the opening matches.");
        put(map, "world_cup_2006", "England", 0, "", 0, "", 0, "", 2, "Wayne Rooney was selected while recovering from a broken metatarsal.");
        put(map, "world_cup_2014", "Colombia", 0, "", 0, "", 2, "Radamel Falcao missed the final squad after an ACL injury.", 0, "");
        put(map, "world_cup_2018", "Spain", 2, "Julen Lopetegui was dismissed one day before kickoff and replaced by Fernando Hierro.", 0, "", 0, "", 0, "");
        put(map, "world_cup_2018", "Egypt", 0, "", 0, "", 0, "", 2, "Mohamed Salah was selected while recovering from his Champions League final shoulder injury.");
        put(map, "euros_2020", "Netherlands", 0, "", 0, "", 2, "Virgil van Dijk missed EURO 2020 while recovering from ACL surgery.", 0, "");
        put(map, "euros_2020", "Hungary", 0, "", 0, "", 1, "Dominik Szoboszlai missed the tournament through injury.", 0, "");
        put(map, "world_cup_2022", "France", 0, "", 0, "", 3, "Benzema, Pogba, Kante, Nkunku and Kimpembe were unavailable before kickoff.", 0, "");
        put(map, "world_cup_2022", "Senegal", 0, "", 0, "", 2, "Sadio Mane withdrew before the tournament through injury.", 0, "");
        put(map, "world_cup_2022", "Argentina", 0, "", 0, "", 1, "Giovani Lo Celso missed the final squad through injury.", 0, "");
        put(map, "euros_2024", "Austria", 0, "", 0, "", 2, "David Alaba and first-choice goalkeeper Alexander Schlager missed the tournament.", 0, "");
        put(map, "euros_2024", "Netherlands", 0, "", 0, "", 2, "Frenkie de Jong and Teun Koopmeiners withdrew before the opening match.", 0, "");
        put(map, "euros_2024", "Italy", 0, "", 0, "", 2, "Francesco Acerbi and Giorgio Scalvini withdrew before the tournament.", 0, "");
        return Map.copyOf(map);
    }

    private static void put(Map<String, Override> map, String tournament, String team,
                            int cohesion, String cohesionNotes, int depth, String depthNotes,
                            int dropouts, String dropoutNotes, int injuries, String injuryNotes) {
        String source = source(tournament);
        map.put(key(tournament, team), new Override(cohesion, cited(cohesionNotes, source), depth, cited(depthNotes, source),
                dropouts, cited(dropoutNotes, source), injuries, cited(injuryNotes, source)));
    }

    private static String cited(String notes, String source) { return notes.isBlank() ? notes : notes + " Source: " + source; }
    private static String source(String tournament) {
        String year = tournament.substring(tournament.length() - 4);
        return tournament.startsWith("euros_")
                ? "https://en.wikipedia.org/wiki/UEFA_Euro_" + year + "_squads"
                : "https://en.wikipedia.org/wiki/" + year + "_FIFA_World_Cup_squads";
    }

    record Profile(int age, String ageNotes, int cohesion, String cohesionNotes, int depth, String depthNotes,
                   int attack, int defence, String qualityNotes, int dropouts, String dropoutNotes,
                   int injuries, String injuryNotes, int heat, String heatNotes) {}
    record Age(int level, double average) { static final Age NONE = new Age(0, 0); }
    private record Quality(int attackLevel, int defenceLevel, double attackResidual, double defenceResidual, int games) {
        static final Quality NONE = new Quality(0, 0, 0, 0, 0);
    }
    private record HistoryMatch(LocalDate date, double attackResidual, double defenceResidual) {}
    private record Override(int cohesion, String cohesionNotes, int depth, String depthNotes,
                            int dropouts, String dropoutNotes, int injuries, String injuryNotes) {
        static final Override NONE = new Override(0, "", 0, "", 0, "", 0, "");
    }
}

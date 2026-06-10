package com.tournamentpredictor.services.training;

import com.tournamentpredictor.services.history.HistoricalProfileProvider;
import com.tournamentpredictor.services.prediction.handler.StartHandler;
import com.tournamentpredictor.services.snapshot.EloRefreshHandler;
import com.tournamentpredictor.services.snapshot.TournamentSnapshotHandler;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Downloads pinned public tournament results and generates disposable calibration inputs. */
public class TrainingDataBootstrapHandler {
    static final String WORLD_CUP_REVISION = "78521cd8f7f2e0c52392b3cfdca32952c6372980";
    static final String EURO_REVISION = "b36bf80e4afb452dbbdc24b3d02f133f107580b0";
    private static final String WORLD_CUP_RAW = "https://raw.githubusercontent.com/openfootball/worldcup/"
            + WORLD_CUP_REVISION + "/";
    private static final String EURO_RAW = "https://raw.githubusercontent.com/openfootball/euro/"
            + EURO_REVISION + "/";
    private static final Pattern GROUP_DECLARATION = Pattern.compile("^Group\\s+([A-H]|[12])\\s*\\|\\s*(.+)$");
    private static final Pattern GROUP_SECTION = Pattern.compile("^.*Group\\s+([A-H]|[12])\\s*$");
    private static final Pattern SCORE = Pattern.compile("(?<!\\d)(\\d+)\\s*-\\s*(\\d+)(?!\\d)");
    private static final String START_HEADER = "group,team,host,squad_age_profile,age_notes,squad_cohesion,"
            + "cohesion_notes,squad_depth,depth_notes,attack_quality,defence_quality,quality_notes,"
            + "squad_dropouts,dropout_notes,injury_impact,injury_notes,heat_impact";
    private static final Map<String, String> CANONICAL_NAMES = Map.ofEntries(
            Map.entry("USA", "United States"),
            Map.entry("Czech Republic", "Czechia"),
            Map.entry("Serbia and Montenegro", "Serbia"),
            Map.entry("Yugoslavia", "Serbia"),
            Map.entry("Côte d'Ivoire", "Ivory Coast"),
            Map.entry("Bosnia-Herzegovina", "Bosnia and Herzegovina"),
            Map.entry("Türkiye", "Turkey"),
            Map.entry("CIS", "Russia")
    );
    private static final List<Tournament> TOURNAMENTS = List.of(
            euro("euros_1992", "1992--sweden/euro.txt", "1992-06-10", Set.of("Sweden")),
            worldCup("world_cup_1994", "1994--usa/cup.txt", "1994-06-17", Set.of("United States")),
            euro("euros_1996", "1996--england/euro.txt", "1996-06-08", Set.of("England")),
            worldCup("world_cup_1998", "1998--france/cup.txt", "1998-06-10", Set.of("France")),
            euro("euros_2000", "2000--belgium-netherlands/euro.txt", "2000-06-10", Set.of("Belgium", "Netherlands")),
            worldCup("world_cup_2002", "2002--south-korea-n-japan/cup.txt", "2002-05-31", Set.of("South Korea", "Japan")),
            euro("euros_2004", "2004--portugal/euro.txt", "2004-06-12", Set.of("Portugal")),
            worldCup("world_cup_2006", "2006--germany/cup.txt", "2006-06-09", Set.of("Germany")),
            euro("euros_2008", "2008--austria-switzerland/euro.txt", "2008-06-07", Set.of("Austria", "Switzerland")),
            worldCup("world_cup_2010", "2010--south-africa/cup.txt", "2010-06-11", Set.of("South Africa")),
            euro("euros_2012", "2012--poland-ukraine/euro.txt", "2012-06-08", Set.of("Poland", "Ukraine")),
            worldCup("world_cup_2014", "2014--brazil/cup.txt", "2014-06-12", Set.of("Brazil")),
            euro("euros_2016", "2016--france/euro.txt", "2016-06-10", Set.of("France")),
            worldCup("world_cup_2018", "2018--russia/cup.txt", "2018-06-14", Set.of("Russia")),
            euro("euros_2020", "2021--europe/euro.txt", "2021-06-11", Set.of()),
            worldCup("world_cup_2022", "2022--qatar/cup.txt", "2022-11-20", Set.of("Qatar")),
            euro("euros_2024", "2024--germany/euro.txt", "2024-06-14", Set.of("Germany"))
    );

    private final Path root;
    private final HttpClient httpClient;
    private final EloRefreshHandler eloRefreshHandler;
    private final TournamentSnapshotHandler snapshotHandler;
    private final StartHandler startHandler;

    public TrainingDataBootstrapHandler(Path root, EloRefreshHandler eloRefreshHandler,
                                        TournamentSnapshotHandler snapshotHandler, StartHandler startHandler) {
        this(root, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
                        .followRedirects(HttpClient.Redirect.NORMAL).build(),
                eloRefreshHandler, snapshotHandler, startHandler);
    }

    TrainingDataBootstrapHandler(Path root, HttpClient httpClient, EloRefreshHandler eloRefreshHandler,
                                 TournamentSnapshotHandler snapshotHandler, StartHandler startHandler) {
        this.root = root;
        this.httpClient = httpClient;
        this.eloRefreshHandler = eloRefreshHandler;
        this.snapshotHandler = snapshotHandler;
        this.startHandler = startHandler;
    }

    public void handle() throws IOException {
        System.out.println("Bootstrapping disposable historical calibration data from pinned public sources...");
        ensureEloSource();
        HistoricalProfileProvider profiles = new HistoricalProfileProvider(root, httpClient);
        for (Tournament tournament : TOURNAMENTS) {
            String source = download(tournament.sourceUrl);
            ParsedTournament parsed = parse(source, tournament);
            writeTournament(tournament, parsed, profiles);
            System.out.println("Prepared " + tournament.name + ": " + parsed.teams.size() + " teams, "
                    + parsed.matches.size() + " group matches.");
        }
        for (Tournament tournament : TOURNAMENTS) {
            snapshotHandler.handle(tournament.name);
            startHandler.handle(tournament.name);
        }
        writeSources();
        System.out.println("Training data bootstrap complete. Generated files are ignored by Git.");
    }

    private void ensureEloSource() throws IOException {
        Path current = root.resolve("data/elo/current");
        if (!Files.exists(current.resolve("world.csv")) || !Files.isDirectory(current.resolve("history"))) {
            System.out.println("Current ELO source is missing; downloading ratings and histories...");
            eloRefreshHandler.handle();
        }
    }

    private String download(String url) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "tournament-predictor/1.0").GET().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("Training source returned HTTP " + response.statusCode() + ": " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + url, e);
        }
    }

    ParsedTournament parse(String source, Tournament tournament) throws IOException {
        Map<String, List<SourceTeam>> groups = new LinkedHashMap<>();
        String currentGroup = null;
        List<Match> matches = new ArrayList<>();
        for (String raw : source.lines().toList()) {
            String line = raw.trim();
            Matcher declaration = GROUP_DECLARATION.matcher(line);
            if (declaration.matches()) {
                String group = normalizeGroup(declaration.group(1));
                List<SourceTeam> teams = parseTeams(declaration.group(2));
                if (teams.size() != 4) {
                    throw new IOException("Expected four teams in " + tournament.name + " group " + group
                            + ", found " + teams.size() + ": " + declaration.group(2));
                }
                groups.put(group, teams);
                continue;
            }
            Matcher section = GROUP_SECTION.matcher(line.replace("▪", "").trim());
            if (section.matches()) {
                currentGroup = normalizeGroup(section.group(1));
                continue;
            }
            if (line.startsWith("▪") && !line.toLowerCase(Locale.ROOT).contains("group")) {
                currentGroup = null;
            }
            if (currentGroup == null || !groups.containsKey(currentGroup)) continue;
            Match match = parseMatch(line, currentGroup, groups.get(currentGroup), tournament.startDate);
            if (match != null) matches.add(match);
        }
        int expected = groups.size() * 6;
        if (groups.isEmpty() || matches.size() != expected) {
            throw new IOException("Parsed " + matches.size() + "/" + expected + " group matches for "
                    + tournament.name + " from " + tournament.sourceUrl);
        }
        Set<String> teams = new LinkedHashSet<>();
        groups.values().forEach(group -> group.forEach(team -> teams.add(team.canonical)));
        return new ParsedTournament(groups, teams, matches);
    }

    private static List<SourceTeam> parseTeams(String text) {
        List<SourceTeam> teams = new ArrayList<>();
        for (String sourceName : text.trim().split("\\s{2,}")) {
            String trimmed = sourceName.trim();
            if (!trimmed.isEmpty()) teams.add(new SourceTeam(trimmed, canonical(trimmed)));
        }
        return teams;
    }

    private static Match parseMatch(String line, String group, List<SourceTeam> teams, LocalDate date) {
        Matcher score = SCORE.matcher(line);
        if (!score.find()) return null;
        List<LocatedTeam> located = new ArrayList<>();
        for (SourceTeam team : teams) {
            int index = locate(line, team);
            if (index >= 0) located.add(new LocatedTeam(index, team));
        }
        located.sort(Comparator.comparingInt(LocatedTeam::index));
        if (located.size() != 2 || located.get(0).team.canonical.equals(located.get(1).team.canonical)) return null;
        return new Match(group, date, located.get(0).team.canonical, located.get(1).team.canonical,
                Integer.parseInt(score.group(1)), Integer.parseInt(score.group(2)));
    }

    private static int locate(String line, SourceTeam team) {
        List<String> aliases = new ArrayList<>();
        aliases.add(team.source);
        if ("Ivory Coast".equals(team.canonical)) aliases.add("Côte d'Ivoire");
        for (String alias : aliases) {
            Matcher matcher = Pattern.compile("(?iu)(?<![\\p{L}])" + Pattern.quote(alias) + "(?![\\p{L}])")
                    .matcher(line);
            if (matcher.find()) return matcher.start();
        }
        return -1;
    }

    private void writeTournament(Tournament tournament, ParsedTournament parsed, HistoricalProfileProvider profileProvider) throws IOException {
        Path predictions = root.resolve("data/predictions").resolve(tournament.name);
        Path backtests = root.resolve("data/backtests").resolve(tournament.name);
        Files.createDirectories(predictions);
        Files.createDirectories(backtests);
        try (var writer = Files.newBufferedWriter(predictions.resolve("start.csv"));
             CSVPrinter csv = CSVFormat.DEFAULT.print(writer)) {
            csv.printRecord((Object[]) START_HEADER.split(","));
            for (Map.Entry<String, List<SourceTeam>> group : parsed.groups.entrySet()) {
                for (SourceTeam team : group.getValue()) {
                    boolean host = tournament.hosts.contains(team.canonical);
                    var p = profileProvider.profile(tournament.name, tournament.startDate, tournament.euro, team.canonical);
                    csv.printRecord(group.getKey(), team.canonical, host ? "yes" : "no",
                            p.age(), p.ageNotes(), p.cohesion(), p.cohesionNotes(), p.depth(), p.depthNotes(),
                            p.attack(), p.defence(), p.qualityNotes(), p.dropouts(), p.dropoutNotes(),
                            p.injuries(), p.injuryNotes(), p.heat());
                }
            }
        }
        Files.deleteIfExists(predictions.resolve("groups.csv"));
        int year = tournament.startDate.getYear();
        Files.writeString(predictions.resolve("tournament.properties"),
                "# Generated historical calibration boundary.\n"
                        + "tournament.start.date=" + tournament.startDate + "\n"
                        + "qual.form.since.year=" + (year - 2) + "\n"
                        + "qual.form.until.year=" + year + "\n"
                        + "pre.tournament.form.since.year=" + year + "\n"
                        + "pre.tournament.form.until.year=" + year + "\n"
                        + "qual.form.match.types=" + (tournament.euro ? "EQ" : "WQ,WQS,FQ") + "\n"
                        + "group.ranking.rules=" + (tournament.euro ? "uefa" : "fifa") + "\n");
        try (var writer = Files.newBufferedWriter(backtests.resolve("actual_results.csv"));
             CSVPrinter csv = CSVFormat.DEFAULT.print(writer)) {
            csv.printRecord("stage", "date", "team1", "team2", "team1_goals", "team2_goals");
            for (Match match : parsed.matches) {
                csv.printRecord("groups", match.date, match.team1, match.team2, match.goals1, match.goals2);
            }
        }
    }

    private void writeSources() throws IOException {
        Path path = root.resolve("data/backtests/SOURCES.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "# Generated training sources\n\n"
                + "- World Cups: https://github.com/openfootball/worldcup at `" + WORLD_CUP_REVISION + "`\n"
                + "- EURO results: https://github.com/openfootball/euro at `" + EURO_REVISION + "`\n"
                + "- World Cup squad ages: Fjelstul World Cup Database at `" + HistoricalProfileProvider.WORLD_CUP_REVISION + "`\n"
                + "- EURO match-sheet ages: Kaggle piterfm EURO dataset version `" + HistoricalProfileProvider.EURO_DATASET_VERSION + "`\n"
                + "- Attack/defence profiles: frozen pre-kickoff ELO match histories.\n"
                + "- Other context: sparse documented assumptions in HistoricalProfileProvider; not exhaustive.\n\n"
                + "Generated by `./predict.sh --mode=training`. Do not commit this directory.\n");
    }

    private static String normalizeGroup(String group) {
        return switch (group) { case "1" -> "A"; case "2" -> "B"; default -> group; };
    }
    private static String canonical(String sourceName) { return CANONICAL_NAMES.getOrDefault(sourceName, sourceName); }
    private static Tournament euro(String name, String path, String start, Set<String> hosts) {
        return new Tournament(name, EURO_RAW + path, LocalDate.parse(start), canonicalSet(hosts), true);
    }
    private static Tournament worldCup(String name, String path, String start, Set<String> hosts) {
        return new Tournament(name, WORLD_CUP_RAW + path, LocalDate.parse(start), canonicalSet(hosts), false);
    }
    private static Set<String> canonicalSet(Set<String> names) {
        Set<String> out = new LinkedHashSet<>();
        names.forEach(name -> out.add(canonical(name)));
        return Set.copyOf(out);
    }

    record Tournament(String name, String sourceUrl, LocalDate startDate, Set<String> hosts, boolean euro) {}
    record SourceTeam(String source, String canonical) {}
    record LocatedTeam(int index, SourceTeam team) {}
    record Match(String group, LocalDate date, String team1, String team2, int goals1, int goals2) {}
    record ParsedTournament(Map<String, List<SourceTeam>> groups, Set<String> teams, List<Match> matches) {}
}

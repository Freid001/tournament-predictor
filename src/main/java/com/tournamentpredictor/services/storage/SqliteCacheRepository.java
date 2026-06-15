package com.tournamentpredictor.services.storage;

import com.tournamentpredictor.model.common.CsvData;
import com.tournamentpredictor.services.web.WebText;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SqliteCacheRepository {
    public static final List<String> TEAM_HEADERS = List.of(
            "group", "team", "host",
            "squad_age_profile", "age_notes",
            "squad_cohesion", "cohesion_notes",
            "squad_depth", "depth_notes",
            "attack_quality", "defence_quality", "quality_notes",
            "squad_dropouts", "dropout_notes",
            "injury_impact", "injury_notes",
            "heat_impact", "confederation");

    public static final List<String> GROUP_HEADERS = List.of(
            "group", "team", "base_elo", "qual_bonus", "pre_tournament_bonus", "confederation_adjustment",
            "squad_age_profile", "squad_cohesion", "squad_depth",
            "attack_quality", "defence_quality", "elo_ranking", "predicted_position",
            "group_winner", "runner_up", "3rd_place");

    private static final CSVFormat CSV = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    private final Path projectRoot;

    public SqliteCacheRepository(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    public Path databasePath(String tournament) {
        return projectRoot.resolve("data").resolve("cache").resolve(tournament + ".sqlite");
    }

    public boolean teamsAvailable(String tournament) throws IOException {
        Path db = databasePath(tournament);
        if (!Files.exists(db)) return false;
        try (Connection connection = connect(db)) {
            initialize(connection);
            try (PreparedStatement ps = connection.prepareStatement("select 1 from teams where tournament = ? limit 1")) {
                ps.setString(1, tournament);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to check teams table for " + tournament, e);
        }
    }

    public CsvData readTeams(String tournament) throws IOException {
        Path db = databasePath(tournament);
        if (!Files.exists(db)) return new CsvData(TEAM_HEADERS, List.of());
        try (Connection connection = connect(db)) {
            initialize(connection);
            List<Map<String, String>> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                    select "group", team, host, squad_age_profile, age_notes,
                           squad_cohesion, cohesion_notes, squad_depth, depth_notes,
                           attack_quality, defence_quality, quality_notes,
                           squad_dropouts, dropout_notes, injury_impact, injury_notes, heat_impact, confederation
                    from teams
                    where tournament = ?
                    order by "group", draw_position, team
                    """)) {
                ps.setString(1, tournament);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> row = new LinkedHashMap<>();
                        for (String header : TEAM_HEADERS) row.put(header, rs.getString(header));
                        rows.add(row);
                    }
                }
            }
            return new CsvData(TEAM_HEADERS, rows);
        } catch (SQLException e) {
            throw new IOException("Failed to read teams table for " + tournament, e);
        }
    }

    public List<String> readTeamsAsCsvLines(String tournament) throws IOException {
        CsvData data = readTeams(tournament);
        if (data.rows().isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        lines.add(csvLine(data.headers(), headerRow(data.headers())));
        for (Map<String, String> row : data.rows()) lines.add(csvLine(data.headers(), row));
        return lines;
    }

    public void replaceTeams(String tournament, List<String> headers, List<Map<String, String>> rows) throws IOException {
        Path db = databasePath(tournament);
        Files.createDirectories(db.getParent());
        try (Connection connection = connect(db)) {
            initialize(connection);
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("delete from teams where tournament = ?")) {
                delete.setString(1, tournament);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    insert into teams (
                      tournament, "group", draw_position, team, host,
                      squad_age_profile, age_notes, squad_cohesion, cohesion_notes,
                      squad_depth, depth_notes, attack_quality, defence_quality, quality_notes,
                      squad_dropouts, dropout_notes, injury_impact, injury_notes, heat_impact, confederation, confederation_adjustment
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                Map<String, Integer> groupPositions = new LinkedHashMap<>();
                for (Map<String, String> row : rows) {
                    String group = value(row, "group").toUpperCase(java.util.Locale.ROOT);
                    String team = value(row, "team");
                    if (group.isBlank() || team.isBlank()) continue;
                    int drawPosition = groupPositions.merge(group, 1, Integer::sum);
                    int index = 1;
                    insert.setString(index++, tournament);
                    insert.setString(index++, group);
                    insert.setInt(index++, drawPosition);
                    insert.setString(index++, team);
                    insert.setString(index++, yesNo(value(row, "host")));
                    insert.setInt(index++, integer(row, "squad_age_profile"));
                    insert.setString(index++, value(row, "age_notes"));
                    insert.setInt(index++, integer(row, "squad_cohesion"));
                    insert.setString(index++, value(row, "cohesion_notes"));
                    insert.setInt(index++, integer(row, "squad_depth"));
                    insert.setString(index++, value(row, "depth_notes"));
                    insert.setInt(index++, integer(row, "attack_quality"));
                    insert.setInt(index++, integer(row, "defence_quality"));
                    insert.setString(index++, value(row, "quality_notes"));
                    insert.setInt(index++, integer(row, "squad_dropouts"));
                    insert.setString(index++, value(row, "dropout_notes"));
                    insert.setInt(index++, integer(row, "injury_impact"));
                    insert.setString(index++, value(row, "injury_notes"));
                    insert.setInt(index++, integer(row, "heat_impact"));
                    String confederation = value(row, "confederation");
                    insert.setString(index++, confederation);
                    insert.setInt(index++, confederationAdjustment(confederation));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            throw new IOException("Failed to replace teams table for " + tournament, e);
        }
    }


    public boolean groupsAvailable(String tournament) throws IOException {
        Path db = databasePath(tournament);
        if (!Files.exists(db)) return false;
        try (Connection connection = connect(db)) {
            initialize(connection);
            try (PreparedStatement ps = connection.prepareStatement("select 1 from groups where tournament = ? limit 1")) {
                ps.setString(1, tournament);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to check groups table for " + tournament, e);
        }
    }

    public CsvData readGroups(String tournament) throws IOException {
        Path db = databasePath(tournament);
        if (!Files.exists(db)) return new CsvData(GROUP_HEADERS, List.of());
        try (Connection connection = connect(db)) {
            initialize(connection);
            List<Map<String, String>> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                    select g."group", g.team,
                           t.base_elo, t.qual_bonus, t.pre_tournament_bonus, t.confederation_adjustment,
                           t.squad_age_profile, t.squad_cohesion, t.squad_depth,
                           t.attack_quality, t.defence_quality, t.elo_ranking,
                           g.predicted_position,
                           '' as group_winner, '' as runner_up, '' as "3rd_place"
                    from groups g
                    join teams t on t.tournament = g.tournament and t.team = g.team
                    where g.tournament = ?
                    order by g."group", g.group_position, g.team
                    """)) {
                ps.setString(1, tournament);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> row = new LinkedHashMap<>();
                        for (String header : GROUP_HEADERS) row.put(header, rs.getString(header));
                        rows.add(row);
                    }
                }
            }
            return new CsvData(GROUP_HEADERS, rows);
        } catch (SQLException e) {
            throw new IOException("Failed to read groups table for " + tournament, e);
        }
    }

    public List<String> readGroupsAsCsvLines(String tournament) throws IOException {
        CsvData data = readGroups(tournament);
        if (data.rows().isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        lines.add(csvLine(data.headers(), headerRow(data.headers())));
        String previousGroup = "";
        for (Map<String, String> row : data.rows()) {
            String group = row.getOrDefault("group", "");
            if (!previousGroup.isBlank() && !previousGroup.equals(group)) lines.add("");
            lines.add(csvLine(data.headers(), row));
            previousGroup = group;
        }
        return lines;
    }

    public void replaceGroups(String tournament, List<String> headers, List<Map<String, String>> rows) throws IOException {
        Path db = databasePath(tournament);
        Files.createDirectories(db.getParent());
        try (Connection connection = connect(db)) {
            initialize(connection);
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("delete from groups where tournament = ?")) {
                delete.setString(1, tournament);
                delete.executeUpdate();
            }
            try (PreparedStatement insertGroup = connection.prepareStatement("""
                    insert into groups (tournament, "group", group_position, team, predicted_position)
                    values (?, ?, ?, ?, ?)
                    """);
                 PreparedStatement updateTeam = connection.prepareStatement("""
                    update teams
                    set base_elo = ?, qual_bonus = ?, pre_tournament_bonus = ?, confederation_adjustment = ?,
                        squad_age_profile = ?, squad_cohesion = ?, squad_depth = ?,
                        attack_quality = ?, defence_quality = ?, elo_ranking = ?
                    where tournament = ? and team = ?
                    """)) {
                Map<String, Integer> groupPositions = new LinkedHashMap<>();
                for (Map<String, String> row : rows) {
                    String group = value(row, "group").toUpperCase(java.util.Locale.ROOT);
                    String team = value(row, "team");
                    if (group.isBlank() || team.isBlank()) continue;
                    int groupPosition = groupPositions.merge(group, 1, Integer::sum);
                    insertGroup.setString(1, tournament);
                    insertGroup.setString(2, group);
                    insertGroup.setInt(3, groupPosition);
                    insertGroup.setString(4, team);
                    insertGroup.setString(5, value(row, "predicted_position"));
                    insertGroup.addBatch();

                    updateTeam.setInt(1, integer(row, "base_elo"));
                    updateTeam.setInt(2, integer(row, "qual_bonus"));
                    updateTeam.setInt(3, integer(row, "pre_tournament_bonus"));
                    updateTeam.setInt(4, integer(row, "confederation_adjustment"));
                    updateTeam.setInt(5, integer(row, "squad_age_profile"));
                    updateTeam.setInt(6, integer(row, "squad_cohesion"));
                    updateTeam.setInt(7, integer(row, "squad_depth"));
                    updateTeam.setInt(8, integer(row, "attack_quality"));
                    updateTeam.setInt(9, integer(row, "defence_quality"));
                    updateTeam.setInt(10, integer(row, "elo_ranking"));
                    updateTeam.setString(11, tournament);
                    updateTeam.setString(12, team);
                    updateTeam.addBatch();
                }
                insertGroup.executeBatch();
                updateTeam.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            throw new IOException("Failed to replace groups table for " + tournament, e);
        }
    }

    public void updateGroupSimulationSummary(String tournament, List<Map<String, String>> rows) throws IOException {
        if (rows == null || rows.isEmpty()) return;
        Path db = databasePath(tournament);
        Files.createDirectories(db.getParent());
        try (Connection connection = connect(db)) {
            initialize(connection);
            try (PreparedStatement ps = connection.prepareStatement("""
                    update groups
                    set finish_1st_pct = ?, finish_2nd_pct = ?, finish_3rd_pct = ?, finish_4th_pct = ?,
                        reach_last_32_pct = ?, simulation_runs = ?, simulation_seed = ?
                    where tournament = ? and team = ?
                    """)) {
                for (Map<String, String> row : rows) {
                    ps.setDouble(1, decimal(row, "finish_1st"));
                    ps.setDouble(2, decimal(row, "finish_2nd"));
                    ps.setDouble(3, decimal(row, "finish_3rd"));
                    ps.setDouble(4, decimal(row, "finish_4th"));
                    ps.setDouble(5, reachPct(row));
                    ps.setInt(6, integer(row, "simulation_runs"));
                    ps.setLong(7, longValue(row, "simulation_seed"));
                    ps.setString(8, tournament);
                    ps.setString(9, value(row, "team"));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            throw new IOException("Failed to update group simulation summary for " + tournament, e);
        }
    }

    public List<Map<String, String>> readMatchupPredictionRows(String tournament, String round) throws IOException {
        Path db = databasePath(tournament);
        if (!Files.exists(db)) return List.of();
        try (Connection connection = connect(db)) {
            initialize(connection);
            List<Map<String, String>> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                    select round, match_id, team1, team2,
                           predicted_scoreline, predicted_scoreline_pct, predicted_winner,
                           team1_win_pct, draw_pct, team2_win_pct,
                           predicted_matchup_runs, prediction_simulation_runs, prediction_simulation_seed
                    from predictions
                    where tournament = ? and round = ? and coalesce(predicted_scoreline, '') <> ''
                    order by match_id, team1, team2
                    """)) {
                ps.setString(1, tournament);
                ps.setString(2, round);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int runs = Math.max(1, rs.getInt("predicted_matchup_runs"));
                        addMatchupOutcomeRow(rows, rs, rs.getString("team1"), rs.getDouble("team1_win_pct"), runs);
                        addMatchupOutcomeRow(rows, rs, "Draw", rs.getDouble("draw_pct"), runs);
                        addMatchupOutcomeRow(rows, rs, rs.getString("team2"), rs.getDouble("team2_win_pct"), runs);
                    }
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new IOException("Failed to read matchup prediction rows for " + tournament + " " + round, e);
        }
    }

    private void addMatchupOutcomeRow(List<Map<String, String>> rows, ResultSet rs, String winner,
                                      double pct, int runs) throws SQLException {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("stage", rs.getString("round"));
        row.put("match_id", rs.getString("match_id"));
        row.put("team1", rs.getString("team1"));
        row.put("team2", rs.getString("team2"));
        row.put("winner", winner == null ? "" : winner);
        row.put("count", String.valueOf(Math.round(pct * runs / 100.0)));
        row.put("matchup_runs", String.valueOf(runs));
        row.put("simulation_runs", String.valueOf(rs.getInt("prediction_simulation_runs")));
        row.put("simulation_seed", String.valueOf(rs.getLong("prediction_simulation_seed")));
        row.put("scoreline", rs.getString("predicted_scoreline"));
        row.put("predicted_scoreline_pct", String.valueOf(rs.getDouble("predicted_scoreline_pct")));
        row.put("predicted_winner", rs.getString("predicted_winner"));
        rows.add(row);
    }

    public void replaceMatchupPredictionSummaries(String tournament, String round, List<Map<String, String>> rows) throws IOException {
        if (rows == null || rows.isEmpty()) return;
        Map<String, MatchupSummary> summaries = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String stage = value(row, "stage");
            String matchId = value(row, "match_id");
            String team1 = value(row, "team1");
            String team2 = value(row, "team2");
            if (stage.isBlank() || matchId.isBlank() || team1.isBlank() || team2.isBlank()) continue;
            String key = stage + "|" + matchId + "|" + team1 + "|" + team2;
            MatchupSummary summary = summaries.computeIfAbsent(key, ignored -> new MatchupSummary(stage, matchId, team1, team2));
            int count = integer(row, "count");
            summary.matchupRuns = Math.max(summary.matchupRuns, integer(row, "matchup_runs"));
            summary.simulationRuns = Math.max(summary.simulationRuns, integer(row, "simulation_runs"));
            summary.simulationSeed = Math.max(summary.simulationSeed, longValue(row, "simulation_seed"));
            String winner = value(row, "winner");
            if (winner.equals(team1)) summary.team1Wins += count;
            else if (winner.equals(team2)) summary.team2Wins += count;
            else if (winner.equalsIgnoreCase("Draw")) summary.draws += count;
            if (count > summary.topScorelineCount) {
                summary.topScorelineCount = count;
                summary.predictedScoreline = value(row, "scoreline");
                summary.predictedWinner = winner;
            }
        }
        Path db = databasePath(tournament);
        Files.createDirectories(db.getParent());
        try (Connection connection = connect(db)) {
            initialize(connection);
            connection.setAutoCommit(false);
            List<String> stages = summaries.values().stream().map(summary -> summary.stage).distinct().toList();
            try (PreparedStatement delete = connection.prepareStatement("""
                    delete from predictions
                    where tournament = ? and round = ?
                    """);
                 PreparedStatement deleteMatches = connection.prepareStatement("""
                    delete from matches
                    where tournament = ? and round = ?
                    """)) {
                for (String stage : stages) {
                    delete.setString(1, tournament);
                    delete.setString(2, stage);
                    delete.addBatch();
                    deleteMatches.setString(1, tournament);
                    deleteMatches.setString(2, stage);
                    deleteMatches.addBatch();
                }
                delete.executeBatch();
                deleteMatches.executeBatch();
            }
            try (PreparedStatement fixture = connection.prepareStatement("""
                    insert or ignore into matches (tournament, round, match_id, team1, team2)
                    values (?, ?, ?, ?, ?)
                    """);
                 PreparedStatement insert = connection.prepareStatement("""
                    insert into predictions (
                      tournament, round, match_id, team1, team2,
                      predicted_scoreline, predicted_scoreline_pct, predicted_winner,
                      team1_win_pct, draw_pct, team2_win_pct, team1_advance_pct, team2_advance_pct,
                      predicted_matchup_runs, prediction_simulation_runs, prediction_simulation_seed
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (MatchupSummary summary : summaries.values()) {
                    int runs = Math.max(1, summary.matchupRuns);
                    int decisiveRuns = Math.max(1, summary.team1Wins + summary.team2Wins);
                    fixture.setString(1, tournament);
                    fixture.setString(2, summary.stage);
                    fixture.setString(3, summary.matchId);
                    fixture.setString(4, summary.team1);
                    fixture.setString(5, summary.team2);
                    fixture.addBatch();
                    insert.setString(1, tournament);
                    insert.setString(2, summary.stage);
                    insert.setString(3, summary.matchId);
                    insert.setString(4, summary.team1);
                    insert.setString(5, summary.team2);
                    insert.setString(6, summary.predictedScoreline);
                    insert.setDouble(7, pct(summary.topScorelineCount, runs));
                    insert.setString(8, summary.predictedWinner);
                    insert.setDouble(9, pct(summary.team1Wins, runs));
                    insert.setDouble(10, pct(summary.draws, runs));
                    insert.setDouble(11, pct(summary.team2Wins, runs));
                    boolean groupStage = "groups".equalsIgnoreCase(summary.stage);
                    insert.setDouble(12, groupStage ? 0.0 : pct(summary.team1Wins, decisiveRuns));
                    insert.setDouble(13, groupStage ? 0.0 : pct(summary.team2Wins, decisiveRuns));
                    insert.setInt(14, summary.matchupRuns);
                    insert.setInt(15, summary.simulationRuns);
                    insert.setLong(16, summary.simulationSeed);
                    insert.addBatch();
                }
                fixture.executeBatch();
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            throw new IOException("Failed to replace matchup prediction summaries for " + tournament + " " + round, e);
        }
    }

    public boolean available(String tournament) {
        return Files.exists(databasePath(tournament));
    }

    public void importDataset(String tournament, String datasetType, String round, Path source) throws IOException {
        if (!Files.exists(source)) return;
        if (!"matchup".equals(datasetType)) return;
        Path db = databasePath(tournament);
        Files.createDirectories(db.getParent());
        try (Connection connection = connect(db)) {
            initialize(connection);
            connection.setAutoCommit(false);
            deleteDataset(connection, tournament, datasetType, round, source);
            importMatchupRows(connection, tournament, round, source);
            connection.commit();
        } catch (SQLException e) {
            throw new IOException("Failed to import SQLite matchup paths for " + source, e);
        }
    }

    public CsvData readDataset(String tournament, String datasetType, String round, Path source) throws IOException {
        if (!Files.exists(source)) return new CsvData(List.of(), List.of());
        try (Reader reader = Files.newBufferedReader(source); CSVParser parser = CSV.parse(reader)) {
            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                for (String header : headers) row.put(header, get(record, header));
                rows.add(row);
            }
            return new CsvData(headers, rows);
        }
    }

    public void updatePathMatchupLikelihoodsFromGroupRoutes(String tournament, String round, Path groupRoutes) throws IOException {
        if (!"last_32".equals(round) || !Files.exists(groupRoutes)) return;
        Map<String, Integer> counts = new LinkedHashMap<>();
        java.util.Set<String> runs = new java.util.LinkedHashSet<>();
        try (Reader reader = Files.newBufferedReader(groupRoutes); CSVParser parser = CSV.parse(reader)) {
            for (CSVRecord record : parser) {
                String run = get(record, "run");
                String matchId = get(record, "match_id");
                String team1 = get(record, "team1");
                String team2 = get(record, "team2");
                if (run.isBlank() || matchId.isBlank() || team1.isBlank() || team2.isBlank()) continue;
                runs.add(run);
                counts.merge(matchupKey(matchId, team1, team2), 1, Integer::sum);
            }
        }
        int totalRuns = runs.size();
        if (totalRuns == 0) return;
        Path db = databasePath(tournament);
        if (!Files.exists(db)) return;
        try (Connection connection = connect(db)) {
            initialize(connection);
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement("""
                    select rowid, match_id, team1, team2
                    from paths
                    where tournament = ? and round = ?
                    """);
                 PreparedStatement update = connection.prepareStatement("""
                    update paths
                    set matchup_pct = ?, matchup_runs = ?
                    where rowid = ?
                    """)) {
                select.setString(1, tournament);
                select.setString(2, round);
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        int count = counts.getOrDefault(matchupKey(rs.getString("match_id"), rs.getString("team1"), rs.getString("team2")), 0);
                        update.setString(1, formatPathLikelihood(count, totalRuns));
                        update.setString(2, String.valueOf(totalRuns));
                        update.setLong(3, rs.getLong("rowid"));
                        update.addBatch();
                    }
                }
                update.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            throw new IOException("Failed to update matchup likelihoods for " + tournament + " " + round, e);
        }
    }

    private static String matchupKey(String matchId, String team1, String team2) {
        return (matchId == null ? "" : matchId.trim()) + "|" + (team1 == null ? "" : team1.trim()) + "|" + (team2 == null ? "" : team2.trim());
    }

    private static String formatPathLikelihood(int count, int totalRuns) {
        if (count <= 0) return "0.0";
        double value = count * 100.0 / Math.max(1, totalRuns);
        if (value < 0.1) return String.format(java.util.Locale.ROOT, "%.3f", value);
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    public List<Map<String, String>> readMatchupsForTeam(String tournament, String round, String team, String pathFilter, Path source) throws IOException {
        importDataset(tournament, "matchup", round, source);
        Path db = databasePath(tournament);
        if (!Files.exists(db)) return List.of();
        String normalizedPath = normalizePathFilter(pathFilter);
        String sql = """
                select p.*, p.team1 as team1_team, p.team2 as team2_team,
                       t1.path_fatigue as team1_path_fatigue, t2.path_fatigue as team2_path_fatigue,
                       t1.path_opponent as team1_path_opponent, t2.path_opponent as team2_path_opponent
                from paths p
                left join tournament_paths t1 on t1.tournament = p.tournament and t1.path_id = p.path_id and t1.path_side = 'team1' and t1.row_type = 'route'
                left join tournament_paths t2 on t2.tournament = p.tournament and t2.path_id = p.path_id and t2.path_side = 'team2' and t2.row_type = 'route'
                where p.tournament = ? and p.round = ?
                  and (lower(p.team1) = lower(?) or lower(p.team2) = lower(?))
                """ + ("all".equals(normalizedPath) ? "" : " and p.path in (" + pathPlaceholders(normalizedPath) + ")")
                + " order by p.match_id, p.path, p.team1, p.team2";
        try (Connection connection = connect(db); PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            ps.setString(idx++, tournament);
            ps.setString(idx++, round);
            ps.setString(idx++, team);
            ps.setString(idx++, team);
            if (!"all".equals(normalizedPath)) {
                for (String value : pathValues(normalizedPath)) ps.setString(idx++, value);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return matchupRows(rs);
            }
        } catch (SQLException e) {
            throw new IOException("Failed to read matchup SQLite rows", e);
        }
    }

    private Connection connect(Path db) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
    }

    private void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("pragma journal_mode=DELETE");
            statement.executeUpdate("""
                    create table if not exists teams (
                      tournament text not null,
                      "group" text not null,
                      draw_position integer not null,
                      team text not null,
                      host text not null,
                      squad_age_profile integer not null default 0,
                      age_notes text not null default '',
                      squad_cohesion integer not null default 0,
                      cohesion_notes text not null default '',
                      squad_depth integer not null default 0,
                      depth_notes text not null default '',
                      attack_quality integer not null default 0,
                      defence_quality integer not null default 0,
                      quality_notes text not null default '',
                      squad_dropouts integer not null default 0,
                      dropout_notes text not null default '',
                      injury_impact integer not null default 0,
                      injury_notes text not null default '',
                      heat_impact integer not null default 0,
                      confederation text not null default '',
                      confederation_adjustment integer not null default 0,
                      primary key (tournament, team),
                      unique (tournament, "group", draw_position)
                    )
                    """);
            addColumnIfMissing(connection, "teams", "base_elo", "integer not null default 0");
            addColumnIfMissing(connection, "teams", "qual_bonus", "integer not null default 0");
            addColumnIfMissing(connection, "teams", "pre_tournament_bonus", "integer not null default 0");
            addColumnIfMissing(connection, "teams", "confederation", "text not null default ''");
            addColumnIfMissing(connection, "teams", "confederation_adjustment", "integer not null default 0");
            addColumnIfMissing(connection, "teams", "elo_ranking", "integer not null default 0");
            migrateLegacyGroups(connection);
            createGroupsTable(connection);
            statement.executeUpdate("drop table if exists dataset_headers");
            statement.executeUpdate("drop table if exists dataset_rows");
            migrateLegacyMatchups(connection);
            migrateMatchupsTableName(connection);
            migratePredictionsTableName(connection);
            migratePathsTableName(connection);
            migrateLegacyTournamentPathTable(connection);
            createMatchupTables(connection);
            ensureTournamentPathRouteColumns(connection);
            addColumnIfMissing(connection, "paths", "upset_path", "text not null default '0'");
            normalizePathsSchema(connection);
            migratePathFatigueIntoTournamentPaths(connection);
            statement.executeUpdate("create index if not exists idx_teams_tournament_group on teams(tournament, \"group\", draw_position)");
            statement.executeUpdate("create index if not exists idx_groups_tournament_group on groups(tournament, \"group\", group_position)");
            addColumnIfMissing(connection, "groups", "finish_1st_pct", "real not null default 0");
            addColumnIfMissing(connection, "groups", "finish_2nd_pct", "real not null default 0");
            addColumnIfMissing(connection, "groups", "finish_3rd_pct", "real not null default 0");
            addColumnIfMissing(connection, "groups", "finish_4th_pct", "real not null default 0");
            addColumnIfMissing(connection, "groups", "reach_last_32_pct", "real not null default 0");
            addColumnIfMissing(connection, "groups", "simulation_runs", "integer not null default 0");
            addColumnIfMissing(connection, "groups", "simulation_seed", "integer not null default 0");
            statement.executeUpdate("create index if not exists idx_matches_lookup on matches(tournament, round, match_id, team1, team2)");
            statement.executeUpdate("create index if not exists idx_predictions_lookup on predictions(tournament, round, match_id, team1, team2)");
            statement.executeUpdate("create index if not exists idx_paths_round_path on paths(tournament, round, path)");
            statement.executeUpdate("create index if not exists idx_paths_round_team1 on paths(tournament, round, team1)");
            statement.executeUpdate("create index if not exists idx_paths_round_team2 on paths(tournament, round, team2)");
            statement.executeUpdate("create index if not exists idx_tournament_paths_route_lookup on tournament_paths(tournament, path_id, row_type, path_side)");
        }
    }

    private void createGroupsTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    create table if not exists groups (
                      tournament text not null,
                      "group" text not null,
                      group_position integer not null,
                      team text not null,
                      predicted_position text not null default '',
                      finish_1st_pct real not null default 0,
                      finish_2nd_pct real not null default 0,
                      finish_3rd_pct real not null default 0,
                      finish_4th_pct real not null default 0,
                      reach_last_32_pct real not null default 0,
                      simulation_runs integer not null default 0,
                      simulation_seed integer not null default 0,
                      primary key (tournament, team),
                      unique (tournament, "group", group_position)
                    )
                    """);
        }
    }

    private void migrateLegacyGroups(Connection connection) throws SQLException {
        if (!tableExists(connection, "groups") || !columnExists(connection, "groups", "base_elo")) return;
        String legacy = nextLegacyTableName(connection, "groups_legacy");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("alter table groups rename to " + legacy);
        }
        createGroupsTable(connection);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert or replace into groups (
                      tournament, "group", group_position, team, predicted_position,
                      finish_1st_pct, finish_2nd_pct, finish_3rd_pct, finish_4th_pct,
                      reach_last_32_pct, simulation_runs, simulation_seed
                    )
                    select tournament, "group", group_position, team, coalesce(predicted_position, ''),
                           coalesce(finish_1st_pct, 0), coalesce(finish_2nd_pct, 0),
                           coalesce(finish_3rd_pct, 0), coalesce(finish_4th_pct, 0),
                           coalesce(reach_last_32_pct, 0), coalesce(simulation_runs, 0), coalesce(simulation_seed, 0)
                    from %s
                    """.formatted(legacy));
            statement.executeUpdate("""
                    update teams
                    set base_elo = coalesce((select base_elo from %s g where g.tournament = teams.tournament and g.team = teams.team), base_elo),
                        qual_bonus = coalesce((select qual_bonus from %s g where g.tournament = teams.tournament and g.team = teams.team), qual_bonus),
                        pre_tournament_bonus = coalesce((select pre_tournament_bonus from %s g where g.tournament = teams.tournament and g.team = teams.team), pre_tournament_bonus),
                        confederation_adjustment = coalesce((select confederation_adjustment from %s g where g.tournament = teams.tournament and g.team = teams.team), confederation_adjustment),
                        squad_age_profile = coalesce((select squad_age_profile from %s g where g.tournament = teams.tournament and g.team = teams.team), squad_age_profile),
                        squad_cohesion = coalesce((select squad_cohesion from %s g where g.tournament = teams.tournament and g.team = teams.team), squad_cohesion),
                        squad_depth = coalesce((select squad_depth from %s g where g.tournament = teams.tournament and g.team = teams.team), squad_depth),
                        attack_quality = coalesce((select attack_quality from %s g where g.tournament = teams.tournament and g.team = teams.team), attack_quality),
                        defence_quality = coalesce((select defence_quality from %s g where g.tournament = teams.tournament and g.team = teams.team), defence_quality),
                        elo_ranking = coalesce((select elo_ranking from %s g where g.tournament = teams.tournament and g.team = teams.team), elo_ranking)
                    where exists (select 1 from %s g where g.tournament = teams.tournament and g.team = teams.team)
                    """.formatted(legacy, legacy, legacy, legacy, legacy, legacy, legacy, legacy, legacy, legacy, legacy));
        }
    }

    private void createMatchupTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    create table if not exists matches (
                      tournament text not null,
                      round text not null,
                      match_id text not null,
                      team1 text not null,
                      team2 text not null,
                      primary key (tournament, round, match_id, team1, team2)
                    )
                    """);
            statement.executeUpdate("""
                    create table if not exists predictions (
                      tournament text not null,
                      round text not null,
                      match_id text not null,
                      team1 text not null,
                      team2 text not null,
                      predicted_scoreline text not null default '',
                      predicted_scoreline_pct real not null default 0,
                      predicted_winner text not null default '',
                      team1_win_pct real not null default 0,
                      draw_pct real not null default 0,
                      team2_win_pct real not null default 0,
                      team1_advance_pct real not null default 0,
                      team2_advance_pct real not null default 0,
                      predicted_matchup_runs integer not null default 0,
                      prediction_simulation_runs integer not null default 0,
                      prediction_simulation_seed integer not null default 0,
                      primary key (tournament, round, match_id, team1, team2)
                    )
                    """);
            statement.executeUpdate("""
                    create table if not exists paths (
                      tournament text not null,
                      path_id text not null,
                      round text not null,
                      match_id text, team1 text, team2 text, path text, prediction text,
                      model_prediction text, selection_source text, matchup_pct text, matchup_runs text, upset_path text not null default '0',
                      team1_slot text, team1_source_match text, team1_group_finish text, team1_bracket_slot text,
                      team2_slot text, team2_source_match text, team2_group_finish text, team2_bracket_slot text,
                      primary key (tournament, path_id)
                    )
                    """);
            statement.executeUpdate("""
                    create table if not exists tournament_paths (
                      tournament text not null,
                      round text not null,
                      team text not null,
                      finish text not null default '',
                      path text not null default '',
                      count integer not null default 0,
                      percentage real not null default 0,
                      simulation_runs integer not null default 0,
                      simulation_seed integer not null default 0,
                      row_type text not null default 'summary',
                      path_id text not null default '',
                      match_id text not null default '',
                      path_side text not null default '',
                      path_fatigue text not null default '',
                      path_opponent text not null default ''
                    )
                    """);
        }
    }



    private void ensureTournamentPathRouteColumns(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "tournament_paths", "row_type", "text not null default 'summary'");
        addColumnIfMissing(connection, "tournament_paths", "path_id", "text not null default ''");
        addColumnIfMissing(connection, "tournament_paths", "match_id", "text not null default ''");
        addColumnIfMissing(connection, "tournament_paths", "path_side", "text not null default ''");
        addColumnIfMissing(connection, "tournament_paths", "path_fatigue", "text not null default ''");
        addColumnIfMissing(connection, "tournament_paths", "path_opponent", "text not null default ''");
    }

    private void migratePathFatigueIntoTournamentPaths(Connection connection) throws SQLException {
        if (!tableExists(connection, "path_fatigue")) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    delete from tournament_paths
                    where row_type = 'route'
                      and exists (
                        select 1 from path_fatigue f
                        where f.tournament = tournament_paths.tournament and f.path_id = tournament_paths.path_id
                      )
                    """);
            statement.executeUpdate("""
                    insert into tournament_paths (
                      tournament, round, team, finish, path, count, percentage, simulation_runs, simulation_seed,
                      row_type, path_id, match_id, path_side, path_fatigue, path_opponent
                    )
                    select p.tournament, p.round, coalesce(p.team1, ''), '', coalesce(f.team1_path_opponent, ''), 0, 0, 0, 0,
                           'route', p.path_id, coalesce(p.match_id, ''), 'team1', coalesce(f.team1_path_fatigue, ''), coalesce(f.team1_path_opponent, '')
                    from paths p
                    join path_fatigue f on f.tournament = p.tournament and f.path_id = p.path_id
                    """);
            statement.executeUpdate("""
                    insert into tournament_paths (
                      tournament, round, team, finish, path, count, percentage, simulation_runs, simulation_seed,
                      row_type, path_id, match_id, path_side, path_fatigue, path_opponent
                    )
                    select p.tournament, p.round, coalesce(p.team2, ''), '', coalesce(f.team2_path_opponent, ''), 0, 0, 0, 0,
                           'route', p.path_id, coalesce(p.match_id, ''), 'team2', coalesce(f.team2_path_fatigue, ''), coalesce(f.team2_path_opponent, '')
                    from paths p
                    join path_fatigue f on f.tournament = p.tournament and f.path_id = p.path_id
                    """);
            statement.executeUpdate("drop table path_fatigue");
        }
    }

    private void normalizePathsSchema(Connection connection) throws SQLException {
        if (!tableExists(connection, "paths")) return;
        boolean hasPathId = columnExists(connection, "paths", "path_id");
        boolean hasDuplicateTeams = columnExists(connection, "paths", "team1_team") || columnExists(connection, "paths", "team2_team");
        boolean hasFatigue = columnExists(connection, "paths", "team1_path_fatigue") || columnExists(connection, "paths", "team2_path_fatigue")
                || columnExists(connection, "paths", "team1_path_opponent") || columnExists(connection, "paths", "team2_path_opponent");
        if (hasPathId && !hasDuplicateTeams && !hasFatigue) return;

        String legacy = nextLegacyTableName(connection, "paths_legacy");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("drop index if exists idx_paths_round_path");
            statement.executeUpdate("drop index if exists idx_paths_round_team1");
            statement.executeUpdate("drop index if exists idx_paths_round_team2");
            statement.executeUpdate("alter table paths rename to " + legacy);
        }
        createMatchupTables(connection);
        for (String column : matchupColumns()) {
            if (!columnExists(connection, legacy, column)) addColumnIfMissing(connection, legacy, column, "text");
        }
        if (!columnExists(connection, legacy, "upset_path")) addColumnIfMissing(connection, legacy, "upset_path", "text not null default '0'");
        String pathIdExpression = columnExists(connection, legacy, "path_id")
                ? "coalesce(nullif(path_id, ''), round || ':' || rowid)"
                : "round || ':' || rowid";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert or replace into paths (
                      tournament, path_id, round, match_id, team1, team2, path, prediction,
                      model_prediction, selection_source, matchup_pct, matchup_runs, upset_path,
                      team1_slot, team1_source_match, team1_group_finish, team1_bracket_slot,
                      team2_slot, team2_source_match, team2_group_finish, team2_bracket_slot
                    )
                    select tournament, %s, round, match_id,
                           coalesce(nullif(team1, ''), team1_team),
                           coalesce(nullif(team2, ''), team2_team),
                           path, prediction, model_prediction, selection_source, matchup_pct, matchup_runs, coalesce(upset_path, '0'),
                           team1_slot, team1_source_match, team1_group_finish, team1_bracket_slot,
                           team2_slot, team2_source_match, team2_group_finish, team2_bracket_slot
                    from %s
                    """.formatted(pathIdExpression, legacy));
            statement.executeUpdate("""
                    insert into tournament_paths (
                      tournament, round, team, finish, path, count, percentage, simulation_runs, simulation_seed,
                      row_type, path_id, match_id, path_side, path_fatigue, path_opponent
                    )
                    select tournament, round, coalesce(nullif(team1, ''), team1_team), '', coalesce(team1_path_opponent, ''), 0, 0, 0, 0,
                           'route', %s, coalesce(match_id, ''), 'team1', coalesce(team1_path_fatigue, ''), coalesce(team1_path_opponent, '')
                    from %s
                    """.formatted(pathIdExpression, legacy));
            statement.executeUpdate("""
                    insert into tournament_paths (
                      tournament, round, team, finish, path, count, percentage, simulation_runs, simulation_seed,
                      row_type, path_id, match_id, path_side, path_fatigue, path_opponent
                    )
                    select tournament, round, coalesce(nullif(team2, ''), team2_team), '', coalesce(team2_path_opponent, ''), 0, 0, 0, 0,
                           'route', %s, coalesce(match_id, ''), 'team2', coalesce(team2_path_fatigue, ''), coalesce(team2_path_opponent, '')
                    from %s
                    """.formatted(pathIdExpression, legacy));
            statement.executeUpdate("drop table " + legacy);
        }
    }

    private void migrateMatchupsTableName(Connection connection) throws SQLException {
        if (!tableExists(connection, "matchups") || columnExists(connection, "matchups", "path")) return;
        if (!tableExists(connection, "matches")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("alter table matchups rename to matches");
            }
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert or ignore into matches (tournament, round, match_id, team1, team2)
                    select tournament, round, match_id, team1, team2 from matchups
                    """);
            statement.executeUpdate("drop table matchups");
        }
    }

    private void migratePredictionsTableName(Connection connection) throws SQLException {
        String source = tableExists(connection, "match_predictions") ? "match_predictions"
                : tableExists(connection, "matchup_predictions") ? "matchup_predictions" : "";
        if (source.isBlank()) return;
        if (!tableExists(connection, "predictions")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("alter table " + source + " rename to predictions");
            }
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert or replace into predictions (
                      tournament, round, match_id, team1, team2,
                      predicted_scoreline, predicted_scoreline_pct, predicted_winner,
                      team1_win_pct, draw_pct, team2_win_pct, team1_advance_pct, team2_advance_pct,
                      predicted_matchup_runs, prediction_simulation_runs, prediction_simulation_seed
                    )
                    select tournament, round, match_id, team1, team2,
                           predicted_scoreline, predicted_scoreline_pct, predicted_winner,
                           team1_win_pct, draw_pct, team2_win_pct, team1_advance_pct, team2_advance_pct,
                           predicted_matchup_runs, prediction_simulation_runs, prediction_simulation_seed
                    from %s
                    """.formatted(source));
            statement.executeUpdate("drop table " + source);
        }
    }

    private void migratePathsTableName(Connection connection) throws SQLException {
        String source = tableExists(connection, "match_paths") ? "match_paths"
                : tableExists(connection, "matchup_paths") ? "matchup_paths" : "";
        if (source.isBlank()) return;
        if (!tableExists(connection, "paths")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("alter table " + source + " rename to paths");
            }
            return;
        }
        addColumnIfMissing(connection, source, "upset_path", "text not null default '0'");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert or replace into paths (
                      tournament, path_id, round, match_id, team1, team2, path, prediction,
                      model_prediction, selection_source, matchup_pct, matchup_runs, upset_path,
                      team1_slot, team1_source_match, team1_group_finish, team1_bracket_slot,
                      team2_slot, team2_source_match, team2_group_finish, team2_bracket_slot
                    )
                    select tournament, 'legacy:' || rowid, round, match_id, coalesce(nullif(team1, ''), team1_team), coalesce(nullif(team2, ''), team2_team), path, prediction,
                           model_prediction, selection_source, matchup_pct, matchup_runs, upset_path,
                           team1_slot, team1_source_match, team1_group_finish, team1_bracket_slot,
                           team2_slot, team2_source_match, team2_group_finish, team2_bracket_slot
                    from %s
                    """.formatted(source));
            statement.executeUpdate("""
                    insert into tournament_paths (
                      tournament, round, team, finish, path, count, percentage, simulation_runs, simulation_seed,
                      row_type, path_id, match_id, path_side, path_fatigue, path_opponent
                    )
                    select tournament, round, coalesce(nullif(team1, ''), team1_team), '', coalesce(team1_path_opponent, ''), 0, 0, 0, 0,
                           'route', 'legacy:' || rowid, coalesce(match_id, ''), 'team1', coalesce(team1_path_fatigue, ''), coalesce(team1_path_opponent, '')
                    from %s
                    """.formatted(source));
            statement.executeUpdate("""
                    insert into tournament_paths (
                      tournament, round, team, finish, path, count, percentage, simulation_runs, simulation_seed,
                      row_type, path_id, match_id, path_side, path_fatigue, path_opponent
                    )
                    select tournament, round, coalesce(nullif(team2, ''), team2_team), '', coalesce(team2_path_opponent, ''), 0, 0, 0, 0,
                           'route', 'legacy:' || rowid, coalesce(match_id, ''), 'team2', coalesce(team2_path_fatigue, ''), coalesce(team2_path_opponent, '')
                    from %s
                    """.formatted(source));
            statement.executeUpdate("drop table " + source);
        }
    }

    private void migrateLegacyMatchups(Connection connection) throws SQLException {
        if (!tableExists(connection, "matchups") || !columnExists(connection, "matchups", "path")) return;
        String legacy = nextLegacyTableName(connection, "matchups_legacy");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("alter table matchups rename to " + legacy);
        }
        createMatchupTables(connection);
        addColumnIfMissing(connection, legacy, "upset_path", "text not null default '0'");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert or ignore into matches (tournament, round, match_id, team1, team2)
                    select tournament, round, coalesce(match_id, ''), coalesce(team1, ''), coalesce(team2, '')
                    from %s
                    where coalesce(match_id, '') <> '' and coalesce(team1, '') <> '' and coalesce(team2, '') <> ''
                    """.formatted(legacy));
            if (columnExists(connection, legacy, "predicted_scoreline")) {
                statement.executeUpdate("""
                        insert or replace into predictions (
                          tournament, round, match_id, team1, team2,
                          predicted_scoreline, predicted_scoreline_pct, predicted_winner,
                          team1_win_pct, draw_pct, team2_win_pct, team1_advance_pct, team2_advance_pct,
                          predicted_matchup_runs, prediction_simulation_runs, prediction_simulation_seed
                        )
                        select tournament, round, coalesce(match_id, ''), coalesce(team1, ''), coalesce(team2, ''),
                               coalesce(predicted_scoreline, ''), coalesce(predicted_scoreline_pct, 0), coalesce(predicted_winner, ''),
                               coalesce(team1_win_pct, 0), coalesce(draw_pct, 0), coalesce(team2_win_pct, 0),
                               coalesce(team1_advance_pct, 0), coalesce(team2_advance_pct, 0),
                               coalesce(predicted_matchup_runs, 0), coalesce(prediction_simulation_runs, 0), coalesce(prediction_simulation_seed, 0)
                        from %s
                        where coalesce(predicted_scoreline, '') <> ''
                          and coalesce(match_id, '') <> '' and coalesce(team1, '') <> '' and coalesce(team2, '') <> ''
                        """.formatted(legacy));
            }
            statement.executeUpdate("""
                    insert or replace into paths (
                      tournament, path_id, round, match_id, team1, team2, path, prediction,
                      model_prediction, selection_source, matchup_pct, matchup_runs, upset_path,
                      team1_slot, team1_source_match, team1_group_finish, team1_bracket_slot,
                      team2_slot, team2_source_match, team2_group_finish, team2_bracket_slot
                    )
                    select tournament, 'legacy:' || rowid, round, match_id, coalesce(nullif(team1, ''), team1_team), coalesce(nullif(team2, ''), team2_team), path, prediction,
                           model_prediction, selection_source, matchup_pct, matchup_runs, upset_path,
                           team1_slot, team1_source_match, team1_group_finish, team1_bracket_slot,
                           team2_slot, team2_source_match, team2_group_finish, team2_bracket_slot
                    from %s
                    where coalesce(path, '') <> '' or coalesce(team1_slot, '') <> '' or coalesce(team2_slot, '') <> ''
                    """.formatted(legacy));
            statement.executeUpdate("""
                    insert into tournament_paths (
                      tournament, round, team, finish, path, count, percentage, simulation_runs, simulation_seed,
                      row_type, path_id, match_id, path_side, path_fatigue, path_opponent
                    )
                    select tournament, round, coalesce(nullif(team1, ''), team1_team), '', coalesce(team1_path_opponent, ''), 0, 0, 0, 0,
                           'route', 'legacy:' || rowid, coalesce(match_id, ''), 'team1', coalesce(team1_path_fatigue, ''), coalesce(team1_path_opponent, '')
                    from %s
                    where coalesce(path, '') <> '' or coalesce(team1_slot, '') <> '' or coalesce(team2_slot, '') <> ''
                    """.formatted(legacy));
            statement.executeUpdate("""
                    insert into tournament_paths (
                      tournament, round, team, finish, path, count, percentage, simulation_runs, simulation_seed,
                      row_type, path_id, match_id, path_side, path_fatigue, path_opponent
                    )
                    select tournament, round, coalesce(nullif(team2, ''), team2_team), '', coalesce(team2_path_opponent, ''), 0, 0, 0, 0,
                           'route', 'legacy:' || rowid, coalesce(match_id, ''), 'team2', coalesce(team2_path_fatigue, ''), coalesce(team2_path_opponent, '')
                    from %s
                    where coalesce(path, '') <> '' or coalesce(team1_slot, '') <> '' or coalesce(team2_slot, '') <> ''
                    """.formatted(legacy));
        }
    }

    private void migrateLegacyTournamentPathTable(Connection connection) throws SQLException {
        if (!tableExists(connection, "tournament_paths") || !columnExists(connection, "tournament_paths", "match_id")
                || columnExists(connection, "tournament_paths", "row_type")) return;
        if (!tableExists(connection, "paths")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("alter table tournament_paths rename to paths");
            }
        } else {
            String legacy = nextLegacyTableName(connection, "tournament_paths_legacy");
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("alter table tournament_paths rename to " + legacy);
            }
        }
    }

    public void replaceTournamentPaths(String tournament, String round, List<Map<String, String>> rows) throws IOException {
        Path db = databasePath(tournament);
        try (Connection connection = connect(db)) {
            initialize(connection);
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("delete from tournament_paths where tournament = ? and round = ? and row_type = 'summary'")) {
                delete.setString(1, tournament);
                delete.setString(2, round);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    insert into tournament_paths (
                      tournament, round, team, finish, path, count, percentage, simulation_runs, simulation_seed, row_type
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'summary')
                    """)) {
                for (Map<String, String> row : rows) {
                    String team = value(row, "team");
                    if (team.isBlank()) continue;
                    insert.setString(1, tournament);
                    insert.setString(2, round);
                    insert.setString(3, team);
                    insert.setString(4, value(row, "finish"));
                    insert.setString(5, value(row, "path"));
                    insert.setInt(6, integer(row, "count"));
                    insert.setDouble(7, decimal(row, "percentage"));
                    insert.setInt(8, integer(row, "simulation_runs"));
                    insert.setLong(9, longValue(row, "simulation_seed"));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            throw new IOException("Failed to replace tournament paths for " + tournament + " " + round, e);
        }
    }

    private String nextLegacyTableName(Connection connection, String baseName) throws SQLException {
        String name = baseName;
        int index = 1;
        while (tableExists(connection, name)) {
            name = baseName + "_" + index++;
        }
        return name;
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("pragma table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) return true;
            }
            return false;
        }
    }

    private void addColumnIfMissing(Connection connection, String tableName, String columnName, String definition) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("pragma table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("alter table " + tableName + " add column " + columnName + " " + definition);
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.ROOT);
            if (message.contains("duplicate column name")) return;
            throw e;
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select 1 from sqlite_master where type = 'table' and name = ?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean tableEmpty(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select count(*) from " + tableName)) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    private void deleteDataset(Connection connection, String tournament, String datasetType, String round, Path source) throws SQLException {
        if ("matchup".equals(datasetType)) {
            try (PreparedStatement ps = connection.prepareStatement("delete from tournament_paths where tournament = ? and round = ? and row_type = 'route'")) {
                ps.setString(1, tournament);
                ps.setString(2, round);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("delete from paths where tournament = ? and round = ?")) {
                ps.setString(1, tournament);
                ps.setString(2, round);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("delete from matches where tournament = ? and round = ?")) {
                ps.setString(1, tournament);
                ps.setString(2, round);
                ps.executeUpdate();
            }
        }
    }

    private void importMatchupRows(Connection connection, String tournament, String round, Path source) throws IOException, SQLException {
        try (Reader reader = Files.newBufferedReader(source); CSVParser parser = CSV.parse(reader);
             PreparedStatement pathInsert = connection.prepareStatement("""
                     insert or replace into paths (
                       tournament, path_id, round, match_id, team1, team2, path, prediction,
                       model_prediction, selection_source, matchup_pct, matchup_runs, upset_path,
                       team1_slot, team1_source_match, team1_group_finish, team1_bracket_slot,
                       team2_slot, team2_source_match, team2_group_finish, team2_bracket_slot
                     ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """);
             PreparedStatement routeInsert = connection.prepareStatement("""
                     insert into tournament_paths (
                       tournament, round, team, finish, path, count, percentage, simulation_runs, simulation_seed,
                       row_type, path_id, match_id, path_side, path_fatigue, path_opponent
                     ) values (?, ?, ?, '', ?, 0, 0, 0, 0, 'route', ?, ?, ?, ?, ?)
                     """);
             PreparedStatement matchInsert = connection.prepareStatement("""
                     insert or ignore into matches (tournament, round, match_id, team1, team2)
                     values (?, ?, ?, ?, ?)
                     """)) {
            for (CSVRecord record : parser) {
                String pathId = round + ":" + record.getRecordNumber();
                int idx = 1;
                pathInsert.setString(idx++, tournament);
                pathInsert.setString(idx++, pathId);
                pathInsert.setString(idx++, round);
                for (String column : pathColumns()) pathInsert.setString(idx++, "path".equals(column) ? storagePathValue(get(record, column)) : get(record, column));
                pathInsert.addBatch();

                String path = get(record, "path");
                String matchId = get(record, "match_id");
                String team1 = get(record, "team1");
                String team2 = get(record, "team2");
                addRoutePathRow(routeInsert, tournament, round, team1, get(record, "team1_path_opponent"), pathId, matchId,
                        "team1", get(record, "team1_path_fatigue"));
                addRoutePathRow(routeInsert, tournament, round, team2, get(record, "team2_path_opponent"), pathId, matchId,
                        "team2", get(record, "team2_path_fatigue"));

                if (isPrimaryPath(path) && !matchId.isBlank() && !team1.isBlank() && !team2.isBlank()) {
                    matchInsert.setString(1, tournament);
                    matchInsert.setString(2, round);
                    matchInsert.setString(3, matchId);
                    matchInsert.setString(4, team1);
                    matchInsert.setString(5, team2);
                    matchInsert.addBatch();
                }
            }
            pathInsert.executeBatch();
            routeInsert.executeBatch();
            matchInsert.executeBatch();
        }
    }


    private void addRoutePathRow(PreparedStatement routeInsert, String tournament, String round, String team,
                                 String pathOpponent, String pathId, String matchId, String pathSide, String pathFatigue) throws SQLException {
        routeInsert.setString(1, tournament);
        routeInsert.setString(2, round);
        routeInsert.setString(3, team);
        routeInsert.setString(4, pathOpponent);
        routeInsert.setString(5, pathId);
        routeInsert.setString(6, matchId);
        routeInsert.setString(7, pathSide);
        routeInsert.setString(8, pathFatigue);
        routeInsert.setString(9, pathOpponent);
        routeInsert.addBatch();
    }

    private Map<String, String> parseCsvRow(List<String> headers, String line) throws IOException {
        try (CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader(line))) {
            CSVRecord record = parser.iterator().next();
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) row.put(headers.get(i), i < record.size() ? record.get(i) : "");
            return row;
        }
    }

    private List<Map<String, String>> matchupRows(ResultSet rs) throws SQLException {
        List<Map<String, String>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, String> row = new LinkedHashMap<>();
            for (String column : matchupColumns()) {
                String value = resultValue(rs, column);
                if (value.isBlank() && "team1_team".equals(column)) value = resultValue(rs, "team1");
                if (value.isBlank() && "team2_team".equals(column)) value = resultValue(rs, "team2");
                row.put(column, value);
            }
            rows.add(row);
        }
        return rows;
    }

    private String resultValue(ResultSet rs, String column) throws SQLException {
        try {
            String value = rs.getString(column);
            return value == null ? "" : value;
        } catch (SQLException e) {
            return "";
        }
    }

    private List<String> matchupColumns() {
        return List.of("match_id", "team1", "team2", "path", "prediction",
                "team1_path_fatigue", "team2_path_fatigue", "team1_path_opponent", "team2_path_opponent",
                "model_prediction", "selection_source", "matchup_pct", "matchup_runs", "upset_path",
                "team1_slot", "team1_team", "team1_source_match", "team1_group_finish", "team1_bracket_slot",
                "team2_slot", "team2_team", "team2_source_match", "team2_group_finish", "team2_bracket_slot");
    }

    private List<String> pathColumns() {
        return List.of("match_id", "team1", "team2", "path", "prediction",
                "model_prediction", "selection_source", "matchup_pct", "matchup_runs", "upset_path",
                "team1_slot", "team1_source_match", "team1_group_finish", "team1_bracket_slot",
                "team2_slot", "team2_source_match", "team2_group_finish", "team2_bracket_slot");
    }

    private String get(CSVRecord record, String column) {
        return record.isMapped(column) ? record.get(column) : "";
    }

    private static String storagePathValue(String path) {
        return "predicted".equalsIgnoreCase(WebText.trim(path)) ? "primary" : path;
    }

    private static boolean isPrimaryPath(String path) {
        String safe = WebText.trim(path).toLowerCase(java.util.Locale.ROOT);
        return "primary".equals(safe) || "predicted".equals(safe) || "prediction".equals(safe);
    }

    private String normalizePathFilter(String pathFilter) {
        String safe = pathFilter == null || pathFilter.isBlank() ? "all" : pathFilter.trim().toLowerCase(java.util.Locale.ROOT);
        if (safe.equals("predicted") || safe.equals("primary")) return "prediction";
        if (safe.equals("upset")) return "alt";
        if (List.of("all", "results", "prediction", "alt").contains(safe)) return safe;
        return "all";
    }

    private String pathPlaceholders(String pathFilter) {
        return pathValues(pathFilter).stream().map(ignored -> "?").collect(Collectors.joining(","));
    }

    private List<String> pathValues(String pathFilter) {
        return switch (pathFilter) {
            case "results" -> List.of("results", "fixture", "result_upset");
            case "prediction" -> List.of("primary", "predicted", "prediction", "live");
            case "alt" -> List.of("alt", "upset");
            default -> List.of();
        };
    }

    private String csvLine(List<String> headers, CSVRecord record) throws IOException {
        Map<String, String> row = headers.stream()
                .collect(Collectors.toMap(header -> header, header -> get(record, header), (a, b) -> a, LinkedHashMap::new));
        return csvLine(headers, row);
    }

    private String csvLine(List<String> headers, Map<String, String> row) throws IOException {
        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            printer.printRecord(headers.stream().map(header -> row.getOrDefault(header, "")).collect(Collectors.toList()));
        }
        return writer.toString().trim();
    }

    private Map<String, String> headerRow(List<String> headers) {
        return headers.stream().collect(Collectors.toMap(header -> header, header -> header, (a, b) -> a, LinkedHashMap::new));
    }

    private static int confederationAdjustment(String confederation) {
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

    private static String value(Map<String, String> row, String key) {
        return row.getOrDefault(key, "").trim();
    }

    private static String yesNo(String value) {
        return "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equals(value) ? "yes" : "no";
    }

    private static int integer(Map<String, String> row, String key) {
        try {
            return Integer.parseInt(value(row, key));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long longValue(Map<String, String> row, String key) {
        try {
            return Long.parseLong(value(row, key));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static double decimal(Map<String, String> row, String key) {
        try {
            return Double.parseDouble(value(row, key));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static double reachPct(Map<String, String> row) {
        for (Map.Entry<String, String> entry : row.entrySet()) {
            if (entry.getKey().startsWith("reach_")) {
                try {
                    return Double.parseDouble(entry.getValue().trim());
                } catch (NumberFormatException ignored) {
                    return 0.0;
                }
            }
        }
        return 0.0;
    }

    private static double pct(int count, int runs) {
        return Math.round((count * 1000.0) / Math.max(1, runs)) / 10.0;
    }

    private static final class MatchupSummary {
        final String stage;
        final String matchId;
        final String team1;
        final String team2;
        int team1Wins;
        int draws;
        int team2Wins;
        int topScorelineCount;
        int matchupRuns;
        int simulationRuns;
        long simulationSeed;
        String predictedScoreline = "";
        String predictedWinner = "";

        MatchupSummary(String stage, String matchId, String team1, String team2) {
            this.stage = stage;
            this.matchId = matchId;
            this.team1 = team1;
            this.team2 = team2;
        }
    }
}

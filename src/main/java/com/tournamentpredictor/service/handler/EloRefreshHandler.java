package com.tournamentpredictor.service.handler;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.DisplayBuilder;
import com.tournamentpredictor.service.util.ThirdPlaceResolver;
import com.tournamentpredictor.service.util.TokenResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EloRefreshHandler {

    private static final Logger log = LoggerFactory.getLogger(EloRefreshHandler.class);
    private final CsvLoader loader;
    private final Path projectRoot;
    private final ThirdPlaceResolver thirdPlaceResolver;
    private final TokenResolver tokenResolver;
    private final DisplayBuilder displayBuilder;

    public EloRefreshHandler(CsvLoader loader, Path projectRoot, ThirdPlaceResolver thirdPlaceResolver,
                             TokenResolver tokenResolver) {
        this(loader, projectRoot, thirdPlaceResolver, tokenResolver, new DisplayBuilder(tokenResolver));
    }

    public EloRefreshHandler(CsvLoader loader, Path projectRoot, ThirdPlaceResolver thirdPlaceResolver,
                             TokenResolver tokenResolver, DisplayBuilder displayBuilder) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.thirdPlaceResolver = thirdPlaceResolver;
        this.tokenResolver = tokenResolver;
        this.displayBuilder = displayBuilder;
    }

    public void handle() throws IOException {
        Map<String, String> teamNames = new HashMap<>();
        URL teamsUrl = new URL("http://www.eloratings.net/en.teams.tsv");
        HttpURLConnection teamsConnection = (HttpURLConnection) teamsUrl.openConnection();
        teamsConnection.setRequestProperty("User-Agent", "tournament-predictor/1.0");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(teamsConnection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\t");
                if (parts.length < 2) {
                    continue;
                }
                teamNames.put(parts[0].trim(), parts[1].trim());
            }
        }

        URL worldUrl = new URL("http://www.eloratings.net/World.tsv");
        HttpURLConnection worldConnection = (HttpURLConnection) worldUrl.openConnection();
        worldConnection.setRequestProperty("User-Agent", "tournament-predictor/1.0");
        List<String> output = new ArrayList<>();
        List<String> allTeamNames = new ArrayList<>();
        output.add("rank,team_code,team_name,rating");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(worldConnection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\t");
                if (parts.length < 4) {
                    continue;
                }
                String rank = parts[0].trim();
                String code = parts[2].trim();
                String rating = parts[3].trim();
                String safeName = displayBuilder.safe(teamNames.getOrDefault(code, code));
                output.add(String.join(",", rank, code, safeName, rating));
                allTeamNames.add(safeName);
            }
        }

        Path outputDir = projectRoot.resolve("data").resolve("elo");
        Files.createDirectories(outputDir);
        Files.write(outputDir.resolve("world.csv"), output);

        Path historyDir = outputDir.resolve("history");
        Files.createDirectories(historyDir);
        int count = 0;
        for (String teamName : allTeamNames) {
            if (teamName == null || teamName.isBlank()) {
                continue;
            }
            try {
                URL historyUrl = new URL("http://eloratings.net/" + teamName.replace(" ", "_") + ".tsv");
                HttpURLConnection historyConnection = (HttpURLConnection) historyUrl.openConnection();
                historyConnection.setConnectTimeout(10_000);
                historyConnection.setReadTimeout(15_000);
                historyConnection.setRequestProperty("User-Agent", "tournament-predictor/1.0");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        historyConnection.getInputStream(), StandardCharsets.UTF_8))) {
                    List<String> historyLines = new ArrayList<>();
                    historyLines.add("year\tmonth\tday\thome_team\taway_team\thome_score\taway_score\tmatch_type\tneutral\telo_change\thome_elo\taway_elo\t_c12\t_c13\thome_rank\taway_rank");
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] cols = line.split("\t", -1);
                        if (cols.length >= 5) {
                            cols[3] = displayBuilder.safe(teamNames.getOrDefault(cols[3].trim(), cols[3].trim()));
                            cols[4] = displayBuilder.safe(teamNames.getOrDefault(cols[4].trim(), cols[4].trim()));
                            historyLines.add(String.join("\t", cols));
                        } else {
                            historyLines.add(line);
                        }
                    }
                    Files.write(historyDir.resolve(teamName + ".tsv"), historyLines);
                    count++;
                }
            } catch (IOException e) {
                log.warn("Could not fetch history for {}: {}", teamName, e.getMessage());
            }
        }
        System.out.println("Fetched history for " + count + " teams.");
    }
}

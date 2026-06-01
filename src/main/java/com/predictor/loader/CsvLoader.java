package com.predictor.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class CsvLoader {
    private final Path projectRoot = Path.of(System.getProperty("user.dir"));

    public Map<String,String> loadGroups(String tournament) throws IOException {
        Path p = projectRoot.resolve("csv").resolve("predictions").resolve(tournament).resolve("groups.csv");
        if(!Files.exists(p)){
            throw new IOException("Groups file not found: " + p.toAbsolutePath() + ". Expected csv/predictions/" + tournament + "/groups.csv");
        }
        List<String> lines = Files.readAllLines(p);
        Map<String,String> map = new HashMap<>();
        for(String line : lines.stream().skip(1).collect(Collectors.toList())){
            if(line.trim().isEmpty()) continue;
            // expect: position,team[,group_winner,qualifies]
            String[] parts = line.split(",",4);
            String pos = parts[0].trim();
            String team = parts.length>1 ? parts[1].trim() : "";
            if(!pos.isEmpty()) map.put(pos, team);
        }
        return map;
    }

    // New: load qualifies column (position -> qualifies value: yes/maybe/no)
    public Map<String,String> loadGroupQualifies(String tournament) throws IOException {
        Path p = projectRoot.resolve("csv").resolve("predictions").resolve(tournament).resolve("groups.csv");
        if(!Files.exists(p)){
            throw new IOException("Groups file not found: " + p.toAbsolutePath() + ". Expected csv/predictions/" + tournament + "/groups.csv");
        }
        List<String> lines = Files.readAllLines(p);
        Map<String,String> map = new HashMap<>();
        for(String line : lines.stream().skip(1).collect(Collectors.toList())){
            if(line.trim().isEmpty()) continue;
            String[] parts = line.split(",",4);
            String pos = parts[0].trim();
            String qualifies = "";
            if(parts.length>3) qualifies = parts[3].trim().toLowerCase();
            if(!pos.isEmpty()) map.put(pos, qualifies);
        }
        return map;
    }

    // New: load group_winner column (position -> group_winner value: yes/maybe/no)
    public Map<String,String> loadGroupWinner(String tournament) throws IOException {
        Path p = projectRoot.resolve("csv").resolve("predictions").resolve(tournament).resolve("groups.csv");
        if(!Files.exists(p)){
            throw new IOException("Groups file not found: " + p.toAbsolutePath() + ". Expected csv/predictions/" + tournament + "/groups.csv");
        }
        List<String> lines = Files.readAllLines(p);
        Map<String,String> map = new HashMap<>();
        for(String line : lines.stream().skip(1).collect(Collectors.toList())){
            if(line.trim().isEmpty()) continue;
            String[] parts = line.split(",",6);
            String pos = parts[0].trim();
            String gw = "";
            if(parts.length>2) gw = parts[2].trim().toLowerCase();
            if(!pos.isEmpty()) map.put(pos, gw);
        }
        return map;
    }

    // New: load runner_up column (position -> runner_up value: yes/maybe/no)
    public Map<String,String> loadRunnerUp(String tournament) throws IOException {
        Path p = projectRoot.resolve("csv").resolve("predictions").resolve(tournament).resolve("groups.csv");
        if(!Files.exists(p)){
            throw new IOException("Groups file not found: " + p.toAbsolutePath() + ". Expected csv/predictions/" + tournament + "/groups.csv");
        }
        List<String> lines = Files.readAllLines(p);
        Map<String,String> map = new HashMap<>();
        for(String line : lines.stream().skip(1).collect(Collectors.toList())){
            if(line.trim().isEmpty()) continue;
            String[] parts = line.split(",",6);
            String pos = parts[0].trim();
            String ru = "";
            if(parts.length>3) ru = parts[3].trim().toLowerCase();
            if(!pos.isEmpty()) map.put(pos, ru);
        }
        return map;
    }

    // New: load third_place column (position -> third_place value: yes/maybe/no)
    public Map<String,String> loadThirdPlace(String tournament) throws IOException {
        Path p = projectRoot.resolve("csv").resolve("predictions").resolve(tournament).resolve("groups.csv");
        if(!Files.exists(p)){
            throw new IOException("Groups file not found: " + p.toAbsolutePath() + ". Expected csv/predictions/" + tournament + "/groups.csv");
        }
        List<String> lines = Files.readAllLines(p);
        Map<String,String> map = new HashMap<>();
        for(String line : lines.stream().skip(1).collect(Collectors.toList())){
            if(line.trim().isEmpty()) continue;
            String[] parts = line.split(",",6);
            String pos = parts[0].trim();
            String tp = "";
            if(parts.length>4) tp = parts[4].trim().toLowerCase();
            if(!pos.isEmpty()) map.put(pos, tp);
        }
        return map;
    }

    public List<BracketEntry> loadBrackets(String tournament) throws IOException {
        Path p = projectRoot.resolve("csv").resolve("bracket").resolve(tournament + ".csv");
        if(!Files.exists(p)){
            throw new IOException("Bracket file not found: " + p.toAbsolutePath() + ". Expected csv/bracket/" + tournament + ".csv");
        }
        List<String> lines = Files.readAllLines(p);
        List<BracketEntry> entries = new ArrayList<>();
        if (lines.isEmpty()) return entries;
        for (String line : lines.stream().skip(1).collect(Collectors.toList())) {
            if (line.trim().isEmpty()) continue;
            String[] cols = line.split(",", -1);
            String type = cols.length > 0 ? cols[0].trim() : "";
            String stage = cols.length > 1 ? cols[1].trim() : "";
            String raw = cols.length > 2 ? cols[2].trim() : ""; // may be matchId or a token
            String notes = cols.length > 3 ? cols[3].trim() : ""; // may contain tokens for match rows

            String matchId = "";
            String t1 = "", t2 = "";

            // If raw looks like a match id (e.g., M73) treat as matchId and parse tokens from notes
            if (raw.matches("^M\\d+$")) {
                matchId = raw;
                // combine any remaining columns after index 2 into a single tokens string
                if (cols.length > 3) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 3; i < cols.length; i++) {
                        if (cols[i] != null && !cols[i].trim().isEmpty()) {
                            if (sb.length() > 0) sb.append(',');
                            sb.append(cols[i].trim());
                        }
                    }
                    String tokens = sb.toString();
                    if (!tokens.isEmpty()) {
                        String[] toks = tokens.split(",", 2);
                        t1 = toks[0].trim();
                        t2 = toks.length > 1 ? toks[1].trim() : "";
                    }
                } else if (!notes.isEmpty()) {
                    String[] toks = notes.split(",", 2);
                    t1 = toks[0].trim();
                    t2 = toks.length > 1 ? toks[1].trim() : "";
                }
            } else {
                // raw is a token (placeholder rows) or empty; treat as token1
                t1 = raw;
                t2 = "";
            }

            entries.add(new BracketEntry(matchId, t1, t2, stage));
        }
        return entries;
    }

    public static class BracketEntry {
        public final String matchId;
        public final String token1;
        public final String token2;
        public final String stage;
        public BracketEntry(String matchId, String token1, String token2, String stage){
            this.matchId = matchId;
            this.token1 = token1;
            this.token2 = token2;
            this.stage = stage;
        }
    }
}

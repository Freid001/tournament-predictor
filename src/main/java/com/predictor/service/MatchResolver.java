package com.predictor.service;

import com.predictor.loader.CsvLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.springframework.stereotype.Service;

@Service
public class MatchResolver {
    private final Path projectRoot = Path.of(System.getProperty("user.dir"));
    private final CsvLoader loader = new CsvLoader();

    public void resolveAndWriteLast32(String tournament) throws IOException{
        Map<String,String> groups = loader.loadGroups(tournament);
        List<CsvLoader.BracketEntry> brackets = loader.loadBrackets(tournament);

        // keep existing prediction output
        Path outDir = projectRoot.resolve("prediction_output");
        Files.createDirectories(outDir);

        // new: detailed matchups directory under csv/matchups/{tournament}
        Path matchupDir = projectRoot.resolve("csv").resolve("matchups").resolve(tournament);
        Files.createDirectories(matchupDir);
        List<String> detailed = new ArrayList<>();
        detailed.add("match_id,team1,team2");

        for(CsvLoader.BracketEntry be : brackets){
            if(!"LAST_32".equalsIgnoreCase(be.stage)) continue;
            List<String> d1List = buildDisplays(be.token1, groups);
            List<String> d2List = buildDisplays(be.token2, groups);
            for(String d1 : d1List){
                for(String d2 : d2List){
                    detailed.add(String.join(",", be.matchId, safe(d1), safe(d2)));
                }
            }
        }

        Files.write(matchupDir.resolve("matchups.csv"), detailed);
    }

    public void resolveAndWrite(String mode, String tournament) throws IOException{
        if(mode==null) mode = "last_32";
        if(mode.equalsIgnoreCase("last_32")){
            if(tournament==null || tournament.isEmpty()){
                throw new IOException("Mode last_32 requires --tournament=<name>");
            }
            resolveAndWriteLast32(tournament);
            return;
        }
        if(mode.equalsIgnoreCase("refresh-elo") || mode.equalsIgnoreCase("elo")){
            resolveAndWriteElo();
            return;
        }
        throw new IOException("Unknown mode: " + mode + ". Supported: last_32, refresh-elo");
    }

    private void resolveAndWriteElo() throws IOException{
        // downloads World.tsv and en.teams.tsv and writes csv/elo/world.csv
        Map<String,String> teamNames = new HashMap<>();
        // fetch en.teams.tsv
        URL teamsUrl = new URL("https://www.eloratings.net/en.teams.tsv");
        HttpURLConnection tconn = (HttpURLConnection) teamsUrl.openConnection();
        tconn.setRequestProperty("User-Agent", "tournament-predictor/1.0");
        try(BufferedReader br = new BufferedReader(new InputStreamReader(tconn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))){
            String line;
            while((line = br.readLine())!=null){
                if(line.trim().isEmpty()) continue;
                String[] parts = line.split("\t");
                if(parts.length<2) continue;
                String code = parts[0].trim();
                String name = parts[1].trim();
                teamNames.put(code, name);
            }
        }

        // fetch world ratings
        URL worldUrl = new URL("https://www.eloratings.net/World.tsv");
        HttpURLConnection wconn = (HttpURLConnection) worldUrl.openConnection();
        wconn.setRequestProperty("User-Agent", "tournament-predictor/1.0");
        List<String> out = new ArrayList<>();
        out.add("rank,team_code,team_name,rating");
        try(BufferedReader br = new BufferedReader(new InputStreamReader(wconn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))){
            String line;
            while((line = br.readLine())!=null){
                if(line.trim().isEmpty()) continue;
                String[] parts = line.split("\t");
                if(parts.length<4) continue;
                String rank = parts[0].trim();
                String code = parts[2].trim();
                String rating = parts[3].trim();
                String name = teamNames.getOrDefault(code, code);
                out.add(String.join(",", rank, code, safe(name), rating));
            }
        }

        Path outDir = projectRoot.resolve("csv").resolve("elo");
        Files.createDirectories(outDir);
        Files.write(outDir.resolve("world.csv"), out);
    }

    private List<String> buildDisplays(String token, Map<String,String> groups){
        List<String> out = new ArrayList<>();
        if(token==null || token.isEmpty()){ out.add(""); return out; }

        // simple slot like A1/A2/A3/A4 -> return one entry per team in that group (A1..A4)
        if(token.matches("^[A-L][1-4]$")){
            char g = token.charAt(0);
            Set<String> seen = new LinkedHashSet<>();
            for(int i=1;i<=4;i++){
                String slot = ""+g+i;
                String team = groups.getOrDefault(slot, "");
                if(team==null || team.isEmpty()) team = slot;
                if(!seen.contains(team)){
                    seen.add(team);
                    out.add(token + "(" + team + ")");
                }
            }
            return out;
        }

        // composite third-place like ABCDF3 -> for each underlying group letter produce one entry per team in that group
        if(token.matches("^[A-L]+3$")){
            for(char c : token.toCharArray()){
                if(c=='3') break;
                // for group c, iterate its 4 positions c1..c4 as possible candidates
                Set<String> seen = new LinkedHashSet<>();
                for(int i=1;i<=4;i++){
                    String slot = ""+c+i;
                    String team = groups.getOrDefault(slot, "");
                    if(team==null || team.isEmpty()) team = slot;
                    if(!seen.contains(team)){
                        seen.add(team);
                        out.add(token + "(" + team + ")");
                    }
                }
            }
            return out;
        }

        // default: single display (may resolve to a name)
        String resolved = resolveToken(token, groups);
        out.add(displayTokenWithName(token, resolved));
        return out;
    }

    private String displayTokenWithName(String token, String resolved){
        if(token==null || token.isEmpty()) return "";
        if(resolved==null || resolved.isEmpty() || resolved.equals(token)) return token;
        return token + "(" + resolved + ")";
    }

    private String safe(String s){ return s==null?"":s.replaceAll(","," "); }

    private boolean isSimpleSlot(String token){ return token!=null && token.matches("^[A-L][12]"); }
    private String swapSlot(String token){ if(token==null) return token; if(token.matches("^[A-L]1$")==true) return token.charAt(0)+"2"; if(token.matches("^[A-L]2$")==true) return token.charAt(0)+"1"; return token; }

    private String resolveToken(String token, Map<String,String> groups){
        if(token==null) return "";
        token = token.trim();
        // direct slot like A1
        if(token.matches("^[A-L][1-4]$")){
            return groups.getOrDefault(token, token);
        }
        // composite third-place like ABCDF3 => expand to list of A3,B3,C3,D3,F3 and join
        if(token.matches("^[A-L]+3$")){
            List<String> parts = new ArrayList<>();
            for(char c : token.toCharArray()){
                if(c=='3') break;
                String p = ""+c+"3";
                String t = groups.getOrDefault(p,"");
                if(!t.isEmpty()) parts.add(t);
            }
            return String.join("/", parts);
        }
        // winners (Wxx) or unresolved — return token as-is
        return token;
    }
}

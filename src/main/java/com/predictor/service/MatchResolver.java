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
        Map<String,String> qualifies = loader.loadGroupQualifies(tournament);
        Map<String,String> groupWinner = loader.loadGroupWinner(tournament);
        Map<String,String> runnerUp = loader.loadRunnerUp(tournament);
        Map<String,String> thirdPlace = loader.loadThirdPlace(tournament);
        List<CsvLoader.BracketEntry> brackets = loader.loadBrackets(tournament);

        // keep existing prediction output
        Path outDir = projectRoot.resolve("prediction_output");
        Files.createDirectories(outDir);

        // new: detailed matchups directory under csv/matchups/{tournament}
        Path matchupDir = projectRoot.resolve("csv").resolve("matchups").resolve(tournament);
        Files.createDirectories(matchupDir);
        List<String> detailed = new ArrayList<>();
        detailed.add("match_id,team1,team2,prediction");

        for(CsvLoader.BracketEntry be : brackets){
            if(!"LAST_32".equalsIgnoreCase(be.stage)) continue;
            List<String> d1List = buildDisplays(be.token1, groups, thirdPlace);
            List<String> d2List = buildDisplays(be.token2, groups, thirdPlace);
            for(String d1 : d1List){
                for(String d2 : d2List){
                    // mark 'yes' only when the opponent (token2) matches the predicted team for token2
                    String pred = "";
                    // If either side is 'maybe', treat the row as maybe (overrides a definite prediction)
                    if(isDisplayMaybe(be.token1, d1, groups, qualifies, groupWinner, runnerUp, thirdPlace) || isDisplayMaybe(be.token2, d2, groups, qualifies, groupWinner, runnerUp, thirdPlace)) pred = "maybe";
                    // Otherwise require BOTH sides to be definite predictions for 'yes'
                    else if(isDisplayPredicted(be.token1, d1, groups, groupWinner, runnerUp) && isDisplayPredicted(be.token2, d2, groups, groupWinner, runnerUp)) pred = "yes";
                    detailed.add(String.join(",", be.matchId, safe(d1), safe(d2), pred));
                }
            }
        }

        Files.write(matchupDir.resolve("last_32.csv"), detailed);

        // Generate a predictions template for Last 16: match_id,predicted_team1,predicted_team2,predicted_winner
        List<String> last16 = new ArrayList<>();
        last16.add("match_id,predicted_team1,predicted_team2,predicted_winner");
        for(CsvLoader.BracketEntry be : brackets){
            if(!"LAST_16".equalsIgnoreCase(be.stage)) continue;
            String p1resolved = resolveTokenWithBrackets(be.token1, groups, brackets);
            String p2resolved = resolveTokenWithBrackets(be.token2, groups, brackets);
            String p1 = displayTokenWithName(be.token1, p1resolved);
            String p2 = displayTokenWithName(be.token2, p2resolved);
            last16.add(String.join(",", be.matchId, safe(p1), safe(p2), ""));
        }
        Files.write(matchupDir.resolve("last_16_predictions.csv"), last16);
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

    private List<String> buildDisplays(String token, Map<String,String> groups, Map<String,String> thirdPlace){
        List<String> out = new ArrayList<>();
        if(token==null || token.isEmpty()){ out.add(""); return out; }

        // simple slot like A1/A2/A3/A4 -> return one entry per team in that group (A1..A4)
        if(token.matches("^[A-L][1-4]$")){
            char g = token.charAt(0);
            Set<String> seen = new LinkedHashSet<>();
            for(int i=1;i<=4;i++){
                String slot = ""+g+i;
                String q = qualifies.getOrDefault(slot, "");
                if("no".equalsIgnoreCase(q)) continue; // skip teams marked 'no'
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
                    String q = qualifies.getOrDefault(slot, "");
                    if("no".equalsIgnoreCase(q)) continue; // skip teams marked 'no'
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

    private boolean isDisplayPredicted(String token, String display, Map<String,String> groups, Map<String,String> groupWinner, Map<String,String> runnerUp){
        if(token==null || token.isEmpty() || display==null) return false;
        String name = "";
        int p = display.indexOf('(');
        if(p>=0){
            int q = display.indexOf(')', p+1);
            if(q>p) name = display.substring(p+1,q);
        } else {
            name = display;
        }
        if(name==null || name.isEmpty()) return false;
        token = token.trim();
        // Only mark predictions for 1st and 2nd place slots (A1/A2..)
        if(token.matches("^[A-L][1-4]$")){
            String slot = token;
            String team = groups.getOrDefault(slot, "");
            if(slot.endsWith("1")){
                return "yes".equalsIgnoreCase(groupWinner.getOrDefault(slot, "")) && name.equals(team);
            }
            if(slot.endsWith("2")){
                return "yes".equalsIgnoreCase(runnerUp.getOrDefault(slot, "")) && name.equals(team);
            }
            // do not mark predictions for 3rd/4th slots
            return false;
        }
        // Do not mark predictions for third-place composites or other unresolved tokens
        return false;
    }

    private boolean isDisplayMaybe(String token, String display, Map<String,String> groups, Map<String,String> qualifies, Map<String,String> groupWinner, Map<String,String> runnerUp, Map<String,String> thirdPlace){
        if(token==null || token.isEmpty() || display==null) return false;
        String name = "";
        int p = display.indexOf('(');
        if(p>=0){
            int q = display.indexOf(')', p+1);
            if(q>p) name = display.substring(p+1,q);
        } else {
            name = display;
        }
        if(name==null || name.isEmpty()) return false;
        token = token.trim();
        // For simple slots, check if that slot has runner_up/group_winner == "maybe" depending on slot index
        if(token.matches("^[A-L][1-4]$")){
            String slot = token;
            String team = groups.getOrDefault(slot, "");
            if(!name.equals(team)) return false;
            if(slot.endsWith("1")){
                return "maybe".equalsIgnoreCase(groupWinner.getOrDefault(slot, ""));
            }
            if(slot.endsWith("2")){
                return "maybe".equalsIgnoreCase(runnerUp.getOrDefault(slot, ""));
            }
            if(slot.endsWith("3")){
                return "maybe".equalsIgnoreCase(thirdPlace.getOrDefault(slot, ""));
            }
            return false;
        }
        // For composite tokens (like ABC3), find any slot among involved groups matching this name that has third_place==maybe
        if(token.matches("^[A-L]+3$")){
            for(char c : token.toCharArray()){
                if(c=='3') break;
                for(int i=1;i<=4;i++){
                    String slot = ""+c+i;
                    String team = groups.getOrDefault(slot,"");
                    String tp = thirdPlace.getOrDefault(slot,"");
                    if(name.equals(team) && "maybe".equalsIgnoreCase(tp)) return true;
                }
            }
            return false;
        }
        return false;
    }

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

    private String resolveTokenWithBrackets(String token, Map<String,String> groups, List<CsvLoader.BracketEntry> brackets){
        if(token==null) return "";
        token = token.trim();
        // If winner reference like W77, find match M77 and resolve its tokens
        if(token.matches("^W\\d+$")){
            String num = token.substring(1);
            String matchId = "M" + num;
            for(CsvLoader.BracketEntry be : brackets){
                if(matchId.equalsIgnoreCase(be.matchId)){
                    String r1 = resolveToken(be.token1, groups);
                    String r2 = resolveToken(be.token2, groups);
                    List<String> parts = new ArrayList<>();
                    if(r1!=null && !r1.isEmpty()) parts.add(r1);
                    if(r2!=null && !r2.isEmpty()) parts.add(r2);
                    return String.join("/", parts);
                }
            }
            return token; // no match found, return token as-is
        }
        return resolveToken(token, groups);
    }
}

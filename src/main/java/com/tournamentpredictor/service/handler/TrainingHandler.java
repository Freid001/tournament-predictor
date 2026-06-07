package com.tournamentpredictor.service.handler;

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
import java.util.Set;
import java.util.TreeSet;

/** Compares frozen historical simulations with actual results to measure model calibration. */
public class TrainingHandler {
    private static final List<String> DEFAULT_TOURNAMENTS = List.of(
            "world_cup_2006", "world_cup_2010", "world_cup_2014", "world_cup_2018",
            "world_cup_2022", "euros_2016", "euros_2020", "euros_2024");
    private static final double[][] BANDS = {{.35,.45},{.45,.55},{.55,.65},{.65,.75},{.75,.85},{.85,1.01}};
    private static final String[] SCORES = {"0-0","1-0","0-1","1-1","2-0","0-2","2-1","1-2","3-0","0-3"};
    private static final Map<String,Integer> FINISH = Map.of(
            "group_stage",0,"last_16",1,"quarter_final",2,"semi_final",3,"runner_up",4,"champion",5);

    private final Path root;
    public TrainingHandler(Path root) { this.root = root; }

    public void handle(String tournament) throws IOException {
        List<String> tournaments = tournament == null || tournament.isBlank() ? DEFAULT_TOURNAMENTS : List.of(tournament);
        List<Map<String,Object>> summaries = new ArrayList<>(), calibration = new ArrayList<>(), scorelines = new ArrayList<>();
        for (String name : tournaments) {
            Result result = analyse(name);
            summaries.add(result.summary); calibration.addAll(result.calibration); scorelines.addAll(result.scorelines);
        }
        if (summaries.size() > 1) {
            summaries.add(combine(summaries));
            calibration.addAll(combineCalibration(calibration));
            scorelines.addAll(combineScorelines(scorelines, summaries.subList(0, summaries.size() - 1)));
        }
        Path out = root.resolve("data/backtests");
        write(out.resolve("training_report.csv"), summaries);
        write(out.resolve("training_calibration.csv"), calibration);
        write(out.resolve("training_scorelines.csv"), scorelines);
        print(summaries);
        System.out.println("Training reports written under data/backtests/.");
    }

    private Result analyse(String tournament) throws IOException {
        Path actual = root.resolve("data/backtests").resolve(tournament);
        List<Map<String,String>> actualResults = read(actual.resolve("actual_results.csv"));
        List<Map<String,String>> finishRows = read(actual.resolve("actual_finish.csv"));
        Map<String,Map<String,String>> finishes = index(finishRows, "team");
        List<Map<String,String>> progressionRows = read(sim(tournament, "simulation_last_16.csv"));
        Map<String,Map<String,String>> progression = index(progressionRows, "team");
        List<Map<String,String>> groupRows = read(sim(tournament, "simulation_groups.csv"));
        Map<String,Prediction> predictions = loadPredictions(tournament);

        List<Match> matches = new ArrayList<>();
        Map<String,Double> predictedScores = new HashMap<>();
        Map<String,Integer> actualScores = new HashMap<>();
        for (Map<String,String> row : actualResults) {
            String team1=row.get("team1"), team2=row.get("team2");
            Prediction raw=predictions.get(key(team1,team2));
            if(raw==null) throw new IOException("Missing simulation for "+team1+" vs "+team2+" in "+tournament);
            Prediction p=raw.orient(team1);
            int g1=i(row,"team1_goals"), g2=i(row,"team2_goals");
            int outcome=g1>g2?0:g2>g1?2:1;
            double[] probs={p.first()/(double)p.runs(),p.draw()/(double)p.runs(),p.second()/(double)p.runs()};
            int favourite=probs[0]>=probs[2]?0:2;
            double expectedGoals=0, over=0;
            for(var e:p.scores().entrySet()) { String[] s=e.getKey().split("-"); int total=Integer.parseInt(s[0])+Integer.parseInt(s[1]); double f=e.getValue()/(double)p.runs(); expectedGoals+=total*f; if(total>=3)over+=f; predictedScores.merge(e.getKey(),f,Double::sum); }
            actualScores.merge(g1+"-"+g2,1,Integer::sum);
            matches.add(new Match(probs,outcome,favourite,Math.max(probs[0],probs[2]),expectedGoals,g1+g2,over,g1+g2>=3));
        }
        int n=matches.size();
        double brier=matches.stream().mapToDouble(m->{double x=0;for(int j=0;j<3;j++)x+=Math.pow(m.p[j]-(j==m.outcome?1:0),2);return x;}).average().orElse(0);
        double logLoss=matches.stream().mapToDouble(m->-Math.log(Math.max(m.p[m.outcome],1e-12))).average().orElse(0);
        double predictedFav=matches.stream().mapToDouble(Match::favProb).sum(), predictedDraw=matches.stream().mapToDouble(m->m.p[1]).sum();
        long actualFav=matches.stream().filter(m->m.outcome==m.favourite).count(), actualDraw=matches.stream().filter(m->m.outcome==1).count();

        int exact=0, groupWinners=0;
        for(Map<String,String> row:groupRows){ Map<String,String> f=finishes.get(row.get("team")); double best=-1;int pos=0;for(int j=1;j<=4;j++){double v=d(row,"finish_"+ordinal(j));if(v>best){best=v;pos=j;}} int actualPos=i(f,"position");if(pos==actualPos)exact++;if(pos==1&&actualPos==1)groupWinners++; }
        int qualifierCount=(int)finishRows.stream().filter(r->!"group_stage".equals(r.get("finish"))).count();
        List<Map<String,String>> predictedQualifiers=new ArrayList<>(), remaining=new ArrayList<>();
        Set<String> groups=new TreeSet<>(); finishRows.forEach(r->groups.add(r.get("group")));
        for(String group:groups){List<Map<String,String>> rows=progressionRows.stream().filter(r->group.equals(finishes.get(r.get("team")).get("group"))).sorted(Comparator.comparingDouble((Map<String,String> r)->d(r,"reach_last_16")).reversed()).toList();predictedQualifiers.addAll(rows.stream().limit(2).toList());remaining.addAll(rows.stream().skip(2).toList());}
        remaining.sort(Comparator.comparingDouble((Map<String,String> r)->d(r,"reach_last_16")).reversed());
        predictedQualifiers.addAll(remaining.stream().limit(Math.max(0,qualifierCount-predictedQualifiers.size())).toList());
        long correctQualifiers=predictedQualifiers.stream().filter(r->!"group_stage".equals(finishes.get(r.get("team")).get("finish"))).count();
        String champion=finishRows.stream().filter(r->"champion".equals(r.get("finish"))).findFirst().orElseThrow().get("team");
        List<Map<String,String>> ordered=progressionRows.stream().sorted(Comparator.comparingDouble((Map<String,String> r)->d(r,"champion")).reversed()).toList();
        int championRank=0;for(int j=0;j<ordered.size();j++)if(champion.equals(ordered.get(j).get("team"))){championRank=j+1;break;}

        Map<String,Object> s=new LinkedHashMap<>();
        s.put("tournament",tournament);s.put("matches",n);s.put("outcome_brier",brier);s.put("log_loss",logLoss);
        s.put("predicted_goals",matches.stream().mapToDouble(Match::expectedGoals).average().orElse(0));s.put("actual_goals",matches.stream().mapToDouble(Match::actualGoals).average().orElse(0));
        s.put("predicted_over_2_5",matches.stream().mapToDouble(Match::over).average().orElse(0));s.put("actual_over_2_5",matches.stream().filter(Match::actualOver).count()/(double)n);
        s.put("predicted_favourite_wins",predictedFav);s.put("actual_favourite_wins",actualFav);s.put("predicted_draws",predictedDraw);s.put("actual_draws",actualDraw);s.put("predicted_upsets",n-predictedFav-predictedDraw);s.put("actual_upsets",n-actualFav-actualDraw);
        s.put("qualifier_brier",progressionBrier(progression,finishes,"reach_last_16","last_16"));s.put("quarter_brier",progressionBrier(progression,finishes,"reach_last_8","quarter_final"));s.put("semi_brier",progressionBrier(progression,finishes,"reach_last_4","semi_final"));s.put("final_brier",progressionBrier(progression,finishes,"reach_final","runner_up"));s.put("champion_brier",progressionBrier(progression,finishes,"champion","champion"));
        s.put("correct_group_winners",groupWinners);s.put("groups",groups.size());s.put("correct_qualifiers",correctQualifiers);s.put("qualifiers",qualifierCount);s.put("exact_positions",exact);s.put("teams",finishRows.size());s.put("champion",champion);s.put("champion_rank",championRank);s.put("champion_probability",d(progression.get(champion),"champion"));
        List<Map<String,Object>> bands=new ArrayList<>();for(double[] band:BANDS){List<Match> ms=matches.stream().filter(m->m.favProb>=band[0]&&m.favProb<band[1]).toList();if(!ms.isEmpty()){Map<String,Object> x=new LinkedHashMap<>();x.put("tournament",tournament);x.put("band",(int)(band[0]*100)+"-"+(int)(band[1]*100));x.put("matches",ms.size());x.put("predicted_win_rate",ms.stream().mapToDouble(Match::favProb).average().orElse(0));x.put("actual_win_rate",ms.stream().filter(m->m.outcome==m.favourite).count()/(double)ms.size());bands.add(x);}}
        List<Map<String,Object>> scores=new ArrayList<>();for(String score:SCORES){Map<String,Object>x=new LinkedHashMap<>();x.put("tournament",tournament);x.put("scoreline",score);x.put("predicted_frequency",predictedScores.getOrDefault(score,0d)/n);x.put("actual_frequency",actualScores.getOrDefault(score,0)/(double)n);scores.add(x);}
        return new Result(s,bands,scores);
    }

    private Map<String,Prediction> loadPredictions(String t)throws IOException{Map<String,Prediction> out=new HashMap<>();for(Map<String,String> r:read(sim(t,"simulation_scorelines_groups.csv"))){String k=key(r.get("team1"),r.get("team2"));Prediction p=out.computeIfAbsent(k,x->new Prediction(r.get("team1"),r.get("team2"),i(r,"matchup_runs"),new HashMap<>(),new HashMap<>()));p.outcomes.merge(r.get("winner"),i(r,"count"),Integer::sum);p.scores.merge(r.get("scoreline"),i(r,"count"),Integer::sum);}return out;}
    private double progressionBrier(Map<String,Map<String,String>> p,Map<String,Map<String,String>> f,String col,String threshold){return f.entrySet().stream().mapToDouble(e->{double x=d(p.get(e.getKey()),col)/100d;boolean y=FINISH.get(e.getValue().get("finish"))>=FINISH.get(threshold);return Math.pow(x-(y?1:0),2);}).average().orElse(0);}
    private Map<String,Object> combine(List<Map<String,Object>> rows){double matches=sum(rows,"matches"),teams=sum(rows,"teams");Map<String,Object>x=new LinkedHashMap<>();x.put("tournament","combined");x.put("matches",(int)matches);for(String k:List.of("outcome_brier","log_loss","predicted_goals","actual_goals","predicted_over_2_5","actual_over_2_5"))x.put(k,weighted(rows,k,"matches",matches));for(String k:List.of("qualifier_brier","quarter_brier","semi_brier","final_brier","champion_brier"))x.put(k,weighted(rows,k,"teams",teams));for(String k:List.of("predicted_favourite_wins","actual_favourite_wins","predicted_draws","actual_draws","predicted_upsets","actual_upsets","correct_group_winners","groups","correct_qualifiers","qualifiers","exact_positions","teams"))x.put(k,sum(rows,k));x.put("champion","");x.put("champion_rank","");x.put("champion_probability","");return x;}
    private List<Map<String,Object>> combineCalibration(List<Map<String,Object>> rows){List<Map<String,Object>>out=new ArrayList<>();for(String band:new TreeSet<>(rows.stream().map(r->r.get("band").toString()).toList())){List<Map<String,Object>>v=rows.stream().filter(r->band.equals(r.get("band"))).toList();double n=sum(v,"matches");Map<String,Object>x=new LinkedHashMap<>();x.put("tournament","combined");x.put("band",band);x.put("matches",(int)n);x.put("predicted_win_rate",weighted(v,"predicted_win_rate","matches",n));x.put("actual_win_rate",weighted(v,"actual_win_rate","matches",n));out.add(x);}return out;}
    private List<Map<String,Object>> combineScorelines(List<Map<String,Object>> rows,List<Map<String,Object>> summaries){double total=sum(summaries,"matches");Map<String,Double>w=new HashMap<>();summaries.forEach(r->w.put(r.get("tournament").toString(),num(r.get("matches"))));List<Map<String,Object>>out=new ArrayList<>();for(String score:List.of(SCORES)){List<Map<String,Object>>v=rows.stream().filter(r->score.equals(r.get("scoreline"))).toList();Map<String,Object>x=new LinkedHashMap<>();x.put("tournament","combined");x.put("scoreline",score);x.put("predicted_frequency",v.stream().mapToDouble(r->num(r.get("predicted_frequency"))*w.get(r.get("tournament").toString())).sum()/total);x.put("actual_frequency",v.stream().mapToDouble(r->num(r.get("actual_frequency"))*w.get(r.get("tournament").toString())).sum()/total);out.add(x);}return out;}
    private void write(Path path,List<Map<String,Object>> rows)throws IOException{if(rows.isEmpty())return;Files.createDirectories(path.getParent());List<String> headers=new ArrayList<>(rows.getFirst().keySet());try(Writer w=Files.newBufferedWriter(path);CSVPrinter p=CSVFormat.DEFAULT.print(w)){p.printRecord(headers);for(var r:rows)p.printRecord(headers.stream().map(h->format(r.getOrDefault(h,""))).toList());}}
    private void print(List<Map<String,Object>> rows){System.out.printf("%-16s %7s %8s %8s %12s %11s %20s%n","Tournament","Matches","Brier","LogLoss","Goals P/A","Qualifiers","Champion");for(var r:rows){String c=r.get("champion").toString().isBlank()?"-":r.get("champion")+" #"+r.get("champion_rank")+" "+String.format(Locale.ROOT,"%.1f%%",num(r.get("champion_probability")));System.out.printf(Locale.ROOT,"%-16s %7d %8.4f %8.4f %5.2f/%-5.2f %2d/%-8d %20s%n",r.get("tournament"),((Number)r.get("matches")).intValue(),num(r.get("outcome_brier")),num(r.get("log_loss")),num(r.get("predicted_goals")),num(r.get("actual_goals")),((Number)r.get("correct_qualifiers")).intValue(),((Number)r.get("qualifiers")).intValue(),c);}}
    private List<Map<String,String>> read(Path p)throws IOException{if(!Files.exists(p))throw new IOException("Training input not found: "+p);try(Reader r=Files.newBufferedReader(p);CSVParser parser=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(r)){List<Map<String,String>>out=new ArrayList<>();for(CSVRecord rec:parser){Map<String,String>x=new LinkedHashMap<>();parser.getHeaderNames().forEach(h->x.put(h,rec.get(h)));out.add(x);}return out;}}
    private Path sim(String t,String f){return root.resolve("data/simulations").resolve(t).resolve(f);}private static Map<String,Map<String,String>>index(List<Map<String,String>>r,String k){Map<String,Map<String,String>>x=new HashMap<>();r.forEach(v->x.put(v.get(k),v));return x;}private static String key(String a,String b){return a.compareToIgnoreCase(b)<=0?a+"|"+b:b+"|"+a;}private static int i(Map<String,String>r,String k){return Integer.parseInt(r.get(k));}private static double d(Map<String,String>r,String k){return Double.parseDouble(r.get(k));}private static String ordinal(int i){return switch(i){case 1->"1st";case 2->"2nd";case 3->"3rd";default->"4th";};}private static double num(Object x){return ((Number)x).doubleValue();}private static double sum(List<Map<String,Object>>r,String k){return r.stream().mapToDouble(x->num(x.get(k))).sum();}private static double weighted(List<Map<String,Object>>r,String v,String w,double total){return r.stream().mapToDouble(x->num(x.get(v))*num(x.get(w))).sum()/total;}private static Object format(Object x){return x instanceof Double d?String.format(Locale.ROOT,"%.4f",d):x;}
    private record Match(double[]p,int outcome,int favourite,double favProb,double expectedGoals,int actualGoals,double over,boolean actualOver){}private record Result(Map<String,Object>summary,List<Map<String,Object>>calibration,List<Map<String,Object>>scorelines){}
    private record Prediction(String team1,String team2,int runs,Map<String,Integer>outcomes,Map<String,Integer>scores){Prediction orient(String first){if(team1.equals(first))return this;Map<String,Integer>s=new HashMap<>();scores.forEach((k,v)->{String[]p=k.split("-");s.put(p[1]+"-"+p[0],v);});return new Prediction(team2,team1,runs,outcomes,s);}int first(){return outcomes.getOrDefault(team1,0);}int draw(){return outcomes.getOrDefault("Draw",0);}int second(){return outcomes.getOrDefault(team2,0);}}
}

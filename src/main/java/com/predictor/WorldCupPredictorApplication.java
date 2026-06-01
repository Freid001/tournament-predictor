package com.predictor;

import com.predictor.service.MatchResolver;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;

@SpringBootApplication
public class WorldCupPredictorApplication implements CommandLineRunner {

    @Autowired
    MatchResolver resolver;

    public static void main(String[] args) {
        SpringApplication.run(WorldCupPredictorApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // If no args provided, show help and exit (per user request)
        if(args == null || args.length == 0){
            System.out.println("Usage: java -jar target/tournament-predictor-0.0.1-SNAPSHOT.jar --tournament=<name> [--mode=<mode>]");
            System.out.println("Generates expanded matchup permutations from bracket and predictions CSVs.");
            System.out.println("Options:");
            System.out.println("  --tournament=<name>   Tournament subdir under csv/ (required for mode=last_32)");
            System.out.println("  --mode=<mode>         Operation mode: last_32 (requires --tournament), refresh-elo (no tournament needed)");
            System.out.println("  -h, --help            Show this help and exit");
            System.out.println("Examples:");
            System.out.println("  java -jar target/tournament-predictor-0.0.1-SNAPSHOT.jar --tournament=world_cup_2026 --mode=last_32");
            System.out.println("  java -jar target/tournament-predictor-0.0.1-SNAPSHOT.jar --mode=refresh-elo");
            System.exit(0);
        }

        String tournament = null;
        String mode = "last_32";
        for(String a : args){
            if(a != null && a.startsWith("--tournament=")){
                String v = a.substring(a.indexOf('=')+1);
                if(!v.trim().isEmpty()) tournament = v.trim();
            }
            if(a != null && a.startsWith("--mode=")){
                String v = a.substring(a.indexOf('=')+1);
                if(!v.trim().isEmpty()) mode = v.trim();
            }
            if("--help".equals(a) || "-h".equals(a)){
                System.out.println("Usage: java -jar target/tournament-predictor-0.0.1-SNAPSHOT.jar --tournament=<name> [--mode=<mode>]");
                System.out.println("Generates expanded matchup permutations from bracket and predictions CSVs.");
                System.out.println("Options:");
                System.out.println("  --tournament=<name>   Tournament subdir under csv/ (required for mode=last_32)");
                System.out.println("  --mode=<mode>         Operation mode: last_32 (requires --tournament), refresh-elo (no tournament needed)");
                System.out.println("  -h, --help            Show this help and exit");
                System.out.println("Examples:");
                System.out.println("  java -jar target/tournament-predictor-0.0.1-SNAPSHOT.jar --tournament=world_cup_2026 --mode=last_32");
                System.out.println("  java -jar target/tournament-predictor-0.0.1-SNAPSHOT.jar --mode=refresh-elo");
                System.exit(0);
            }
        }
        System.out.println("Resolving (mode=" + mode + ")...");
        try{
            resolver.resolveAndWrite(mode, tournament);
            System.out.println("Done. Outputs in prediction_output/");
            System.exit(0);
        }catch(IOException e){
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }
}

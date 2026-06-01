Tournament Predictor (Java Spring Boot CLI)

Run:
mvn -q package
java -jar target/tournament-predictor-0.0.1-SNAPSHOT.jar --tournament=world_cup_2026

Usage / Options:
  --tournament=<name>   Tournament subdirectory under csv/ (default: world_cup_2026)
  --mode=<mode>         Operation mode: last_32 (default). Future: bets, elo
  -h, --help            Show short help and exit

Examples:
  java -jar target/tournament-predictor-0.0.1-SNAPSHOT.jar --tournament=world_cup_2026

Outputs:
  csv/matchups/{tournament}/last_32_matchups.csv  (expanded permutations)

What it does now:
- Reads predictions/groups.csv (position,team)
- Reads csv/brackets.csv and resolves LAST_32 entries into prediction_output/last_32_resolved.csv and matchups.txt
- Produces alternative swap matchups for simple A1<->A2 scenarios

Next: add Elo/odds loaders, probability math, and Monte Carlo simulator.
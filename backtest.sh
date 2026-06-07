#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
JAR="$ROOT/target/tournament-predictor-0.0.1-SNAPSHOT.jar"
if [[ ! -f "$JAR" ]]; then
  (cd "$ROOT" && mvn -q package -DskipTests)
fi
if [[ $# -gt 1 ]]; then
  echo "Use --mode=training without --tournament for all historical tournaments, or pass one tournament." >&2
  exit 2
fi
TOURNAMENT_ARG=()
if [[ $# -eq 1 ]]; then TOURNAMENT_ARG=("--tournament=$1"); fi
exec java --enable-native-access=ALL-UNNAMED -jar "$JAR" --mode=training "${TOURNAMENT_ARG[@]}"

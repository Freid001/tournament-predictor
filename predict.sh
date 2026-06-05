#!/bin/sh
exec java --enable-native-access=ALL-UNNAMED -jar "$(dirname "$0")/target/tournament-predictor-0.0.1-SNAPSHOT.jar" "$@"

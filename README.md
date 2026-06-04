# Tournament Predictor

A Java CLI tool for predicting tournament bracket outcomes using ELO ratings, head-to-head history, home advantage, and injury impact.

## Contents

- [About](#about)
- [Installation](#installation)
- [Usage](#usage)
- [Docs](#docs)

## About

Expands your group stage picks into every possible knockout matchup path. Matchups are labelled `primary` (predicted path) or `alt` (if an upset changes the bracket). Run each mode in sequence to build a full bracket prediction from the group stage to the final.

**What it is good at:**
- Showing every realistic matchup that could occur given your picks
- Combined predictions using ELO ratings, competition history, and friendly history
- Spotting alternative paths where an upset could change the bracket

**What it does not do:**
- Full tournament win probabilities (e.g. "France has a 23% chance of winning the tournament"). For that you would need a Monte Carlo simulator that runs millions of full bracket simulations and accounts for path dependency.

## Prediction methodology

Each knockout matchup prediction combines three signals:

| Signal | Weight | Source |
|---|---|---|
| ELO rating | 70% | `data/elo/world.csv` (refreshed via `elo-refresh`) |
| Competition head-to-head | 21% | Historical competitive match results |
| Friendly head-to-head | 9% | Historical friendly match results |

The combined formula is: `finalPct = 0.7 × elo + 0.3 × (0.7 × competition + 0.3 × friendly)`

**Adjustments applied at group stage (`start` mode):**

| Factor | Effect |
|---|---|
| Home advantage (`host=yes`) | +100 ELO |
| Injury impact level 1 (minor) | −25 ELO |
| Injury impact level 2 (significant) | −50 ELO |
| Injury impact level 3 (critical) | −100 ELO |

These adjustments affect a team's effective ELO used to predict group stage rankings — they do not carry forward to knockout round predictions.

## Installation

### Quick start — download and run

No build tools required. Download the latest jar and run script, then run:

```bash
curl -LO https://github.com/freid001/tournament-predictor/releases/latest/download/tournament-predictor.jar
curl -LO https://github.com/freid001/tournament-predictor/releases/latest/download/predict.sh
chmod +x predict.sh
./predict.sh --tournament=world_cup_2026
```

> **Requires Java 17+.** Check with `java -version`.

You will also need the `data/` folder from the repository alongside the jar — ELO ratings, match histories, and tournament input files all live there. Download and extract the latest `data.zip` from the [releases page](https://github.com/freid001/tournament-predictor/releases).

### Developer install — build from source

```bash
git clone https://github.com/freid001/tournament-predictor.git
cd tournament-predictor
mvn -q package
./predict.sh --tournament=world_cup_2026
```

> **Requires Java 17+ and Maven 3.8+.**

## Usage

```bash
./predict.sh --tournament=<name> --mode=<mode>
```

| Option | Description |
|---|---|
| `--tournament=<name>` | Tournament subfolder under `data/` (required for all modes except `elo-refresh`) |
| `--mode=<mode>` | See [Modes](docs/MODES.md) (default: `groups`) |
| `--filter=<expr>` | Filter output — team name for bracket modes, or filter expression for path mode (see [Path Mode](docs/PATH_MODE.md)) |
| `--path=primary\|alt\|both` | Filter bracket output by matchup path (default: `both`) |
| `--page=<n>` | Page number for paginated output (default: `1`) |
| `--flags` | Show emoji flags next to team names (default: off) |
| `-h, --help` | Show help and exit |

## Docs

| Document | Description |
|---|---|
| [Modes](docs/MODES.md) | All modes, run order, and full bracket walkthrough |
| [Path Mode](docs/PATH_MODE.md) | Path tracing, difficulty scoring, and filter expressions |
| [Configuration](docs/CONFIGURATION.md) | All configurable prediction parameters |
| [CSV Format](docs/CSV_FORMAT.md) | Matchup file columns and locking behaviour |

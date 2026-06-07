# International Football Tournament Prediction Engine

An international football tournament prediction engine combining ELO-based match forecasting, xG and Poisson score modelling, bracket prediction, Monte Carlo simulation, and route analysis.

## Contents

- [About](#about)
- [Quick start](#quick-start)
- [Prediction methodology](#prediction-methodology)
- [Docs](#docs)

## About

The browser UI is the primary way to use the predictor. Configure the teams and review the model-selected group outcomes, then run the simulation in two explicit steps:

1. **Run Group Stage** simulates every group fixture and saves the correlated Last 32 bracket from each run.
2. **Run Knockout Rounds** resumes those saved brackets and carries each route through the final.

The completed round pages expose the primary model-selected bracket and alternate matchups, while the Monte Carlo probabilities retain unlikely routes that the primary bracket does not select.

The engine is both:

- **A bracket prediction tool** for the model-selected route and alternate matchups.
- **A Monte Carlo tournament simulator** for progression and title probabilities.

**What it models:**
- Group-stage rankings using base ELO plus current-form, availability, environment, cohesion, age, and depth adjustments
- Separate Attack and Defence inputs that shape xG and scorelines without being added to Adjusted ELO
- Every possible knockout matchup given your group picks
- Match predictions using adjusted ELO, attack/defence-shaped xG, Poisson score probabilities, route fatigue, and model-selected paths
- Bracket paths showing every route a team could take to the final
- Round-by-round and end-to-end Monte Carlo probabilities, including group qualification, third-place routing, every knockout round, and champion chances
- Tournament-specific group ranking: UEFA head-to-head mini-tables for EURO tournaments; FIFA-style overall criteria remain the default
- Best-third ranking uses points, goal difference, goals scored and wins, with ELO only as the final fallback when disciplinary/competition-ranking data is unavailable
- Historical training datasets currently included: FIFA World Cups 2006, 2010, 2014, 2018 and 2022 plus UEFA EURO 2016, 2020 and 2024; each snapshot excludes results from tournament kickoff onward
- Repeatable calibration reports from committed actual results via `./predict.sh --mode=training`
- **Tournament path fatigue** - route-specific depletion based on opponents already faced

**What it does not do:**
- Claim statistically proven calibration or a guaranteed betting edge. Attack/Defence and other custom weights remain user inputs that need historical training evaluation.

## Quick start

### Download and run

No build tools required:

```bash
curl -LO https://github.com/freid001/tournament-predictor/releases/latest/download/tournament-predictor.jar
curl -LO https://github.com/freid001/tournament-predictor/releases/latest/download/predict.sh
chmod +x predict.sh
./predict.sh --browser --tournament=world_cup_2026
```

> **Requires Java 25+.** Check with `java -version`.

You will also need the `data/` folder from the repository. Download and extract the latest `data.zip` from the [releases page](https://github.com/freid001/tournament-predictor/releases).

### Build from source

```bash
git clone https://github.com/freid001/tournament-predictor.git
cd tournament-predictor
mvn -q package
./predict.sh --browser --tournament=world_cup_2026
```

> **Requires Java 25+ and Maven 3.8+.**

## Usage

```bash
# Start the browser UI (recommended)
./predict.sh --browser --tournament=world_cup_2026

# Refresh global ELO ratings from latest data
./predict.sh --mode=elo-refresh

# Freeze the current ELO/history inputs for one tournament
./predict.sh --mode=snapshot-refresh --tournament=world_cup_2026
```

See [Modes](docs/MODES.md) for the full pipeline reference.

### Tournament snapshots

Global ELO refreshes update ignored working files in `data/elo/current/`, but tournament modes do not read those files directly. Run `snapshot-refresh` after editing `start.csv`, changing `tournament.properties`, or after new friendlies/ELO data should be included for that tournament.

A snapshot is stored under `data/elo/snapshots/<tournament>/`:

- `teams.csv` contains only the tournament teams and their frozen base ELO ratings.
- `history/` contains only those teams' recent ELO history rows for the configured form window.
- `metadata.properties` records the form windows, tournament start-date cutoff, and source files used.

Each tournament must define `tournament.start.date=YYYY-MM-DD` and can define form windows in `data/predictions/<tournament>/tournament.properties`. `snapshot-refresh` reads those values and freezes them into snapshot metadata. After that, `start` and the UI ELO breakdowns read from the tournament snapshot and its metadata; they do not fall back to the refreshable current ELO files. History rows after the frozen tournament start date are excluded, even if the snapshot is refreshed years later. This keeps old tournaments reproducible even if `eloratings.net` changes, goes offline, or global ELO is refreshed for a future tournament.

## Prediction methodology

### Knockout round predictions

Current match prediction has two layers:

1. **Adjusted ELO** combines frozen base ELO with current-form and tournament-context adjustments. Knockout path fatigue can then modify that strength for the specific route. The standard 400-point expectation scale remains the base strength model:

`team1WinPct = 1 / (1 + 10^((team2Elo - team1Elo) / 400))`

2. **Goal model inputs** apply `attack_quality` and `defence_quality` directly to expected goals. Each level changes xG by `0.15`: attack changes the team's own xG, while the opponent's defence changes that xG in the opposite direction. These inputs are deliberately excluded from Adjusted ELO.

The score model converts xG into 90-minute win/draw/loss and exact-score probabilities using independent Poisson distributions. Drawn knockout matches use the ELO expectation for the extra-time/penalty advancement split. Monte Carlo runs sample these score distributions through the group stage and knockout bracket.

The plus/minus ELO values are temporary pre-match deltas, not permanent World Football Elo updates. Attack and Defence are separate xG inputs, not ELO deltas.


### Historical training report

Run every committed historical tournament:

```bash
./predict.sh --mode=training
```

Run one tournament:

```bash
./predict.sh --mode=training --tournament=world_cup_2022
```

Actual group results and final tournament finishes live under `data/backtests/<tournament>/`. The command compares them with the saved Monte Carlo outputs and writes:

- `data/backtests/training_report.csv` - Brier score, log loss, goals, progression accuracy and champion ranking
- `data/backtests/training_calibration.csv` - favourite probability bands versus actual win rates
- `data/backtests/training_scorelines.csv` - predicted versus actual exact-score frequencies

Use the same committed inputs when comparing model changes. Do not tune against one tournament and report that same tournament as independent validation.

### Model accuracy and calibration

This is an explainable tournament model, not a fully calibrated market-grade forecasting system yet. It should be useful for ranking teams, exposing why a route is hard, comparing close matchups, and stress-testing bracket assumptions. It should not be treated as a proven edge over bookmaker odds or professional forecasting models.

The project includes an xG/Poisson score model, a two-process group-to-final Monte Carlo simulation that preserves each sampled route between steps, and separate round-conditional simulations. Calibration now covers World Cups 2006-2022 and EURO 2016-2024. The older added datasets use frozen historical ELO/form with neutral manual squad adjustments, while the originally curated tournaments retain their tournament-specific squad inputs. This is a stronger calibration sample, but it is still not evidence of a market-grade betting edge.

The highest-value accuracy upgrades would be:

1. Backtest every signal against past World Cups and recent international matches, then tune weights against log loss or Brier score instead of intuition.
2. Continue validating the `0.15 xG` Attack/Defence step and Poisson assumptions against historical score distributions.
3. Add external cross-checks such as market odds, external xG/xGA, rest days, travel distance, and venue/weather by match.
4. Track prediction calibration so a 70% model pick wins about 70% of comparable historical games.


### Group stage adjustments

Applied once at the group stage to calculate each team's effective ELO ranking. Attack and Defence are carried separately as goal-model inputs.

| Signal | Max impact | Notes |
|---|---|---|
| **Home advantage** | +100 ELO | Official host-country advantage. |
| **Qualification form** | Removed | No extra ELO because qualifying results are already present in base ELO. |
| **Pre-tournament form** | +/-50 ELO | The three most recent friendlies before tournament kickoff. |
| **Injuries** | -22 / -45 / -90 ELO | Current selected-squad availability. |
| **Heat advantage** | +9 / +18 / +35 ELO | Venue and climate adaptation. |
| **Squad omissions** | -18 / -35 / -70 ELO | Players missing from the selected squad. |
| **Squad age** | -12 young / -8 aging ELO | Inexperience or recovery risk. |
| **Squad cohesion** | -11 / -22 / -45 ELO | Preparation, tactical, or institutional disruption. |
| **Bench depth** | +10 / -10 / -20 ELO | Replacement-quality penalty; also controls fatigue sensitivity. |
| **Attack quality** | -2 to +2; 0.15 xG per level | Changes the team's xG, not Adjusted ELO. |
| **Defence quality** | -2 to +2; 0.15 xG per level | Changes the opponent's xG, not Adjusted ELO. |

ELO adjustment values are configurable in `application.properties`. Attack and Defence are direct signed inputs in `start.csv`.

### Knockout stage: tournament path fatigue

Applied at each knockout round (last 32 through final) as a live ELO adjustment on top of the team's ranking. The first knockout match is seeded with group load from above-average opponents already faced.

**Why it matters:** sports science research shows teams that face tougher opponents in earlier knockout rounds underperform expectations in later rounds relative to their base ELO, even after controlling for raw strength. The primary driver is **accumulated fatigue** — physical depletion from high-intensity matches against strong opposition compounds across rounds.

**How it works:** each group or prior knockout opponent's ELO is compared to the tournament average (1850). Group load only counts opponents above that baseline, so an easy group is never a rest bonus. Knockout opponents then stack on top round by round. Each stage is weighted by round importance, and the cumulative total is converted to an ELO penalty.

| Stage beaten | Weight | Rationale |
|---|---|---|
| Group | × 0.25 | Group-stage load carried into the first knockout match |
| Last 32 | × 0.5 | Early round — squad still fresh, bench minutes available |
| Last 16 | × 1.0 | Baseline — first team heavily involved |
| Quarter-final | × 1.2 | Legs heavier, rotation harder |
| Semi-final | × 1.5 | Virtually no rotation, fatigue at peak |

`fatigueElo = (cumulativeWeightedTotal / 100) × −12`

Capped at 0 — an easy group/bracket is not a rest bonus; it simply means no accumulated fatigue burden. If a team faces the same opponent in the group and again in the knockouts, both entries count because they are separate matches.

UI notes split the source of fatigue as `G:` for group load and `KO:` for knockout path, for example `G: Brazil -3, Morocco -3` and `KO: Brazil -5`. The tournament route breadcrumb stays route-only, for example `C3 › Brazil › France`.

**Bench-depth interaction:** `squad_depth` also changes how strongly cumulative fatigue hits a team. Excellent benches reduce negative fatigue by 30%, good/deep benches reduce it by 15%, limited benches amplify it by 15%, and thin benches amplify it by 30%. Easy paths still cap at 0, so bench depth never creates a rest bonus.

| Difficulty label | Cumulative weighted total |
|---|---|
| Very Easy | < −200 |
| Easy | −200 to −60 |
| Medium | −60 to +60 |
| Hard | +60 to +200 |
| Very Hard | > +200 |

All values configurable under `path.fatigue.*` in `application.properties`.


### Opposing Signals

Some signals intentionally push against each other because they describe different facts. That is useful as long as the same evidence is not counted twice.

| Signal | Can be offset by | Example |
|---|---|---|
| `attack_quality` / `defence_quality` | `squad_dropouts`, `injury_impact` | Goal quality describes the selected squad's profile; omissions and injuries describe availability. |
| `attack_quality` / `defence_quality` | `squad_depth` | Starting quality and replacement depth are separate facts. |
| `host` | `injury_impact`, `squad_dropouts`, `squad_cohesion` | Home advantage can be partly cancelled by missing players or unstable preparation. |
| `heat_impact` | `squad_age_profile`, `squad_depth`, path fatigue | Heat adaptation helps, but old/thin/tired squads can still fade. |
| `qual_bonus` / `pre_tournament_bonus` | base ELO | Recent observed results can push against long-run strength. |
| `squad_cohesion` | Attack/Defence | A talented squad can underperform under tactical or locker-room disruption. |
| `squad_depth` | path fatigue | Deep benches soften accumulated fatigue; limited/thin benches amplify it. |

Availability rule: not selected belongs in `squad_dropouts`; selected but limited, doubtful, recently returned, or managed belongs in `injury_impact`.

Heat is currently a static ELO overlay. It does not directly multiply path fatigue because doing so safely requires venue and date conditions.

### Double-counting notes

Base ELO already reflects historical team strength, including prior qualifying matches, friendlies, coaching stability, and squad effects once they appear in results.

- **Low double-count risk:** home advantage, active-squad injuries, current squad omissions, venue heat, and knockout path fatigue.
- **Qualification form:** excluded from Adjusted ELO and the adjustment breakdown to avoid double counting.
- **Pre-tournament form:** the last three pre-tournament friendlies remain a small current-form overlay because they may post-date the frozen base ELO.
- **Subjective profile risk:** Attack and Defence can remain alongside form when they come from separate current-squad evidence such as personnel, tactical role, chance creation, finishing, or defensive structure. Do not set them merely because the same recent results were good or bad.
- Attack/Defence are excluded from Adjusted ELO. Their xG effect already changes scorelines, win/draw probabilities, advancement probabilities, Monte Carlo outcomes, and betting comparisons.

### Qualification form formula

`formScore = 0.6 x PPG + 0.2 x goalsForNorm + 0.2 x goalsAgainstNorm`

Where PPG = `(3 x wins + draws) / (3 x played)`. A score above 0.5 gives a positive ELO bonus; below 0.5 gives a penalty. Only configured qualifying matches are used. Hosts with no qualifying matches receive no bonus or penalty.

## Docs

| Document | Description |
|---|---|
| [Modes](docs/MODES.md) | Browser UI walkthrough and pipeline mode reference |
| [Configuration](docs/CONFIGURATION.md) | All configurable prediction parameters |
| [CSV Format](docs/CSV_FORMAT.md) | Matchup file columns and locking behaviour |


# Tournament Predictor

A tournament bracket predictor for WC2026 using World Football Elo ratings, tournament-specific squad adjustments, bracket-path generation, manual overrides, and knockout path fatigue.

## Contents

- [About](#about)
- [Quick start](#quick-start)
- [Prediction methodology](#prediction-methodology)
- [Docs](#docs)

## About

The browser UI is the primary way to use the predictor. It walks you through the full tournament from group stage to final — configuring teams, reviewing predictions, overriding picks, and exploring every possible bracket path.

**What it models:**
- Group stage rankings using ELO + home advantage + qualification form + friendly form + injuries + heat + squad age/cohesion/depth/quality/omissions
- Every possible knockout matchup given your group picks
- Game predictions using adjusted ELO, knockout path fatigue, and optional manual overrides
- Bracket paths showing every route a team could take to the final
- **Tournament path fatigue** — how depleted a team is based on the quality of opponents already beaten en route to each match, backed by sports science research

**What it does not do:**
- Full tournament win probabilities (e.g. "France has a 23% chance of winning"). That would require a Monte Carlo simulator running millions of full bracket simulations.

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
- `metadata.properties` records the form-year ranges and source files used.

Each tournament can define form windows in `data/predictions/<tournament>/tournament.properties`. `snapshot-refresh` reads those values and freezes them into snapshot metadata. After that, `start` and the UI ELO breakdowns read from the tournament snapshot and its metadata; they do not fall back to the refreshable current ELO files. This keeps old tournaments reproducible even if `eloratings.net` changes, goes offline, or global ELO is refreshed for a future tournament.

## Prediction methodology

### Knockout round predictions

Current game prediction is ELO-first. Each matchup uses the standard Elo expected-score formula with a 400-point divisor:

`team1WinPct = 1 / (1 + 10^((team2Elo - team1Elo) / 400))`

The team ELO used in knockouts is the tournament-adjusted ELO from `groups.csv`, generated from the tournament snapshot when present, not the live global `world.csv` value. From last 16 onward, each team can also receive a live knockout path-fatigue penalty before the ELO formula is applied.

The plus/minus values in this project are **temporary pre-match Elo deltas**, not permanent World Football Elo rating updates. World Football Elo uses the same 400-point expectation scale and a 100-point home adjustment, while post-match rating movement is controlled by match importance, result, and goal difference. This app uses Elo-point-sized overlays so every signal translates consistently into win probability before a tournament match is predicted.

`do_you_disagree=yes` manually flips the predicted winner for a matchup.

### Model accuracy and calibration

This is an explainable tournament model, not a fully calibrated market-grade forecasting system yet. It should be useful for ranking teams, exposing why a route is hard, comparing close matchups, and stress-testing bracket assumptions. It should not be treated as a proven edge over bookmaker odds or professional forecasting models.

Compared with stronger public World Cup predictors, this project has three clear gaps:

- It predicts each matchup directly from adjusted Elo, but does not yet convert team strength into expected goals with a Poisson or Dixon-Coles score model.
- It does not yet run Monte Carlo simulations over the full bracket, so it cannot produce calibrated title, final, semi-final, or group-qualification probabilities.
- It is not yet backtested against past tournaments or held-out international matches, so the custom signal weights are reasoned and configurable rather than statistically fitted.

The highest-value accuracy upgrades would be:

1. Backtest every signal against past World Cups and recent international matches, then tune weights against log loss or Brier score instead of intuition.
2. Add a Poisson or Dixon-Coles layer so Elo difference predicts goal distributions, draws, extra time, and penalties more realistically.
3. Add Monte Carlo tournament simulation for group and knockout paths, including uncertainty around group positions and third-place qualification.
4. Add external cross-checks such as market odds, xG/xGA, squad market value, rest days, travel distance, and venue/weather by match.
5. Track prediction calibration in generated outputs so a 70% model pick actually wins about 70% of comparable historical games.


### Group stage adjustments

Applied once at the group stage to calculate each team's effective ELO ranking. Carried forward into all knockout round predictions.

| Signal | Max impact | Notes |
|---|---|---|
| **Home advantage** | +100 ELO | Applied to host nations. This matches the World Football Elo convention of adding 100 points for the home team when projecting a match. |
| **Qualification form** | ±100 ELO | Based on 2023-2026 qualifying results in ELO history files. This is intentionally a current-form overlay, but it can partly double-count recent competitive results already present in base ELO. |
| **Friendly form** | ±50 ELO | Based on recent pre-tournament friendlies. Also partly overlaps with base ELO because friendlies are already in the source history, but with lower match weight. |
| **Injuries** | -22 / -45 / -90 ELO | Current-availability penalty. Critical injury is deliberately just below home advantage: severe enough to nearly cancel a host boost, but not stronger than playing the tournament at home. |
| **Heat advantage** | +9 / +18 / +35 ELO | Venue/climate adaptation. Kept much smaller than home advantage because it is situational and less universal. |
| **Squad omissions** | -18 / -35 / -70 ELO | Players missing from the selected squad for injury, coach choice, discipline, retirement, refusal, or federation dispute. Active-squad fitness belongs under Injuries. |
| **Squad age** | -12 young / -8 aging ELO | Small negative-only adjustment for inexperience or recovery risk. |
| **Squad cohesion** | -11 / -22 / -45 ELO | Negative-only disruption signal. Some risk may already be reflected in recent ELO results. |
| **Bench depth** | -10 / -20 ELO | Static bench-depth penalty for limited or thin squads. Also controls fatigue sensitivity in knockouts. |
| **Squad quality** | +10 / +20 ELO | Small forward-looking talent bonus. Kept small because base ELO already captures most team quality. |

All values are configurable in `application.properties` — see [Configuration](docs/CONFIGURATION.md).

### Knockout stage: tournament path fatigue

Applied at each knockout round (last 32 through final) as a **live ELO adjustment** on top of the team's base ranking. The first knockout match is seeded with **group load**: the strength of above-average opponents already faced in the group. This is entirely separate from base ELO — it does not double-count any existing signal.

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

**Bench-depth interaction:** `squad_depth` also changes how strongly cumulative fatigue hits a team. Good/deep benches reduce negative fatigue by 15%, limited benches amplify it by 15%, and thin benches amplify it by 30%. Easy paths still cap at 0, so bench depth never creates a rest bonus.

| Difficulty label | Cumulative weighted total |
|---|---|
| Very Easy | < −200 |
| Easy | −200 to −60 |
| Medium | −60 to +60 |
| Hard | +60 to +200 |
| Very Hard | > +200 |

All values configurable under `path.fatigue.*` in `application.properties`.


### Opposing Signals

Some signals are intentionally allowed to push against each other because they describe different facts. That is useful, not a bug, as long as the same player/event is not counted twice.

| Signal | Can be offset by | Example |
|---|---|---|
| `squad_quality` | `squad_dropouts` / Squad Omissions | Brazil can still have elite selected-squad talent while losing Rodrygo, Estevao, and Eder Militao from the squad. |
| `squad_quality` | `injury_impact` | A deep squad can absorb knocks better, but an active-squad star carrying an injury still reduces reliability. |
| `squad_quality` | `squad_depth` | A brilliant starting XI can still have a limited bench; quality is ceiling, depth is replacement floor. |
| `host` | `injury_impact`, `squad_dropouts`, `squad_cohesion` | Home advantage can be partly cancelled by missing players or unstable preparation. |
| `heat_impact` | `squad_age_profile`, `squad_depth`, path fatigue | Heat adaptation helps, but old/thin squads and brutal knockout paths can still create fatigue risk. |
| `qual_bonus` / `pre_tournament_bonus` | base Elo | Recent form can push against long-run Elo when a team is improving or declining. |
| `squad_cohesion` | `squad_quality` | Talent can be dragged down by a new coach, selection row, or tactical reset. |
| `squad_depth` | path fatigue | This is directly modeled: deep benches soften accumulated fatigue; limited/thin benches amplify it. |

Availability rule of thumb: if a player is **not in the selected squad**, put the effect in `squad_dropouts`/Squad Omissions even when the reason is injury. If a player is **in the selected squad but limited, doubtful, recently returned, or managed**, put it in `injury_impact`.

Heat is currently a static group-stage overlay carried forward through adjusted Elo. It does **not** directly multiply knockout path fatigue yet; doing that safely probably needs venue/date conditions so heat is not double-counted for every knockout match.

### Double-counting notes

`world.csv` already reflects historical team strength, including prior qualifying matches, friendlies, squad quality, coaching stability, and some injury effects once those effects have shown up in results. The extra adjustments are therefore not all independent signals.

The safest interpretation is:
- **Low double-count risk:** home advantage, active-squad injuries, current squad omissions, venue heat, and knockout path fatigue. These are match/tournament-context adjustments not fully present in a neutral pre-tournament ELO rating.
- **Medium double-count risk:** qualification form and friendly form. These use recent results that are already part of ELO, but emphasize current-cycle form more heavily than the long-run rating.
- **Higher double-count risk:** squad quality, cohesion, age, and static bench depth. These are partly reflected in recent results, so their values are intentionally modest and should be used only for clear current-cycle information not already obvious in ELO.

### Qualification form formula

`formScore = 0.6 × PPG + 0.2 × goalsForNorm + 0.2 × goalsAgainstNorm`

Where PPG = `(3 × wins + draws) / (3 × played)`, goals for/against are normalised per game against a max of 3. A score above 0.5 gives a positive ELO bonus; below 0.5 gives a penalty. Only WC qualifying matches (WQ, WQS, FQ) from the configured year range (default 2023–2026) are used. Hosts with no qualifying matches receive no bonus or penalty.

## Docs

| Document | Description |
|---|---|
| [Modes](docs/MODES.md) | Browser UI walkthrough and pipeline mode reference |
| [Configuration](docs/CONFIGURATION.md) | All configurable prediction parameters |
| [CSV Format](docs/CSV_FORMAT.md) | Matchup file columns and locking behaviour |


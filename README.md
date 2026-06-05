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
- Group stage rankings using ELO + home advantage + qualification form + friendly form + injuries + heat + squad age/cohesion/depth/quality/dropouts
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

# Refresh ELO ratings from latest data
./predict.sh --mode=elo-refresh
```

See [Modes](docs/MODES.md) for the full pipeline reference.

## Prediction methodology

### Knockout round predictions

Current game prediction is ELO-first. Each matchup uses the standard Elo expected-score formula with a 400-point divisor:

`team1WinPct = 1 / (1 + 10^((team2Elo - team1Elo) / 400))`

The team ELO used in knockouts is the tournament-adjusted ELO from `groups.csv`, not the raw `world.csv` value. From last 16 onward, each team can also receive a live knockout path-fatigue penalty before the ELO formula is applied.

The plus/minus values in this project are **temporary pre-match Elo deltas**, not permanent World Football Elo rating updates. World Football Elo uses the same 400-point expectation scale and a 100-point home adjustment, while post-match rating movement is controlled by match importance, result, and goal difference. This app uses Elo-point-sized overlays so every signal translates consistently into win probability before a tournament match is predicted.

`do_you_disagree=yes` manually flips the predicted winner for a matchup.


### Group stage adjustments

Applied once at the group stage to calculate each team's effective ELO ranking. Carried forward into all knockout round predictions.

| Signal | Max impact | Notes |
|---|---|---|
| **Home advantage** | +100 ELO | Applied to host nations. This matches the World Football Elo convention of adding 100 points for the home team when projecting a match. |
| **Qualification form** | ±100 ELO | Based on 2023-2026 qualifying results in ELO history files. This is intentionally a current-form overlay, but it can partly double-count recent competitive results already present in base ELO. |
| **Friendly form** | ±50 ELO | Based on recent pre-tournament friendlies. Also partly overlaps with base ELO because friendlies are already in the source history, but with lower match weight. |
| **Injuries** | -22 / -45 / -90 ELO | Current-availability penalty. Critical injury is deliberately just below home advantage: severe enough to nearly cancel a host boost, but not stronger than playing the tournament at home. |
| **Heat advantage** | +9 / +18 / +35 ELO | Venue/climate adaptation. Kept much smaller than home advantage because it is situational and less universal. |
| **Squad dropouts** | -18 / -35 / -70 ELO | Planned absences or omissions. Lower than injury penalties because coaches have more time to adapt. |
| **Squad age** | -12 young / -8 aging ELO | Small negative-only adjustment for inexperience or recovery risk. |
| **Squad cohesion** | -11 / -22 / -45 ELO | Negative-only disruption signal. Some risk may already be reflected in recent ELO results. |
| **Bench depth** | -10 / -20 ELO | Static bench-depth penalty for limited or thin squads. Also controls fatigue sensitivity in knockouts. |
| **Squad quality** | +10 / +20 ELO | Small forward-looking talent bonus. Kept small because base ELO already captures most team quality. |

All values are configurable in `application.properties` — see [Configuration](docs/CONFIGURATION.md).

### Knockout stage: tournament path fatigue

Applied at each knockout round (last 32 through final) as a **live ELO adjustment** on top of the team's base ranking. This is entirely separate from base ELO — it does not double-count any existing signal.

**Why it matters:** sports science research shows teams that face tougher opponents in earlier knockout rounds underperform expectations in later rounds relative to their base ELO, even after controlling for raw strength. The primary driver is **accumulated fatigue** — physical depletion from high-intensity matches against strong opposition compounds across rounds.

**How it works:** each prior knockout opponent's ELO is compared to the tournament average (1850). Beating a stronger-than-average team adds more fatigue weight; beating a weaker team adds less. Each stage is weighted by round importance, and the cumulative total is converted to an ELO penalty.

| Stage beaten | Weight | Rationale |
|---|---|---|
| Last 32 | × 0.5 | Early round — squad still fresh, bench minutes available |
| Last 16 | × 1.0 | Baseline — first team heavily involved |
| Quarter-final | × 1.2 | Legs heavier, rotation harder |
| Semi-final | × 1.5 | Virtually no rotation, fatigue at peak |

`fatigueElo = (cumulativeWeightedTotal / 100) × −12`

Capped at 0 — an easy bracket is not a rest bonus; it simply means no accumulated fatigue burden.

**Bench-depth interaction:** `squad_depth` also changes how strongly cumulative fatigue hits a team. Good/deep benches reduce negative fatigue by 15%, limited benches amplify it by 15%, and thin benches amplify it by 30%. Easy paths still cap at 0, so bench depth never creates a rest bonus.

| Difficulty label | Cumulative weighted total |
|---|---|
| Very Easy | < −200 |
| Easy | −200 to −60 |
| Medium | −60 to +60 |
| Hard | +60 to +200 |
| Very Hard | > +200 |

All values configurable under `path.fatigue.*` in `application.properties`.

### Double-counting notes

`world.csv` already reflects historical team strength, including prior qualifying matches, friendlies, squad quality, coaching stability, and some injury effects once those effects have shown up in results. The extra adjustments are therefore not all independent signals.

The safest interpretation is:
- **Low double-count risk:** home advantage, current injuries, current dropouts, venue heat, and knockout path fatigue. These are match/tournament-context adjustments not fully present in a neutral pre-tournament ELO rating.
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


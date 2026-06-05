# Tournament Predictor

A tournament bracket predictor for WC2026 using ELO ratings, head-to-head history, qualification form, friendly form, and real-world squad adjustments (home advantage, injuries, heat, squad strength, fatigue).

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
- Combined predictions using ELO, competitive head-to-head, and friendly history
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

> **Requires Java 17+.** Check with `java -version`.

You will also need the `data/` folder from the repository. Download and extract the latest `data.zip` from the [releases page](https://github.com/freid001/tournament-predictor/releases).

### Build from source

```bash
git clone https://github.com/freid001/tournament-predictor.git
cd tournament-predictor
mvn -q package
./predict.sh --browser --tournament=world_cup_2026
```

> **Requires Java 17+ and Maven 3.8+.**

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

Each matchup combines three signals:

| Signal | Weight | Source |
|---|---|---|
| ELO rating | 70% | `data/elo/world.csv` (overridden by `groups.csv` for tournament teams) |
| Competitive head-to-head | 21% | Historical competitive match results |
| Friendly head-to-head | 9% | Historical friendly match results |

Combined formula: `finalPct = 0.7 × elo + 0.3 × (0.7 × competition + 0.3 × friendly)`

The ELO rating used per team is their **adjusted ELO** — base rating plus all applicable signals below.

### Group stage adjustments

Applied once at the group stage to calculate each team's effective ELO ranking. Carried forward into all knockout round predictions.

| Signal | Max impact | Notes |
|---|---|---|
| **Home Advantage** | +100 ELO | Applied to host nations. Matches eloratings.net standard. |
| **Qualification Form** | ±50 ELO | Based on 2023–2026 WC qualifying results (PPG + goals scored/conceded). |
| **Friendly Form** | ±25 ELO | Based on last 5 pre-tournament friendlies. |
| **Injuries** | −75 ELO | Scaled by impact level: minor −20, significant −40, critical −75. |
| **Heat Advantage** | +25 ELO | Team acclimatised to heat at their venue. mild +8, moderate +15, strong +25. |
| **Squad Age** | −ELO | Penalty for very young or very old squad profiles. |
| **Squad Cohesion** | −ELO | Penalty for unsettled squads (manager changes, dressing room issues). |
| **Squad Depth** | −ELO | Penalty for thin squads with limited rotation options. |
| **Squad Quality** | +ELO | Bonus for squads with exceptional individual quality above their ELO. |
| **Squad Dropouts** | −ELO | Penalty for key players withdrawing pre-tournament. |

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

**Squad depth interaction:** thin squads cannot rotate so path fatigue hits them harder. A depth multiplier is applied only to negative fatigue values (no effect on easy paths):

| Depth level | Multiplier | When to use |
|---|---|---|
| Normal | × 1.00 | Default |
| Limited | × 1.15 | Bench quality drops sharply after 13–14 players |
| Thin | × 1.30 | Minnow-level depth — tiny pools, first World Cup nations |

This is separate from the static squad depth ELO penalty (which always applies regardless of path). The multiplier only activates when the team has a genuinely hard path.

| Difficulty label | Cumulative weighted total |
|---|---|
| Very Easy | < −200 |
| Easy | −200 to −60 |
| Medium | −60 to +60 |
| Hard | +60 to +200 |
| Very Hard | > +200 |

All values configurable under `path.fatigue.*` in `application.properties`.

### Qualification form formula

`formScore = 0.6 × PPG + 0.2 × goalsForNorm + 0.2 × goalsAgainstNorm`

Where PPG = `(3 × wins + draws) / (3 × played)`, goals for/against are normalised per game against a max of 3. A score above 0.5 gives a positive ELO bonus; below 0.5 gives a penalty. Only WC qualifying matches (WQ, WQS, FQ) from the configured year range (default 2023–2026) are used. Hosts with no qualifying matches receive no bonus or penalty.

## Docs

| Document | Description |
|---|---|
| [Modes](docs/MODES.md) | Browser UI walkthrough and pipeline mode reference |
| [Configuration](docs/CONFIGURATION.md) | All configurable prediction parameters |
| [CSV Format](docs/CSV_FORMAT.md) | Matchup file columns and locking behaviour |


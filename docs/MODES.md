# Modes

The browser workflow requires group review followed by one Run Tournament action. The lower-level CLI modes below remain available for development and running individual stages.

## Available modes

| Mode | Description | Output |
|---|---|---|
| `elo-refresh` | Refresh global ELO ratings and all team match histories (no `--tournament` needed) | `data/elo/current/world.csv`, `data/elo/current/history/` |
| `snapshot-refresh` | Save the pre-tournament team ratings and recent results used by one tournament | `data/elo/snapshots/<t>/` |
| `start` | Generate group rankings from `start.csv` (applies home advantage and injury adjustments) | `data/predictions/<t>/groups.csv` |
| `groups` | Generate last_32 matchup permutations from group predictions | `data/simulations/<t>/matchup_paths_last_32.csv` |
| `last_32` | Score last_32 predictions, generate last_16 matchups | `data/simulations/<t>/matchup_paths_last_16.csv` |
| `last_16` | Score last_16 predictions, generate last_8 matchups | `data/simulations/<t>/matchup_paths_last_8.csv` |
| `last_8` | Score last_8 predictions, generate last_4 matchups | `data/simulations/<t>/matchup_paths_last_4.csv` |
| `last_4` | Score last_4 predictions, generate final matchup | `data/simulations/<t>/matchup_paths_final.csv` |
| `final` | Score final predictions | — |

> Each knockout mode writes the model-selected starting field for the next round. Alternate matchup rows remain in matchup outputs for analysis but are not copied into the next prediction file. The separate group-to-final simulation still propagates sampled upset winners through every round.

## Bracket walkthrough

**Step 1** — Refresh global ELO ratings (run once, or whenever source ratings need updating):
```bash
./predict.sh --mode=elo-refresh
```

**Step 2** — Fill in `data/predictions/world_cup_2026/start.csv` with the group teams, setting `host=yes` for the host nation and `injury_impact=0–3` for each team.

**Step 3** — Freeze the tournament snapshot. This uses `data/predictions/world_cup_2026/tournament.properties` for the required tournament start-date cutoff and form windows, then copies only the selected tournament teams and recent form-window history. Future global ELO refreshes do not change this tournament unless you refresh the snapshot again:
```bash
./predict.sh --tournament=world_cup_2026 --mode=snapshot-refresh
```

**Step 4** — Generate group rankings:
```bash
./predict.sh --tournament=world_cup_2026 --mode=start
```

**Step 5** — Fill in `data/predictions/world_cup_2026/groups.csv` with your group stage picks (group winners, runners-up, 3rd-place qualifiers).

**Step 6** — Generate all possible last_32 matchup permutations from your group picks:
```bash
./predict.sh --tournament=world_cup_2026 --mode=groups
```

**CLI only:** run each knockout stage in order. These round files form the model-selected bracket and their simulations are conditional on the starting round. In the browser, the single Run Tournament action performs this whole sequence. The model-selected path advances automatically; use alternate matchup rows to inspect other routes.
```bash
./predict.sh --tournament=world_cup_2026 --mode=last_32
./predict.sh --tournament=world_cup_2026 --mode=last_16
./predict.sh --tournament=world_cup_2026 --mode=last_8
./predict.sh --tournament=world_cup_2026 --mode=last_4
./predict.sh --tournament=world_cup_2026 --mode=final
```

## Locking

Once a mode writes its output file it is **locked** — re-running the same mode will print a warning and show the existing output rather than overwriting it. Delete the output file to re-run a mode.

## Training bootstrap and calibration

```bash
./predict.sh --mode=training
```

Downloads pinned tournament results and squad datasets, derives historical age and pre-kickoff goal profiles, applies sparse documented context overrides, generates ignored snapshots, then runs the ELO/xG, warm-up, core, and contextual calibration grids. The generated historical data and reports under `data/backtests/`, `data/elo/snapshots/`, and `data/predictions/` are disposable and can be rebuilt with this command.

## Calibration grid

```bash
./predict.sh --mode=training-grid
```

Runs only the ELO goal-separation and total-xG grid against already generated historical data. Run `training` first after cloning or deleting the ignored training workspace.

## Warm-up calibration grid

```bash
./predict.sh --mode=training-warmup-grid
```

Tests warm-up friendly ELO caps from `0` through `200` ELO against already generated historical data. Reports include Brier score, log loss, and predicted-versus-actual upset rates.

## Core calibration grid

```bash
./predict.sh --mode=training-core-grid
```

Evaluates host advantage and attack/defence xG steps independently, then runs a recent-weighted joint grid over ELO goal separation, total xG, host advantage, and attack/defence quality. It requires the generated training workspace. See [Training Results](TRAINING_RESULTS.md) for decisions and limitations.

## Context calibration grid

```bash
./predict.sh --mode=training-context-grid
```

Tests production-weight multipliers for squad age, cohesion, depth, omissions, active injuries, and heat. It reports signal coverage and leave-one-tournament-out results; run `training` first to generate the historical profiles.

# Modes

Run modes in order to build a full bracket prediction from the group stage to the final.

## Available modes

| Mode | Description | Output |
|---|---|---|
| `elo-refresh` | Refresh ELO ratings and all team match histories (no `--tournament` needed) | `data/elo/world.csv`, `data/elo/history/` |
| `start` | Generate group rankings from `start.csv` (applies home advantage and injury adjustments) | `data/predictions/<t>/groups.csv` |
| `groups` | Generate last_32 matchup permutations from group predictions | `data/matchups/<t>/last_32.csv` |
| `last_32` | Score last_32 predictions, generate last_16 matchups | `data/matchups/<t>/last_16.csv` |
| `last_16` | Score last_16 predictions, generate last_8 matchups | `data/matchups/<t>/last_8.csv` |
| `last_8` | Score last_8 predictions, generate last_4 matchups | `data/matchups/<t>/last_4.csv` |
| `last_4` | Score last_4 predictions, generate final matchup | `data/matchups/<t>/final.csv` |
| `final` | Score final predictions | — |

> Each mode also writes a predictions file to `data/predictions/<t>/<round>.csv` for you to fill in.

## Bracket walkthrough

**Step 1** — Refresh ELO ratings (run once, or whenever ratings need updating):
```bash
./predict.sh --mode=elo-refresh
```

**Step 2** — Fill in `data/predictions/world_cup_2026/start.csv` with the group teams, setting `host=yes` for the host nation and `injury_impact=0–3` for each team.

**Step 3** — Generate group rankings:
```bash
./predict.sh --tournament=world_cup_2026 --mode=start
```

**Step 4** — Fill in `data/predictions/world_cup_2026/groups.csv` with your group stage picks (group winners, runners-up, 3rd-place qualifiers).

**Step 5** — Generate all possible last_32 matchup permutations from your group picks:
```bash
./predict.sh --tournament=world_cup_2026 --mode=groups
```

**Repeat for each round** — open `data/predictions/world_cup_2026/<round>.csv`, review the predicted winner, set `do_you_disagree=yes` to override if needed, then run:
```bash
./predict.sh --tournament=world_cup_2026 --mode=last_32
./predict.sh --tournament=world_cup_2026 --mode=last_16
./predict.sh --tournament=world_cup_2026 --mode=last_8
./predict.sh --tournament=world_cup_2026 --mode=last_4
./predict.sh --tournament=world_cup_2026 --mode=final
```

## Locking

Once a mode writes its output file it is **locked** — re-running the same mode will print a warning and show the existing output rather than overwriting it. Delete the output file to re-run a mode.

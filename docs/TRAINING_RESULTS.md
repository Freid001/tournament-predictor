# Training and Calibration Results

Last updated: 7 June 2026.

## Status

Core match calibration is complete for the currently available historical data. Production settings remain conservative because aggregate winners did not improve the newest independent holdouts consistently.

This does not mean the model is perfectly accurate or that calibration never needs to be rerun. Rerun the grids when tournaments, model inputs, or scoring logic change.

## Dataset

Deterministic match calibration covers 612 group matches from 17 tournaments:

- FIFA World Cups: 1994, 1998, 2002, 2006, 2010, 2014, 2018, 2022
- UEFA EUROs: 1992, 1996, 2000, 2004, 2008, 2012, 2016, 2020, 2024

Every tournament uses a frozen pre-kickoff ELO/history snapshot. Snapshot history rows on or after tournament kickoff are excluded.

Training data is generated locally by Java and ignored by Git. World Cup squad ages come from the pinned Fjelstul World Cup Database; EURO ages use unique players listed on tournament match sheets in the pinned Petro Ivaniuk EURO dataset. Attack and defence levels are derived from residual goals versus the production ELO/xG expectation over each team's last 4-10 competitive matches before kickoff. Heat is a documented regional adaptation proxy for hot-weather World Cups. Injuries, omissions, and cohesion use sparse pre-tournament overrides for unusually material cases.

These are reconstructed proxies, not perfect measurements. Final-tournament results are never used to assign a level. Squad depth remains neutral because the available sources do not measure replacement quality consistently.

## Validation Method

Parameter selection uses leave-one-tournament-out validation:

1. Exclude one tournament.
2. Select the best parameters using the other 16 tournaments.
3. Score the selected parameters on the excluded tournament.
4. Repeat for all 17 tournaments.

Primary metrics are multiclass Brier score and log loss. Lower is better. Goals and upset-rate calibration are included in the objective as secondary checks.

The joint core grid also uses recent-tournament weights:

- 2020 and newer: `2.0`
- 2010 through 2018: `1.0`
- Before 2010: `0.5`

This prevents 1990s and early-2000s football from dominating settings intended for World Cup 2026.

## Production Decision

Current production settings remain:

| Parameter | Production |
|---|---:|
| ELO scale divisor | 400 |
| Goal difference per 400 ELO | 1.20 |
| Total xG multiplier | 0.93 |
| Host advantage | +100 ELO |
| Warm-up friendly cap | +/-50 ELO |
| Attack/defence quality step | 0.05 xG per level |

No setting was changed merely because it won the combined sample. A candidate also needed stable holdout improvement and acceptable results on World Cup 2022 and EURO 2024.

## Results

### ELO and total xG

With reconstructed context enabled, the unweighted 612-match aggregate preferred `1.80 / 0.93`. Leave-one-tournament-out selections beat the production baseline on 12/17 Brier and 11/17 log-loss holdouts, but the selected ELO separation remains much stronger than the current model and is not yet stable enough for a production change. The recent-weighted joint grid preferred `1.60 / 0.95 / 75 / 0.00`; it beat the baseline on 11/17 Brier and 12/17 log-loss holdouts but slightly worsened both newest tournaments.

Decision: retain `1.20 / 0.93`.

Reports:

- `data/backtests/training_grid.csv`
- `data/backtests/training_grid_holdouts.csv`
- `data/backtests/training_joint_grid.csv`
- `data/backtests/training_joint_grid_holdouts.csv`

### Warm-up friendlies

Caps from `0` through `200` ELO were tested. The aggregate objective had a broad optimum around `120-140`, with `140` ranked first. Selected higher caps beat the current 50-ELO cap on only 9/17 Brier and 9/17 log-loss holdouts.

Decision: retain the conservative `+/-50` cap.

Reports:

- `data/backtests/training_warmup_grid.csv`
- `data/backtests/training_warmup_grid_holdouts.csv`

### Host advantage

Values from `0` through `250` ELO were tested. The combined sample preferred `225` and holdout selections beat the production baseline on 11/17 Brier and log-loss holdouts, but hosting formats and eras are not sufficiently comparable to treat that as a universal causal estimate.

This instability reflects different hosting formats and eras: single hosts, co-hosts, EURO 2020's distributed venues, and varying home conditions are not equivalent.

Decision: retain `+100 ELO` as a conservative universal setting.

Reports:

- `data/backtests/training_home_grid.csv`
- `data/backtests/training_home_grid_holdouts.csv`

### Attack and defence quality

Steps from `0.00` through `0.25` xG per level were tested over 605 matches containing at least one non-neutral pre-tournament attack or defence profile. A `0.05` step was selected in 15/17 leave-one-tournament-out runs and improved both World Cup 2022 and EURO 2024 versus the former `0.15` baseline.

Decision: change production from `0.15` to `0.05 xG` per level. After the change, rerunning the grid selects the production baseline in 15/17 folds; alternatives no longer beat it.

Reports:

- `data/backtests/training_quality_grid.csv`
- `data/backtests/training_quality_grid_holdouts.csv`

## Upsets

The model cannot identify a future upset with certainty. It can estimate an underdog probability and be calibrated over many comparable matches.

A useful upset forecast means, for example, that underdogs assigned approximately 25% should win approximately one quarter of comparable matches. The grids therefore report predicted and actual upset rates while keeping favourite identity fixed to the production baseline when comparing parameter candidates.

The current training sample suggests stronger separation and larger warm-up caps can reduce an historical tendency to overpredict underdog wins. Those changes were not adopted because improvements were inconsistent on recent holdouts.

## Contextual Inputs

Each contextual ELO adjustment was tested at `0`, `0.5`, `1.0`, `1.5`, and `2.0` times its production weight.

| Signal | Matches | Aggregate best | Holdout conclusion |
|---|---:|---:|---|
| Squad age | 122 | `1.5x` | Selections ranged across the full grid; retain `1.0x`. |
| Cohesion | 9 | `0x` | Too sparse; retain conservative production values. |
| Squad depth | 0 | tie | No defensible historical depth data; uncalibrated. |
| Squad omissions | 29 | `0x` | Sparse curated cases do not justify removing the signal. |
| Active injuries | 9 | `2x` | Too sparse to increase severe penalties. |
| Heat adaptation | 125 | `2x` | Only 1/17 holdouts improved over `1.0x`; retain production values. |

Reports:

- `data/backtests/training_context_grid.csv`
- `data/backtests/training_context_grid_holdouts.csv`

Knockout path fatigue remains uncalibrated because this dataset scores group matches only. Depth also remains uncalibrated until historical player-quality or market-value data can measure replacement strength without using tournament outcomes.

## Commands

```bash
mvn -q package
./predict.sh --mode=training
```

`training` downloads pinned source files, generates the ignored historical workspace, and runs all deterministic grids. The individual grid modes remain available when the generated workspace already exists.

Generated files under `data/backtests/`, historical `data/elo/snapshots/`, and historical `data/predictions/` are not repository source. Delete them whenever desired; the next `training` run recreates them.

## Completion Criteria

Core calibration is considered complete when:

- Tested optima are not unexplored grid boundaries.
- Settings are evaluated with leave-one-tournament-out validation.
- Brier score and log loss are considered together.
- Recent tournaments receive explicit attention.
- Upset and goal-rate calibration are checked.
- Production values are changed only when improvements are stable rather than aggregate-only.

These criteria are now satisfied for evaluating ELO/xG separation, total xG, warm-up form, host advantage, attack/defence quality, squad age, and the current heat proxy. Only the attack/defence step met the standard for a production change. Injuries, omissions, cohesion, and especially depth remain too sparse for firm calibration; knockout path fatigue is outside the group-match dataset.

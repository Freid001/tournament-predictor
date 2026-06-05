# Configuration

All prediction parameters live in `src/main/resources/application.properties`. The defaults are sensible — you only need to change these if you want to experiment with the model.

Win% impact shown for a 50/50 match (equal ELO teams). Formula: `1 / (1 + 10^(−delta/400)) − 50%`.

## Prediction weights

| Property | Default | Description |
|---|---|---|
| `prediction.elo.weight` | `0.70` | How much ELO counts vs head-to-head history. `0.70` = 70% ELO, 30% history. |
| `prediction.history.competition.weight` | `0.70` | Within the 30% history slice: competitive matches get 70%, friendlies get 30%. Full split: **70% ELO + 21% competitive history + 9% friendly history**. |
| `elo.scale.divisor` | `400.0` | Standard chess/FIFA ELO divisor — not recommended to change. Lower = exaggerates gaps; higher = more coin-flip outcomes. |

## Head-to-head decay

| Property | Default | Description |
|---|---|---|
| `h2h.decay.half.life.years` | `5.0` | How quickly old results lose relevance. A result from exactly this many years ago counts for 50% of a recent result. |

## Qualification form

| Property | Default | Description |
|---|---|---|
| `qual.form.since.year` | `2023` | First year of qualifying matches to include. |
| `qual.form.until.year` | `2026` | Last year of qualifying matches to include. |
| `qual.form.elo.max` | `100` | Max ELO bonus/penalty from qualification form (±100 ELO, **+14.0%**). Calibrated so perfect qual form exactly cancels home advantage — in practice no team scores 1.0, so the theoretical max is unreachable. |
| `pre.tournament.form.elo.max` | `50` | Max ELO bonus/penalty from pre-tournament friendlies (±50 ELO, **+7.0%**). Set at 50% of qual form — mirrors eloratings.net K-factor ratio (qualifiers K=40, friendlies K=20). |
| `pre.tournament.form.since.year` | `2026` | First year of pre-tournament friendlies to include. |
| `pre.tournament.form.until.year` | `2026` | Last year of pre-tournament friendlies to include. |

## Group stage adjustments

| Property | Default | ELO | Win% | Description |
|---|---|---|---|---|
| `group.home.advantage.elo` | `100` | +100 | **+14.0%** | ELO bonus for the host nation (~1 World Cup game win). |
| `group.confidence.gap` | `25` | — | — | Minimum ELO gap between teams to call a predicted group position `yes` rather than `maybe`. |

### Injury penalties

ELO deducted when key players are unavailable or hampered.

| Property | Default | ELO | Win% | Description |
|---|---|---|---|---|
| `group.injury.penalty.minor` | `22` | −22 | **−3.2%** | Minor — rotation player out or starter hampered. |
| `group.injury.penalty.significant` | `45` | −45 | **−6.4%** | Significant — important player missing, weakens a key position. |
| `group.injury.penalty.critical` | `90` | −90 | **−12.7%** | Critical — star player out; roughly equivalent to undoing a World Cup game win. |

### Heat/climate advantage

ELO bonus when a team is adapted to extreme heat or altitude conditions.

| Property | Default | ELO | Win% | Description |
|---|---|---|---|---|
| `group.heat.advantage.mild` | `9` | +9 | **+1.3%** | Some acclimatisation but conditions are not extreme. |
| `group.heat.advantage.moderate` | `18` | +18 | **+2.6%** | Team genuinely suited to difficult conditions. |
| `group.heat.advantage.strong` | `35` | +35 | **+5.0%** | Major advantage — opposition will be severely affected. |

### Squad dropout penalties

ELO deducted when players pull out before the tournament (separate from in-tournament injuries — coach had time to adapt).

| Property | Default | ELO | Win% | Description |
|---|---|---|---|---|
| `group.squad.dropout.penalty.minor` | `18` | −18 | **−2.6%** | Fringe player or late replacement. |
| `group.squad.dropout.penalty.significant` | `35` | −35 | **−5.0%** | Regular starter pulled out. |
| `group.squad.dropout.penalty.critical` | `70` | −70 | **−9.9%** | Key player/captain dropout; significant tactical impact. Less than injury critical (−12.7%) because planned absences allow tactical adaptation. |

## Squad signals

Applied on top of base ELO to reflect the current state of a squad independent of historical results.

### Age profile

| Property | Default | ELO | Win% | Description |
|---|---|---|---|---|
| `squad.age.young.elo` | `12` | −12 | **−1.7%** | Inexperienced squad — tournament naivety offsets raw talent. |
| `squad.age.aging.elo` | `8` | −8 | **−1.2%** | Squad past peak — recovery and sprint decline over multiple games. |

> Young (−1.7%) is penalised slightly more than aging (−1.2%) because inexperience in knockout football typically costs more than gradual physical decline.

### Cohesion

| Property | Default | ELO | Win% | Description |
|---|---|---|---|---|
| `squad.cohesion.unsettled.elo` | `11` | −11 | **−1.6%** | Minor disruption — late reshuffles, first tournament under current coach. |
| `squad.cohesion.disrupted.elo` | `22` | −22 | **−3.2%** | Serious disruption — new coach mid-cycle, major selection rows. |
| `squad.cohesion.fractured.elo` | `45` | −45 | **−6.4%** | Extreme breakdown — political crisis, player boycotts, institutional dysfunction. |

### Depth

| Property | Default | ELO | Win% | Description |
|---|---|---|---|---|
| `squad.depth.limited.elo` | `10` | −10 | **−1.4%** | Bench quality drops sharply after the first 13–14 players. |
| `squad.depth.thin.elo` | `20` | −20 | **−2.9%** | Minnow-level depth — first World Cup nations, tiny pools. |

### Quality

| Property | Default | ELO | Win% | Description |
|---|---|---|---|---|
| `squad.quality.good.elo` | `10` | +10 | **+1.4%** | Strong squad with several world-class players above their base ELO. |
| `squad.quality.exceptional.elo` | `20` | +20 | **+2.9%** | Generational squad quality — France, Spain, Argentina level in their prime cycles. |

> Quality bonuses are intentionally small to avoid double-counting with ELO, which already prices in historical team quality.

## Tournament path fatigue

Applied at each knockout round as a live ELO adjustment. See [README § knockout stage](../README.md#knockout-stage-tournament-path-fatigue) for the full formula.

### Formula constants

| Property | Default | Description |
|---|---|---|
| `path.fatigue.tournament.avg.elo` | `1850` | Baseline "neutral" opponent ELO. Opponents above this add fatigue; below subtract it. |
| `path.fatigue.elo.factor` | `12` | ELO cost per 100 units of weighted path pressure (−12 ELO, **−1.7%** per 100 pts). |

### Stage multipliers

| Property | Default | Stage | Rationale |
|---|---|---|---|
| `path.fatigue.stage.last32.multiplier` | `0.5` | Last 32 | Early round — squad still fresh |
| `path.fatigue.stage.last16.multiplier` | `1.0` | Last 16 | Baseline |
| `path.fatigue.stage.last8.multiplier` | `1.2` | Quarter-final | Legs heavier, rotation harder |
| `path.fatigue.stage.last4.multiplier` | `1.5` | Semi-final | Virtually no rotation |

### Depth multipliers

Applied only to negative fatigue values — no effect on easy paths. Not double-counting with the static squad depth penalty (which always applies regardless of path).

| Property | Default | Win% amplification | When to use |
|---|---|---|---|
| `path.fatigue.depth.limited.multiplier` | `1.15` | ×1.15 on fatigue ELO | Bench quality drops sharply after 13–14 players |
| `path.fatigue.depth.thin.multiplier` | `1.30` | ×1.30 on fatigue ELO | Minnow-level depth, tiny pools |

## Betting labels

| Property | Default | Description |
|---|---|---|
| `betting.candidate.min.pct` | `40` | Minimum ELO win % to qualify as a betting candidate. |
| `betting.risky.min.pct` | `30` | Minimum ELO win % for a risky candidate (below the main threshold). |
| `betting.strong.candidate.min.profit` | `20.0` | Minimum net profit per £10 stake to label **Strong Candidate**. |
| `betting.candidate.min.profit` | `10.0` | Minimum net profit per £10 stake to label **Candidate**. |
| `betting.weak.candidate.min.profit` | `5.0` | Minimum net profit per £10 stake to label **Weak Candidate**. |
| `betting.moonshot.min.profit` | `20.0` | Minimum net profit per £10 stake to label **Moonshot** (low win %, high return). |

# Configuration

Model defaults live in `src/main/resources/application.properties`. Tournament-specific form windows live in `data/predictions/<tournament>/tournament.properties` and are frozen into `data/elo/snapshots/<tournament>/metadata.properties` when you run `snapshot-refresh`.

Win% impact shown for a 50/50 match (equal ELO teams). Formula: `1 / (1 + 10^(−delta/400)) − 50%`.

## Elo prediction scale

| Property | Default | Description |
|---|---|---|
| `elo.scale.divisor` | `400.0` | Standard World Football Elo expected-score divisor. Lower = exaggerates gaps; higher = more coin-flip outcomes. |

Game predictions are currently adjusted Elo plus knockout path fatigue, with optional manual override. Head-to-head history is not part of the active formula.

## Qualification form

| Property | Default | Description |
|---|---|---|
| `qual.form.elo.max` | `100` | Max ELO bonus/penalty from qualification form (±100 ELO, **+14.0%**). Calibrated so perfect qual form exactly cancels home advantage — in practice no team scores 1.0, so the theoretical max is unreachable. |
| `pre.tournament.form.elo.max` | `50` | Max ELO bonus/penalty from pre-tournament friendlies (±50 ELO, **+7.0%**). Set at 50% of qual form — mirrors eloratings.net K-factor ratio (qualifiers K=40, friendlies K=20). |

Tournament form windows are set per tournament, for example in `data/predictions/world_cup_2026/tournament.properties`:

```properties
qual.form.since.year=2023
qual.form.until.year=2026
pre.tournament.form.since.year=2026
pre.tournament.form.until.year=2026
```

After `snapshot-refresh`, generated rankings use the frozen values in snapshot metadata, not the global defaults.


## Signal Audit Guide

Use these definitions when refreshing `data/predictions/<tournament>/start.csv` from current news.

| Signal | What to look for | Do not include |
|---|---|---|
| `host` | Official host nations receiving genuine home-crowd/travel advantage. | Heat familiarity; use `heat_impact` for climate. |
| `squad_age_profile` | Unusually young/inexperienced finals squads, or aging cores with several key 33+ players and recovery risk. | A single old goalkeeper or one young star. |
| `squad_cohesion` | New coach, tactical reset, selection rows, political/federation disruption, late squad churn, poor preparation time. | Normal squad rotation or ordinary selection debate. |
| `squad_depth` | Replacement quality after the main starters; small pools where one absence causes a big quality drop. | Missing selected-squad players themselves; use Squad Omissions or active-squad Injury. |
| `squad_quality` | Current talent clearly above historical/base Elo, used sparingly for elite current cycles. | General reputation already priced into Elo. |
| `squad_dropouts` | Players missing from the selected squad before the tournament: injury ruled them out, coach omission, discipline, retirement, refusal, federation dispute. | Active-squad fitness risk; use `injury_impact`. |
| `injury_impact` | Active selected-squad players who are doubtful, limited, recently returned, managed, or carrying knocks. | Players not selected at all; use Squad Omissions. |
| `heat_impact` | Adaptation to hot/humid/altitude venues and likely physical resilience in WC conditions. | Host status or general team quality. |

A player should normally appear in only one availability signal. Neymar carrying a calf issue while selected belongs in `injury_notes`; Rodrygo missing the squad through injury belongs in `dropout_notes`/Squad Omissions.

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

### Squad omission/dropout penalties

ELO deducted for players missing from the selected squad before the tournament: injury ruled them out, coach omission, discipline, retirement, refusal, or federation/fallout cases. This is separate from `injury_impact`, which is only for active selected-squad players with fitness risk.

| Property | Default | ELO | Win% | Description |
|---|---|---|---|---|
| `group.squad.dropout.penalty.minor` | `18` | −18 | **−2.6%** | One notable selected-squad absence or fringe loss. |
| `group.squad.dropout.penalty.significant` | `35` | −35 | **−5.0%** | Regular starter or several useful players missing from the selected squad. |
| `group.squad.dropout.penalty.critical` | `70` | −70 | **−9.9%** | Multiple starters or a key player missing from the selected squad; lower than injury critical because the squad list is known before kickoff. |

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

### Bench depth

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


## Signal Interactions

Opposing signals are expected when they describe different facts.

| Signal | Can be offset by | Example |
|---|---|---|
| `squad_quality` | `squad_dropouts` / Squad Omissions | Elite selected-squad talent can remain even after major players miss the squad. |
| `squad_quality` | `injury_impact` | Talent ceiling remains high, but active-squad fitness risk reduces reliability. |
| `squad_quality` | `squad_depth` | Strong starters do not guarantee strong replacements. |
| `host` | `injury_impact`, `squad_dropouts`, `squad_cohesion` | Home advantage can be cancelled by availability or preparation problems. |
| `heat_impact` | `squad_age_profile`, `squad_depth`, path fatigue | Heat adaptation helps, but old/thin/tired teams can still fade. |
| `qual_bonus` / `pre_tournament_bonus` | base Elo | Recent form can disagree with long-run strength. |
| `squad_cohesion` | `squad_quality` | Talent can underperform under tactical/locker-room disruption. |
| `squad_depth` | path fatigue | Deep benches reduce fatigue; limited/thin benches amplify it. |

Current heat handling: `heat_impact` is a static tournament-context Elo overlay. It does not directly multiply path fatigue yet. A heat-fatigue interaction may be useful later, but should probably depend on actual venue/date conditions to avoid applying the same heat penalty to every knockout match.

## Tournament path fatigue

Applied at each knockout round as a live ELO adjustment. First knockout matches are seeded with group-stage load from above-average group opponents; later rounds add knockout opponents on top. See [README § knockout stage](../README.md#knockout-stage-tournament-path-fatigue) for the full formula.

### Formula constants

| Property | Default | Description |
|---|---|---|
| `path.fatigue.tournament.avg.elo` | `1850` | Baseline "neutral" opponent ELO. Group opponents above this add fatigue; weaker group opponents do not create a bonus. |
| `path.fatigue.elo.factor` | `12` | ELO cost per 100 units of weighted path pressure (−12 ELO, **−1.7%** per 100 pts). |

### Stage multipliers

| Property | Default | Stage | Rationale |
|---|---|---|---|
| `path.fatigue.stage.group.multiplier` | `0.25` | Group | Group-stage load carried into first knockout |
| `path.fatigue.stage.last32.multiplier` | `0.5` | Last 32 | Early round — squad still fresh |
| `path.fatigue.stage.last16.multiplier` | `1.0` | Last 16 | Baseline |
| `path.fatigue.stage.last8.multiplier` | `1.2` | Quarter-final | Legs heavier, rotation harder |
| `path.fatigue.stage.last4.multiplier` | `1.5` | Semi-final | Virtually no rotation |

### Bench-depth multipliers

Applied only to negative fatigue values — no effect on easy group loads or paths. This is separate from the static bench-depth penalty: the static value captures weak bench quality before kickoff, while this multiplier controls how well the bench absorbs accumulated knockout fatigue.

| Property | Default | Fatigue effect | When to use |
|---|---|---|---|
| `path.fatigue.depth.good.multiplier` | `0.85` | Reduces fatigue ELO by 15% | Deep squads that can rotate without a major quality drop |
| `path.fatigue.depth.limited.multiplier` | `1.15` | Amplifies fatigue ELO by 15% | Bench quality drops sharply after 13-14 players |
| `path.fatigue.depth.thin.multiplier` | `1.30` | Amplifies fatigue ELO by 30% | Minnow-level depth, tiny pools |

## Betting labels

| Property | Default | Description |
|---|---|---|
| `betting.candidate.min.pct` | `40` | Minimum ELO win % to qualify as a betting candidate. |
| `betting.risky.min.pct` | `30` | Minimum ELO win % for a risky candidate (below the main threshold). |
| `betting.strong.candidate.min.profit` | `20.0` | Minimum net profit per £10 stake to label **Strong Candidate**. |
| `betting.candidate.min.profit` | `10.0` | Minimum net profit per £10 stake to label **Candidate**. |
| `betting.weak.candidate.min.profit` | `5.0` | Minimum net profit per £10 stake to label **Weak Candidate**. |
| `betting.moonshot.min.profit` | `20.0` | Minimum net profit per £10 stake to label **Moonshot** (low win %, high return). |

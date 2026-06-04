# Configuration

All prediction parameters live in `src/main/resources/application.properties`. The defaults are sensible — you only need to change these if you want to experiment with the model.

## Prediction weights

| Property | Default | Description |
|---|---|---|
| `prediction.elo.weight` | `0.70` | How much ELO counts vs head-to-head history. `0.70` = 70% ELO, 30% history. |
| `prediction.history.competition.weight` | `0.70` | Within the 30% history slice: competitive matches get 70%, friendlies get 30%. So the full split is **70% ELO + 21% competitive history + 9% friendly history**. |
| `elo.scale.divisor` | `400.0` | 400 is the standard chess/FIFA ELO value — not recommended to change this. A lower number exaggerates the gap between strong and weak teams; higher makes outcomes feel more like a coin flip. |

## Head-to-head decay

| Property | Default | Description |
|---|---|---|
| `h2h.decay.half.life.years` | `5.0` | How quickly old results lose relevance. A result from exactly this many years ago counts for 50% of a recent result. |

## Group stage adjustments

| Property | Default | Description |
|---|---|---|
| `group.home.advantage.elo` | `100` | ELO bonus added to the host nation. |
| `group.injury.penalty.minor` | `25` | ELO deducted for injury level 1 (minor). |
| `group.injury.penalty.significant` | `50` | ELO deducted for injury level 2 (significant). |
| `group.injury.penalty.critical` | `100` | ELO deducted for injury level 3 (critical). |
| `group.confidence.gap` | `25` | Minimum ELO score gap between teams to call a predicted position `yes` rather than `maybe`. |

## Betting labels

| Property | Default | Description |
|---|---|---|
| `betting.candidate.min.pct` | `40` | Minimum ELO win % for a team to qualify as a betting candidate. |
| `betting.risky.min.pct` | `30` | Minimum ELO win % for a risky candidate (below the main threshold). |
| `betting.strong.candidate.min.profit` | `20.0` | Minimum net profit per £10 stake to label **Strong Candidate**. |
| `betting.candidate.min.profit` | `10.0` | Minimum net profit per £10 stake to label **Candidate**. |
| `betting.weak.candidate.min.profit` | `5.0` | Minimum net profit per £10 stake to label **Weak Candidate**. |
| `betting.moonshot.min.profit` | `20.0` | Minimum net profit per £10 stake to label **Moonshot** (low win %, high return). |

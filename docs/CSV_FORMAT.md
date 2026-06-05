# Matchup CSV Format

Active scored matchup files use this shape:

```csv
match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent
M89,E1(England),B2(Germany),predicted,England (55%),2020,48,1925,34,0,-12,,France:-12
```

| Column | Description |
|---|---|
| `match_id` | Bracket match identifier. |
| `team1`, `team2` | Display token and team name for each side. |
| `path` | `predicted` = current primary bracket path; `alt` = alternative possible path. |
| `prediction` | Final predicted winner after adjusted Elo, path fatigue, and `do_you_disagree` override. |
| `team*_base_elo` | Raw team Elo from `data/elo/world.csv` before tournament overlays. |
| `team*_qual_bonus` | Qualification-form Elo overlay included in the adjusted group Elo. |
| `team*_path_fatigue` | Cumulative weighted path difficulty before conversion to Elo penalty. |
| `team*_path_opponent` | Prior knockout opponent chain with each opponent contribution. |

Prediction input files also include `do_you_disagree`. Set it to `yes` to override the model and pick the other team.

Legacy H2H columns such as `history_competitions` and `history_friendlies` may still be accepted by validators for old files, but head-to-head is not part of the active prediction formula.

## qualification.csv (data/{tournament}/qualification.csv)

Team qualification campaign statistics. Used to compute qualification form — how well a team performed in their route to the tournament.

| Column | Description |
|---|---|
| `team` | Team name (must match `world.csv` exactly) |
| `played` | Matches played in qualification |
| `won` | Matches won |
| `drawn` | Matches drawn |
| `lost` | Matches lost |
| `gf` | Goals for (scored) |
| `ga` | Goals against (conceded) |

Host nations who skip qualification default to neutral form.
Teams not listed also default to neutral form.

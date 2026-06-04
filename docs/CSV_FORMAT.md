# Matchup CSV Format

```
match_id,team1,team2,path,predicted_winner,elo,history_competitions,history_friendlies,qual_form,prediction
M89,🏴E1(England),🇩🇪B2(Germany),primary,England (55%),England (52%),England (60%),Germany (55%),51%,England (55%)
```

| Column | Description |
|---|---|
| `path` | `primary` = both teams confirmed; `alt` = at least one side uncertain |
| `predicted_winner` | Final predicted winner after applying `do_you_disagree` override |
| `elo` | ELO-only prediction with win probability |
| `history_competitions` | Head-to-head prediction from competitive matches |
| `history_friendlies` | Head-to-head prediction from friendly matches |
| `qual_form` | Qualification-form share for team1 in the matchup, or `N/A` if neither side has qualification data |
| `prediction` | Combined prediction (70% ELO + 15% qualification form + 10.5% competition history + 4.5% friendly history by default) |
| `do_you_disagree` | Set to `yes` to override the combined prediction with the other team |

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

Host nations (who skip qualification) should not be included — they default to `0.5` neutral.
Teams not listed also default to `0.5` neutral.

Form score formula: `70% points-per-game (normalised) + 30% goal-difference-per-game (clamped to [-3,+3])`.

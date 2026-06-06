# Matchup CSV Format

Active scored matchup files use this shape:

```csv
match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent
M89,E1(England),B2(Germany),predicted,England (55%),2020,48,1925,34,-3,-15,G|Uruguay:-3,G|Brazil:-3 > France:-12
```

| Column | Description |
|---|---|
| `match_id` | Bracket match identifier. |
| `team1`, `team2` | Display token and team name for each side. |
| `path` | `predicted` = current primary bracket path; `alt` = alternative possible path. |
| `prediction` | Model-predicted winner after adjusted ELO and path fatigue. |
| `team*_base_elo` | Raw team Elo from the tournament snapshot before tournament overlays. Tournament modes require a snapshot. |
| `team*_qual_bonus` | Qualification-form Elo overlay included in the adjusted group Elo. |
| `team*_path_fatigue` | Cumulative weighted group/path difficulty before conversion to Elo penalty. |
| `team*_path_opponent` | Fatigue contribution chain. `G|Team:-3` marks group-stage load; unprefixed or `KO|Team:-5` marks knockout path load. Repeated opponents can appear once under `G` and once under `KO` if the teams meet twice. |

Legacy prediction files may include `do_you_disagree`, but it is ignored. Knockout paths now always use the model prediction; alternate routes are explored through generated matchup rows.

Legacy H2H columns such as `history_competitions` and `history_friendlies` may still be accepted by validators for old files, but head-to-head is not part of the active prediction formula.

## Tournament snapshot (`data/elo/snapshots/<tournament>/`)

Created by `--mode=snapshot-refresh`. These files freeze external ELO inputs for one tournament so later global refreshes do not alter it accidentally.

| File | Meaning |
|---|---|
| `teams.csv` | `rank,team_code,team_name,rating` rows for only teams in `start.csv`. This is the tournament-local base ELO source. |
| `history/*.tsv` | Per-team ELO history files filtered to the configured form-year window and capped at the tournament start date. Used for qualification and friendly form. |
| `metadata.properties` | Snapshot creation time, source paths, team count, form-year ranges, and frozen `tournament_start_date`. |

`start` mode and group ELO breakdowns require the snapshot and use its `metadata.properties` form windows. Refreshable current ELO files are only used by `snapshot-refresh`.

## tournament.properties (`data/predictions/<tournament>/tournament.properties`)

Tournament-local settings used when refreshing the snapshot:

```properties
tournament.start.date=2026-06-11
qual.form.since.year=2023
qual.form.until.year=2026
pre.tournament.form.since.year=2026
pre.tournament.form.until.year=2026
```

`tournament.start.date` is required and must use `YYYY-MM-DD`. Snapshot refresh physically excludes later history rows, and form calculations enforce the frozen metadata date again when reading them.

After `snapshot-refresh`, the effective values are copied into `data/elo/snapshots/<tournament>/metadata.properties` and that metadata becomes the replay source for generated rankings and UI breakdowns.

## start.csv (`data/predictions/<tournament>/start.csv`)

Manual group setup input. These fields are the evidence-backed overlays applied before `groups.csv` is generated.

| Column | Meaning |
|---|---|
| `group`, `team` | Tournament group and exact team name. |
| `host` | `yes` only for official hosts receiving home advantage. |
| `squad_age_profile` / `age_notes` | 0 balanced, 1 young/inexperienced, 2 aging core. Notes should cite age/recovery context. |
| `squad_cohesion` / `cohesion_notes` | 0 settled, 1 unsettled, 2 disrupted, 3 fractured. Use for coach changes, selection rows, political disruption, or late churn. |
| `squad_depth` / `depth_notes` | −1 excellent (+10 ELO), 0 good/deep, 1 limited bench, 2 thin bench. Measures replacement quality, not missing selected-squad players themselves. |
| `attack_quality` / `defence_quality` | Signed values from -2 to +2: very weak, weak, average, strong, elite. Each level changes xG by 0.15. Attack changes the team's xG; Defence changes the opponent's xG. Neither changes Adjusted ELO. |
| `quality_notes` | Evidence for the Attack/Defence assessment, such as personnel, tactical role, chance creation, finishing, or defensive structure. Do not derive it only from the same results used for form. |
| `squad_dropouts` / `dropout_notes` | Squad Omissions: players missing from the selected squad because injury ruled them out, coach omission, discipline, retirement, refusal, or federation dispute. |
| `injury_impact` / `injury_notes` | Active selected-squad fitness availability: doubtful, limited, managed, recently returned, or carrying knocks. |
| `heat_impact` | 0 none, 1 mild, 2 moderate, 3 strong climate adaptation. |


Legacy `squad_quality` values are accepted during migration and copied to both Attack and Defence, but new saves write only `attack_quality` and `defence_quality`.
Keep omissions and injury evidence separate: not selected = `squad_dropouts`; selected but fitness-limited = `injury_impact`.

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

# Path Mode

Path mode traces every possible knockout route a team could take from the last 32 to the final, showing predicted opponents at each stage, win probabilities, and an overall difficulty score.

```bash
./predict.sh --tournament=world_cup_2026 --mode=path --filter=England
```

Results are sorted hardest → easiest. Up to 100 paths are shown per page.

## Difficulty score

A stage-weighted average of opponent strength across the path:

```
difficulty = Σ((100 − win%) × stageWeight) / Σ(stageWeight)
```

Stage weights: Last 32 = 1, Last 16 = 2, Quarter-final = 3, Semi-final = 4, Final = 5.

| Label | Score |
|---|---|
| **EASY** | < 25 |
| **MEDIUM** | < 40 |
| **HARD** | < 55 |
| **BRUTAL** | ≥ 55 |

## Filter expressions

The `--filter` flag accepts one or more conditions joined by `&` (AND):

```
--filter="<key>:<value>[&<key>:<value>...]"
```

| Key | Shorthand | Description | Example |
|---|---|---|---|
| `team` | `t` | Team to trace — required for path mode | `t:England` |
| `difficulty` | `d` | Filter by difficulty label | `d:hard` |
| `last_32` | `32` | Team faced in the last 32 | `32:Germany` |
| `last_16` | `16` | Team faced in the last 16 | `16:France` |
| `quarter` | `q` | Team faced in the quarter-final | `q:Brazil` |
| `semi` | `s` | Team faced in the semi-final | `s:Spain` |

Values for `difficulty`: `easy`, `medium`, `hard`, `brutal`

## Examples

```bash
# All paths for England
--filter="t:England"

# England's hard paths only
--filter="t:England&d:hard"

# England paths where Germany is faced in the last 16
--filter="t:England&16:Germany"

# England facing France in the semi on a hard path
--filter="t:England&s:France&d:hard"

# Second page of results
--filter="t:England" --page=2

# Primary predicted paths only (exclude upset-dependent routes)
--filter="t:England" --path=primary
```

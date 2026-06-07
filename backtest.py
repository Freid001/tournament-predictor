#!/usr/bin/env python3
import argparse
import csv
import math
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DEFAULT_TOURNAMENTS = ("world_cup_2018", "world_cup_2022", "euros_2024")
BANDS = ((0.35, 0.45), (0.45, 0.55), (0.55, 0.65),
         (0.65, 0.75), (0.75, 0.85), (0.85, 1.01))
SCORES = ((0, 0), (1, 0), (0, 1), (1, 1), (2, 0),
          (0, 2), (2, 1), (1, 2), (3, 0), (0, 3))
FINISH_ORDER = {
    "group_stage": 0, "last_16": 1, "quarter_final": 2,
    "semi_final": 3, "runner_up": 4, "champion": 5,
}


def read_csv(path):
    if not path.exists():
        raise FileNotFoundError(f"Backtest input not found: {path}")
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def pair_key(team1, team2):
    return tuple(sorted((team1, team2)))


def load_predictions(tournament):
    rows = read_csv(ROOT / "data/simulations" / tournament /
                    "simulation_scorelines_groups.csv")
    predictions = {}
    for row in rows:
        key = pair_key(row["team1"], row["team2"])
        match = predictions.setdefault(key, {
            "team1": row["team1"], "team2": row["team2"],
            "runs": int(row["matchup_runs"]),
            "outcomes": Counter(), "scores": Counter(),
        })
        count = int(row["count"])
        match["outcomes"][row["winner"]] += count
        match["scores"][tuple(map(int, row["scoreline"].split("-")))] += count
    return predictions


def orient_prediction(match, team1, team2):
    reverse = match["team1"] != team1
    first = match["team2"] if reverse else match["team1"]
    second = match["team1"] if reverse else match["team2"]
    runs = match["runs"]
    scores = Counter()
    for (goals1, goals2), count in match["scores"].items():
        scores[(goals2, goals1) if reverse else (goals1, goals2)] += count
    return {
        "team1": match["outcomes"][first] / runs,
        "draw": match["outcomes"]["Draw"] / runs,
        "team2": match["outcomes"][second] / runs,
        "scores": scores,
        "runs": runs,
    }


def progression_brier(probabilities, finishes, column, threshold):
    total = 0.0
    for team, actual in finishes.items():
        probability = float(probabilities[team][column]) / 100.0
        observed = FINISH_ORDER[actual["finish"]] >= FINISH_ORDER[threshold]
        total += (probability - observed) ** 2
    return total / len(finishes)


def analyse(tournament):
    actual_dir = ROOT / "data/backtests" / tournament
    actual_results = read_csv(actual_dir / "actual_results.csv")
    finish_rows = read_csv(actual_dir / "actual_finish.csv")
    finishes = {row["team"]: row for row in finish_rows}
    predictions = load_predictions(tournament)
    progression_rows = read_csv(ROOT / "data/simulations" / tournament /
                                "simulation_last_16.csv")
    progression = {row["team"]: row for row in progression_rows}
    group_rows = read_csv(ROOT / "data/simulations" / tournament /
                          "simulation_groups.csv")

    matches = []
    score_predicted = Counter()
    score_actual = Counter()
    for actual in actual_results:
        team1, team2 = actual["team1"], actual["team2"]
        prediction = predictions.get(pair_key(team1, team2))
        if prediction is None:
            raise ValueError(f"Missing simulation for {team1} vs {team2} in {tournament}")
        prediction = orient_prediction(prediction, team1, team2)
        goals1, goals2 = int(actual["team1_goals"]), int(actual["team2_goals"])
        outcome = 0 if goals1 > goals2 else 2 if goals2 > goals1 else 1
        probabilities = (prediction["team1"], prediction["draw"], prediction["team2"])
        favourite = 0 if probabilities[0] >= probabilities[2] else 2
        favourite_probability = max(probabilities[0], probabilities[2])
        expected_goals = sum((a + b) * count for (a, b), count
                             in prediction["scores"].items()) / prediction["runs"]
        over_25 = sum(count for (a, b), count in prediction["scores"].items()
                      if a + b >= 3) / prediction["runs"]
        for score, count in prediction["scores"].items():
            score_predicted[score] += count / prediction["runs"]
        score_actual[(goals1, goals2)] += 1
        matches.append({
            "probabilities": probabilities, "outcome": outcome,
            "favourite": favourite, "favourite_probability": favourite_probability,
            "expected_goals": expected_goals, "actual_goals": goals1 + goals2,
            "over_25": over_25, "actual_over_25": goals1 + goals2 >= 3,
        })

    count = len(matches)
    outcome_brier = sum(sum((probability - (index == match["outcome"])) ** 2
                            for index, probability in enumerate(match["probabilities"]))
                          for match in matches) / count
    log_loss = -sum(math.log(max(match["probabilities"][match["outcome"]], 1e-12))
                    for match in matches) / count
    predicted_favourite_wins = sum(match["favourite_probability"] for match in matches)
    predicted_draws = sum(match["probabilities"][1] for match in matches)
    actual_favourite_wins = sum(match["outcome"] == match["favourite"] for match in matches)
    actual_draws = sum(match["outcome"] == 1 for match in matches)

    exact_positions = correct_group_winners = 0
    for row in group_rows:
        finish = finishes[row["team"]]
        values = [float(row[f"finish_{suffix}"])
                  for suffix in ("1st", "2nd", "3rd", "4th")]
        predicted_position = values.index(max(values)) + 1
        exact_positions += predicted_position == int(finish["position"])
        correct_group_winners += predicted_position == 1 and finish["position"] == "1"

    qualifier_count = sum(row["finish"] != "group_stage" for row in finish_rows)
    predicted_qualifiers = []
    remaining = []
    for group_name in sorted({row["group"] for row in finish_rows}):
        group_rows = [row for row in progression_rows
                      if finishes[row["team"]]["group"] == group_name]
        group_rows.sort(key=lambda row: float(row["reach_last_16"]), reverse=True)
        predicted_qualifiers.extend(group_rows[:2])
        remaining.extend(group_rows[2:])
    extra_slots = qualifier_count - len(predicted_qualifiers)
    if extra_slots > 0:
        remaining.sort(key=lambda row: float(row["reach_last_16"]), reverse=True)
        predicted_qualifiers.extend(remaining[:extra_slots])
    correct_qualifiers = sum(finishes[row["team"]]["finish"] != "group_stage"
                             for row in predicted_qualifiers)

    champion = next(row["team"] for row in finish_rows if row["finish"] == "champion")
    champion_order = sorted(progression_rows, key=lambda row: float(row["champion"]), reverse=True)
    champion_rank = next(index + 1 for index, row in enumerate(champion_order)
                         if row["team"] == champion)
    champion_probability = float(progression[champion]["champion"])

    summary = {
        "tournament": tournament, "matches": count,
        "outcome_brier": outcome_brier, "log_loss": log_loss,
        "predicted_goals": sum(m["expected_goals"] for m in matches) / count,
        "actual_goals": sum(m["actual_goals"] for m in matches) / count,
        "predicted_over_2_5": sum(m["over_25"] for m in matches) / count,
        "actual_over_2_5": sum(m["actual_over_25"] for m in matches) / count,
        "predicted_favourite_wins": predicted_favourite_wins,
        "actual_favourite_wins": actual_favourite_wins,
        "predicted_draws": predicted_draws, "actual_draws": actual_draws,
        "predicted_upsets": count - predicted_favourite_wins - predicted_draws,
        "actual_upsets": count - actual_favourite_wins - actual_draws,
        "qualifier_brier": progression_brier(progression, finishes, "reach_last_16", "last_16"),
        "quarter_brier": progression_brier(progression, finishes, "reach_last_8", "quarter_final"),
        "semi_brier": progression_brier(progression, finishes, "reach_last_4", "semi_final"),
        "final_brier": progression_brier(progression, finishes, "reach_final", "runner_up"),
        "champion_brier": progression_brier(progression, finishes, "champion", "champion"),
        "correct_group_winners": correct_group_winners,
        "groups": len({row["group"] for row in finish_rows}),
        "correct_qualifiers": correct_qualifiers, "qualifiers": qualifier_count,
        "exact_positions": exact_positions, "teams": len(finish_rows),
        "champion": champion, "champion_rank": champion_rank,
        "champion_probability": champion_probability,
    }

    calibration = []
    for low, high in BANDS:
        band = [match for match in matches
                if low <= match["favourite_probability"] < high]
        if band:
            calibration.append({
                "tournament": tournament, "band": f"{int(low*100)}-{int(high*100)}",
                "matches": len(band),
                "predicted_win_rate": sum(m["favourite_probability"] for m in band) / len(band),
                "actual_win_rate": sum(m["outcome"] == m["favourite"] for m in band) / len(band),
            })

    scorelines = [{
        "tournament": tournament, "scoreline": f"{a}-{b}",
        "predicted_frequency": score_predicted[(a, b)] / count,
        "actual_frequency": score_actual[(a, b)] / count,
    } for a, b in SCORES]
    return summary, calibration, scorelines


def combine(summaries):
    total_matches = sum(row["matches"] for row in summaries)
    total_teams = sum(row["teams"] for row in summaries)
    combined = {"tournament": "combined", "matches": total_matches}
    match_averages = ("outcome_brier", "log_loss", "predicted_goals", "actual_goals",
                      "predicted_over_2_5", "actual_over_2_5")
    team_averages = ("qualifier_brier", "quarter_brier", "semi_brier",
                     "final_brier", "champion_brier")
    for key in match_averages:
        combined[key] = sum(row[key] * row["matches"] for row in summaries) / total_matches
    for key in team_averages:
        combined[key] = sum(row[key] * row["teams"] for row in summaries) / total_teams
    for key in ("predicted_favourite_wins", "actual_favourite_wins", "predicted_draws",
                "actual_draws", "predicted_upsets", "actual_upsets", "correct_group_winners",
                "groups", "correct_qualifiers", "qualifiers", "exact_positions", "teams"):
        combined[key] = sum(row[key] for row in summaries)
    combined.update(champion="", champion_rank="", champion_probability="")
    return combined



def combine_calibration(rows):
    combined = []
    for band in sorted({row["band"] for row in rows}):
        values = [row for row in rows if row["band"] == band]
        matches = sum(row["matches"] for row in values)
        combined.append({
            "tournament": "combined", "band": band, "matches": matches,
            "predicted_win_rate": sum(row["predicted_win_rate"] * row["matches"] for row in values) / matches,
            "actual_win_rate": sum(row["actual_win_rate"] * row["matches"] for row in values) / matches,
        })
    return combined


def combine_scorelines(rows, summaries):
    total = sum(row["matches"] for row in summaries)
    weights = {row["tournament"]: row["matches"] for row in summaries}
    combined = []
    for scoreline in sorted({row["scoreline"] for row in rows}):
        values = [row for row in rows if row["scoreline"] == scoreline]
        combined.append({
            "tournament": "combined", "scoreline": scoreline,
            "predicted_frequency": sum(row["predicted_frequency"] * weights[row["tournament"]] for row in values) / total,
            "actual_frequency": sum(row["actual_frequency"] * weights[row["tournament"]] for row in values) / total,
        })
    return combined

def write_csv(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        return
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        for row in rows:
            writer.writerow({key: format_value(value) for key, value in row.items()})


def format_value(value):
    return f"{value:.4f}" if isinstance(value, float) else value


def print_report(rows):
    print(f"{'Tournament':<16} {'Matches':>7} {'Brier':>8} {'LogLoss':>8} "
          f"{'Goals P/A':>12} {'Qualifiers':>11} {'Champion':>20}")
    for row in rows:
        champion = "-" if not row["champion"] else (
            f"{row['champion']} #{row['champion_rank']} {float(row['champion_probability']):.1f}%")
        print(f"{row['tournament']:<16} {row['matches']:>7} {row['outcome_brier']:>8.4f} "
              f"{row['log_loss']:>8.4f} {row['predicted_goals']:>5.2f}/{row['actual_goals']:<5.2f} "
              f"{row['correct_qualifiers']:>2}/{row['qualifiers']:<8} {champion:>20}")


def main():
    parser = argparse.ArgumentParser(description="Compare historical tournament simulations with actual results.")
    parser.add_argument("tournament", nargs="*", help="Tournament names; defaults to all historical backtests.")
    args = parser.parse_args()
    tournaments = args.tournament or list(DEFAULT_TOURNAMENTS)
    summaries, calibration, scorelines = [], [], []
    for tournament in tournaments:
        summary, bands, scores = analyse(tournament)
        summaries.append(summary); calibration.extend(bands); scorelines.extend(scores)
    report_rows = summaries + ([combine(summaries)] if len(summaries) > 1 else [])
    if len(summaries) > 1:
        calibration.extend(combine_calibration(calibration))
        scorelines.extend(combine_scorelines(scorelines, summaries))
    output = ROOT / "data/backtests"
    write_csv(output / "backtest_report.csv", report_rows)
    write_csv(output / "backtest_calibration.csv", calibration)
    write_csv(output / "backtest_scorelines.csv", scorelines)
    print_report(report_rows)
    print("Reports written under data/backtests/.")


if __name__ == "__main__":
    main()

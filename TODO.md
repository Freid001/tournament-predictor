# Tasks

## Curated vs neutral squad-input experiment

Compare the existing curated squad inputs against neutral inputs for:

- World Cup 2018
- World Cup 2022
- EURO 2024

For each tournament, run identical 25,000-run simulations with the same seed and model parameters. In the neutral variant, set attack, defence, injuries, squad depth, cohesion, age, omissions, heat, and other manual squad adjustments to zero while preserving historical ELO, host advantage, tournament format, and pre-tournament form.

Compare:

- Match Brier score and log loss
- Correct qualifiers, group winners, and exact positions
- Progression Brier scores for each knockout stage and champion
- Predicted versus actual goals
- Favourite, draw, and upset calibration
- Actual champion rank and probability

Keep curated inputs only where they improve out-of-sample probability quality. Do not select settings based solely on predicting the known winner.

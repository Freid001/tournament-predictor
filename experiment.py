#!/usr/bin/env python3
import argparse, shutil, subprocess
from pathlib import Path
import backtest

ROOT = Path(__file__).resolve().parent
JAR = ROOT / "target/tournament-predictor-0.0.1-SNAPSHOT.jar"
TOURNAMENTS = ("world_cup_2018", "world_cup_2022", "euros_2024")
RUNS = 25000
V = {
 "baseline": {},
 "xg_minus_3_pct": {"xg.total.multiplier":"0.97"},
 "no_qualification_form_xg_minus_3_pct": {"qual.form.elo.max":"0","xg.total.multiplier":"0.97"},
 "no_qualification_form": {"qual.form.elo.max":"0"},
 "no_recent_form": {"pre.tournament.form.elo.max":"0"},
 "no_home_advantage": {"group.home.advantage.elo":"0"},
 "no_injuries": {k:"0" for k in ("group.injury.penalty.minor","group.injury.penalty.significant","group.injury.penalty.critical")},
 "no_heat": {k:"0" for k in ("group.heat.advantage.mild","group.heat.advantage.moderate","group.heat.advantage.strong")},
 "no_squad_dropouts": {k:"0" for k in ("group.squad.dropout.penalty.minor","group.squad.dropout.penalty.significant","group.squad.dropout.penalty.critical")},
 "no_age": {"squad.age.young.elo":"0","squad.age.aging.elo":"0"},
 "no_cohesion": {k:"0" for k in ("squad.cohesion.unsettled.elo","squad.cohesion.disrupted.elo","squad.cohesion.fractured.elo")},
 "no_depth": {k:"0" for k in ("squad.depth.excellent.elo","squad.depth.limited.elo","squad.depth.thin.elo")},
 "elo_adjustments_50_pct": {
  "qual.form.elo.max":"50","pre.tournament.form.elo.max":"25","group.home.advantage.elo":"50",
  "group.injury.penalty.minor":"11","group.injury.penalty.significant":"23","group.injury.penalty.critical":"45",
  "group.heat.advantage.mild":"5","group.heat.advantage.moderate":"9","group.heat.advantage.strong":"18",
  "group.squad.dropout.penalty.minor":"9","group.squad.dropout.penalty.significant":"18","group.squad.dropout.penalty.critical":"35",
  "squad.age.young.elo":"5","squad.age.aging.elo":"4","squad.cohesion.unsettled.elo":"6",
  "squad.cohesion.disrupted.elo":"11","squad.cohesion.fractured.elo":"23",
  "squad.depth.excellent.elo":"5","squad.depth.limited.elo":"5","squad.depth.thin.elo":"10"}
}

def name(t, v): return f"__exp_{t}_{abs(hash(v)) % 100000}"

def clean(n):
 for d in ("predictions","simulations","matchups"):
  shutil.rmtree(ROOT/"data"/d/n, ignore_errors=True)
 shutil.rmtree(ROOT/"data/elo/snapshots"/n, ignore_errors=True)
 shutil.rmtree(ROOT/"data/backtests"/n, ignore_errors=True)
 (ROOT/"data/bracket"/f"{n}.csv").unlink(missing_ok=True)

def copy_inputs(t, n):
 p=ROOT/"data/predictions"/n; p.mkdir(parents=True)
 for f in ("start.csv","tournament.properties"):
  shutil.copy2(ROOT/"data/predictions"/t/f,p/f)
 shutil.copy2(ROOT/"data/bracket"/f"{t}.csv",ROOT/"data/bracket"/f"{n}.csv")
 shutil.copytree(ROOT/"data/elo/snapshots"/t,ROOT/"data/elo/snapshots"/n)
 shutil.copytree(ROOT/"data/backtests"/t,ROOT/"data/backtests"/n)

def engine(n, mode, props):
 cmd=["java","--enable-native-access=ALL-UNNAMED",f"-Dsimulation.runs={RUNS}"]
 cmd += [f"-D{k}={v}" for k,v in props.items()]
 cmd += ["-jar",str(JAR),f"--tournament={n}",f"--mode={mode}"]
 subprocess.run(cmd,cwd=ROOT,check=True,stdout=subprocess.DEVNULL)

def run(t,v):
 n=name(t,v); clean(n)
 try:
  copy_inputs(t,n)
  for mode in ("start","group-simulation","knockout-simulation"): engine(n,mode,V[v])
  row=backtest.analyse(n)[0]; row.update(tournament=t,variant=v); return row
 finally: clean(n)

def reports(rows,selected):
 summaries=[]
 for v in selected:
  r=backtest.combine([x for x in rows if x["variant"]==v]); r["variant"]=v; summaries.append(r)
 base=summaries[0]; base_goal=abs(base["predicted_goals"]-base["actual_goals"])
 for r in summaries:
  r["brier_delta"]=r["outcome_brier"]-base["outcome_brier"]
  r["log_loss_delta"]=r["log_loss"]-base["log_loss"]
  r["goal_error"]=abs(r["predicted_goals"]-r["actual_goals"]); r["goal_error_delta"]=r["goal_error"]-base_goal
 fields=("variant","tournament","matches","outcome_brier","log_loss","predicted_goals","actual_goals","qualifier_brier","quarter_brier","semi_brier","final_brier","champion_brier","correct_qualifiers","qualifiers","champion","champion_rank","champion_probability")
 backtest.write_csv(ROOT/"data/backtests/experiment_report.csv",[{k:r.get(k,"") for k in fields} for r in rows])
 fields=("variant","outcome_brier","brier_delta","log_loss","log_loss_delta","predicted_goals","actual_goals","goal_error","goal_error_delta","qualifier_brier","quarter_brier","semi_brier","final_brier","champion_brier","correct_qualifiers","qualifiers")
 backtest.write_csv(ROOT/"data/backtests/experiment_summary.csv",[{k:r.get(k,"") for k in fields} for r in summaries])
 return summaries

def main():
 p=argparse.ArgumentParser(); p.add_argument("variant",nargs="*"); a=p.parse_args()
 selected=a.variant or list(V)
 if "baseline" not in selected: selected.insert(0,"baseline")
 rows=[]
 for i,v in enumerate(selected,1):
  for t in TOURNAMENTS:
   print(f"[{i}/{len(selected)}] {v}: {t}",flush=True); rows.append(run(t,v))
 ss=reports(rows,selected)
 print(f"{'Variant':<28} {'Brier':>7} {'Delta':>8} {'LogLoss':>8} {'Goals':>11} {'Qual':>7}")
 for r in sorted(ss,key=lambda x:x["outcome_brier"]):
  print(f"{r['variant']:<28} {r['outcome_brier']:>7.4f} {r['brier_delta']:>+8.4f} {r['log_loss']:>8.4f} {r['predicted_goals']:>5.2f}/{r['actual_goals']:<5.2f} {r['correct_qualifiers']:>2}/{r['qualifiers']:<2}")
if __name__=="__main__": main()

"""Append measured full-campaign statistics after all refinement/evaluation jobs."""
import csv,json,math,statistics,collections
from pathlib import Path
R=Path(__file__).resolve().parent
rows=list(csv.DictReader((R/'summary.csv').open()));summary=json.loads((R/'summary.json').read_text());audit=json.loads((R/'audit.json').read_text())
assert len(rows)==71 and summary['states']=={'refined':71} and audit['passed']==71
plan=json.loads((R/'campaign_plan.json').read_text())
def val(r,k):
 try:
  x=float(r[k]);return x if math.isfinite(x) else None
 except (KeyError,TypeError,ValueError):return None
lines=['','## Full 71-case results','',f"All 71 refinements completed; atom identity, non-coordinate records, hashes, finite coordinates, decreased target and derivative-check acceptance passed the final audit for {audit['passed']} cases.",'',f"Converged: {summary['converged']}; reached the 200-iteration cap: {summary['iteration_capped']}.",'',f"Production code commit: `{plan['git_commit']}`. Fixed data weight 0.05; gradient RMS tolerance 1.0; coordinate-only optimization. Final successful Slurm jobs: "+', '.join(sorted({r['job_id'] for r in rows}))+'.','', '| Group | Refined | Converged | Data-fit pairs | Improved | Median change |','|---|---:|---:|---:|---:|---:|']
measured={}
for group in ['cryoem','xray_low','xray_high']:
 rr=[r for r in rows if r['group']==group];key='cc_mask' if group=='cryoem' else 'r_free';direction=1 if group=='cryoem' else -1
 deltas=[val(r,'ffx_ad_gbn2_'+key)-val(r,'prepared_'+key) for r in rr if val(r,'ffx_ad_gbn2_'+key) is not None and val(r,'prepared_'+key) is not None]
 stats=dict(metric=key,n=len(deltas),improved=sum(direction*d>0 for d in deltas),median_change=statistics.median(deltas) if deltas else None)
 measured[group]=stats
 lines.append(f"| {group} | {len(rr)} | {sum(r['converged']=='True' for r in rr)} | {len(deltas)} | {stats['improved']} | {stats['median_change']:+.5f} |" if deltas else f'| {group} | {len(rr)} | 0 | 0 | 0 | NA |')
errors={}
for r in rows:
 for label in ['original','prepared','ffx_ad_gbn2']:
  if r.get(label+'_metrics_state')!='complete':errors.setdefault(r['code'],{})[label]=dict(state=r.get(label+'_metrics_state'),errors=r.get(label+'_metric_errors'))
lines+=['','Changes are final minus prepared: higher CCmask and lower Rfree indicate better data fit. These independent metrics do not all improve; the CSV retains every result, including geometry and EMRinger.','',f'Independent evaluation records with errors or missing metrics: {len(errors)} cases. Exact fields: `{json.dumps(errors,sort_keys=True)}`.','','See `summary.csv` for all 71 cases and `audit.json` for the output checks.']
p=R/'RESULTS.md';s=p.read_text();marker='\n## Full 71-case results\n';s=s.split(marker)[0];p.write_text(s+'\n'.join(lines)+'\n')
(R/'measured_statistics.json').write_text(json.dumps(dict(groups=measured,metric_errors=errors),indent=2));print(json.dumps(dict(summary=summary,statistics=measured,metric_errors=errors),indent=2))

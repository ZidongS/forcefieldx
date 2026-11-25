"""Collect every attempted variant, including interrupted attempts, without rerunning work."""
import json, csv, re, hashlib, subprocess
from pathlib import Path
A=Path('/home/shuzidong/workfile/alphadynamics/application_structure_refinement/FFX/validation/adaptation')
F=A.parents[1]
P=F/'validation/AD_GBn2_71_test_package'
cases={c['code']:c for c in json.loads((P/'cases.json').read_text())}
rows=[]
for out in sorted((A/'runs').glob('*/*')):
 if not out.is_dir(): continue
 sp=out/'status.json'; rp=out/'refinement.json'; ep=out/'evaluation/metrics.json'; lp=out/'refine.log'
 if not any(p.exists() for p in (sp,rp,lp)):continue
 s=json.loads(sp.read_text()) if sp.exists() else {}
 r=s.get('result',{}) or (json.loads(rp.read_text()) if rp.exists() else {})
 m=json.loads(ep.read_text()) if ep.exists() else {}; g=m.get('geometry',{})
 text=lp.read_text(errors='replace') if lp.exists() else ''
 row=dict(code=out.parent.name,variant=out.name,group=cases[out.parent.name]['group'],
  state=s.get('state','incomplete'),job=s.get('job'),converged=r.get('converged'),
  failure_detail=next((v.strip() for v in reversed(text.splitlines()) if v.startswith(('java.lang.','Exception','AssertionError'))),None) if s.get('state')=='failed' else None,
  final_grms=r.get('final_grms'),max_iterations=s.get('max_iterations',r.get('max_iterations')),
  elapsed_seconds=s.get('elapsed'),reason=r.get('optimizer_reason',s.get('reason')),weight=r.get('final_weight',r.get('data_weight',s.get('properties',{}).get('weight'))),
  optimizer=r.get('optimizer'),minimum_contact_A=s.get('minimum_contact_A'),
  iterations_observed=max([int(v) for v in re.findall(r'(?:ADOPT|ADFIRE) iter=(\d+)',text)] or [0]) or None,
  evaluation_state=m.get('state'),metric_errors=json.dumps(m.get('metric_errors',m.get('errors',[]))),
  output_sha256=hashlib.sha256((out/'refined.pdb').read_bytes()).hexdigest() if (out/'refined.pdb').exists() else None)
 for name in ('cc_mask','r_work','r_free','emringer'):row[name]=m.get(name)
 for name,key in [('bond_rms_A','bond'),('angle_rms_deg','angle')]:row[name]=(g.get(key) or {}).get('mean')
 row['clashscore']=(g.get('clash') or {}).get('score')
 row['molprobity_score']=g.get('molprobity_score')
 row['ramachandran_outliers']=(g.get('ramachandran') or {}).get('outliers')
 row['rotamer_outliers']=(g.get('rotamer') or {}).get('outliers')
 rows.append(row)
(A/'experiment_summary.json').write_text(json.dumps(rows,indent=2))
with (A/'experiment_summary.csv').open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
print('attempts',len(rows),'converged',sum(r['converged'] is True for r in rows),'evaluated',sum(r['cc_mask'] is not None or r['r_free'] is not None for r in rows))
for r in rows:
 if r['variant'] in ('fire','legacyBond1000') or r['code'] in ('1m10',):print(json.dumps(r))

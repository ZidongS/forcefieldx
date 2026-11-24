"""Collect all 71 cases, including explicit missing/failed cases, without filtering."""
import json,csv,re,collections,sys
from pathlib import Path
R=Path(__file__).resolve().parent;P=R.parents[1]/'validation/AD_GBn2_71_test_package'
rows=[]
for c in json.loads((P/'cases.json').read_text()):
 code=c['code'];latest=R/'runs'/code/'latest.json';s=json.loads(latest.read_text()) if latest.exists() else {'state':'not_started'}
 row=dict(code=code,group=c['group'],state=s['state'],natoms=s.get('natoms'),job_id=s.get('job_id'),shard=s.get('shard'),elapsed_seconds=s.get('elapsed_seconds'),returncode=s.get('returncode'),directory=s.get('directory'))
 row.update(s.get('result',{}));row['state']=s['state'];row['converged']=s.get('result',{}).get('lbfgs_status')==0 if s['state']=='refined' else None
 if s.get('directory'):
  log=Path(s['directory'])/'refine.log'
  if log.exists():
   matches=re.findall(r'INFO:\s+(\d+)\s*(-\d+\.\d+)\s+(\d+\.\d+)',log.read_text(errors='replace'))
   if matches:row['iterations_completed']=int(matches[-1][0]);row['final_grms']=float(matches[-1][2])
 for label in ['original','prepared','ffx_ad_gbn2']:
  p=R/'metrics'/code/(label+'.json')
  if p.exists():
   d=json.loads(p.read_text());row[label+'_metrics_state']=d['state'];row[label+'_metric_errors']=';'.join(d.get('errors',{}))
   for k in ['r_work','r_free','cc_mask','emringer']:row[label+'_'+k]=d.get(k)
   g=d.get('geometry',{})
   for name,key in [('bond','mean'),('angle','mean'),('clash','score')]:row[label+'_'+name+'_'+key]=(g.get(name) or {}).get(key)
 rows.append(row)
fields=list(dict.fromkeys(k for row in rows for k in row))
with (R/'summary.csv').open('w',newline='') as out:
 w=csv.DictWriter(out,fieldnames=fields);w.writeheader();w.writerows(rows)
summary=dict(total=len(rows),states=dict(collections.Counter(r['state'] for r in rows)),groups={g:dict(collections.Counter(r['state'] for r in rows if r['group']==g)) for g in sorted({r['group'] for r in rows})},converged=sum(r['converged'] is True for r in rows),iteration_capped=sum(r.get('iterations_completed')==200 for r in rows),failed=[r['code'] for r in rows if r['state']=='failed'])
(R/'summary.json').write_text(json.dumps(summary,indent=2));print(json.dumps(summary,indent=2))

"""Independent metrics; never use author or historical refined coordinates as input."""
import json,sys,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parent;FFX=ROOT.parents[1];PACKAGE=FFX/'validation/AD_GBn2_71_test_package'
entry=FFX.parent/'AQuaRef/development_20261124_full71/evaluation_entry.sh'
shard=int(sys.argv[1]);plan=json.loads((ROOT/'campaign_plan.json').read_text())
for c in plan['shards'][shard]:
 code=c['code'];out=ROOT/'metrics'/code;out.mkdir(parents=True,exist_ok=True)
 case={**c,**{k:str(PACKAGE/c[k]) for k in ('model','data','author')}}
 spec=out/'case.json';spec.write_text(json.dumps(case,indent=2))
 models={'original':PACKAGE/c['model'],'prepared':ROOT/'prepared'/code/'prepared.pdb'}
 latest=ROOT/'runs'/code/'latest.json'
 if latest.is_file():
  state=json.loads(latest.read_text())
  if state['state']=='refined':models['ffx_ad_gbn2']=Path(state['directory'])/'refined.pdb'
 for label,pdb in models.items():
  target=out/(label+'.json')
  if target.exists():continue
  with (out/(label+'.log')).open('w') as log:
   try:
    rc=subprocess.run(['bash',str(entry),str(spec),label,str(pdb),str(out)],stdout=log,stderr=subprocess.STDOUT,timeout=1800).returncode
   except subprocess.TimeoutExpired:rc=124
  if not target.exists():target.write_text(json.dumps(dict(state='failed',returncode=rc,code=code,label=label)))
  print(code,label,json.loads(target.read_text()).get('state'),flush=True)

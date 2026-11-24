import json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent;F=R.parents[1];P=F/'validation/AD_GBn2_71_test_package';E=F.parent/'AQuaRef/development_20261124_full71/evaluation_entry.sh'
for c in json.loads((P/'cases.json').read_text()):
 if c['code'] not in ['5zgl','8dat','1yab']:continue
 code=c['code'];out=R/'pilot_metrics'/code;out.mkdir(parents=True,exist_ok=True)
 case={**c,**{k:str(P/c[k]) for k in ('model','data','author')}};spec=out/'case.json';spec.write_text(json.dumps(case))
 for label,pdb in [('original',P/c['model']),('prepared',R/'prepared'/code/'prepared.pdb'),('ffx_ad_gbn2',R/'pilot'/code/'refined.pdb')]:
  if not pdb.exists():continue
  with (out/(label+'.log')).open('w') as log:
   try:rc=subprocess.run(['bash',str(E),str(spec),label,str(pdb),str(out)],stdout=log,stderr=subprocess.STDOUT,timeout=1800).returncode
   except subprocess.TimeoutExpired:rc=124
  print(code,label,rc,flush=True)

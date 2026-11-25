import json,subprocess,sys,hashlib
from pathlib import Path
R=Path(__file__).resolve().parent;F=R.parents[1];P=F/'validation/AD_GBn2_71_test_package';E=F.parent/'AQuaRef/development_20261124_full71/evaluation_entry.sh'
cases={c['code']:c for c in json.loads((P/'cases.json').read_text())}
for i,p in enumerate(sorted((R/'runs').glob('*/*/status.json'))):
 if i%2!=int(sys.argv[1]):continue
 s=json.loads(p.read_text());out=p.parent
 if s['state']!='refined':continue
 target=out/'evaluation/metrics.json'
 if target.exists():continue
 c=cases[s['code']];case={**c,**{k:str(P/c[k]) for k in ('model','data','author')}}
 spec=out/'case.json';spec.write_text(json.dumps(case));ev=out/'evaluation';ev.mkdir(exist_ok=True)
 with (ev/'evaluation.log').open('w') as log:rc=subprocess.run(['bash',str(E),str(spec),'metrics',str(out/'refined.pdb'),str(ev)],stdout=log,stderr=subprocess.STDOUT,timeout=1800).returncode
 print(s['code'],s['variant'],rc,flush=True)

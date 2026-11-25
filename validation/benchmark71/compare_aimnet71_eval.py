import json,hashlib,subprocess,sys,time
from pathlib import Path
R=Path(__file__).resolve().parent;F=R.parents[1];P=F/'validation/AD_GBn2_71_test_package'
B=F.parent/'AQuaRef/reproduction_20261106/benchmark71';E=F.parent/'AQuaRef/development_20261124_full71/evaluation_entry.sh'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
cases=json.loads((P/'cases.json').read_text());historical={c['code']:c for c in json.loads((B/'cases.json').read_text())}
records=[]
for i,c in enumerate(cases):
 if i%2!=int(sys.argv[1]):continue
 code=c['code'];h=historical[code];run=B/'runs/aimnet2'/c['group']/code
 state=json.loads((run/'status.json').read_text());assert state['state']=='complete',code
 pdb=run/'refined.pdb';assert pdb.is_file(),code
 for key in ['model','data']:assert sha(P/c[key])==sha(Path(h[key])),(code,key)
 out=R/'comparison_aimnet2'/code;out.mkdir(parents=True,exist_ok=True)
 case={**c,**{k:str(P/c[k]) for k in ('model','data','author')}}
 (out/'case.json').write_text(json.dumps(case,indent=2))
 target=out/'aimnet2.json'
 if not target.exists():
  with (out/'evaluation.log').open('w') as log:rc=subprocess.run(['bash',str(E),str(out/'case.json'),'aimnet2',str(pdb),str(out)],stdout=log,stderr=subprocess.STDOUT,timeout=1800).returncode
  assert rc==0 and target.exists(),code
 d=json.loads(target.read_text());d.update(model_sha256=sha(pdb),original_model_sha256=sha(P/c['model']),experimental_data_sha256=sha(P/c['data']),source_status=state)
 target.write_text(json.dumps(d,indent=2));records.append(d);print(code,d['state'],flush=True)
(R/'comparison_aimnet2'/('shard'+sys.argv[1]+'.json')).write_text(json.dumps(records,indent=2))

"""Bundle only this campaign's outputs and measured provenance."""
import json,zipfile,hashlib
from pathlib import Path
R=Path(__file__).resolve().parent
summary=json.loads((R/'summary.json').read_text());assert summary['total']==71 and summary['states']=={'refined':71},summary
assert json.loads((R/'audit.json').read_text())['passed']==71
out=R/'FFX_AD_GBn2_71_results.zip'
with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for name in ['summary.csv','summary.json','audit.json','campaign_plan.json','runtime_provenance.json','README.md','RESULTS.md']:
  z.write(R/name,name)
 for p in sorted((R/'runs').glob('*/latest.json')):
  s=json.loads(p.read_text());dest=Path(s['directory']);code=s['code']
  z.write(p,f'refined/{code}/provenance.json')
  for name in ['refined.pdb','refinement.json','refine.log','initial_data_fit.txt','final_data_fit.txt']:
   f=dest/name
   if f.exists():z.write(f,f'refined/{code}/{name}')
  prep=R/'prepared'/code
  for name in ['status.json','charge.txt']:
   z.write(prep/name,f'preparation/{code}/{name}')
  for m in (R/'metrics'/code).glob('*.json'):z.write(m,f'metrics/{code}/{m.name}')
h=hashlib.sha256(out.read_bytes()).hexdigest();(R/'FFX_AD_GBn2_71_results.sha256').write_text(h+'  '+out.name+'\n');print(out,out.stat().st_size,h)

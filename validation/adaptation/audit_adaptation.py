import json,hashlib,math
from pathlib import Path
A=Path('/home/shuzidong/workfile/alphadynamics/application_structure_refinement/FFX/validation/adaptation');F=A.parents[1]
def atoms(p):return [s for s in p.read_text().splitlines() if s.startswith(('ATOM  ','HETATM'))]
audit=[]
for sp in sorted((A/'runs').glob('*/*/status.json')):
 s=json.loads(sp.read_text());out=sp.parent
 if s['state']!='refined':continue
 ini=atoms(F/'validation/benchmark71/prepared'/s['code']/'prepared.pdb');final=atoms(out/'refined.pdb');errors=[]
 if len(ini)!=len(final):errors.append('atom count')
 elif any(a.ljust(80)[:30]!=b[:30] or a.ljust(80)[54:]!=b[54:] for a,b in zip(ini,final)):errors.append('atom identity/noncoordinate records')
 if any(not math.isfinite(float(l[j:j+8])) for l in final for j in (30,38,46)):errors.append('nonfinite coordinates')
 r=s['result']
 if r['final_energy_kcal_mol']>r['initial_energy_kcal_mol']+1e-5:errors.append('objective increase')
 if bool(r['converged'])!=(r['final_grms']<=r['gradient_tolerance']):errors.append('convergence label')
 if not (out/'evaluation/metrics.json').exists():errors.append('missing evaluation')
 else:
  m=json.loads((out/'evaluation/metrics.json').read_text())
  if m.get('errors'):errors.append('evaluation errors: '+str(m['errors']))
  if m.get('state')!='complete':errors.append('evaluation incomplete')
 audit.append(dict(code=s['code'],variant=s['variant'],errors=errors,sha256=hashlib.sha256((out/'refined.pdb').read_bytes()).hexdigest()))
result=dict(checked=len(audit),failed=sum(bool(a['errors']) for a in audit),attempts=audit)
(A/'audit.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))

"""Audit atom identity, coordinate-only output, provenance and numerical checks."""
import json,math,hashlib,sys
from pathlib import Path
from scipy.spatial import cKDTree
R=Path(__file__).resolve().parent;P=R.parents[1]/'validation/AD_GBn2_71_test_package'
passed=[];errors={};failed_cases=[];pending_cases=[];minimum_distances={}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
for c in json.loads((P/'cases.json').read_text()):
 code=c['code']
 try:
  latest=R/'runs'/code/'latest.json'
  if not latest.exists() and '--partial' in sys.argv:pending_cases.append(code);continue
  s=json.loads(latest.read_text())
  if s['state']=='failed':failed_cases.append(code);continue
  if s['state']!='refined' and '--partial' in sys.argv:pending_cases.append(code);continue
  assert s['state']=='refined',s['state']
  prep=R/'prepared'/code/'prepared.pdb';out=Path(s['directory'])/'refined.pdb';result=s['result']
  assert sha(prep)==s['source']['prepared_sha256'];assert sha(out)==s['refined_sha256']
  old=prep.read_text().splitlines();new=out.read_text().splitlines();assert len(old)==len(new)
  n=0;rms=0;coordinates=set();xyz=[];atom_labels=[]
  for a,b in zip(old,new):
   if a.startswith(('ATOM  ','HETATM')):
    assert a.ljust(80)[:30]==b[:30] and a.ljust(80)[54:]==b[54:],'Atom identity or non-coordinate field changed'
    x=[float(a[j:j+8]) for j in [30,38,46]];y=[float(b[j:j+8]) for j in [30,38,46]]
    assert all(math.isfinite(v) for v in y)
    assert tuple(y) not in coordinates,'Coincident output atoms'
    coordinates.add(tuple(y))
    xyz.append(y);atom_labels.append(b[:30].strip())
    rms+=sum((u-v)**2 for u,v in zip(x,y));n+=1
   else:assert a==b,'Non-coordinate record changed'
  assert n==result['natoms']
  distances,indices=cKDTree(xyz).query(xyz,k=2)
  nearest=int(distances[:,1].argmin());minimum_distances[code]=float(distances[nearest,1])
  assert distances[nearest,1]>=0.5,f"Sub-0.5 A atomic contact: {atom_labels[nearest]} / {atom_labels[int(indices[nearest,1])]} distance={distances[nearest,1]:.6f} A"
  assert abs(math.sqrt(rms/n)-result['rms_displacement_A'])<0.001
  assert result['final_energy_kcal_mol']<=result['initial_energy_kcal_mol']+1e-5
  assert result['directional_fd_error']<=0.15+0.02*abs(result['directional_analytic'])+1e-3
  assert math.isfinite(result['final_grms'])
  passed.append(code)
 except Exception as e:errors[code]=str(e)
d=dict(passed=len(passed),codes=passed,failed_cases=failed_cases,pending_cases=pending_cases,minimum_distances_A=minimum_distances,errors=errors);(R/'audit.json').write_text(json.dumps(d,indent=2));print(json.dumps(d,indent=2))
if errors:sys.exit(1)

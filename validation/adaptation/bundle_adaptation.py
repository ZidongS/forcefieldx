import hashlib,json,subprocess,zipfile
from pathlib import Path
A=Path('/home/shuzidong/workfile/alphadynamics/application_structure_refinement/FFX/validation/adaptation');F=A.parents[1]
dest=A/'FFX_AD_GBn2_adaptation_case_studies.zip'
with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as z:
 for name in ('experiment_summary.csv','experiment_summary.json','audit.json','README.md','collect_adaptation.py','audit_adaptation.py','adapt_eval.py','adapt_build.sbatch'):
  p=A/name
  if p.exists():z.write(p,name)
 for p in sorted((A/'runs').rglob('*')):
  if p.is_file() and (p.name in ('status.json','refinement.json','refined.pdb','refine.log','metrics.json','evaluation.log','case.json','initial_data_fit.txt','final_data_fit.txt')):
   z.write(p,p.relative_to(A))
 for name in ('build_24962.log',):
  if (A/name).exists():z.write(A/name,'verification/'+name)
 for name in ('modules/numerics/src/main/java/ffx/numerics/optimization/LineSearch.java','modules/numerics/src/test/java/ffx/numerics/optimization/LineSearchTest.java','modules/numerics/target/surefire-reports/ffx.numerics.optimization.LineSearchTest.txt'):
  z.write(F/name,'retained/'+name)
 z.writestr('retained/changes.patch',subprocess.check_output(['git','diff','4535b6f','HEAD','--','modules/numerics'],cwd=F))
 z.writestr('commits.txt',subprocess.check_output(['git','log','-6','--format=%H %s'],cwd=F))
digest=hashlib.sha256(dest.read_bytes()).hexdigest();(A/(dest.name+'.sha256')).write_text(digest+'  '+dest.name+'\n')
print(dest.name,dest.stat().st_size,digest)

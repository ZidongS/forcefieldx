"""Retry explicitly failed cases with steepest descent; preserve failed attempts."""
import os,sys,json,time,subprocess,hashlib,traceback,signal
from pathlib import Path
R=Path(__file__).resolve().parent;F=R.parents[1];P=F/'validation/AD_GBn2_71_test_package';plan=json.loads((R/'campaign_plan.json').read_text())
def save(p,s):
 t=p.with_suffix('.tmp');t.write_text(json.dumps(s,indent=2));t.replace(p)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
failed=[]
codes=sys.argv[1:]
if codes==['--failed']:
 codes=[p.parent.name for p in sorted((R/'runs').glob('*/latest.json')) if json.loads(p.read_text())['state']=='failed']
for code in codes:
 latest=R/'runs'/code/'latest.json';previous=json.loads(latest.read_text())
 if previous['state']=='refined':continue
 assert previous['state'] in ['failed','interrupted'],previous['state']
 c=next(c for shard in plan['shards'] for c in shard if c['code']==code);dest=latest.parent/('attempt%02d'%(len(list(latest.parent.glob('attempt*')))+1));dest.mkdir()
 prior_log=(Path(previous['directory'])/'refine.log').read_text(errors='replace')
 memory=7 if 'Directional derivative mismatch' in prior_log else 0
 s=dict(code=code,group=c['group'],natoms=c['natoms'],state='running',directory=str(dest),started=time.time(),job_id=os.environ.get('SLURM_JOB_ID'),shard=previous['shard'],source=c,git_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=F,text=True).strip(),optimizer='lbfgs' if memory else 'steepest_descent',retry_reason='Expanded finite-difference steps; original optimizer retained for check-only failures; steepest descent for L-BFGS instability. Same target and tolerance.',retry_source_sha256=sha(R/'FFXRefine71Retry.java'))
 save(latest,s)
 try:
  prep=R/'prepared'/code;assert sha(prep/'prepared.pdb')==c['prepared_sha256'];assert sha(prep/'parameters.json')==c['parameters_sha256']
  cmd=['xvfb-run','-a','java','-Dpj.nt=1',f'-Dffx71.memory={memory}','--enable-native-access=ALL-UNNAMED','--add-modules','jdk.incubator.vector','-Xmx16g','-cp',os.environ['FFX71_CP'],'ffx.potential.FFXRefine71Retry',str(F),str(prep),str(P/c['data']),str(dest),c['group'],'200'];s['command']=cmd;save(latest,s)
  with (dest/'refine.log').open('w') as log:
   p=subprocess.Popen(cmd,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
   try:rc=p.wait(timeout=6*3600)
   except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGKILL);rc=124
  s['returncode']=rc;assert rc==0,f'FFX exit {rc}'
  result=json.loads((dest/'refinement.json').read_text());assert result['state']=='refined';s.update(state='refined',result=result,refined_sha256=sha(dest/'refined.pdb'))
 except Exception:s.update(state='failed',error=traceback.format_exc());failed.append(code)
 s['elapsed_seconds']=time.time()-s['started'];save(dest/'status.json',s);save(latest,s);print(json.dumps(s),flush=True)
if failed:sys.exit(1)

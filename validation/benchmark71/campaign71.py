"""Run a balanced, resumable single-GPU shard of the FFX 71-case campaign."""
import os,sys,json,time,subprocess,hashlib,traceback,signal
from pathlib import Path
ROOT=Path(__file__).resolve().parent
FFX=ROOT.parents[1]
PACKAGE=FFX/'validation/AD_GBn2_71_test_package'
def save(p,d):
 t=p.with_suffix('.tmp');t.write_text(json.dumps(d,indent=2));t.replace(p)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(command,log,timeout):
 with log.open('w') as stream:
  proc=subprocess.Popen(command,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
  try:return proc.wait(timeout=timeout)
  except subprocess.TimeoutExpired:
   os.killpg(proc.pid,signal.SIGTERM)
   try:proc.wait(timeout=30)
   except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait()
   return 124
if sys.argv[1]=='plan':
 cases=json.loads((PACKAGE/'cases.json').read_text());shards=[[],[]];cost=[0,0]
 for c in cases:
  s=json.loads((ROOT/'prepared'/c['code']/'status.json').read_text())
  assert s['state']=='prepared',s
  assert sha(ROOT/'prepared'/c['code']/'prepared.pdb')==s['prepared_sha256']
  c.update(natoms=s['natoms'],prepared_sha256=s['prepared_sha256'],parameters_sha256=s['parameters_sha256'])
 for c in sorted(cases,key=lambda c:-c['natoms']**2):
  k=min(range(2),key=lambda k:cost[k]);shards[k].append(c);cost[k]+=c['natoms']**2
 plan=dict(shards=shards,estimated_cost=cost,iterations=200,gradient_rms_tolerance=1.0,data_weight=0.05,git_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=FFX,text=True).strip())
 save(ROOT/'campaign_plan.json',plan);print([(len(s),c) for s,c in zip(shards,cost)])
else:
 shard=int(sys.argv[1]);plan=json.loads((ROOT/'campaign_plan.json').read_text());cases=plan['shards'][shard]
 cp=os.environ['FFX71_CP'];failures=[]
 for c in cases:
  code=c['code'];dest=ROOT/'runs'/code;dest.mkdir(parents=True,exist_ok=True);latest=dest/'latest.json'
  if latest.exists() and json.loads(latest.read_text()).get('state')=='refined':continue
  attempt=dest/('attempt%02d'%(len(list(dest.glob('attempt*')))+1));attempt.mkdir()
  prep=ROOT/'prepared'/code
  state=dict(code=code,group=c['group'],natoms=c['natoms'],state='running',directory=str(attempt),started=time.time(),job_id=os.environ.get('SLURM_JOB_ID'),shard=shard,source=c,git_commit=plan['git_commit'])
  save(latest,state)
  try:
   assert sha(prep/'prepared.pdb')==c['prepared_sha256']
   assert sha(prep/'parameters.json')==c['parameters_sha256']
   threads=1 if c['group']=='cryoem' else 4
   command=['xvfb-run','-a','java',f'-Dpj.nt={threads}',f'-Dffx71.threads={threads}','-Dffx71.sampling=0.6','--enable-native-access=ALL-UNNAMED','--add-modules','jdk.incubator.vector','-Xmx16g','-cp',cp,'ffx.potential.FFXRefine71',str(FFX),str(prep),str(PACKAGE/c['data']),str(attempt),c['group'],str(plan['iterations'])]
   state['command']=command;save(latest,state)
   rc=run(command,attempt/'refine.log',6*3600);state['returncode']=rc
   assert rc==0,f'FFX exit {rc}'
   result=json.loads((attempt/'refinement.json').read_text());assert result['state']=='refined'
   state.update(state='refined',result=result,refined_sha256=sha(attempt/'refined.pdb'))
  except Exception:
   state.update(state='failed',error=traceback.format_exc());failures.append(code)
  state['elapsed_seconds']=time.time()-state['started'];save(attempt/'status.json',state);save(latest,state)
  print(json.dumps(state),flush=True)
 save(ROOT/f'shard{shard}_summary.json',dict(shard=shard,failures=failures,finished=time.time()))
 if failures:sys.exit(1)

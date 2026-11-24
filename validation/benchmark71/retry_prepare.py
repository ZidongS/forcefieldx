from pathlib import Path
import os,json,subprocess,concurrent.futures,time
D=Path(__file__).resolve().parent
F=D.parents[1];OLD=F.parent/'AQuaRef/development_20261124_full71'
P=F/'validation/AD_GBn2_71_test_package'
cases=json.loads((P/'cases.json').read_text())
def run(code):
 c=next(x for x in cases if x['code']==code)
 r=D/'preparation_record_h_contact'/code;r.mkdir(parents=True,exist_ok=True)
 env=os.environ.copy();env.update(AD_STAGE='reduce',PYTHONPATH=str(OLD),LIBTBX_BUILD=str(OLD/'envs/upstream/lib/python3.12/site-packages/build'))
 args=[str(OLD/'envs/upstream/bin/python'),str(D/'reduce_record_h_contact.py'),str(P/c['model']),'qmready=True','output.file_name=prepared.pdb','output.description_file_name=description.txt','overwrite=True']
 start=time.time()
 with (r/'prepare.log').open('w') as log: p=subprocess.run(args,cwd=r,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=3600)
 status=dict(code=code,state='prepared' if p.returncode==0 and (r/'prepared.pdb').is_file() else 'failed',directory=str(r),returncode=p.returncode,seconds=time.time()-start,command=args)
 (r/'status.json').write_text(json.dumps(status,indent=2));print(json.dumps(status),flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as ex:list(ex.map(run,['8dl8','1wl3','4jp6']))

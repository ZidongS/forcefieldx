import sys,os,json,hashlib,shutil,subprocess,traceback,importlib.util
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parent
FFX=ROOT.parents[1]
PACKAGE=FFX/'validation/AD_GBn2_71_test_package'
OLD=FFX.parent/'AQuaRef/development_20261124_full71'
TOOLKIT=Path('/home/shuzidong/workfile/alphadynamics/zidong_analysis_toolkit')
sys.path.insert(0,str(TOOLKIT))
# Import the deployed solvent module only to initialize its internal OpenMM dependencies.
from AD_singlepoint_implicit.singlepoint_implicit import solvent
spec=importlib.util.spec_from_file_location('parameter_source',OLD/'qrefine/plugin/ase/alphadynamics_worker.py')
gb=importlib.util.module_from_spec(spec);spec.loader.exec_module(gb)
def digest(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
cases=json.loads((PACKAGE/'cases.json').read_text())
for item in json.loads((PACKAGE/'source_manifest.json').read_text()):
    p=PACKAGE/item['path']
    assert p.stat().st_size==item['size'] and digest(p)==item['sha256'],str(p)
print('All source manifest hashes verified',flush=True)
rows=[]
for c in cases:
    dest=ROOT/'prepared'/c['code'];dest.mkdir(parents=True,exist_ok=True)
    if (dest/'status.json').is_file() and json.loads((dest/'status.json').read_text()).get('state')=='prepared':
        rows.append(json.loads((dest/'status.json').read_text()));continue
    state=dict(code=c['code'],group=c['group'],state='preparing',input_sha256=digest(PACKAGE/c['model']))
    try:
        oldcase=next(x for x in json.loads((OLD/'cases.json').read_text()) if x['code']==c['code'])
        assert digest(oldcase['model'])==state['input_sha256'],'Preparation source is not the packaged input'
        prep=json.loads((OLD/'prepared'/c['code']/'latest.json').read_text())
        retry=ROOT/'preparation_record_h_contact'/c['code']/'status.json'
        if prep['state']!='prepared' and retry.is_file():prep=json.loads(retry.read_text())
        assert prep['state']=='prepared',prep
        pdb=Path(prep['directory'])/'prepared.pdb'
        shutil.copyfile(pdb,dest/'prepared.pdb')
        lines=pdb.read_text().splitlines(); records=[l.ljust(80) for l in lines if l.startswith(('ATOM  ','HETATM'))]
        assert records
        if len({l[16] for l in records}-{' '})>0:
            # AD evaluates a physical conformer, not overlapping fractional atoms.
            # Choose the highest-occupancy residue conformer; ties choose A first.
            scores={}
            for l in records:
                if l[16]!=' ':scores.setdefault(l[21:27],{}).setdefault(l[16],[]).append(float(l[54:60]))
            choices={key:sorted(v,key=lambda a:(-sum(v[a])/len(v[a]),a))[0] for key,v in scores.items()}
            selected=[]; kept_serials=set()
            for l in lines:
                if l.startswith(('ATOM  ','HETATM')):
                    l=l.ljust(80)
                    if l[16]!=' ' and l[16]!=choices[l[21:27]]:continue
                    if l[16]!=' ':l=l[:16]+' '+l[17:54]+'  1.00'+l[60:]
                    kept_serials.add(l[6:11]);selected.append(l)
                elif l.startswith('ANISOU'):
                    if l[6:11] in kept_serials:selected.append(l[:16]+' '+l[17:])
                else:selected.append(l)
            # Retain anisotropic records only for selected serials; coordinates only refined.
            selected=[l for l in selected if not l.startswith(('CONECT','MASTER'))]
            (dest/'prepared.pdb').write_text('\n'.join(selected)+'\n')
            lines=selected;records=[l.ljust(80) for l in lines if l.startswith(('ATOM  ','HETATM'))]
            state['single_conformer_selection']=choices
            state['selected_conformer_occupancy_normalized']=True
        pdb=dest/'prepared.pdb'
        symbols=[l[76:78].strip().capitalize() for l in records]
        assert all(s and s!='X' for s in symbols)
        xyz=np.array([[float(l[30:38]),float(l[38:46]),float(l[46:54])] for l in records])
        assert len(np.unique(xyz,axis=0))==len(xyz),'Coincident atoms'
        metadata=[];rid=-1;last=None;chain_index=-1;lastchain=None
        for l in records:
            chain=l[21];key=(chain,l[22:27],l[17:20])
            if key!=last:rid+=1;last=key
            if chain!=lastchain:chain_index+=1;lastchain=chain
            metadata.append(dict(chain=chain,chain_index=chain_index,residue_index=rid,resid=l[22:26].strip(),resname=l[17:20].strip(),name=l[12:16].strip()))
        radii,scales,extra,parents=gb.gbn2_parameters(symbols,xyz,metadata)
        env=os.environ.copy();env['PYTHONPATH']=str(OLD);env['LIBTBX_BUILD']=str(OLD/'envs/upstream/lib/python3.12/site-packages/build')
        python=OLD/'envs/upstream/bin/python'
        q=subprocess.run([str(python),str(ROOT/'charge_case.py'),str(dest/'prepared.pdb')],env=env,capture_output=True,text=True,timeout=180)
        (dest/'charge.log').write_text(q.stdout+q.stderr)
        assert q.returncode==0,'Charge calculation failed: '+q.stderr[-1000:]
        charge=float(next(l.split('=',1)[1] for l in q.stdout.splitlines() if l.startswith('FFX_FORMAL_CHARGE=')))
        assert abs(charge-round(charge))<1e-6
        params=dict(atoms=[dict(symbol=s,name=m['name'],charge_e=charge/len(records),radius_A=float(r),scale=float(sc),alpha=float(e[0]),beta=float(e[1]),gamma=float(e[2])) for s,m,r,sc,e in zip(symbols,metadata,radii,scales,extra)])
        (dest/'parameters.json').write_text(json.dumps(params))
        (dest/'charge.txt').write_text(str(charge))
        (dest/'parents.json').write_text(json.dumps(parents))
        state.update(state='prepared',natoms=len(records),charge=charge,prepared_sha256=digest(pdb),preparation=prep,parameter_function_sha256=digest(OLD/'qrefine/plugin/ase/alphadynamics_worker.py'),parameters_sha256=digest(dest/'parameters.json'),anisou_records=sum(l.startswith('ANISOU') for l in lines))
    except Exception:state.update(state='failed',error=traceback.format_exc())
    (dest/'status.json').write_text(json.dumps(state,indent=2));rows.append(state)
    print(json.dumps({k:v for k,v in state.items() if k in ('code','state','natoms','charge','error')}),flush=True)
(ROOT/'preparation_summary.json').write_text(json.dumps(rows,indent=2))

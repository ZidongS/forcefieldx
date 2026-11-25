#!/usr/bin/env python3
"""Persistent, heavy-atom monomer geometry prior; dimensionless squared-z target.

Bonds, angles, chirality and planarity are enabled. The optional --nonbonded
flag also adds library repulsion, including crystal symmetry contacts. No
torsion, Ramachandran or reference-coordinate terms are added. This is an
explicit extra prior, not part of the AD+GBn2 chemical energy.
"""
import argparse,os,sys,math

def main():
 p=argparse.ArgumentParser();p.add_argument('--pdb',required=True);p.add_argument('--check',action='store_true');p.add_argument('--nonbonded',action='store_true');args=p.parse_args()
 wire=os.fdopen(os.dup(sys.stdout.fileno()),'w',buffering=1);os.dup2(sys.stderr.fileno(),sys.stdout.fileno())
 import iotbx.pdb,mmtbx.model
 from libtbx.utils import null_out
 from cctbx.array_family import flex
 from cctbx.geometry_restraints.flags import flags
 inp=iotbx.pdb.input(file_name=args.pdb)
 hierarchy=inp.construct_hierarchy();full=hierarchy.atoms();n=len(full)
 def identity(atom):
  label=atom.fetch_labels()
  return (label.model_id,label.chain_id,label.resseq,label.icode,label.resname,label.altloc,label.name)
 lookup={identity(a):i for i,a in enumerate(full)}
 assert len(lookup)==n,'Ambiguous input atom identity'
 model=mmtbx.model.manager(model_input=inp,log=null_out()).remove_hydrogens()
 params=mmtbx.model.manager.get_default_pdb_interpretation_params()
 params.pdb_interpretation.flip_symmetric_amino_acids=False
 params.pdb_interpretation.use_neutron_distances=True
 params.pdb_interpretation.restraints_library.cdl=False
 model.process(make_restraints=True,pdb_interpretation_params=params)
 sites=model.get_sites_cart();heavy=model.get_hierarchy().atoms()
 indices=[lookup[identity(a)] for a in heavy]
 assert len(indices)==len(set(indices))
 assert len(indices)==sum(a.element.strip().upper() not in ('H','D') for a in full)
 assert all(max(abs(x-y) for x,y in zip(site,full[i].xyz))<1e-8 for site,i in zip(sites,indices)), 'Geometry processing changed coordinates'
 geo=model.get_restraints_manager().geometry
 enabled=flags(default=False,bond=True,angle=True,chirality=True,planarity=True,nonbonded=args.nonbonded)
 def evaluate(x):return geo.energies_sites(sites_cart=x,compute_gradients=True,flags=enabled)
 if args.check:
  probe=sites.deep_copy();v=list(probe[0]);v[0]+=.03;probe[0]=tuple(v);result=evaluate(probe);worst=0
  for i in (0,len(probe)//2,len(probe)-1):
   for axis in range(3):
    xp=probe.deep_copy();xm=probe.deep_copy();a=list(xp[i]);b=list(xm[i]);a[axis]+=1e-5;b[axis]-=1e-5;xp[i]=tuple(a);xm[i]=tuple(b)
    numeric=(evaluate(xp).target-evaluate(xm).target)/2e-5
    worst=max(worst,abs(numeric-result.gradients[i][axis]))
  assert worst<1e-3,worst
  print('GEOMETRY_FD_PASS',n,len(indices),'max_error',worst,'target',result.target,flush=True)
  return
 wire.write(f'GEOMETRY 1 READY {n}\n')
 from cctbx.eltbx import tiny_pse
 expected_numbers=[1 if a.element.strip().upper()=='D' else tiny_pse.table(a.element.strip()).atomic_number() for a in full]
 initialized=False
 for line in sys.stdin:
  if line.split()==['INIT',str(n)]:
   assert not initialized,'Duplicate initialization'
   for i,atom in enumerate(full):
    row=sys.stdin.readline().split();assert len(row)==5,'Invalid initial atom record'
    assert int(row[0])==expected_numbers[i] and row[1]==atom.name.strip(),'Geometry reference atom identity/order mismatch'
    xyz=tuple(map(float,row[2:]));assert all(math.isfinite(t) for t in xyz)
    assert max(abs(x-y) for x,y in zip(xyz,atom.xyz))<=.0011,'Geometry reference coordinates do not match current assembly'
   initialized=True;wire.write('INITIALIZED\n');continue
  if line.strip()=='QUIT':return
  assert initialized,'Geometry initialization required'
  assert line.split()==['EVAL',str(n)],'Invalid geometry request'
  coords=[tuple(map(float,sys.stdin.readline().split())) for _ in range(n)]
  assert all(len(v)==3 and all(math.isfinite(t) for t in v) for v in coords)
  result=evaluate(flex.vec3_double([coords[i] for i in indices]));gradient=[0.]*(3*n)
  for j,i in enumerate(indices):gradient[3*i:3*i+3]=result.gradients[j]
  assert math.isfinite(result.target) and all(math.isfinite(t) for t in gradient)
  wire.write('RESULT '+' '.join(format(t,'.17g') for t in [result.target,*gradient])+'\n')
if __name__=='__main__':main()

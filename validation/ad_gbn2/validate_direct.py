import sys,json,pathlib
import numpy as np
toolkit=pathlib.Path('/home/shuzidong/workfile/alphadynamics/zidong_analysis_toolkit')
sys.path.insert(0,str(toolkit))
from AD_singlepoint_implicit import make_solvent,ImplicitSinglePoint
case=toolkit/'AD_singlepoint_implicit/examples'
lines=(case/'water.xyz').read_text().splitlines()[2:5]
symbols=[v.split()[0] for v in lines]
xyz=np.array([[float(v) for v in line.split()[1:4]] for line in lines])
with make_solvent(model='GBn2', parameters=case/'water_parameters.json', platform='CUDA', precision='double',charge_potential_method='analytic') as solvent:
    with ImplicitSinglePoint(solvent,charge_mode='aux',aux_response='total') as engine:
        r=engine.compute(symbols,xyz,target_charge=0)
        gradient=-r['forces']; fd=np.zeros_like(xyz); step=1e-3
        for i in range(3):
            for j in range(3):
                plus=xyz.copy(); minus=xyz.copy(); plus[i,j]+=step; minus[i,j]-=step
                fd[i,j]=(engine.compute(symbols,plus,target_charge=0)['pe']-engine.compute(symbols,minus,target_charge=0)['pe'])/(2*step)
        error=float(abs(gradient-fd).max())
        result=dict(energy_eV=r['pe'],forces_eV_A=r['forces'].tolist(),finite_difference_max_error_eV_A=error,finite_difference=fd.tolist(),solvent=r['solvent'],aux_response=r['aux_response'],aux_evaluation=r['aux_evaluation'])
        pathlib.Path('direct.json').write_text(json.dumps(result,indent=2,default=str)+'\n')
        print(json.dumps(result,default=str),flush=True)
        assert error<3e-3,error
        assert r['solvent']['platform']=='CUDA'

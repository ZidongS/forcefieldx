import sys,json
import iotbx.pdb,mmtbx.model
from mmtbx.ligands import electrons
from libtbx.utils import null_out
params=mmtbx.model.manager.get_default_pdb_interpretation_scope().extract()
params.pdb_interpretation.automatic_linking.link_metals=True
params.pdb_interpretation.allow_polymer_cross_special_position=True
model=mmtbx.model.manager(model_input=iotbx.pdb.input(file_name=sys.argv[1]),log=null_out())
model.process(make_restraints=True,pdb_interpretation_params=params)
charge=electrons.run(model=model)
print('FFX_FORMAL_CHARGE='+json.dumps(float(charge)))

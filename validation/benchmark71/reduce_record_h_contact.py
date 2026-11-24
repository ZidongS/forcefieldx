"""Retain and explicitly report initial H contacts for subsequent AD refinement.

Uses the same qmready preparation, caps and water H as the other cases.
Only the final short-H-contact rejection is converted to an audit message.
Coincident coordinates and heavy-heavy contacts remain fatal.
"""
import re
from iotbx.cli_parser import run_program
from mmtbx.programs.reduce2 import Program as ReduceProgram
from libtbx.utils import Sorry
class Program(ReduceProgram):
    def _CheckQmreadyClashes(self,model):
        sites=model.get_sites_cart()
        assert len(set(tuple(x) for x in sites))==len(sites),'Coincident prepared atoms'
        try:return super()._CheckQmreadyClashes(model)
        except Sorry as error:
            labels=re.findall(r'pdb="([^"]+)"',str(error))
            if len(labels)!=2 or not any(label[:4].strip().startswith(('H','D')) for label in labels):raise
            print('FFX_INITIAL_H_CONTACT_RETAINED: '+str(error),file=self.logger)
run_program(program_class=Program)

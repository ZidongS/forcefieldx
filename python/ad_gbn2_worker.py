#!/usr/bin/env python3
"""Persistent AD+GBn2 worker. stdout is reserved for the versioned wire protocol."""
import argparse
import contextlib
import sys
import traceback


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--toolkit', required=True)
    params = parser.add_mutually_exclusive_group(required=True)
    params.add_argument('--parameters')
    params.add_argument('--prmtop')
    parser.add_argument('--charge', type=float)
    parser.add_argument('--platform', default='CUDA', choices=['CUDA', 'Reference', 'auto'])
    args = parser.parse_args()
    wire = sys.stdout
    # Native libraries and model diagnostics must not corrupt protocol stdout.
    import os
    wire = os.fdopen(os.dup(sys.stdout.fileno()), 'w', buffering=1)
    os.dup2(sys.stderr.fileno(), sys.stdout.fileno())
    with contextlib.redirect_stdout(sys.stderr):
        sys.path.insert(0, args.toolkit)
        import numpy as np
        from AD_singlepoint_implicit import ImplicitSinglePoint, make_solvent
        solvent = make_solvent(model='GBn2', parameters=args.parameters, prmtop=args.prmtop,
                              platform=args.platform, precision='double',
                              charge_potential_method='analytic')
        engine = ImplicitSinglePoint(solvent, charge_mode='aux', aux_response='total')
        try:
            reported = False
            wire.write('ADGBN2 1 READY\n')
            for line in sys.stdin:
                if line.strip() == 'QUIT':
                    break
                fields = line.split()
                if len(fields) != 2 or fields[0] != 'EVAL':
                    raise ValueError('expected EVAL atom_count')
                n = int(fields[1])
                if n <= 0:
                    raise ValueError('atom_count must be positive')
                symbols, names, xyz = [], [], []
                for _ in range(n):
                    row = sys.stdin.readline().split()
                    if len(row) != 5:
                        raise ValueError('expected symbol name x y z')
                    symbols.append(row[0]); names.append(row[1]); xyz.append([float(v) for v in row[2:]])
                result = engine.compute(symbols, np.asarray(xyz), target_charge=args.charge, atom_names=names)
                energy = float(result['energy_eV'])
                force = np.asarray(result['forces'])
                if force.shape != (n, 3) or not np.isfinite(force).all() or not np.isfinite(energy):
                    raise ValueError('invalid AD energy/force')
                wire.write('RESULT ' + ' '.join(format(v, '.17g') for v in [energy, *force.ravel()]) + '\n')
                if not reported:
                    print('ADGBN2 metadata:', result['solvent'], 'aux_evaluation=', result['aux_evaluation'], flush=True)
                    reported = True
        finally:
            engine.close()


if __name__ == '__main__':
    try:
        main()
    except Exception:
        traceback.print_exc(file=sys.stderr)
        raise SystemExit(1)

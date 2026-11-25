# AD+GBn2 refinement adaptations

FFX keeps its conventional minimizer and the pure AD+GBn2 chemical target as
the defaults. The following options are explicit and currently support one
assembly, coordinate-only refinement, and all atoms active.

```properties
ad-gbn2 true
ad-gradient-optimizer true
ad-max-atom-step 0.03
ad-geometry-weight 0.05
ad-geometry-python /absolute/path/to/cctbx/environment/bin/python
ad-geometry-worker /absolute/path/to/FFX/python/ad_geometry_worker.py
ad-geometry-reference /absolute/path/to/the/starting/prepared.pdb
ad-geometry-nonbonded true
```

The gradient optimizer uses unscaled Cartesian Angstroms, respects the requested
L-BFGS memory, reports through the FFX optimization listener and limits the
largest per-atom displacement to `ad-max-atom-step`. Its bounded line search
uses directional gradients instead of subtracting quantized ML total energies.
It never relaxes the requested GRMS threshold after a stalled search, and it
restores the last accepted coordinates on rejected trials. Individual accepted
steps are not guaranteed to decrease total energy, so callers should inspect the
final objective, convergence state and independent model-quality metrics.

The geometry term is an optional additional prior, not part of pure AD+GBn2:

```text
E_total = E_AD+GBn2 + w_data * E_experiment + w_geometry * G_monomer
```

`G_monomer` is CCTBX's dimensionless squared-z target for heavy-atom bond,
angle, chirality and planarity restraints. With `ad-geometry-nonbonded true`, it
also includes monomer-library repulsion and crystal-symmetry contacts. Its
coefficient converts the target to kcal/mol. The worker uses neutron distances,
disables CDL and symmetric flips, and requires CCTBX/mmtbx with a monomer
library. Hydrogens retain the AD+GBn2 target and get no added geometry gradient.

The starting PDB supplies topology, atom identities and symmetry only. The
worker handshake checks element, name, count, order and coordinates against the
assembly. A mismatch or worker error stops refinement; it never silently drops
the added term. There are no positional, torsion or Ramachandran priors.

Weight zero disables the geometry term. A positive value requires all paths.
Joint B-factor/occupancy refinement, atom selections and multiple assemblies
are rejected. The screened setting `w_geometry=0.05`, `w_data=0.05` improved
geometry across eight evaluable cases and produced mixed fit changes, including
small fit losses on held-out 4xcr and 5zgl. See the round-two report under
`outputs/ffx_adaptation_round2/` before applying it to production data.

The focused Maven build and regression test command used for this integration:

```bash
bash mvnw -B -ntp -DskipTests=false \
  -Dtest=LineSearchTest,ADGradientOptimizerTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dcheckstyle.skip \
  -Dlicense.skip=true -Dmaven.javadoc.skip=true -Dexec.skip=true install
```

## Optional hydrogen geometry (round three)

```properties
ad-geometry-hydrogens true
```

This option includes existing hydrogen atoms in the monomer-library geometry
and optional repulsion target. The default is `false`, preserving the original
heavy-atom behavior and constructor API. It adds no atoms and keeps the same
element/name/order/coordinate handshake. The worker's `--check --hydrogens`
also probes a hydrogen gradient when one is present.

The screened combination `ad-geometry-weight 0.1`, experimental data weight
`0.075`, and standard repulsion gave mixed improvements: five of six additional
or repeated case pairs improved fit, while 7kzn lost a small amount of CC.
8dat gained CC but had more clashes, and 1yab's clashes increased. These are
development cases and do not establish a universally better preset. A direct
same-weight hydrogen ablation and the full experiment evidence are described
in the round-three report. Data-weight changes are a separate contributor to
fit improvements and must not be attributed to hydrogen geometry alone.

The independently scaled repulsion and staged-release experimental strategies
were rejected and are not exposed as production options. In particular, the
strong-repulsion setting failed the 7kzn repeat fit-loss criterion. The default
geometry and data weights have not been changed.

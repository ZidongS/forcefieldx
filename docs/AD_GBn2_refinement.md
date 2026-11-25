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

## Round four: rejected scale and density-buffer experiments

The 2026-11-25 development screen retained no new implementation or preset.
Re-fitting diffraction scales between coordinate stages did not provide stable
quality gains on four X-ray cases and worsened 1yab clashscore/MolProbity.
Reducing the diagnostic runner's scattering buffer from 4 to 1 Angstrom gave
mixed full-budget results: 1yab Rfree 0.251347 -> 0.258355, 4xcr 0.288890 ->
0.284623, and 5zgl 0.120240 -> 0.120467. The 1yab loss exceeded the predefined
0.002 limit. The 2fma 1-Angstrom run reached 300 steps at GRMS 1.662; its
4-Angstrom control was stopped after the pre-optimization directional finite
difference log stopped progressing for over 15 minutes. Neither the speedup
nor equivalent refinement quality was established. DiffractionData's own
scattering-buffer default remains 0.75; the 4-Angstrom value was the diagnostic
runner's explicit baseline, not the library default.

The final matrix has 30 attempts, 29 evaluated structures, 25 converged runs,
four iteration-limit outputs and one cancelled control. All 29 output identity,
finite-coordinate, final-objective, convergence-label and metric audits passed.
Earlier guard-development attempts are preserved separately in the archive.
The experiment directory was moved outside the production checkout; no
rejected Java implementation or tuning preset was added. The evidence ZIP
SHA-256 is bec63d0f5ba25257c7ee204125d9ea6841a29ac8130e5c5eab6d245d448aa0e4.

Rfree is descriptive for these development experiments: FFX's scale and
SigmaA estimation use free-R-labelled reflections. Historical AIMNet2 results
are whole-pipeline comparisons, not an isolated potential ablation.

## Native ADP restraint repair (round five)

The native anisotropic/anisotropic B-factor similarity branch reused the first
atom's tensor buffer when loading the second atom. This overwrote the first
tensor, making the determinant difference and restoring gradient incorrectly
zero for unequal determinants. The repair uses independent reusable buffers;
the existing restraint formula and coefficient are unchanged. The pair branch
is extracted into a package-private helper for direct energy/gradient tests,
while the production loop retains scratch-buffer reuse across atom pairs.

The original branch fails two of five focused tests; the repaired branch
passes all five, including finite differences of all twelve U components,
positive/zero penalties, exchange symmetry, and energy-only state preservation.
The full eleven-module Maven reactor build passed with eighteen selected
regression tests (not the entire test suite). This is a native ADP correctness
repair. Current coordinate-only AD refinement does not execute the B-factor
restraint branch; no improvement to the 71-system coordinate benchmark is
established by these tests.

The separate fixed-coordinate global/residue log-B development screen did not
meet its predeclared quality gates and adds no production preset. Residue-wise
updates improved 4xcr Rfree by 0.006621 but worsened 1yab by 0.007926 and widened
its Rfree/Rwork gap by 0.008076. All six produced outputs passed identity,
coordinate/occupancy preservation, bounds and metric audits. Three 1u87 runs
failed the frozen directional derivative gate before optimization. No threshold
was relaxed. A frozen-coordinate accuracy diagnostic with denser FFT sampling
0.4 and 0.3 passed the same gate (absolute errors about 0.030 and 0.033 versus
0.432 at sampling 0.6), but did not optimize coordinates or B factors and is
not evidence of better refined geometry. Default sampling and AD weights stay
unchanged. Raw development evidence is archived outside the production checkout.

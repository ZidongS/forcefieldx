# AD+GBn2 integration validation — 2026-11-25

Validated on Wang Lab Slurm using FFX 26.0.4, Temurin Java 25.0.5.1,
the existing AD conda environment and one CUDA GPU. No model weights or
credentials are stored in this repository.

* Full Maven reactor through refinement: job 24825, COMPLETED, exit 0:0.
* GPU Java integration: job 24838, COMPLETED, exit 0:0, 43 seconds.
* Energy agrees with the independent Python calculation:
  -48005.928481518850 kcal/mol (-2081.734086897024 eV).
* Force-to-gradient bridge maximum error: 0 at printed precision.
* Cartesian finite-difference maximum error: 0.027010926 kcal/mol/Angstrom
  (step 0.001 Angstrom; tolerance 0.07).
* Scaled coordinates, unchanged caller arrays, refinement gradient mapping
  and frozen-atom checks: PASS.
* FFX L-BFGS: status 0, 5 iterations; energy decreased from
  -48005.928481518850 to -48006.032899314974 kcal/mol.
* Actual RefinementEnergy + periodic synthetic CCP4 target: PASS.
  Chemical energy -48005.929578543306; map target -9.274873810198;
  map weight 0.3; combined total -48008.712040686370.
  Weighted gradient-sum error 4.1633363e-16; combined finite-difference
  maximum error 0.018503906 kcal/mol/Angstrom.
* Metadata confirms GBn2/ACE, CUDA double precision, analytic GB response,
  AUX total response, full dq/dR and single_forward evaluation.

The first two Java attempts (24824 and 24837) failed before AD evaluation
because Java3D needed a working X11 display. A user-local Xvfb plus XKB
dependencies resolved that; the successful job used the committed script.
`setup_xvfb.sh` reproduces the local Debian package extraction/relocation.
Java incubator/native-access and PyTorch CPU ISA warnings remained in
stderr; all numerical assertions and process exit checks passed.

This validates the integration with three-atom water and a synthetic density
map. It does not validate protein-scale accuracy, experimental refinement
quality, or a particular protein's topology/protonation setup.

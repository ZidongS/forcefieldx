# AD+GBn2 refinement backend

`ADGBn2Potential` implements the FFX `Potential` interface and is selected by
the refinement energy path with `ad-gbn2 true`. The default FFX potential is
unchanged when this option is absent.

The backend combines the updated `AD_singlepoint` vacuum model with GBn2/ACE
mechanical embedding and complete `aux_response=total` charge response. It
converts eV to kcal/mol and converts forces to FFX coordinate gradients.

Configure `ad-python`, `ad-worker`, `ad-toolkit`, exactly one of `ad-parameters`
or `ad-prmtop`, `ad-charge`, `ad-gb-platform` (default `CUDA`) and
`ad-timeout` (default 1800 seconds). Parameters must match atom count, element
order, names, protonation state and net charge. The worker reuses its Python
and GPU contexts across calls; failures abort refinement without MM fallback.

The mode supports coordinate refinement with `STATE.BOTH`. It rejects RESPA
splitting and alchemical scaling. The crystallographic or real-space target
remains combined by FFX's normal `RefinementEnergy` path. It replaces all
classical chemical energy, including chemical restraints.

`validation/ad_gbn2/build.sbatch` builds FFX through refinement. The Java smoke
test checks protocol/unit conversion, finite-difference gradients, frozen
atoms and L-BFGS objective decrease. The GPU Python reference is recorded in
`validation/ad_gbn2/direct_reference.txt`; its maximum finite-difference error
was `0.0011713047 eV/Angstrom` for the three-atom GBn2 AUX test.

## Run on Wang Lab Slurm

From the FFX checkout, submit `sbatch validation/ad_gbn2/build.sbatch` and wait
for `BUILD SUCCESS`. Then submit `sbatch validation/ad_gbn2/java_smoke.sbatch`.
The scripts discover the Java 25 installation in the sibling `.ffx-tools`
directory. GPU calculations use the existing `AD` conda environment.
For an independent Python reference, run
`sbatch validation/ad_gbn2/direct.sbatch` from `validation/ad_gbn2`.

The Java test also checks scaled coordinates and unchanged input arrays,
`RefinementModel.addAssemblyGradient`, and an actual `RefinementEnergy`
combining the AD chemical potential with a generated periodic CCP4 density
map. It checks the weighted sum of energies and gradients and finite
differences of that combined objective. The synthetic water/map test verifies
the integration path; a protein with measured diffraction or cryo-EM data
requires its own matching topology and validation.

## Enable for a structure

Copy the settings from `validation/ad_gbn2/example.properties` into the
structure's FFX properties file. Set its net charge and parameter/topology
path, then use the normal FFX coordinate refinement workflow. The assembly
must already contain all AD atoms, including hydrogens. FFX topology loading
and alternate-conformer mapping still apply before the backend is selected;
keep atom naming and ordering consistent with the supplied solvent topology.
Changing active atom selections requires constructing a new AD potential.
Multiple assemblies each own a worker and should be sized for GPU memory.

`ffx.potential.ForceFieldEnergy` and ordinary standalone FFX energy commands
retain their existing behavior. The `ad-gbn2` switch selects this backend in
`RefinementEnergy`; programmatic clients can construct `ADGBn2Potential`
directly. This backend does not add FFX chemical restraint energies, and the
refinement constructor rejects configured restraints, constraints, and
lambda-dependent chemical terms. The Python toolkit and model weights stay
external to this repository.

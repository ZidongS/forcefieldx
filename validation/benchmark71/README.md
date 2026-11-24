# FFX AD+GBn2 coordinate refinement: 71-case campaign

The archive is `validation/AD_GBn2_71_test_package.zip`, SHA256
`e85483cd063e134c08b6e46d4b2fbba7fd54f056ee683d4ea8e3964357a2d3e6`.
All source-manifest hashes are checked before preparation. There are 41 cryo-EM,
20 low-resolution X-ray, and 10 high-resolution X-ray cases.

`prepare71.py` uses preparations of the **original packaged input**, after matching
its SHA256, from the existing Q|R preparation environment. It never starts from
historical refined or author coordinates. OpenMM GBn2 parameters and CCTBX formal
charges are generated for the exact resulting atom order. The worker replaces the
placeholder charge array with AUX charges and includes the full charge response.

The three initial H-contact exceptions (8dl8, 1wl3, 4jp6) retain qmready protonation,
caps and water hydrogens. `reduce_record_h_contact.py` records only the final
H-contact validation exception; heavy-atom errors and coincident atoms remain
fatal. These initial contacts are recorded in the preparation logs. For 3njw,
the highest mean-occupancy conformer is selected per residue (alphabetical tie
break), its occupancy becomes one, and matching ANISOU records are preserved.
Every choice is recorded in `prepared/<code>/status.json`.

`FFXRefine71.java` preserves atom identity and invokes the integrated FFX
`RefinementEnergy`, combining AD+GBn2 with `RealSpaceData` or `DiffractionData`.
B factors, anisotropic tensors and occupancies are held fixed. X-ray uses the
polynomial bulk-solvent mask, Bondi radii for that mask only, a scattering buffer
of 4 A (as in upstream gradient tests), and reciprocal sampling 0.6. No classical
force-field chemical energy is added.

The full-target descent-direction derivative is checked at steps 0.001, 0.0003,
0.0001, 0.003, and 0.01 A, stopping at the first agreement within 0.15 + 2% of the projected
gradient. This accommodates neighbor-list changes at larger displacements and
exported-model energy precision at smaller displacements. Failed checks stop
that case and preserve diagnostics. This is a smoke check, not an exhaustive
per-coordinate gradient verification.

The campaign uses two independent single-GPU shards, balanced by atom count
squared. Each case runs L-BFGS with memory 7, at most 200 iterations, and gradient
RMS tolerance 1.0. An iteration cap is recorded separately from convergence.
`campaign71.py` keeps immutable per-attempt outputs, hashes, commands, job IDs,
return codes, and elapsed times; resuming skips only successful cases. A case
failure does not discard the rest of its shard. Joint target values include a
weighted experimental term and must not be interpreted as standalone chemical
energies or compared across systems.

Independent CCTBX evaluation (`evaluate71.py`) measures geometry, CCmask/EMRinger
or Rwork/Rfree for the original, prepared, and final model. Rfree is used only
for reporting, never for choosing weights or stopping optimization. Metrics
errors are retained separately from refinement failures.

```bash
python3 campaign71.py plan
sbatch shard71.sbatch 0
sbatch shard71.sbatch 1
# After refinement, evaluate with CPU-only jobs:
sbatch evaluate71.sbatch 0
sbatch evaluate71.sbatch 1
```

Generated structures, input data, logs and environments are excluded from Git.
Read `RESULTS.md` for the executed protocol, jobs, and measured outcomes.

Failed L-BFGS cases can be retried with `retry71.sbatch CODE`. The separate
`FFXRefine71Retry` entry uses steepest descent (L-BFGS memory zero) with the
same potential, data weight, gradient tolerance and iteration cap. Its outputs
explicitly record the optimizer and preserve the failed attempt. The retry
uses a separate class directory so running production callers stay fixed.

CCTBX's geometry fields `bond.mean` and `angle.mean` are RMS deviations
(as implemented by geometry_restraints.energies), despite their field names.
The CSV preserves these raw field names; reports label them as RMS deviations.

`retry71.sbatch --failed` discovers terminal failures when it starts. For a
derivative-check-only failure it retains L-BFGS memory 7 and additionally
checks steps 0.03 and 0.00003 A at the unchanged error criterion; optimization
failures use memory zero. Retry trajectories are saved every ten iterations.

Diffraction calculations use four CPU threads after the initial serial batch;
cryo-EM uses one. Four CPUs are allocated per GPU worker. Case metadata records
the code revision and thread count. Completed serial results are preserved.

Successful outputs additionally pass an atom-order, provenance, coordinate-only
and numeric audit. The minimum distance between any two supplied output atoms,
including optimized hydrogen atoms, must be at least 0.5 A; this is a gross
collapse check, not a substitute for independent stereochemical validation.
Rejected attempts and their structures are retained under history in the result
archive. Metrics are associated with their exact output attempt and model hash.
Run the final audit with `sbatch audit71.sbatch` (or `--partial` while running).

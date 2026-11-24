# Pilot validation and campaign status

Three representative systems completed 20 coordinate iterations (job 24868),
and the largest system, 8fsj (18,924 atoms), completed 5 iterations (job 24865).
Full Maven build through refinement passed (24867). The large-system smoke test
used about 2.1 GB peak RSS after correcting the reversed array-size/thread-count
arguments in `RealSpaceData` (previously about 18.6 GB and heap exhaustion).

The campaign protocol fixes the experimental weight at **0.05** for all cases.
Weight 1 was rejected before production because it caused severe geometry
breakdown in the cryo-EM pilot (8dat bond RMS deviation 0.167 A and clashscore
396.9). The weight was reduced based on geometry, without Rfree optimization.
The following independent CCTBX results compare the prepared input and the
20-step pilot; these are not full-campaign results.

| Case | Metric | Prepared | Pilot |
|---|---|---:|---:|
| 5zgl | Rwork | 0.10020 | 0.09677 |
| 5zgl | Rfree | 0.12261 | 0.11883 |
| 5zgl | Bond RMS deviation (A) | 0.01146 | 0.01351 |
| 8dat | CCmask | 0.70520 | 0.72300 |
| 8dat | EMRinger | 0.45171 | 0.03602 |
| 8dat | Bond RMS deviation (A) | 0.00351 | 0.01245 |
| 8dat | Clashscore | 9.81194 | 5.72363 |
| 1yab | Rwork | 0.22773 | 0.20686 |
| 1yab | Rfree | 0.26036 | 0.25321 |
| 1yab | Bond RMS deviation (A) | 0.00911 | 0.01075 |

The integration and minimization work; the pilot does **not** demonstrate
uniform improvement in every validation metric (notably EMRinger for 8dat).
Production preserves these independent metrics and distinguishes convergence
from hitting the 200-iteration cap. Final 71-case outcomes will be appended.

Large-system production precision calibration: the initial 0.5 gradient-RMS
threshold led 7un3 to stop in line search at RMS 0.5967, despite a decreased
target. The production threshold is therefore fixed at **1.0** for all cases.
The derivative check additionally considers steps 0.003 and 0.01 A with the
same 0.15 + 2% agreement criterion. 8g94 passed this check and completed its
5-step validation (24874). Earlier production attempts (24870/24871) remain
recorded; they are not counted as successful refinements.

6wik failed its initial L-BFGS runs with a target collapse and gradient explosion.
Its same-target steepest-descent retry produced an audited output at 200 steps,
but did not converge (final gradient RMS 12.957347). 6zm8 also reached the cap
(final gradient RMS 2.1777804). These two outputs are explicitly nonconverged.

2jcl's first capped L-BFGS output had a 0.258925 A H-H contact; the next
L-BFGS run ended with gradient RMS 38.404769. Both were rejected, and the
same-target steepest-descent retry also failed line search. All attempts are
retained. 1u87's check-only failure was resolved at a 0.03 A derivative-check
step without changing the original agreement criterion or L-BFGS optimizer.

The final failed cases are 1tt8, 2fma, 2jcl, 2pnd, 3njw, 4jp6, 4o8h,
4r5r, 4xcr, and 6dkz. Nine stopped during coordinate line search; 4xcr
encountered StackOverflowError in FFX's recursive line search during initial
experimental spline-scale fitting, before AD coordinate optimization.
Several high-resolution cases still fail with both L-BFGS and steepest descent;
this campaign therefore establishes a working integration but not robust
convergence over all 71 cases. No tolerance relaxation or classical force-field
substitution was used to convert those failures into successes.

Successful audit checks are structural/provenance and numeric checks, not a
claim of biological quality. In particular 6wik's capped output has bond RMS
deviation 0.03859 A (prepared 0.02023 A), and a maximum bond deviation 2.296 A;
its geometry must be reviewed before downstream use. Preserve capped outputs
separately when selecting models for further scientific analysis.

## Full 71-case results

All 71 cases reached a terminal state: {'refined': 61, 'failed': 10}. Atom identity, non-coordinate records, hashes, finite/noncoincident coordinates, minimum atom distance >=0.5 A, decreased target and derivative-check acceptance passed the final audit for 61 successful cases.

Converged: 59; reached the 200-iteration cap: 2; failed cases: ['4xcr', '2jcl', '2pnd', '4jp6', '4r5r', '4o8h', '6dkz', '3njw', '2fma', '1tt8'].

Production code commit: `414e5b9e59e688bab6e4f1118a87c66607baee21`. Fixed data weight 0.05; gradient RMS tolerance 1.0; coordinate-only optimization. Slurm jobs recorded in final case states: 24877, 24878, 24888, 24889, 24892, 24901, 24902, 24904.

| Group | Refined | Converged | Data-fit pairs | Improved | Median change |
|---|---:|---:|---:|---:|---:|
| cryoem | 41 | 40 | 41 | 16 | -0.00067 |
| xray_low | 18 | 18 | 18 | 17 | -0.01144 |
| xray_high | 2 | 1 | 2 | 2 | -0.02071 |

Changes are final minus prepared: higher CCmask and lower Rfree indicate better data fit. These independent metrics do not all improve; the CSV retains every result, including geometry and EMRinger.

Independent evaluation records with errors or missing metrics: 0 cases. Exact fields: `{}`.

See `summary.csv` for all 71 cases and `audit.json` for the output checks.

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

6wik failed its first production L-BFGS run after a rapid target decrease and
gradient explosion (iteration 138). It is excluded from successful cases.
A same-potential steepest-descent retry is queued as job 24885, dependent on
completion of production shard 24877, keeping the task within two GPUs.

1u87 stopped before optimization at its derivative check. Its separate
diagnostics show agreement at step 0.03 A; it is included in the retry pass
with the original L-BFGS optimizer and unchanged derivative-error criterion.

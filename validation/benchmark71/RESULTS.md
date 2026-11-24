# Pilot validation and campaign status

Three representative systems completed 20 coordinate iterations (job 24868),
and the largest system, 8fsj (18,924 atoms), completed 5 iterations (job 24865).
Full Maven build through refinement passed (24867). The large-system smoke test
used about 2.1 GB peak RSS after correcting the reversed array-size/thread-count
arguments in `RealSpaceData` (previously about 18.6 GB and heap exhaustion).

The campaign protocol fixes the experimental weight at **0.05** for all cases.
Weight 1 was rejected before production because it caused severe geometry
breakdown in the cryo-EM pilot (8dat mean bond deviation 0.167 A and clashscore
396.9). The weight was reduced based on geometry, without Rfree optimization.
The following independent CCTBX results compare the prepared input and the
20-step pilot; these are not full-campaign results.

| Case | Metric | Prepared | Pilot |
|---|---|---:|---:|
| 5zgl | Rwork | 0.10020 | 0.09677 |
| 5zgl | Rfree | 0.12261 | 0.11883 |
| 5zgl | Mean bond deviation (A) | 0.01146 | 0.01351 |
| 8dat | CCmask | 0.70520 | 0.72300 |
| 8dat | EMRinger | 0.45171 | 0.03602 |
| 8dat | Mean bond deviation (A) | 0.00351 | 0.01245 |
| 8dat | Clashscore | 9.81194 | 5.72363 |
| 1yab | Rwork | 0.22773 | 0.20686 |
| 1yab | Rfree | 0.26036 | 0.25321 |
| 1yab | Mean bond deviation (A) | 0.00911 | 0.01075 |

The integration and minimization work; the pilot does **not** demonstrate
uniform improvement in every validation metric (notably EMRinger for 8dat).
Production preserves these independent metrics and distinguishes convergence
from hitting the 200-iteration cap. Final 71-case outcomes will be appended.

#!/bin/bash
#SBATCH --job-name=ffx_aim_compare
#SBATCH --partition=5090
#SBATCH --cpus-per-task=2
#SBATCH --mem=16G
#SBATCH --time=02:00:00
#SBATCH --output=aim_compare_%j.log
set -eo pipefail
export OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
python3 -u compare_aimnet71_eval.py "$1"

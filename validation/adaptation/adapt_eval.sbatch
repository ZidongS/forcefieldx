#!/bin/bash
#SBATCH --job-name=ffx_adapt_eval
#SBATCH --partition=5090
#SBATCH --cpus-per-task=2
#SBATCH --mem=16G
#SBATCH --time=01:00:00
#SBATCH --output=eval_%j.log
set -eo pipefail
export OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
python3 -u adapt_eval.py "$1"

#!/bin/bash
#SBATCH --job-name=ffx71_metrics
#SBATCH --partition=5090
#SBATCH --cpus-per-task=2
#SBATCH --mem=16G
#SBATCH --time=2-00:00:00
#SBATCH --output=metrics_%j.log
set -eo pipefail
export OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
python3 -u evaluate71.py "$1"

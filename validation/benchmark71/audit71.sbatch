#!/bin/bash
#SBATCH --job-name=ffx71_audit
#SBATCH --partition=5090
#SBATCH --cpus-per-task=2
#SBATCH --mem=4G
#SBATCH --time=00:10:00
#SBATCH --output=audit_%j.log
set -eo pipefail
export PYTHONNOUSERSITE=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
/home/shuzidong/miniconda3/envs/AD/bin/python -u audit71.py "$@"

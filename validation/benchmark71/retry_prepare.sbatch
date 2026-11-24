#!/bin/bash
#SBATCH --job-name=ffx71_prep_retry
#SBATCH --partition=5090
#SBATCH --cpus-per-task=4
#SBATCH --mem=16G
#SBATCH --time=01:00:00
#SBATCH --output=retry_prepare_%j.log
set -eo pipefail
export PYTHONNOUSERSITE=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
/home/shuzidong/miniconda3/envs/AD/bin/python -u retry_prepare.py

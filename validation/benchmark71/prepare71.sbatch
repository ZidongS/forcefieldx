#!/bin/bash
#SBATCH --job-name=ffx71_prepare
#SBATCH --partition=5090
#SBATCH --cpus-per-task=4
#SBATCH --mem=12G
#SBATCH --time=01:00:00
#SBATCH --output=prepare_%j.log
set -eo pipefail
export PYTHONNOUSERSITE=1 OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
/home/shuzidong/miniconda3/envs/AD/bin/python -u prepare71.py

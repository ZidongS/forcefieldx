#!/bin/bash
#SBATCH --job-name=ffx71_bundle
#SBATCH --partition=5090
#SBATCH --cpus-per-task=2
#SBATCH --mem=4G
#SBATCH --time=00:15:00
#SBATCH --output=bundle_%j.log
set -eo pipefail
export PYTHONNOUSERSITE=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
/home/shuzidong/miniconda3/envs/AD/bin/python audit71.py
python3 collect71.py
python3 finalize_report71.py
python3 bundle71.py

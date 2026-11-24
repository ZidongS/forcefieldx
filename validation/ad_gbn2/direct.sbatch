#!/bin/bash
#SBATCH --job-name=ffx_ad_direct
#SBATCH --partition=5090
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --gres=gpu:1
#SBATCH --cpus-per-task=4
#SBATCH --mem=24G
#SBATCH --time=00:30:00
#SBATCH --output=direct_%j.out
#SBATCH --error=direct_%j.err
set -eo pipefail
source /home/shuzidong/miniconda3/bin/activate AD
export PYTHONNOUSERSITE=1 OMP_NUM_THREADS=4 OPENMM_CPU_THREADS=1
python -u validate_direct.py

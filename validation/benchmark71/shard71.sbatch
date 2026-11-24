#!/bin/bash
#SBATCH --job-name=ffx71_shard
#SBATCH --partition=5090
#SBATCH --gres=gpu:1
#SBATCH --cpus-per-task=4
#SBATCH --mem=24G
#SBATCH --time=3-00:00:00
#SBATCH --output=shard_%j.log
set -eo pipefail
export PYTHONNOUSERSITE=1 OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1 OPENMM_CPU_THREADS=1
FFX=$(realpath ../..)
export JAVA_HOME=$(realpath "$(find "$FFX/../.ffx-tools" -maxdepth 1 -name 'jdk-*' -type d | head -1)")
X="$FFX/../.ffx-tools/xvfb"
export PATH="$JAVA_HOME/bin:$X/bin:$X/usr/bin:$PATH"
export LD_LIBRARY_PATH="$X/usr/lib/x86_64-linux-gnu:$X/usr/lib:${LD_LIBRARY_PATH:-}"
CP=$(find "$FFX/modules" -path '*/target/classes' -type d | paste -sd:):$(find "$FFX/lib" -name '*.jar' | paste -sd:)
export FFX71_CP="$PWD/classes:$CP"
/home/shuzidong/miniconda3/envs/AD/bin/python -u campaign71.py "$1"

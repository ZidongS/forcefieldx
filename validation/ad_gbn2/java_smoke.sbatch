#!/bin/bash
#SBATCH --job-name=ffx_ad_java
#SBATCH --partition=5090
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --gres=gpu:1
#SBATCH --cpus-per-task=4
#SBATCH --mem=24G
#SBATCH --time=00:30:00
#SBATCH --output=validation/ad_gbn2/java_%j.out
#SBATCH --error=validation/ad_gbn2/java_%j.err
set -eo pipefail
source /home/shuzidong/miniconda3/bin/activate AD
export PYTHONNOUSERSITE=1 OMP_NUM_THREADS=4 OPENMM_CPU_THREADS=1
export JAVA_HOME=$(realpath "$(find ../.ffx-tools -maxdepth 1 -type d -name 'jdk-*' | head -1)")
export PATH="$JAVA_HOME/bin:$PATH"
XVFB_BIN="$PWD/../.ffx-tools/xvfb/bin"
XVFB_ROOT="$PWD/../.ffx-tools/xvfb"
export PATH="$XVFB_BIN:$XVFB_ROOT/usr/bin:$PATH"
export LD_LIBRARY_PATH="$XVFB_ROOT/usr/lib/x86_64-linux-gnu:$XVFB_ROOT/usr/lib:${LD_LIBRARY_PATH:-}"
CP=$(find modules -path '*/target/classes' -type d | paste -sd:):$(find lib -name '*.jar' | paste -sd:)
mkdir -p validation/ad_gbn2/classes
javac --add-modules jdk.incubator.vector -cp "$CP" -d validation/ad_gbn2/classes validation/ad_gbn2/ADGBn2Smoke.java
xvfb-run -a java --add-modules jdk.incubator.vector -cp "validation/ad_gbn2/classes:$CP" ffx.potential.ADGBn2Smoke "$PWD" /home/shuzidong/workfile/alphadynamics/zidong_analysis_toolkit

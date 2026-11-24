#!/bin/bash
#SBATCH --job-name=ffx71_pilot
#SBATCH --partition=5090
#SBATCH --gres=gpu:1
#SBATCH --cpus-per-task=4
#SBATCH --mem=32G
#SBATCH --time=03:00:00
#SBATCH --output=pilot_%j.log
set -eo pipefail
export PYTHONNOUSERSITE=1 OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1 OPENMM_CPU_THREADS=1
FFX=$(realpath ../..)
export JAVA_HOME=$(realpath "$(find "$FFX/../.ffx-tools" -maxdepth 1 -name 'jdk-*' -type d | head -1)")
X="$FFX/../.ffx-tools/xvfb"
export PATH="$JAVA_HOME/bin:$X/bin:$X/usr/bin:$PATH"
export LD_LIBRARY_PATH="$X/usr/lib/x86_64-linux-gnu:$X/usr/lib:${LD_LIBRARY_PATH:-}"
CP=$(find "$FFX/modules" -path '*/target/classes' -type d | paste -sd:):$(find "$FFX/lib" -name '*.jar' | paste -sd:)
mkdir -p classes
javac --add-modules jdk.incubator.vector -cp "$CP" -d "$FFX/modules/refinement/target/classes" "$FFX/modules/refinement/src/main/java/ffx/realspace/RealSpaceData.java"
javac --add-modules jdk.incubator.vector -cp "$CP" -d classes FFXRefine71.java

for spec in '8fsj cryoem'; do
  read -r code group <<< "$spec"
  mkdir -p "large_pilot/$code"
  xvfb-run -a java -Dpj.nt=1 -Dffx71.sampling=0.6 --enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector -Xmx16g -cp "classes:$CP" ffx.potential.FFXRefine71 "$FFX" "$PWD/prepared/$code" "$FFX/validation/AD_GBn2_71_test_package/data/$group/$code/input.$([[ $group == cryoem ]] && echo map || echo mtz)" "$PWD/large_pilot/$code" "$group" 5 > "large_pilot/$code/refine.log" 2>&1
done

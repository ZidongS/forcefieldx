#!/bin/bash
#SBATCH --job-name=ffx71_compile
#SBATCH --partition=5090
#SBATCH --cpus-per-task=4
#SBATCH --mem=8G
#SBATCH --time=00:10:00
#SBATCH --output=compile_%j.log
set -eo pipefail
FFX=$(realpath ../..)
export JAVA_HOME=$(realpath "$(find "$FFX/../.ffx-tools" -maxdepth 1 -name 'jdk-*' -type d | head -1)")
export PATH="$JAVA_HOME/bin:$PATH"
CP=$(find "$FFX/modules" -path '*/target/classes' -type d | paste -sd:):$(find "$FFX/lib" -name '*.jar' | paste -sd:)
mkdir -p classes retry_classes
javac --add-modules jdk.incubator.vector -cp "$CP" -d classes FFXRefine71.java
javac --add-modules jdk.incubator.vector -cp "$CP" -d retry_classes FFXRefine71Retry.java

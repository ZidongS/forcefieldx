#!/bin/bash
#SBATCH --job-name=ffx_build
#SBATCH --partition=5090
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=4
#SBATCH --mem=12G
#SBATCH --time=00:20:00
#SBATCH --output=validation/ad_gbn2/build_%j.out
#SBATCH --error=validation/ad_gbn2/build_%j.err
set -eo pipefail
export JAVA_HOME=$(find ../.ffx-tools -maxdepth 1 -type d -name 'jdk-*' | head -1)
export JAVA_HOME=$(realpath "$JAVA_HOME")
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx8g'
java -version
bash ./mvnw -B -ntp -pl modules/refinement -am -DskipTests -Dcheckstyle.skip -Dlicense.skip=true -Dmaven.javadoc.skip=true -Dexec.skip=true install

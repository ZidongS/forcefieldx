#!/bin/bash
#SBATCH --job-name=ffx_adapt_build
#SBATCH --partition=5090
#SBATCH --cpus-per-task=4
#SBATCH --mem=12G
#SBATCH --time=00:30:00
#SBATCH --output=validation/adaptation/build_%j.log
set -eo pipefail
export JAVA_HOME=$(realpath "$(find ../.ffx-tools -maxdepth 1 -name 'jdk-*' -type d | head -1)")
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx8g'
bash ./mvnw -B -ntp -pl modules/refinement -am -DskipTests=false -Dtest=LineSearchTest -Dsurefire.failIfNoSpecifiedTests=false -Dcheckstyle.skip -Dlicense.skip=true -Dmaven.javadoc.skip=true -Dexec.skip=true install

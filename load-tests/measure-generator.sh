#!/usr/bin/env bash

set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
backend_dir="$repo_root/backend"
results_dir="$repo_root/load-tests/results"
generator_jar="$backend_dir/dataset-generator/target/reconriver-dataset-generator.jar"

export JAVA_HOME
JAVA_HOME=$(/usr/libexec/java_home -v 21)

mkdir -p "$results_dir"

environment_file="$results_dir/environment.txt"
{
  system_profiler SPHardwareDataType
  sw_vers
  "$JAVA_HOME/bin/java" -version
  echo "JVM options: -Xms128m -Xmx320m -XX:+ExitOnOutOfMemoryError"
} >"$environment_file" 2>&1

cd "$backend_dir"
./mvnw -B -ntp -Pdataset-generator -pl dataset-generator -am package

for record_count in 10000 100000; do
  output_dir="$repo_root/datasets/generated/local-performance/$record_count"
  metrics_file="$results_dir/generator-$record_count.txt"

  /usr/bin/time -l "$JAVA_HOME/bin/java" \
    -Xms128m -Xmx320m -XX:+ExitOnOutOfMemoryError \
    -jar "$generator_jar" \
    --scenario local-performance \
    --records "$record_count" \
    --seed 42 \
    --start-date 2026-01-01 \
    --output "$output_dir" \
    >"$metrics_file" 2>&1
done

echo "Generator measurements written to $results_dir"

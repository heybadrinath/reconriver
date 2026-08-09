#!/usr/bin/env bash

set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
backend_dir="$repo_root/backend"
committed_root="$repo_root/datasets/generated"
manifest_schema="$repo_root/datasets/schemas/scenario_manifest.schema.json"
column_schema="$repo_root/datasets/schemas/csv-columns.json"
generator_jar="$backend_dir/dataset-generator/target/reconriver-dataset-generator.jar"

for command_name in java jq diff check-jsonschema; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command is unavailable: $command_name" >&2
    exit 1
  fi
done

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME must point to Java 21" >&2
  exit 1
fi

java_feature_version=$("$JAVA_HOME/bin/java" -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}')
if [[ "$java_feature_version" != "21" ]]; then
  echo "Dataset verification requires Java 21; found Java $java_feature_version" >&2
  exit 1
fi

verify_tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/reconriver-datasets.XXXXXX")
cleanup_dataset_files() {
  dataset_exit_code=$?
  trap - EXIT
  rm -rf "$verify_tmp_dir"
  exit "$dataset_exit_code"
}
trap cleanup_dataset_files EXIT
regenerated_root="$verify_tmp_dir/generated"

calculate_sha256() {
  local file_path=$1
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file_path" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file_path" | awk '{print $1}'
  else
    echo "Neither sha256sum nor shasum is available" >&2
    return 1
  fi
}

verify_manifest_checksums() {
  local scenario_directory=$1
  local manifest="$scenario_directory/scenario_manifest.json"

  while IFS=$'\t' read -r file_name expected_checksum; do
    local checked_file="$scenario_directory/$file_name"
    if [[ ! -f "$checked_file" ]]; then
      echo "Manifest references a missing file: $checked_file" >&2
      return 1
    fi
    local actual_checksum
    actual_checksum=$(calculate_sha256 "$checked_file")
    if [[ "$actual_checksum" != "$expected_checksum" ]]; then
      echo "Manifest checksum mismatch: $checked_file" >&2
      return 1
    fi
  done < <(jq -r '.file_sha256_checksums | to_entries[] | [.key, .value] | @tsv' "$manifest")
}

verify_manifest_row_counts() {
  local scenario_directory=$1
  local manifest="$scenario_directory/scenario_manifest.json"

  while IFS=$'\t' read -r file_name expected_rows; do
    local checked_file="$scenario_directory/$file_name"
    if [[ ! -f "$checked_file" ]]; then
      echo "Manifest row count references a missing file: $checked_file" >&2
      return 1
    fi
    local actual_rows
    actual_rows=$(awk 'END {print (NR > 0 ? NR - 1 : 0)}' "$checked_file")
    if [[ "$actual_rows" != "$expected_rows" ]]; then
      echo "Manifest row-count mismatch: $checked_file" >&2
      return 1
    fi
  done < <(jq -r '.file_row_counts | to_entries[] | [.key, .value] | @tsv' "$manifest")
}

verify_csv_contract() {
  local scenario_directory=$1
  local file_name
  for file_name in \
      internal_transactions.csv \
      processor_transactions.csv \
      bank_settlements.csv \
      expected_reconciliation.csv; do
    local expected_header
    expected_header=$(jq -er --arg file "$file_name" '.properties[$file].const | join(",")' "$column_schema")
    local actual_header
    actual_header=$(head -n 1 "$scenario_directory/$file_name" | tr -d '\r')
    if [[ "$actual_header" != "$expected_header" ]]; then
      echo "CSV header mismatch: $scenario_directory/$file_name" >&2
      return 1
    fi
  done
}

verify_global_checksums_if_present() {
  local checksum_file="$repo_root/datasets/SHA256SUMS"
  [[ -f "$checksum_file" ]] || return 0

  local listed_paths="$verify_tmp_dir/global-checksum-paths.txt"
  local all_dataset_paths="$verify_tmp_dir/all-dataset-paths.txt"
  : >"$listed_paths"

  while read -r expected_checksum relative_path; do
    [[ -n "${expected_checksum:-}" ]] || continue
    [[ "$expected_checksum" == \#* ]] && continue
    relative_path=${relative_path#\*}
    relative_path=${relative_path#datasets/}
    case "$relative_path" in
      /*|../*|*/../*)
        echo "Global checksum contains an unsafe path: $relative_path" >&2
        return 1
        ;;
    esac
    local checked_file="$repo_root/datasets/$relative_path"
    if [[ ! -f "$checked_file" ]]; then
      echo "Global checksum references a missing file: $relative_path" >&2
      return 1
    fi
    local actual_checksum
    actual_checksum=$(calculate_sha256 "$checked_file")
    if [[ "$actual_checksum" != "$expected_checksum" ]]; then
      echo "Global checksum mismatch: $relative_path" >&2
      return 1
    fi
    printf '%s\n' "$relative_path" >>"$listed_paths"
  done <"$checksum_file"

  find "$repo_root/datasets" -type f ! -name SHA256SUMS -print \
    | sed "s#^$repo_root/datasets/##" \
    | LC_ALL=C sort >"$all_dataset_paths"
  duplicate_paths=$(LC_ALL=C sort "$listed_paths" | uniq -d)
  if [[ -n "$duplicate_paths" ]]; then
    echo "Global checksums contain duplicate paths" >&2
    return 1
  fi
  LC_ALL=C sort -u "$listed_paths" -o "$listed_paths"
  if ! diff --brief "$all_dataset_paths" "$listed_paths"; then
    echo "Global checksums do not cover every dataset artifact exactly once" >&2
    return 1
  fi
}

cd "$backend_dir"
./mvnw -B -ntp -Pdataset-generator -pl dataset-generator -am -DskipTests package

scenarios=(
  'clean-settlement:100'
  'mixed-exceptions:1000'
  'month-end-close:10000'
  'failure-recovery:10000'
)

for scenario_configuration in "${scenarios[@]}"; do
  scenario_slug=${scenario_configuration%%:*}
  record_count=${scenario_configuration##*:}
  "$JAVA_HOME/bin/java" \
    -Xms128m -Xmx320m -XX:+ExitOnOutOfMemoryError \
    -jar "$generator_jar" \
    --scenario "$scenario_slug" \
    --records "$record_count" \
    --seed 42 \
    --start-date 2026-01-01 \
    --output "$regenerated_root"
done

check-jsonschema --check-metaschema "$manifest_schema" "$column_schema"
check-jsonschema --schemafile "$manifest_schema" \
  "$committed_root"/*/scenario_manifest.json \
  "$regenerated_root"/*/scenario_manifest.json

for scenario_configuration in "${scenarios[@]}"; do
  scenario_slug=${scenario_configuration%%:*}
  record_count=${scenario_configuration##*:}
  committed_scenario="$committed_root/$scenario_slug"
  regenerated_scenario="$regenerated_root/$scenario_slug"

  jq -e \
    --arg slug "$scenario_slug" \
    --argjson count "$record_count" \
    '.scenario_id == $slug
      and .canonical_payment_count == $count
      and .random_seed == 42
      and .fixed_start_date == "2026-01-01"' \
    "$regenerated_scenario/scenario_manifest.json" >/dev/null

  verify_manifest_checksums "$committed_scenario"
  verify_manifest_checksums "$regenerated_scenario"
  verify_manifest_row_counts "$committed_scenario"
  verify_manifest_row_counts "$regenerated_scenario"
  verify_csv_contract "$committed_scenario"
  verify_csv_contract "$regenerated_scenario"

  if ! diff -qr "$committed_scenario" "$regenerated_scenario"; then
    echo "Committed artifacts are not reproducible for $scenario_slug" >&2
    exit 1
  fi
done

verify_global_checksums_if_present
echo 'Dataset schema, checksum, and byte-reproducibility verification passed'

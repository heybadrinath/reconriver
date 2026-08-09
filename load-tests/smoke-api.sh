#!/usr/bin/env bash

set -euo pipefail

api_base_url=${1:-http://localhost:8080}
scenario_slug=${2:-mixed-exceptions}
poll_timeout_seconds=${SMOKE_TIMEOUT_SECONDS:-300}
curl_connect_timeout_seconds=${CURL_CONNECT_TIMEOUT_SECONDS:-5}
curl_max_time_seconds=${CURL_MAX_TIME_SECONDS:-30}

for command_name in curl jq; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command is unavailable: $command_name" >&2
    exit 1
  fi
done

smoke_tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/reconriver-smoke.XXXXXX")
cleanup_smoke_files() {
  smoke_exit_code=$?
  trap - EXIT
  rm -rf "$smoke_tmp_dir"
  exit "$smoke_exit_code"
}
trap cleanup_smoke_files EXIT

authorization_header=""
curl_arguments=(
  --silent
  --show-error
  --connect-timeout "$curl_connect_timeout_seconds"
  --max-time "$curl_max_time_seconds"
)

api_request() {
  local method=$1
  local path=$2
  local output_file=$3
  local body=${4:-}
  local -a arguments=(
    "${curl_arguments[@]}"
    --fail-with-body
    --request "$method"
    --header "Accept: application/json"
    --output "$output_file"
  )

  if [[ -n "$authorization_header" ]]; then
    arguments+=(--header "$authorization_header")
  fi

  if [[ -n "$body" ]]; then
    arguments+=(--header "Content-Type: application/json" --data "$body")
  fi

  curl "${arguments[@]}" "$api_base_url$path"
}

wait_for_import() {
  local import_id=$1
  local response_file="$smoke_tmp_dir/import-$import_id.json"
  local deadline=$(( $(date +%s) + poll_timeout_seconds ))

  while (( $(date +%s) < deadline )); do
    api_request GET "/api/v1/imports/$import_id" "$response_file"
    local status
    status=$(jq -r '.status' "$response_file")

    case "$status" in
      COMPLETED|COMPLETED_WITH_ERRORS)
        return 0
        ;;
      FAILED)
        echo "Import $import_id failed: $(jq -r '.failureMessage // "unknown error"' "$response_file")" >&2
        return 1
        ;;
    esac

    sleep 1
  done

  echo "Import $import_id did not finish within ${poll_timeout_seconds}s" >&2
  return 1
}

wait_for_run() {
  local run_id=$1
  local expected_terminal_status=$2
  local response_file="$smoke_tmp_dir/run-$run_id.json"
  local deadline=$(( $(date +%s) + poll_timeout_seconds ))

  while (( $(date +%s) < deadline )); do
    api_request GET "/api/v1/reconciliation-runs/$run_id/progress" "$response_file"
    local status
    status=$(jq -r '.status' "$response_file")

    if [[ "$status" == "$expected_terminal_status" ]]; then
      return 0
    fi

    if [[ "$status" == "FAILED" || "$status" == "COMPLETED" ]]; then
      echo "Run $run_id ended as $status; expected $expected_terminal_status" >&2
      jq '{status, processed, total, failureMessage}' "$response_file" >&2
      return 1
    fi

    sleep 1
  done

  echo "Run $run_id did not finish within ${poll_timeout_seconds}s" >&2
  return 1
}

health_file="$smoke_tmp_dir/health.json"
api_request GET "/actuator/health" "$health_file"
jq -e '.status == "UP"' "$health_file" >/dev/null

session_file="$smoke_tmp_dir/session.json"
api_request POST "/api/v1/demo/sessions" "$session_file"
session_token=$(jq -er '.token' "$session_file")
workspace_id=$(jq -er '.workspaceId' "$session_file")
authorization_header="Authorization: Bearer $session_token"

load_file="$smoke_tmp_dir/load.json"
api_request POST "/api/v1/demo/scenarios/$scenario_slug/load" "$load_file"
jq -e --arg scenario "$scenario_slug" --arg workspace "$workspace_id" \
  '.scenarioSlug == $scenario and .workspaceId == $workspace and (.imports | length) >= 3' \
  "$load_file" >/dev/null

for import_id in $(jq -r '.imports[].id' "$load_file"); do
  wait_for_import "$import_id"
done

controlled_failure=$(jq -r '.controlledFailureAfterChunks // empty' "$load_file")
create_run_body=""
expected_first_status="COMPLETED"
if [[ -n "$controlled_failure" ]]; then
  create_run_body=$(jq -nc --argjson chunks "$controlled_failure" '{controlledFailureAfterChunks: $chunks}')
  expected_first_status="FAILED"
fi

run_file="$smoke_tmp_dir/run-created.json"
api_request POST "/api/v1/reconciliation-runs" "$run_file" "$create_run_body"
run_id=$(jq -er '.id' "$run_file")
wait_for_run "$run_id" "$expected_first_status"

if [[ "$expected_first_status" == "FAILED" ]]; then
  restart_file="$smoke_tmp_dir/run-restarted.json"
  api_request POST "/api/v1/reconciliation-runs/$run_id/restart" "$restart_file"
  jq -e --arg id "$run_id" '.id == $id' "$restart_file" >/dev/null
  wait_for_run "$run_id" "COMPLETED"
fi

summary_file="$smoke_tmp_dir/summary.json"
api_request GET "/api/v1/reconciliation-runs/$run_id/summary" "$summary_file"
jq -e '.totalResults > 0 and (.matchedCount + .exceptionCount == .totalResults)' "$summary_file" >/dev/null

exceptions_file="$smoke_tmp_dir/exceptions.json"
api_request GET "/api/v1/exceptions?runId=$run_id&page=0&size=1" "$exceptions_file"
if [[ $(jq -r '.totalElements' "$exceptions_file") -gt 0 ]]; then
  exception_id=$(jq -er '.items[0].id' "$exceptions_file")
  resolve_file="$smoke_tmp_dir/resolved.json"
  api_request POST "/api/v1/exceptions/$exception_id/resolve" "$resolve_file" \
    '{"note":"Verified by the end-to-end smoke journey."}'
  jq -e '.resolutionStatus == "RESOLVED"' "$resolve_file" >/dev/null

  reopen_file="$smoke_tmp_dir/reopened.json"
  api_request POST "/api/v1/exceptions/$exception_id/reopen" "$reopen_file" \
    '{"note":"Reopened to verify the append-only audit trail."}'
  jq -e '.resolutionStatus == "REOPENED"' "$reopen_file" >/dev/null
fi

audit_file="$smoke_tmp_dir/audit.json"
api_request GET "/api/v1/audit-events?page=0&size=100" "$audit_file"
jq -e '.totalElements > 0' "$audit_file" >/dev/null

report_file="$smoke_tmp_dir/reconciliation.csv"
curl "${curl_arguments[@]}" --fail-with-body \
  --header "$authorization_header" \
  --header "Accept: text/csv" \
  --output "$report_file" \
  "$api_base_url/api/v1/reconciliation-runs/$run_id/report.csv"
grep -q '^result_id,result_scope,work_key,' "$report_file"

jq -n \
  --arg baseUrl "$api_base_url" \
  --arg scenario "$scenario_slug" \
  --arg workspaceId "$workspace_id" \
  --arg runId "$run_id" \
  --slurpfile summary "$summary_file" \
  '{
    status: "passed",
    baseUrl: $baseUrl,
    scenario: $scenario,
    workspaceId: $workspaceId,
    runId: $runId,
    totalResults: $summary[0].totalResults,
    matchedCount: $summary[0].matchedCount,
    exceptionCount: $summary[0].exceptionCount
  }'

#!/usr/bin/env bash

set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
api_base_url=${1:-http://127.0.0.1:8080}
trusted_origin=${2:-https://reconriver.vercel.app}
rejected_origin=${3:-https://untrusted.example}
startup_timeout_seconds=${STARTUP_TIMEOUT_SECONDS:-180}
curl_connect_timeout_seconds=${CURL_CONNECT_TIMEOUT_SECONDS:-5}
curl_max_time_seconds=${CURL_MAX_TIME_SECONDS:-20}
verify_openapi_drift=${VERIFY_OPENAPI_DRIFT:-true}

for command_name in curl jq awk diff grep; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command is unavailable: $command_name" >&2
    exit 1
  fi
done

verify_tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/reconriver-runtime.XXXXXX")
cleanup_runtime_files() {
  runtime_exit_code=$?
  trap - EXIT
  rm -rf "$verify_tmp_dir"
  exit "$runtime_exit_code"
}
trap cleanup_runtime_files EXIT

curl_arguments=(
  --silent
  --show-error
  --connect-timeout "$curl_connect_timeout_seconds"
  --max-time "$curl_max_time_seconds"
)

extract_header() {
  local header_name=$1
  local headers_file=$2
  awk -v expected="${header_name}:" '
    tolower($1) == tolower(expected) {
      $1 = ""
      sub(/^ /, "")
      sub(/\r$/, "")
      print
      exit
    }
  ' "$headers_file"
}

wait_for_readiness() {
  local response_file="$verify_tmp_dir/readiness.json"
  local deadline=$(( $(date +%s) + startup_timeout_seconds ))

  while (( $(date +%s) < deadline )); do
    rm -f "$response_file"
    local status_code
    status_code=$(curl "${curl_arguments[@]}" \
      --output "$response_file" \
      --write-out '%{http_code}' \
      "$api_base_url/actuator/health/readiness" 2>/dev/null || true)
    if [[ "$status_code" == "200" ]] \
        && jq -e '.status == "UP"' "$response_file" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "API readiness did not become healthy within ${startup_timeout_seconds}s" >&2
  return 1
}

wait_for_readiness

openapi_file="$verify_tmp_dir/openapi.json"
curl "${curl_arguments[@]}" --fail-with-body \
  --header 'Accept: application/json' \
  --output "$openapi_file" \
  "$api_base_url/v3/api-docs"
if ! jq -e '
    (.openapi | startswith("3."))
    and (.info.title | type == "string" and length > 0)
    and (.info.version | type == "string" and length > 0)
    and (.paths | type == "object" and length > 0)
  ' "$openapi_file" >/dev/null; then
  echo "The API did not return a complete OpenAPI 3 document" >&2
  exit 1
fi

docs_file="$verify_tmp_dir/docs.html"
curl "${curl_arguments[@]}" --fail-with-body --location \
  --header 'Accept: text/html' \
  --output "$docs_file" \
  "$api_base_url/docs"
if ! grep -Eqi '<title>.*swagger|swagger-ui' "$docs_file"; then
  echo "The public /docs route did not return the Swagger UI" >&2
  exit 1
fi

committed_openapi="$repo_root/frontend/openapi/openapi.json"
if [[ "$verify_openapi_drift" == "true" && -f "$committed_openapi" ]]; then
  jq --sort-keys . "$openapi_file" >"$verify_tmp_dir/openapi-live-normalized.json"
  jq --sort-keys . "$committed_openapi" >"$verify_tmp_dir/openapi-committed-normalized.json"
  if ! diff --brief \
      "$verify_tmp_dir/openapi-committed-normalized.json" \
      "$verify_tmp_dir/openapi-live-normalized.json"; then
    echo "The committed OpenAPI document differs from the running Spring API." >&2
    exit 1
  fi
fi

trusted_headers="$verify_tmp_dir/trusted-headers.txt"
trusted_body="$verify_tmp_dir/trusted-body.json"
trusted_status=$(curl "${curl_arguments[@]}" \
  --header "Origin: $trusted_origin" \
  --dump-header "$trusted_headers" \
  --output "$trusted_body" \
  --write-out '%{http_code}' \
  "$api_base_url/actuator/health/readiness")
if [[ "$trusted_status" != "200" ]]; then
  echo "Trusted CORS request returned HTTP $trusted_status" >&2
  exit 1
fi
trusted_allow_origin=$(extract_header 'Access-Control-Allow-Origin' "$trusted_headers")
if [[ "$trusted_allow_origin" != "$trusted_origin" ]]; then
  echo "Trusted CORS origin was not echoed exactly" >&2
  exit 1
fi

preflight_headers="$verify_tmp_dir/preflight-headers.txt"
preflight_status=$(curl "${curl_arguments[@]}" \
  --request OPTIONS \
  --header "Origin: $trusted_origin" \
  --header 'Access-Control-Request-Method: POST' \
  --header 'Access-Control-Request-Headers: authorization,content-type' \
  --dump-header "$preflight_headers" \
  --output /dev/null \
  --write-out '%{http_code}' \
  "$api_base_url/api/v1/demo/sessions")
if [[ "$preflight_status" != "200" ]]; then
  echo "Trusted CORS preflight returned HTTP $preflight_status" >&2
  exit 1
fi
preflight_allow_origin=$(extract_header 'Access-Control-Allow-Origin' "$preflight_headers")
if [[ "$preflight_allow_origin" != "$trusted_origin" ]]; then
  echo "Trusted preflight origin was not echoed exactly" >&2
  exit 1
fi

rejected_headers="$verify_tmp_dir/rejected-headers.txt"
rejected_status=$(curl "${curl_arguments[@]}" \
  --header "Origin: $rejected_origin" \
  --dump-header "$rejected_headers" \
  --output "$verify_tmp_dir/rejected-body.txt" \
  --write-out '%{http_code}' \
  "$api_base_url/actuator/health/readiness")
if [[ "$rejected_status" != "403" ]]; then
  echo "Rejected CORS origin returned HTTP $rejected_status instead of 403" >&2
  exit 1
fi
if [[ -n "$(extract_header 'Access-Control-Allow-Origin' "$rejected_headers")" ]]; then
  echo "Rejected CORS origin received an allow-origin header" >&2
  exit 1
fi

session_file="$verify_tmp_dir/session.json"
curl "${curl_arguments[@]}" --fail-with-body \
  --request POST \
  --header 'Accept: application/json' \
  --output "$session_file" \
  "$api_base_url/api/v1/demo/sessions"
session_token=$(jq -er '.token' "$session_file")

actuator_index="$verify_tmp_dir/actuator.json"
curl "${curl_arguments[@]}" --fail-with-body \
  --header "Authorization: Bearer $session_token" \
  --header 'Accept: application/json' \
  --output "$actuator_index" \
  "$api_base_url/actuator"
if ! jq -e '
    ._links.health != null
    and ._links.info != null
    and ._links.metrics == null
    and ._links.modulith == null
  ' "$actuator_index" >/dev/null; then
  echo "The Actuator index does not expose exactly the public health/info surface" >&2
  exit 1
fi

for hidden_path in /actuator/metrics /actuator/modulith; do
  hidden_status=$(curl "${curl_arguments[@]}" \
    --header "Authorization: Bearer $session_token" \
    --output "$verify_tmp_dir/hidden-response.txt" \
    --write-out '%{http_code}' \
    "$api_base_url$hidden_path")
  if [[ "$hidden_status" != "404" ]]; then
    echo "$hidden_path is reachable with HTTP $hidden_status; expected 404" >&2
    exit 1
  fi
done

echo "Runtime API verification passed for $api_base_url"

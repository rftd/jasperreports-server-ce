#!/usr/bin/env bash
# Smoke-check a running JasperReports Server instance in the disposable CI runner.
# The test must fail on HTTP errors or Tomcat's own 404/500 pages.
set -Eeuo pipefail

base="${JRS_BASE_URL:-http://127.0.0.1:8080/jasperserver}"
attempts="${JRS_SMOKE_ATTEMPTS:-72}"
page="${RUNNER_TEMP:-/tmp}/jrs-login-smoke.html"
response="${RUNNER_TEMP:-/tmp}/jrs-login-headers.txt"

for ((i=1; i<=attempts; i++)); do
  status=$(curl --silent --show-error --location --max-time 10 \
    --dump-header "$response" --output "$page" \
    --write-out '%{http_code}' "$base/login.html" 2>/dev/null) || status="000"

  if [[ "$status" == "200" ]] && grep -Eqi '(jasperreports|jasperserver|j_username|login)' "$page"; then
    if ! grep -Eqi '(HTTP Status 404|HTTP Status 500|Exception report)' "$page"; then
      echo "JasperReports Server login page is responding (HTTP $status, attempt $i)."
      echo "Checked URL: $base/login.html"
      exit 0
    fi
  fi

  if (( i % 6 == 0 )); then
    echo "Waiting for JasperReports Server startup ($i/$attempts); HTTP=$status"
  fi
  sleep 5
done

echo "::error::JasperReports Server login page did not become healthy after $attempts attempts."
echo "Last HTTP status: $status"
echo "Last response headers (truncated):"
head -40 "$response" || true
echo "Last response body (truncated):"
head -c 2500 "$page" || true
echo
exit 1

#!/usr/bin/env bash
# REST v2 functional checks against the isolated JasperReports Server CI instance.
# Never run with the bundled default credentials against a production server.
set -Eeuo pipefail

base="${JRS_BASE_URL:-http://127.0.0.1:8080/jasperserver}"
user="${JRS_SMOKE_USER:-superuser}"
password="${JRS_SMOKE_PASSWORD:-superuser}"
tmp="$(mktemp -d "${RUNNER_TEMP:-/tmp}/jrs-functional.XXXXXXXX")"
trap 'rm -rf "$tmp"' EXIT

failure() {
  echo "::error title=JasperReports functional smoke::$*" >&2
  exit 1
}

request() {
  local output="$1" url="$2"
  shift 2
  local status
  status=$(curl --silent --show-error --location --max-time 150 \
    --write-out '%{http_code}' --output "$output" "$@" "$url") ||
      failure "Network error requesting ${url##*/}"
  if [[ "$status" != "200" ]]; then
    echo "Unexpected response from ${url##*/}: HTTP $status" >&2
    # Only diagnostics from non-secret resource/report endpoints reach this helper.
    head -c 700 "$output" >&2 || true
    echo >&2
    failure "Expected HTTP 200, got $status from ${url##*/}"
  fi
}

echo "Checking REST v2 login using the disposable CI administrator."
login_status=$(curl --silent --show-error --max-time 30 \
  --cookie-jar "$tmp/cookies" --output "$tmp/login-body" \
  --write-out '%{http_code}' --request POST \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "j_username=$user" \
  --data-urlencode "j_password=$password" \
  "$base/rest_v2/login") || failure "REST login request failed"
# Both session login and HTTP Basic are documented REST v2 authentication
# mechanisms. Prefer the session; diagnose its failure separately from report
# authorization, rather than treating an HTML login page as successful.
if [[ "$login_status" == "200" ]] && grep -q 'JSESSIONID' "$tmp/cookies"; then
  auth_args=(--cookie "$tmp/cookies")
  echo "REST session login succeeded and returned a session cookie."
else
  echo "::warning title=REST session login unsuccessful::POST /rest_v2/login returned HTTP $login_status; validating the documented HTTP Basic alternative."
  auth_args=(--basic --user "$user:$password")
fi

# The repository descriptor proves that the authenticated session is accepted,
# and that the expected built-in sample is actually available.
echo "Checking the CustomersReport repository report."
request "$tmp/report-resource" "$base/rest_v2/resources/reports/interactive/CustomersReport" \
  "${auth_args[@]}" --header 'Accept: application/repository.reportUnit+json'
grep -Eqi '(CustomersReport|reportUnit)' "$tmp/report-resource" ||
  failure "Report descriptor was missing or unexpected"

# The report uses the bundled SugarCRM example database.
report="$base/rest_v2/reports/reports/interactive/CustomersReport"
echo "Exporting CustomersReport as PDF."
request "$tmp/accounts.pdf" "$report.pdf" "${auth_args[@]}" --header 'Accept: application/pdf'
[[ "$(head -c 5 "$tmp/accounts.pdf")" == '%PDF-' ]] ||
  failure "PDF export has no PDF signature (possible HTML login/error response)"
[[ $(stat -c %s "$tmp/accounts.pdf") -ge 1000 ]] ||
  failure "PDF export is unexpectedly small"

echo "Exporting CustomersReport as XLSX."
request "$tmp/accounts.xlsx" "$report.xlsx" "${auth_args[@]}" \
  --header 'Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
python3 - "$tmp/accounts.xlsx" <<'PY'
import sys
import zipfile
from pathlib import Path

path = Path(sys.argv[1])
if path.stat().st_size < 1000:
    raise SystemExit("XLSX export is unexpectedly small")
if not zipfile.is_zipfile(path):
    raise SystemExit("XLSX export is not a ZIP document")
with zipfile.ZipFile(path) as workbook:
    if workbook.testzip() is not None:
        raise SystemExit("XLSX ZIP is corrupt")
    names = set(workbook.namelist())
    required = {"[Content_Types].xml", "xl/workbook.xml"}
    if not required.issubset(names) or not any(
        n.startswith("xl/worksheets/sheet") and n.endswith(".xml") for n in names
    ):
        raise SystemExit("XLSX export is missing workbook or worksheet files")
print(f"XLSX workbook and worksheets verified ({path.stat().st_size} bytes).")
PY

echo "Functional smoke PASSED: authenticated REST access, report descriptor, PDF and XLSX."

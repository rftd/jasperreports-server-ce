# Runtime regression checks

Both full CI workflows run `scripts/test-report-runtime.py` against freshly
bootstrapped servers, in addition to the existing REST and browser checks. The
test uses the public REST API and parses exported report contents rather than
accepting HTTP 200 or a file extension alone.

## Coverage

- The shipped CustomersReport exports as PDF, XLSX, CSV and HTML; parsed output
  must contain the known sample customer `Alfreds`.
- The legacy REST `.xls` endpoint returns a real XLSX workbook and its matching
  content type. This mapping already exists in the original 8.2 source
  (`applicationContext-remote-services.xml`); the test preserves that contract.
  It does **not** validate binary Excel 97–2003/XLS output or change the mapping.
  The inherited CSV MIME type `application/vnd.ms-excel` is also preserved;
  CSV bytes are parsed as UTF-8 with strict quoting and intact parameter cells.
- A newly uploaded native JR7 report accepts a string input control containing
  accents, commas and quotes. PDF, XLSX, CSV and HTML must contain that exact
  parameter value; using the JRXML default instead is a failure.
- The native report embeds a test PNG and hyperlink. Its REST HTML export must
  retain the link and a retrievable PNG image. Link targets are not followed;
  image requests are limited to the configured server origin.
- An enabled disposable `ROLE_USER` account is denied access to the private
  report, then receives read-only access and executes it successfully. Valid
  update and delete requests must return HTTP 403 and leave the report intact.
- An actual one-shot Quartz job runs the native report with parameters and
  persists PDF/XLSX in the disposable repository folder. The authorized reader
  must be able to download and parse both outputs. No mail notification, alert,
  local filesystem or FTP destination is configured.
- Revoking access must block a fresh report export and download of the already
  generated scheduled PDF.

## Running locally

Use a disposable local installation with the bundled sample data. The script
creates a uniquely named folder under `/public`, a report and a temporary user;
it changes ACLs only inside that folder and generates a random test password.
Existing reports, users, global roles, database volumes and encryption keys are
not modified. The account running the test needs the standard Jasper admin
permissions to create and remove these fixtures.

Example for a Bash shell, from the repository root:

```sh
python3 -m venv /tmp/openjrs-runtime-tests
/tmp/openjrs-runtime-tests/bin/pip install --require-hashes \
  -r scripts/requirements-runtime-tests.txt
export JRS_BASE_URL=http://127.0.0.1:8080/jasperserver
export JRS_SMOKE_USER=jasperadmin
read -rsp 'Local OpenJRS password: ' JRS_SMOKE_PASSWORD; echo
export JRS_SMOKE_PASSWORD
/tmp/openjrs-runtime-tests/bin/python scripts/test-report-runtime.py
unset JRS_SMOKE_PASSWORD
```

The hash-pinned `pypdf` parser is a test dependency only. It is not added to the
server, Maven reactor or runtime image. XLSX parsing uses the Python standard
library. The tests require Python 3.9 or later and do not require a browser.

## Results and cleanup

Parsed exports and `result.json` are saved under
`$RUNNER_TEMP/report-runtime-tests/<uuid>/`, defaulting to `/tmp`. Both workflows
include this directory in their diagnostic artifacts. Request payloads, account
passwords and user-management response bodies are never written to these files.

Cleanup runs on success and failure and verifies that the created folder, user
and job no longer exist. Completed one-shot jobs can already be absent; HTTP
404 is accepted only as confirmed absence during that cleanup. Cleanup failures
fail the test and appear in `result.json`; the unique fixture names identify
what needs inspection after an interrupted process.

## Remaining scope

These checks do not establish full coverage of recurring/timezone schedules,
mail/FTP delivery, all input-control types, job ownership rules, every custom
report/component, binary XLS output or accessible/tagged PDFs. Existing browser
tests cover viewer pagination, zoom, search, hyperlinks, table actions, native
employee/MDX reports and maps. Broader security and PDF accessibility work remain
separate release tasks.

#!/usr/bin/env python3
"""Validate native Community JRXML, employee drilldown and SQL/MDX exports."""
import base64
from io import BytesIO
import json
import os
from pathlib import Path
import shutil
from urllib.parse import parse_qs, urlsplit
from zipfile import ZipFile

from playwright.sync_api import sync_playwright


def main():
    base = os.environ.get("JRS_BASE_URL", "http://127.0.0.1:8080/jasperserver").rstrip("/")
    user, password = os.environ["JRS_SMOKE_USER"], os.environ["JRS_SMOKE_PASSWORD"]
    chrome = os.environ.get("JRS_CHROME_PATH") or shutil.which("google-chrome") or shutil.which("chromium")
    assert chrome, "Sandbox-capable Chromium is required"
    output = Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "native-sample-tests"
    output.mkdir(parents=True, exist_ok=True)
    root = Path(__file__).resolve().parents[1] / "jasperserver/samples/jrxml7"
    manifest = json.loads((root / "manifest.json").read_text())
    checks, errors, failed = [], [], []
    with sync_playwright() as playwright:
        api = playwright.request.new_context(extra_http_headers={
            "Authorization": "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode(),
        })
        browser = playwright.chromium.launch(executable_path=chrome, headless=True, chromium_sandbox=True)
        page = browser.new_page(viewport={"width": 1440, "height": 1100})
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.on("response", lambda response: failed.append({"status": response.status,
                "url": response.url.split("?")[0]}) if response.status >= 400 else None)
        try:
            for report in manifest["reports"]:
                response = api.get(base + "/rest_v2/resources" + report["resource"],
                                   headers={"Accept": "application/octet-stream"})
                assert response.status == 200, report["uri"]
                assert response.body() == (root / report["file"]).read_bytes(), "Catalog is not using native JR7 samples"
            checks.append("repository JRXML matches native JR7 sources")
            page.goto(base + "/login.html", wait_until="networkidle")
            page.locator("#j_username").fill(user)
            page.locator("#j_password_pseudo").fill(password)
            page.locator("#submitButton").click()
            page.wait_for_url(lambda url: "login.html" not in str(url), timeout=60000)
            page.goto(base + "/flow.html?_flowId=viewReportFlow&reportUnit=/analysis/reports/Employees&standAlone=true",
                      wait_until="domcontentloaded")
            page.wait_for_selector(".jrPage", timeout=60000)
            page.wait_for_function("!document.querySelector('#export').disabled", timeout=60000)
            text = page.locator(".jrPage").first.inner_text()
            assert all(label in text for label in ("Employees", "User", "Name", "Title", "Accounts")), text
            page.screenshot(path=str(output / "employees.png"), full_page=True)
            # The bundled user max has accounts; the first user (jim) has none
            # and correctly produces an empty detail report.
            row = page.locator(".jrPage tr").filter(has=page.get_by_text("max", exact=True)).last
            link = row.locator("._jrHyperLink.ReportExecution span").first
            with page.expect_navigation(wait_until="domcontentloaded"):
                link.click()
            params = parse_qs(urlsplit(page.url).query)
            assert params.get("reportUnit") == ["/analysis/reports/EmployeeAccounts"], page.url
            employee = params["EmployeeID"][0]
            assert employee == "max_id", params
            page.wait_for_selector(".jrPage", timeout=60000)
            page.wait_for_function("!document.querySelector('#export').disabled", timeout=60000)
            assert "Accounts" in page.locator(".jrPage").first.inner_text()
            page.screenshot(path=str(output / "employee-accounts.png"), full_page=True)
            checks.append("Employees displays database rows and drills into EmployeeAccounts with EmployeeID")
            for report in ("Employees", "EmployeeAccounts"):
                for extension in ("pdf", "xlsx"):
                    response = api.get(base + "/rest_v2/reports/analysis/reports/" + report + "." + extension,
                                       params={"EmployeeID": employee} if report == "EmployeeAccounts" else {}, timeout=150000)
                    assert response.status == 200, f"{report}.{extension}: HTTP {response.status}"
                    data = response.body()
                    if extension == "pdf":
                        assert data.startswith(b"%PDF-") and len(data) > 1000
                    else:
                        with ZipFile(BytesIO(data)) as archive:
                            assert archive.testzip() is None and "xl/workbook.xml" in archive.namelist()
                            assert any(n.startswith("xl/worksheets/sheet") for n in archive.namelist())
                    (output / (report + "." + extension)).write_bytes(data)
            checks.append("Employees and EmployeeAccounts PDF/XLSX exports")
            page.goto(base + "/flow.html?_flowId=viewReportFlow&reportUnit=/analysis/reports/FoodmartSalesMondrianReport&standAlone=true",
                      wait_until="domcontentloaded")
            page.wait_for_selector(".jrPage", timeout=150000)
            page.wait_for_function("!document.querySelector('#export').disabled", timeout=150000)
            assert "Sales" in page.locator(".jrPage").first.inner_text()
            page.screenshot(path=str(output / "foodmart.png"), full_page=True)
            for extension in ("pdf", "xlsx"):
                response = api.get(base + "/rest_v2/reports/analysis/reports/FoodmartSalesMondrianReport." + extension,
                                   timeout=180000)
                assert response.status == 200, f"Foodmart.{extension}: HTTP {response.status}"
                data = response.body()
                if extension == "pdf":
                    assert data.startswith(b"%PDF-") and len(data) > 1000
                else:
                    with ZipFile(BytesIO(data)) as archive:
                        assert archive.testzip() is None and "xl/workbook.xml" in archive.namelist()
                (output / ("Foodmart." + extension)).write_bytes(data)
            checks.append("Native MDX Foodmart viewer and PDF/XLSX exports")
            assert not errors, errors
            assert not failed, failed
        except Exception:
            page.screenshot(path=str(output / "failure.png"), full_page=True)
            (output / "failure.html").write_text(page.content())
            raise
        finally:
            (output / "result.json").write_text(json.dumps({"checks": checks, "errors": errors,
                    "failed_requests": failed}, indent=2))
            browser.close()
            api.dispose()
    print("Native JR7 samples PASSED: " + ", ".join(checks))


if __name__ == "__main__":
    main()

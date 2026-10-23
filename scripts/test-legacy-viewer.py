#!/usr/bin/env python3
"""Browser checks for the disposable CI server (creates/deletes one report)."""
import base64
import json
import os
import shutil
from pathlib import Path
from uuid import uuid4

from playwright.sync_api import sync_playwright


def main():
    base = os.environ.get("JRS_BASE_URL", "http://127.0.0.1:8080/jasperserver").rstrip("/")
    user = os.environ["JRS_SMOKE_USER"]
    password = os.environ["JRS_SMOKE_PASSWORD"]
    chrome = os.environ.get("JRS_CHROME_PATH") or shutil.which("google-chrome") or shutil.which("chromium")
    if not chrome:
        raise RuntimeError("Chrome/Chromium is required for the viewer check")
    output = Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "viewer-smoke"
    output.mkdir(parents=True, exist_ok=True)
    checks, errors, bad_responses = [], [], []
    target = "https://example.com/openjrs-viewer-smoke"
    resource = "/public/openjrs_viewer_smoke_" + uuid4().hex
    xml = '''<jasperReport name="ViewerSmoke" pageWidth="300" pageHeight="200"
        columnWidth="260" leftMargin="20" rightMargin="20" topMargin="20" bottomMargin="20"
        whenNoDataType="AllSectionsNoDetail"><title height="90">
        <element kind="textField" x="0" y="0" width="250" height="30"
        linkType="Reference" linkTarget="Blank"><expression><![CDATA["OpenJRS hyperlink"]]></expression>
        <hyperlinkReferenceExpression><![CDATA["https://example.com/openjrs-viewer-smoke"]]></hyperlinkReferenceExpression>
        </element></title></jasperReport>'''
    with sync_playwright() as playwright:
        api = playwright.request.new_context(extra_http_headers={
            "Authorization": "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode(),
            "Accept": "application/repository.reportUnit+json",
        })
        created = []
        browser = playwright.chromium.launch(executable_path=chrome, headless=True,
                                             chromium_sandbox=True)
        page = browser.new_page(viewport={"width": 1440, "height": 1100})
        page.on("pageerror", lambda error: errors.append(error.stack or str(error)))
        page.on("response", lambda response: bad_responses.append(
            {"status": response.status, "url": response.url.split("?")[0]}
        ) if response.status >= 400 else None)

        def report(uri):
            page.goto(base + "/flow.html?_flowId=viewReportFlow&reportUnit=" + uri
                      + "&standAlone=true", wait_until="domcontentloaded")
            page.wait_for_selector(".jrPage", timeout=60000)
            page.wait_for_function('!document.querySelector("#export").disabled', timeout=60000)

        def create_report(uri, label, jrxml):
            response = api.put(base + "/rest_v2/resources" + uri, data={
                "label": label,
                "jrxml": {"jrxmlFile": {"label": "Viewer smoke JRXML", "type": "jrxml",
                                        "content": base64.b64encode(jrxml.encode()).decode()}},
                "alwaysPromptControls": False,
            }, headers={"Content-Type": "application/repository.reportUnit+json"})
            assert response.status in (200, 201), f"Create fixture: HTTP {response.status}"
            created.append(uri)

        try:
            page.goto(base + "/login.html", wait_until="networkidle")
            page.locator("#j_username").fill(user)
            page.locator("#j_password_pseudo").fill(password)
            page.locator("#submitButton").click()
            page.wait_for_url(lambda url: "login.html" not in str(url), timeout=60000)
            page.wait_for_load_state("networkidle")
            assert page.locator("#heartbeatOptin").count() == 0, "Vendor heartbeat dialog remains"
            assert page.evaluate("typeof window.__jrsConfigs__.heartbeatInitOptions") == "undefined"
            checks.append("UI login")
            report("/reports/interactive/CustomersReport")
            checks.append("HTML report")
            before = page.locator(".jrPage").first.inner_text()
            page.locator("#page_next").click()
            page.wait_for_function('document.querySelector("#page_current").value === "2"')
            page.wait_for_function('(text) => document.querySelector(".jrPage").innerText !== text', arg=before)
            page.locator("#page_first").click()
            page.wait_for_function('document.querySelector("#page_current").value === "1"')
            checks.append("pagination")
            width = page.locator(".jrPage").first.bounding_box()["width"]
            page.locator("#zoom_in").click()
            page.wait_for_function('(width) => document.querySelector(".jrPage").getBoundingClientRect().width > width', arg=width)
            page.locator("#zoom_out").click()
            page.wait_for_function('(width) => Math.abs(document.querySelector(".jrPage").getBoundingClientRect().width - width) < 2', arg=width)
            checks.append("zoom in/out")
            page.locator("#search_report").fill("Alfreds")
            page.locator("#search_report_button").click()
            page.wait_for_selector(".jr_search_result.highlight", state="attached", timeout=30000)
            assert "Alfreds" in page.locator(".jr_search_result.highlight").first.text_content()
            checks.append("search and highlighted match")
            page.screenshot(path=str(output / "customers.png"), full_page=True)

            create_report(resource, "OpenJRS disposable viewer smoke", xml)
            report(resource)
            # Intercept the link target so the check needs no external service.
            page.context.route(target, lambda route: route.fulfill(body="OpenJRS hyperlink target"))
            with page.expect_popup() as popup:
                page.locator(".jrPage").get_by_text("OpenJRS hyperlink", exact=True).click()
            popup.value.wait_for_load_state()
            assert popup.value.url == target
            checks.append("hyperlink opens correct target")

            # Exercise the real component protocol/cache with a deterministic
            # AMD renderer served by the browser test, without external services.
            script_url = base + "/openjrs-cv-smoke.js"
            renderer = (Path(__file__).resolve().parents[1] / "jasperserver"
                        / "jasperserver-custom-visualization/src/test/resources/cv-browser-smoke.js").read_text()
            page.route(script_url + "*", lambda route: route.fulfill(content_type="text/javascript", body=renderer))
            cv_xml = f'''<jasperReport name="CVViewerSmoke" pageWidth="300" pageHeight="200"
                columnWidth="260" leftMargin="20" rightMargin="20" topMargin="20" bottomMargin="20"
                whenNoDataType="AllSectionsNoDetail"><title height="110">
                <element kind="component" x="0" y="0" width="200" height="100">
                <component kind="customvisualization">
                <itemProperty name="script" value="{script_url}"/>
                <itemProperty name="module" value="openjrs-cv-smoke"/>
                </component></element></title></jasperReport>'''
            cv_resource = resource + "_cv"
            create_report(cv_resource, "OpenJRS disposable CV viewer smoke", cv_xml)
            report(cv_resource)
            circle = page.locator(".jrPage svg circle")
            circle.wait_for(state="visible", timeout=30000)
            assert circle.get_attribute("fill") == "#1264a3"
            checks.append("Community visualization rendered in viewer")
            page.screenshot(path=str(output / "visualization.png"), full_page=True)

            table_resource = resource + "_table"
            table_xml = (Path(__file__).resolve().parents[1] / "jasperserver"
                         / "jasperserver-war-jar/src/test/resources/interactive-table.jrxml").read_text()
            create_report(table_resource, "OpenJRS disposable table viewer smoke", table_xml)
            report(table_resource)
            page.locator(".jrcolHeader").first.click()
            page.locator(".sortAscIcon:visible").click()
            sorted_rows = '''() => {const text=document.querySelector('.jrPage').innerText;
                return text.indexOf('alpha')>=0 && text.indexOf('alpha')<text.indexOf('beta')
                    && text.indexOf('beta')<text.indexOf('zeta');}'''
            original_rows = '''() => {const text=document.querySelector('.jrPage').innerText;
                return text.indexOf('zeta')>=0 && text.indexOf('zeta')<text.indexOf('alpha');}'''
            page.wait_for_function(sorted_rows, timeout=30000)
            page.locator("#undo").click()
            page.wait_for_function(original_rows, timeout=30000)
            page.locator("#redo").click()
            page.wait_for_function(sorted_rows, timeout=30000)
            page.locator("#undo").click()
            page.wait_for_function(original_rows, timeout=30000)
            checks.append("interactive table sort, undo and redo")
            page.locator(".jrcolHeader").first.click()
            page.locator(".filterIcon:visible").click()
            dialog = page.locator(".tableFilterDialog:visible")
            dialog.locator('input[name="clearFilter"][value="false"]').check()
            dialog.locator('select[name="filterTypeOperator"]').select_option(label="Equals")
            dialog.locator('input[name="fieldValueStart"]').fill("alpha")
            dialog.get_by_role("button", name="OK", exact=True).click()
            page.wait_for_function('''() => {const text=document.querySelector('.jrPage').innerText;
                return text.includes('alpha') && !text.includes('zeta') && !text.includes('beta');}''', timeout=30000)
            checks.append("interactive table filter")
            page.screenshot(path=str(output / "table-filter.png"), full_page=True)
            page.locator("#undo").click()
            page.wait_for_function(original_rows, timeout=30000)
            checks.append("undo table filter")
            assert not errors, errors
            assert not bad_responses, bad_responses
        except Exception:
            page.screenshot(path=str(output / "failure.png"), full_page=True)
            (output / "failure.html").write_text(page.content())
            raise
        finally:
            (output / "result.json").write_text(json.dumps({
                "checks": checks, "javascript_errors": errors, "failed_requests": bad_responses,
            }, indent=2))
            for uri in reversed(created):
                response = api.delete(base + "/rest_v2/resources" + uri)
                assert response.status in (200, 204), f"Delete fixture: HTTP {response.status}"
            api.dispose()
            browser.close()
    print("Viewer smoke PASSED: " + ", ".join(checks))


if __name__ == "__main__":
    main()

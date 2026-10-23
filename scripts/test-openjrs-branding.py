#!/usr/bin/env python3
"""Check public identity against the disposable CI server and its stored themes."""
import json
import os
import shutil
from pathlib import Path
from urllib.parse import urljoin

from playwright.sync_api import sync_playwright


def main():
    base = os.environ.get("JRS_BASE_URL", "http://127.0.0.1:8080/jasperserver").rstrip("/")
    chrome = os.environ.get("JRS_CHROME_PATH") or shutil.which("google-chrome") or shutil.which("chromium")
    if not chrome:
        raise RuntimeError("A sandbox-capable Chrome/Chromium is required")
    output = Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "openjrs-branding"
    output.mkdir(parents=True, exist_ok=True)
    checks, errors, failed_requests = [], [], []
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=chrome, headless=True, chromium_sandbox=True)
        context = browser.new_context(viewport={"width": 1440, "height": 1000})
        page = context.new_page()
        page.set_default_timeout(30000)
        page.on("pageerror", lambda error: errors.append(error.stack or str(error)))
        page.on("response", lambda response: failed_requests.append({
            "status": response.status, "url": response.url.split("?")[0]
        }) if response.status >= 400 else None)
        try:
            for locale in ["en_US", "pt_BR", "de_DE", "es_ES", "fr_FR", "it_IT", "ja_JP", "zh_CN"]:
                page.goto(base + "/login.html?userLocale=" + locale, wait_until="networkidle")
                assert page.title().startswith("OpenJRS Server:"), (locale, page.title())
                brand = page.locator("#loginForm .openjrs-login-brand")
                assert "OpenJRS" in brand.inner_text() and "Server" in brand.inner_text()
                assert brand.evaluate("element => getComputedStyle(element).backgroundImage") == "none"
                assert float(brand.evaluate("element => parseFloat(getComputedStyle(element).fontSize)")) >= 30
                assert "OpenJRS Server" in page.locator("#welcome").inner_text()
                assert "OpenJRS Server" in page.locator("#about a").inner_text()
                assert "Cloud Software Group" in page.locator("#copyright").inner_text()
                if locale in ("en_US", "pt_BR"):
                    page.screenshot(path=str(output / ("login-" + locale + ".png")), full_page=True)
                checks.append("login identity " + locale)

            icon_url = page.locator('link[rel="shortcut icon"], link[rel="icon"]').first.get_attribute("href")
            response = context.request.get(urljoin(base + "/", icon_url))
            assert response.status == 200, f"Favicon HTTP {response.status}"
            assert "image/svg+xml" in response.headers.get("content-type", "")
            assert "<svg" in response.text() and ">O</text>" in response.text()
            checks.append("OpenJRS favicon")

            # The login remains usable on a narrow screen with the text mark.
            page.set_viewport_size({"width": 375, "height": 812})
            page.locator("#j_username").scroll_into_view_if_needed()
            assert page.locator("#j_username").is_visible()
            brand_box = page.locator(".openjrs-login-brand").bounding_box()
            assert brand_box and brand_box["x"] >= 0 and brand_box["x"] + brand_box["width"] <= 375
            page.screenshot(path=str(output / "login-narrow.png"), full_page=True)
            checks.append("narrow login")
            page.set_viewport_size({"width": 1440, "height": 1000})
            context.clear_cookies()

            page.goto(base + "/login.html?userLocale=en_US", wait_until="networkidle")
            page.locator("#j_username").fill(os.environ["JRS_SMOKE_USER"])
            page.locator("#j_password_pseudo").fill(os.environ["JRS_SMOKE_PASSWORD"])
            page.locator("#submitButton").click()
            page.wait_for_url(lambda url: "login.html" not in str(url), timeout=60000)
            page.wait_for_load_state("networkidle")
            if page.locator("#heartbeatOptin").is_visible():
                page.locator("#heartbeatCheck").uncheck()
                page.locator("#heartbeatOptin button").filter(has_text="OK").click()
                page.locator("#heartbeatOptin").wait_for(state="hidden")
            assert page.locator("#logo").inner_text() == "OpenJRS Server"
            assert page.locator("#logo").evaluate("element => getComputedStyle(element).backgroundImage") == "none"
            page.screenshot(path=str(output / "home.png"), full_page=True)
            checks.append("authenticated header")
            page.locator("#about a").click()
            page.locator("#aboutBox").wait_for(state="visible")
            page.wait_for_function('getComputedStyle(document.querySelector("#aboutBox")).opacity === "1"')
            assert "OpenJRS Server" in page.locator("#aboutDialogTitle").inner_text()
            assert "JasperReports Server Community Edition" in page.locator(".openjrs-origin").inner_text()
            assert "Cloud Software Group" in page.locator("#aboutDialogBody").inner_text()
            assert "Upstream base version" in page.locator("#aboutDialogBody").inner_text()
            page.screenshot(path=str(output / "about.png"), full_page=True)
            page.locator("#aboutBox button").click()
            checks.append("About identity and preserved upstream attribution")

            page.goto(base + "/flow.html?_flowId=viewReportFlow&reportUnit="
                      + "/reports/interactive/CustomersReport&standAlone=true", wait_until="domcontentloaded")
            page.wait_for_selector(".jrPage", timeout=60000)
            page.wait_for_function('!document.querySelector("#export").disabled', timeout=60000)
            assert page.title().startswith("OpenJRS Server:")
            assert page.locator("#logo").inner_text() == "OpenJRS Server"
            page.screenshot(path=str(output / "report.png"), full_page=True)
            checks.append("report viewer identity")
            assert not errors, errors
            assert not failed_requests, failed_requests
        except Exception:
            page.screenshot(path=str(output / "failure.png"), full_page=True)
            raise
        finally:
            (output / "result.json").write_text(json.dumps({
                "checks": checks, "javascript_errors": errors, "failed_requests": failed_requests,
            }, indent=2))
            browser.close()
    print("OpenJRS branding PASSED: " + ", ".join(checks))


if __name__ == "__main__":
    main()

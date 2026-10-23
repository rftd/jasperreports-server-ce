#!/usr/bin/env python3
"""Exercise shipped Table/Map reports with controlled tiles, never a public tile crawl."""
import base64
from io import BytesIO
import json
import os
from pathlib import Path
import shutil
from urllib.parse import unquote, urlsplit
from zipfile import ZipFile

from playwright.sync_api import sync_playwright


def main():
    base = os.environ.get("JRS_BASE_URL", "http://127.0.0.1:8080/jasperserver").rstrip("/")
    user, password = os.environ["JRS_SMOKE_USER"], os.environ["JRS_SMOKE_PASSWORD"]
    chrome = os.environ.get("JRS_CHROME_PATH") or shutil.which("google-chrome") or shutil.which("chromium")
    assert chrome, "Sandbox-capable Chromium is required"
    output = Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "map-table-tests"
    output.mkdir(parents=True, exist_ok=True)
    tile = (Path(__file__).resolve().parents[1] / "jasperserver/jasperserver-custom-visualization/src/test/resources/map-test-tile.png").read_bytes()
    checks, errors, failed, tile_requests, requested_hosts = [], [], [], [], []

    def record(message):
        checks.append(message)
        print(message, flush=True)

    with sync_playwright() as playwright:
        api = playwright.request.new_context(extra_http_headers={
            "Authorization": "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode(),
        })
        browser = playwright.chromium.launch(executable_path=chrome, headless=True, chromium_sandbox=True)
        context = browser.new_context(viewport={"width": 1440, "height": 1100})
        context.on("request", lambda request: requested_hosts.append(urlsplit(request.url).netloc))

        def tile_response(route):
            tile_requests.append(route.request.url)
            route.fulfill(status=200, content_type="image/png", body=tile)

        context.route("https://tile.openstreetmap.org/**", tile_response)
        context.route("**/scripts/openjrs-map-test-tile.png", tile_response)
        page = context.new_page()
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.on("response", lambda response: failed.append({"status": response.status,
                 "url": response.url.split("?")[0]}) if response.status >= 400 else None)

        def report(name, parameters=""):
            page.goto(base + "/flow.html?_flowId=viewReportFlow&reportUnit=/reports/interactive/"
                      + name + "&standAlone=true" + parameters, wait_until="domcontentloaded")
            page.wait_for_selector(".jrPage", timeout=60000)
            page.wait_for_function("!document.querySelector('#export').disabled", timeout=60000)

        def map_ready(latitude, longitude, zoom):
            selector = ".openjrs-osm-map[data-tiles-ready='true']"
            page.wait_for_selector(selector, timeout=60000)
            map_element = page.locator(selector).first
            assert abs(float(map_element.get_attribute("data-latitude")) - latitude) < 0.0002
            assert abs(float(map_element.get_attribute("data-longitude")) - longitude) < 0.0002
            assert int(map_element.get_attribute("data-zoom")) == zoom
            assert map_element.locator(".leaflet-tile-loaded").count() > 0
            assert "OpenStreetMap" in map_element.locator(".leaflet-control-attribution").inner_text()
            return map_element

        try:
            page.goto(base + "/login.html", wait_until="networkidle")
            page.locator("#j_username").fill(user)
            page.locator("#j_password_pseudo").fill(password)
            page.locator("#submitButton").click()
            page.wait_for_url(lambda url: "login.html" not in str(url), timeout=60000)
            report("TableReport")
            text = page.locator(".jrPage").first.inner_text()
            assert "Address Report" in text and "CSV Data Adapter" in text
            assert "Robert White" in text and "San Francisco" in text, "CSV rows are missing"
            link = page.locator(".jrPage ._jrHyperLink.ReportExecution span").first
            link.wait_for(state="visible", timeout=30000)
            record("shipped CSV table, data and map drilldown parameters")
            page.screenshot(path=str(output / "table.png"), full_page=True)
            for extension in ("pdf", "xlsx"):
                response = api.get(base + "/rest_v2/reports/reports/interactive/TableReport." + extension, timeout=150000)
                assert response.status == 200, f"Table {extension}: HTTP {response.status}"
                body = response.body()
                if extension == "pdf":
                    assert body.startswith(b"%PDF-") and len(body) > 1000
                else:
                    with ZipFile(BytesIO(body)) as archive:
                        assert archive.testzip() is None and "xl/workbook.xml" in archive.namelist()
                (output / ("table." + extension)).write_bytes(body)
            record("Table PDF and XLSX exports")
            # Follow the shipped report's own link, including its coordinates.
            with page.expect_navigation(wait_until="domcontentloaded"):
                link.click()
            assert "/reports/interactive/MapReport" in unquote(page.url), page.url
            assert "latitude=" in page.url and "longitude=" in page.url
            page.wait_for_selector(".openjrs-osm-map[data-tiles-ready='true']", timeout=60000)
            record("Table-to-Map drilldown renders")
            report("MapReport")
            map_element = map_ready(44.435, 26.096, 12)
            map_element.locator(".leaflet-control-zoom-in").click()
            page.wait_for_function("document.querySelector('.openjrs-osm-map').dataset.zoom === '13'")
            map_element.locator(".leaflet-control-zoom-out").click()
            page.wait_for_function("document.querySelector('.openjrs-osm-map').dataset.zoom === '12'")
            box = map_element.bounding_box()
            page.mouse.move(box["x"] + 200, box["y"] + 150)
            page.mouse.down()
            page.mouse.move(box["x"] + 260, box["y"] + 150, steps=10)
            page.mouse.up()
            page.wait_for_function("Math.abs(Number(document.querySelector('.openjrs-osm-map').dataset.longitude) - 26.096) > 0.001")
            record("OpenStreetMap tiles, attribution, default center, zoom and pan")
            report("MapReport", "&latitude=-20.4697&longitude=-54.6201&zoom=9")
            map_ready(-20.4697, -54.6201, 9)
            page.screenshot(path=str(output / "map.png"), full_page=True)
            record("requested latitude, longitude and numeric zoom")
            if os.environ.get("JRS_MAP_EXPORT") == "1":
                for extension in ("pdf", "xlsx"):
                    response = api.get(base + "/rest_v2/reports/reports/interactive/MapReport." + extension, timeout=150000)
                    assert response.status == 200, f"Map {extension}: HTTP {response.status}: {response.text()[:500]}"
                    body = response.body()
                    if extension == "pdf":
                        assert body.startswith(b"%PDF-") and len(body) > 1000
                    else:
                        with ZipFile(BytesIO(body)) as archive:
                            assert archive.testzip() is None and any(n.startswith("xl/media/") for n in archive.namelist())
                    (output / ("map." + extension)).write_bytes(body)
                record("Map PDF/XLSX images rendered by sandboxed server Chromium")
            assert tile_requests, "Map did not request tiles"
            assert not errors, errors
            assert not failed, failed
            assert not any("google" in host for host in requested_hosts), requested_hosts
        except Exception:
            page.screenshot(path=str(output / "failure.png"), full_page=True)
            (output / "failure.html").write_text(page.content())
            raise
        finally:
            (output / "result.json").write_text(json.dumps({"checks": checks, "errors": errors,
                "failed_requests": failed, "tile_request_count": len(tile_requests)}, indent=2))
            api.dispose()
            browser.close()
    print("Map/Table smoke PASSED: " + ", ".join(checks))


if __name__ == "__main__":
    main()

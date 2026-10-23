#!/usr/bin/env python3
"""REST export, repository ACL and one-shot scheduler checks on a disposable server."""

import base64
import csv
from html.parser import HTMLParser
from io import BytesIO, StringIO
import json
import os
from pathlib import Path
import secrets
import time
from urllib.error import HTTPError
from urllib.parse import quote, urlencode, urljoin, urlsplit
from urllib.request import build_opener, HTTPRedirectHandler, Request
from uuid import uuid4
import xml.etree.ElementTree as ET
from zipfile import ZipFile

from pypdf import PdfReader


MARKER = 'OpenJRS café, "JR7"'
LINK = "https://example.com/openjrs-runtime-regression"
REPORT = '''<jasperReport name="RuntimeRegression" pageWidth="400" pageHeight="240"
    columnWidth="360" leftMargin="20" rightMargin="20" topMargin="20" bottomMargin="20"
    whenNoDataType="AllSectionsNoDetail">
    <parameter name="Marker" class="java.lang.String">
        <defaultValueExpression><![CDATA["DEFAULT_PARAMETER_WAS_NOT_REPLACED"]]></defaultValueExpression>
    </parameter>
    <title height="100">
        <element kind="staticText" x="0" y="0" width="350" height="25" fontName="DejaVu Sans">
            <text>OpenJRS runtime regression</text>
        </element>
        <element kind="textField" x="0" y="30" width="350" height="25" fontName="DejaVu Sans"
            linkType="Reference" linkTarget="Blank">
            <expression><![CDATA[$P{Marker}]]></expression>
            <hyperlinkReferenceExpression><![CDATA["https://example.com/openjrs-runtime-regression"]]></hyperlinkReferenceExpression>
        </element>
        <element kind="textField" x="0" y="60" width="250" height="25">
            <expression><![CDATA[Integer.valueOf(42)]]></expression>
        </element>
        <element kind="image" x="300" y="60" width="24" height="24">
            <expression><![CDATA[new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode("OPENJRS_IMAGE_DATA"))]]></expression>
        </element>
    </title>
</jasperReport>'''


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        return None


class Client:
    def __init__(self, base, username=None, password=None):
        self.base = base
        self.opener = build_opener(NoRedirect())
        self.authorization = None if username is None else "Basic " + base64.b64encode(
            f"{username}:{password}".encode()).decode()

    def request(self, method, path, payload=None, *, accept="application/json",
                content_type="application/json", expected=(200,), timeout=150):
        url = urljoin(self.base + "/", path) if path.startswith("http") else self.base + path
        if (urlsplit(url).scheme, urlsplit(url).netloc) != (urlsplit(self.base).scheme, urlsplit(self.base).netloc):
            raise RuntimeError("Export references an unexpected external server")
        headers = {"Accept": accept}
        if self.authorization:
            headers["Authorization"] = self.authorization
        data = None
        if payload is not None:
            data = json.dumps(payload).encode()
            headers["Content-Type"] = content_type
        request = Request(url, data=data, headers=headers, method=method)
        try:
            response = self.opener.open(request, timeout=timeout)
        except HTTPError as error:
            response = error
        with response:
            status, body, result_headers = response.status, response.read(), response.headers
        if status not in expected:
            # Never include request payloads, passwords or user endpoint responses.
            raise RuntimeError(f"{method} {urlsplit(url).path}: HTTP {status}; expected {expected}")
        return body, result_headers, status

    def json(self, method, path, payload=None, **options):
        body, _, _ = self.request(method, path, payload, **options)
        return json.loads(body) if body else None


class ReportHTML(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.text, self.images, self.links = [], [], []

    def handle_data(self, data):
        self.text.append(data)

    def handle_starttag(self, tag, attributes):
        attributes = dict(attributes)
        if tag == "img" and attributes.get("src"):
            self.images.append(attributes["src"])
        if tag == "a" and attributes.get("href"):
            self.links.append(attributes["href"])


def validate_export(extension, data, expected_text):
    if extension == "pdf":
        assert data.startswith(b"%PDF-"), "PDF signature missing"
        reader = PdfReader(BytesIO(data), strict=True)
        assert reader.pages and not reader.is_encrypted
        text = "\n".join(page.extract_text() or "" for page in reader.pages)
    elif extension == "xlsx":
        namespace = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
        with ZipFile(BytesIO(data)) as workbook:
            assert workbook.testzip() is None and "xl/workbook.xml" in workbook.namelist()
            assert any(n.startswith("xl/worksheets/sheet") for n in workbook.namelist())
            text = "\n".join(node.text or "" for name in workbook.namelist()
                             if name == "xl/sharedStrings.xml" or name.startswith("xl/worksheets/")
                             for node in ET.fromstring(workbook.read(name)).iter()
                             if node.tag in ("{" + namespace["s"] + "}t", "{" + namespace["s"] + "}v"))
    elif extension == "csv":
        rows = list(csv.reader(StringIO(data.decode("utf-8-sig"))))
        assert rows, "CSV contains no rows"
        text = "\n".join(cell for row in rows for cell in row)
    elif extension == "html":
        document = ReportHTML()
        document.feed(data.decode("utf-8"))
        assert "<html" in data.decode("utf-8").lower(), "HTML document missing"
        text = "".join(document.text)
    else:
        raise ValueError(extension)
    assert expected_text in text, f"{extension}: expected report data missing"
    assert "DEFAULT_PARAMETER_WAS_NOT_REPLACED" not in text, "Report parameter was ignored"


def run():
    base = os.environ.get("JRS_BASE_URL", "http://127.0.0.1:8080/jasperserver").rstrip("/")
    admin = Client(base, os.environ["JRS_SMOKE_USER"], os.environ["JRS_SMOKE_PASSWORD"])
    anonymous = Client(base)
    token = uuid4().hex
    folder = "/public/openjrs_runtime_" + token
    report, destination = folder + "/Report", folder + "/Output"
    username = "openjrs_rt_" + token[:20]
    credential = secrets.token_urlsafe(30)
    reader = Client(base, username, credential)
    output = Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "report-runtime-tests" / token
    output.mkdir(parents=True, mode=0o700)
    checks, cleanup_errors = [], []
    created_folder = created_user = False
    job_id = None
    failure = None

    def resource(uri):
        return "/rest_v2/resources" + quote(uri, safe="/")

    def create_resource(uri, payload, kind):
        admin.request("PUT", resource(uri) + "?overwrite=false&createFolders=false", payload,
                      content_type="application/repository." + kind + "+json", expected=(201,))

    def permissions(readable):
        # Change only these two test ACL entries. Administrator ACLs are immutable.
        for recipient, mask in [("role:/ROLE_USER", 0), ("user:/" + username, 2 if readable else 0)]:
            path = "/rest_v2/permissions" + folder + ";recipient=" + quote(recipient, safe="")
            admin.request("PUT", path, {"mask": mask})

    image = (Path(__file__).resolve().parents[1] / "jasperserver/jasperserver-custom-visualization"
             / "src/test/resources/map-test-tile.png").read_bytes()
    report_xml = REPORT.replace("OPENJRS_IMAGE_DATA", base64.b64encode(image).decode())
    payload = {"label": "Runtime Report", "alwaysPromptControls": False,
               "jrxml": {"jrxmlFile": {"label": "Runtime JRXML", "type": "jrxml",
                         "content": base64.b64encode(report_xml.encode()).decode()}},
               "inputControls": [{"inputControl": {"label": "Marker", "type": 2,
                   "mandatory": True, "visible": True,
                   "dataType": {"dataType": {"label": "Marker type", "type": "text"}}}}]}

    def exports(client, uri, label, expected_text, parameters=None):
        for extension in ("pdf", "xlsx", "xls", "csv", "html"):
            path = "/rest_v2/reports" + uri + "." + extension
            if parameters:
                path += "?" + urlencode(parameters)
            data, headers, _ = client.request("GET", path, accept="*/*")
            # The original server 8.2 maps the legacy .xls endpoint to XLSX.
            actual_format = "xlsx" if extension == "xls" else extension
            (output / (label + "-" + extension + "." + actual_format)).write_bytes(data)
            validate_export(actual_format, data, expected_text)
            if extension == "xls":
                assert headers.get_content_type() in ("application/xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            if extension == "html":
                document = ReportHTML()
                document.feed(data.decode("utf-8"))
                if parameters:
                    assert LINK in document.links, "HTML hyperlink target changed"
                    assert document.images, "HTML report image is missing"
                report_images = 0
                for image in document.images:
                    if image.startswith("data:"):
                        image_data = base64.b64decode(image.split(",", 1)[1])
                    else:
                        image_url = urljoin(base + path, image)
                        image_data, image_headers, _ = client.request("GET", image_url, accept="image/*")
                        assert image_data and image_headers.get_content_type().startswith("image/"), "HTML image missing"
                    if image_data.startswith(b"\x89PNG\r\n\x1a\n") and len(image_data) > 100:
                        report_images += 1
                if parameters:
                    assert report_images, "HTML image attachment is not the report PNG"
            checks.append(label + " " + extension + (" (XLSX alias)" if extension == "xls" else "") + " content")

    try:
        admin.request("GET", resource(folder), expected=(404,))
        admin.request("GET", "/rest_v2/users/" + username, expected=(404,))
        create_resource(folder, {"label": "OpenJRS disposable runtime " + token}, "folder")
        created_folder = True
        create_resource(destination, {"label": "Scheduled output"}, "folder")
        create_resource(report, payload, "reportUnit")
        admin.request("PUT", "/rest_v2/users/" + username, {"username": username, "password": credential,
                      "fullName": "OpenJRS disposable regression reader", "enabled": True,
                      "externallyDefined": False, "roles": [{"name": "ROLE_USER"}]}, expected=(201,))
        created_user = True
        permissions(False)
        anonymous.request("GET", resource(report), expected=(401, 403))
        reader.request("GET", resource("/public"))  # Credential is valid independently of the private ACL.
        reader.request("GET", resource(report), expected=(403, 404))
        checks.append("anonymous access and private report access denied")
        exports(admin, "/reports/interactive/CustomersReport", "customers", "Alfreds")
        permissions(True)
        current_report = reader.json("GET", resource(report))
        exports(reader, report, "native-parameter", MARKER, {"Marker": MARKER})
        current_report["label"] = "FORBIDDEN UPDATE"
        reader.request("PUT", resource(report) + "?overwrite=true", current_report,
                       content_type="application/repository.reportUnit+json", expected=(403,))
        reader.request("DELETE", resource(report), expected=(403,))
        assert admin.json("GET", resource(report))["label"] == payload["label"]
        checks.append("read-only user executes native JR7 but cannot update/delete it")

        job = admin.json("PUT", "/rest_v2/jobs", {
            "label": "OpenJRS disposable scheduled export " + token,
            "source": {"reportUnitURI": report, "parameters": {"parameterValues": {"Marker": [MARKER]}}},
            "trigger": {"simpleTrigger": {"startType": 1, "occurrenceCount": 1}},
            "baseOutputFilename": "scheduled", "outputFormats": {"outputFormat": ["PDF", "XLSX"]},
            "repositoryDestination": {"folderURI": destination, "saveToRepository": True,
                                      "overwriteFiles": False, "sequentialFilenames": False}})
        job_id = int(job["id"])
        deadline, scheduled = time.monotonic() + 180, {}
        while time.monotonic() < deadline and len(scheduled) < 2:
            listing = admin.json("GET", "/rest_v2/resources?" + urlencode({
                "folderUri": destination, "recursive": "false", "type": "file", "limit": 20}),
                expected=(200, 204)) or {}
            for item in listing.get("resourceLookup", []):
                uri = item["uri"]
                assert uri.startswith(destination + "/"), "Scheduler output escaped its private folder"
                data, _, _ = reader.request("GET", resource(uri), accept="application/octet-stream")
                extension = "pdf" if data.startswith(b"%PDF-") else "xlsx"
                validate_export(extension, data, MARKER)
                scheduled[extension] = uri
                (output / ("scheduled." + extension)).write_bytes(data)
            if len(scheduled) < 2:
                time.sleep(1)
        assert set(scheduled) == {"pdf", "xlsx"}, "Scheduled PDF/XLSX not persisted within 180 seconds"
        checks.append("one-shot Quartz job persists parameterized PDF/XLSX readable by authorized user")
        permissions(False)
        reader.request("GET", "/rest_v2/reports" + report + ".pdf?" + urlencode({"Marker": MARKER}),
                       expected=(403, 404))
        reader.request("GET", resource(scheduled["pdf"]), accept="application/octet-stream", expected=(403, 404))
        checks.append("revoked access blocks new export and stored scheduled output")
    except Exception as error:
        failure = error
    finally:
        if job_id is not None:
            try:
                # Completed one-shot jobs may already have been removed by Quartz.
                admin.request("DELETE", "/rest_v2/jobs/" + str(job_id), expected=(200, 204, 404))
                admin.request("GET", "/rest_v2/jobs/" + str(job_id), expected=(404,))
            except Exception as error:
                cleanup_errors.append(str(error))
        if created_folder:
            try:
                admin.request("DELETE", resource(folder), expected=(200, 204))
                admin.request("GET", resource(folder), expected=(404,))
            except Exception as error:
                cleanup_errors.append(str(error))
        if created_user:
            try:
                admin.request("DELETE", "/rest_v2/users/" + username, expected=(200, 204))
                admin.request("GET", "/rest_v2/users/" + username, expected=(404,))
            except Exception as error:
                cleanup_errors.append(str(error))
        (output / "result.json").write_text(json.dumps({"checks": checks,
            "failure": str(failure) if failure else None, "cleanup_errors": cleanup_errors}, indent=2))
    if failure or cleanup_errors:
        raise RuntimeError(str(failure) if failure else "Fixture cleanup failed: " + "; ".join(cleanup_errors))
    print("Runtime regression PASSED: " + ", ".join(checks))


if __name__ == "__main__":
    run()

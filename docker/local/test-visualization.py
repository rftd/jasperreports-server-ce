#!/usr/bin/env python3
"""Exercise the server's real Chromium CV exporter, inside the runtime container."""
import base64
import os
from pathlib import Path
from uuid import uuid4

from playwright.sync_api import sync_playwright


def main():
    base = os.environ['JRS_BASE_URL'].rstrip('/')
    resource = '/public/openjrs_docker_cv_' + uuid4().hex
    # This URL is opened by Chromium inside the Tomcat container, not the host.
    script = 'http://127.0.0.1:8080/jasperserver/scripts/openjrs-cv-export.js'
    jrxml = f'''<jasperReport name="DockerCVSmoke" pageWidth="300" pageHeight="200"
        columnWidth="260" leftMargin="20" rightMargin="20" topMargin="20" bottomMargin="20"
        whenNoDataType="AllSectionsNoDetail"><title height="110">
        <element kind="component" x="0" y="0" width="200" height="100">
        <component kind="customvisualization">
        <itemProperty name="script" value="{script}"/>
        <itemProperty name="module" value="openjrs-cv-smoke"/>
        </component></element></title></jasperReport>'''
    credentials = (os.environ['JRS_SMOKE_USER'] + ':' + os.environ['JRS_SMOKE_PASSWORD']).encode()
    with sync_playwright() as playwright:
        api = playwright.request.new_context(extra_http_headers={
            'Authorization': 'Basic ' + base64.b64encode(credentials).decode(),
        })
        response = api.put(base + '/rest_v2/resources' + resource, data={
            'label': 'Disposable Docker visualization export check',
            'jrxml': {'jrxmlFile': {'label': 'Docker CV JRXML', 'type': 'jrxml',
                                    'content': base64.b64encode(jrxml.encode()).decode()}},
            'alwaysPromptControls': False,
        }, headers={'Content-Type': 'application/repository.reportUnit+json'})
        assert response.status in (200, 201), f'Create CV fixture: HTTP {response.status}'
        try:
            response = api.get(base + '/rest_v2/reports' + resource + '.pdf', timeout=150000)
            assert response.status == 200, f'CV PDF export: HTTP {response.status}'
            pdf = response.body()
            assert pdf.startswith(b'%PDF-') and len(pdf) > 1000, 'Invalid visualization PDF'
            (Path(os.environ.get('RUNNER_TEMP', '/tmp')) / 'docker-cv.pdf').write_bytes(pdf)
        finally:
            deleted = api.delete(base + '/rest_v2/resources' + resource)
            assert deleted.status in (200, 204), f'Delete CV fixture: HTTP {deleted.status}'
            api.dispose()
    print('Community visualization PDF exported by sandboxed Chromium inside Tomcat PASSED.')


if __name__ == '__main__':
    main()

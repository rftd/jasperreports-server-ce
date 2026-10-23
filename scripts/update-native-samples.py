#!/usr/bin/env python3
"""Preview or update unchanged bundled JRXML samples in an existing repository."""
import argparse
import base64
from datetime import datetime, timezone
import getpass
import hashlib
import json
import os
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote, urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=os.environ.get("JRS_BASE_URL", "http://127.0.0.1:8080/jasperserver"))
    parser.add_argument("--user", default=os.environ.get("JRS_USER", "jasperadmin"))
    parser.add_argument("--apply", action="store_true", help="save backups and update the unchanged samples")
    parser.add_argument("--backup-dir", type=Path, default=Path("openjrs-sample-backups"))
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    parsed = urlsplit(base)
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        parser.error("Use a base URL without credentials, query or fragment")
    if parsed.scheme != "https" and not (parsed.scheme == "http" and parsed.hostname in ("localhost", "127.0.0.1", "::1")):
        parser.error("Use HTTPS, or HTTP on localhost for the local test environment")
    password = os.environ.get("JRS_PASSWORD") or getpass.getpass("OpenJRS password: ")
    auth = "Basic " + base64.b64encode((args.user + ":" + password).encode()).decode()
    opener = build_opener(NoRedirect())

    def request(resource, accept, method="GET", payload=None):
        url = base + "/rest_v2/resources" + quote(resource, safe="/")
        if method == "PUT":
            # overwrite=true replaces a mismatched version instead of rejecting
            # it in SingleRepositoryServiceImpl.saveOrUpdate.
            url += "?overwrite=false&createFolders=false"
        headers = {"Authorization": auth, "Accept": accept}
        if payload is not None:
            headers["Content-Type"] = "application/repository.file+json"
        req = Request(url, data=payload, headers=headers, method=method)
        try:
            with opener.open(req, timeout=60) as response:
                return response.read()
        except HTTPError as error:
            raise RuntimeError(f"{method} {resource}: HTTP {error.code}; no further samples were updated") from None

    root = Path(__file__).resolve().parents[1] / "jasperserver/samples/jrxml7"
    manifest = json.loads((root / "manifest.json").read_text())
    pending, conflicts = [], []
    for report in manifest["reports"]:
        resource = report["resource"]
        metadata = json.loads(request(resource, "application/repository.file+json"))
        old = request(resource, "application/octet-stream")
        current = json.loads(request(resource, "application/repository.file+json"))
        if metadata.get("version") is None or metadata["version"] != current.get("version"):
            raise RuntimeError("Resource changed while reading: " + resource + "; retry")
        new = (root / report["file"]).read_bytes()
        if old == new:
            print("Already native: " + report["uri"])
        elif hashlib.sha256(old).hexdigest() != report["legacySha256"]:
            conflicts.append(report["uri"])
        else:
            pending.append((report, metadata, old, new))
            print("Ready to migrate: " + report["uri"])
    if conflicts:
        raise RuntimeError("Custom or unknown JRXML; nothing was updated: " + ", ".join(conflicts))
    if not args.apply or not pending:
        print("Preview complete; use --apply to back up and update." if pending else "No updates required.")
        return
    backup = args.backup_dir / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    backup.mkdir(mode=0o700, parents=True, exist_ok=False)
    # Back up every original before the first write. The server checks each
    # supplied resource version, so a concurrent edit causes a conflict.
    for report, metadata, old, _ in pending:
        name = Path(report["file"]).stem
        for suffix, content in ((".jrxml", old), (".json", json.dumps(metadata, indent=2).encode())):
            with (backup / (name + suffix)).open("xb") as stream:
                stream.write(content)
    print("Backups: " + str(backup.resolve()))
    for report, metadata, _, new in pending:
        metadata["content"] = base64.b64encode(new).decode()
        request(report["resource"], "application/repository.file+json", "PUT", json.dumps(metadata).encode())
        if request(report["resource"], "application/octet-stream") != new:
            raise RuntimeError("Updated JRXML differs from the source: " + report["uri"])
        print("Updated: " + report["uri"])


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError) as error:
        raise SystemExit(str(error))

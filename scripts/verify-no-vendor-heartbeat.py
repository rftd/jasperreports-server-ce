#!/usr/bin/env python3
"""Reject vendor heartbeat code and wiring in the deployable WAR, including JARs."""
import argparse
from io import BytesIO
from pathlib import Path
import sys
from zipfile import ZipFile


FORBIDDEN = (
    b"jaspersoft.com/heartbeat",
    b"com/jaspersoft/jasperserver/war/common/Heartbeat",
    b"com/jaspersoft/jasperserver/war/common/StandardHeartbeat",
    b"concreteHeartbeatBean",
    b"heartbeatInitOptions",
    b"heartbeat.enabled",
    b"heartbeatJob",
    b"heartbeatTrigger",
    b"heartbeat.html",
    b"heartbeatInfo.html",
)
SUFFIXES = {".class", ".js", ".jsp", ".xml", ".properties", ".css"}


def violations(archive, prefix=""):
    for entry in archive.infolist():
        name = prefix + entry.filename
        if entry.is_dir():
            continue
        if any(token in entry.filename.encode() for token in FORBIDDEN):
            yield name + ": forbidden heartbeat entry"
        suffix = Path(entry.filename).suffix.lower()
        if suffix == ".jar":
            with ZipFile(BytesIO(archive.read(entry))) as nested:
                yield from violations(nested, name + "!/")
        elif suffix in SUFFIXES:
            content = archive.read(entry)
            for token in FORBIDDEN:
                if token in content:
                    yield name + ": " + token.decode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("war", type=Path)
    args = parser.parse_args()
    with ZipFile(args.war) as archive:
        failures = list(violations(archive))
    if failures:
        print("Vendor heartbeat found in WAR:\n" + "\n".join(failures), file=sys.stderr)
        return 1
    print("WAR contains no vendor heartbeat client, endpoints, configuration or browser integration.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

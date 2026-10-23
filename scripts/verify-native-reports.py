#!/usr/bin/env python3
"""Require native JR7 Community report sources and reject the removed WAR adapter."""
import argparse
from io import BytesIO
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile


LEGACY_TAGS = {"queryString", "reportElement", "textElement", "textFieldExpression",
               "imageExpression", "subreportExpression", "subDataset", "componentElement"}


def check_xml(data, name):
    if b"<!DOCTYPE" in data.upper() or b"<!ENTITY" in data.upper():
        raise ValueError(f"{name}: DTDs/entities are not allowed")
    root = ET.fromstring(data)
    if root.tag not in {"jasperReport", "jasperTemplate"}:
        raise ValueError(f"{name}: migrate to native JR7 XML without the JR6 namespace")
    for node in root.iter():
        if node.tag in LEGACY_TAGS or node.tag.startswith("{"):
            raise ValueError(f"{name}: legacy report syntax {node.tag}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--war", type=Path, help="also check that the built WAR has no JR6 loader")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1] / "jasperserver"
    checked = 0
    for directory in ("samples", "production-tests/src/test/resources",
                      "jasperserver-remote-tests/src/test/resources",
                      "jasperserver-war-jar/src/test/resources"):
        for path in (root / directory).rglob("*"):
            if path.suffix.lower() in {".jrxml", ".jrtx"}:
                check_xml(path.read_bytes(), str(path.relative_to(root)))
                checked += 1
    archives = list((root / "buildomatic/install_resources/export").glob("*-ce.zip"))
    archives += list((root / "production-tests/src/test/resources").rglob("*.zip"))
    for path in archives:
        with ZipFile(path) as archive:
            for name in archive.namelist():
                if name.endswith(".data"):
                    data = archive.read(name)
                    if b"<jasperReport" in data or b"<jasperTemplate" in data:
                        check_xml(data, path.name + ":" + name)
                        checked += 1
    if args.war:
        with ZipFile(args.war) as war:
            for name in war.namelist():
                if "jasperserver-legacy-jrxml" in name:
                    raise ValueError("Removed JR6 adapter is still packaged: " + name)
                if name.startswith("WEB-INF/lib/") and name.endswith(".jar"):
                    with ZipFile(BytesIO(war.read(name))) as jar:
                        if any(entry.startswith("org/openjrs/reports/compat/") for entry in jar.namelist()):
                            raise ValueError("Removed JR6 adapter classes are still packaged: " + name)
    print(f"Native JR7 report verification PASSED: {checked} definitions; JR6 conversion is not supported.")


if __name__ == "__main__":
    main()

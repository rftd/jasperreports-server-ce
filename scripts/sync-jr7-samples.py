#!/usr/bin/env python3
"""Check or synchronize the native JR7 sample sources in the bootstrap catalogs."""
import argparse
import json
from pathlib import Path
import tempfile
from zipfile import ZipFile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="update catalogs; default only checks")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    samples = root / "jasperserver/samples/jrxml7"
    manifest = json.loads((samples / "manifest.json").read_text())
    replacements = {"resources" + report["resource"] + ".data":
                    (samples / report["file"]).read_bytes() for report in manifest["reports"]}
    outdated = []
    for name in manifest["catalogs"]:
        catalog = root / "jasperserver/buildomatic/install_resources/export" / name
        with ZipFile(catalog) as source:
            differences = [entry for entry, data in replacements.items() if source.read(entry) != data]
            if not differences:
                continue
            if not args.write:
                outdated.append(name)
                continue
            with tempfile.NamedTemporaryFile(dir=catalog.parent, suffix=".zip", delete=False) as temp:
                temporary = Path(temp.name)
            try:
                with ZipFile(temporary, "w") as target:
                    target.comment = source.comment
                    for entry in source.infolist():
                        data = replacements.get(entry.filename)
                        target.writestr(entry, source.read(entry) if data is None else data)
                temporary.chmod(catalog.stat().st_mode)
                temporary.replace(catalog)
            finally:
                temporary.unlink(missing_ok=True)
        print("Updated " + name)
    if outdated:
        raise SystemExit("Native samples differ from catalogs: " + ", ".join(outdated)
                         + "; run scripts/sync-jr7-samples.py --write")
    print("Native JR7 sample sources and catalogs match.")


if __name__ == "__main__":
    main()

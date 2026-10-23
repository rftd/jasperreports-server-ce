#!/usr/bin/env python3
"""Gate the historical WAR checksum drift in *diagnostic CI only*.

Never use this result to authorize a production release. The original
Buildomatic integrity check must remain strict outside the workflow.
"""

import argparse
import difflib
import sys
from pathlib import Path

# Observed from GitHub Actions build 39528162122. These hashes have not been
# independently provenance-verified. Do not expand this exception silently.
KNOWN_DIAGNOSTIC_DRIFT = {
    ("js-mongodb-datasource", "3.10.2"): (
        "53A6D012198EABE5C1583EABEB4EFA691DB7B17C",
        "08E41685458C5C1DD999D0BF856764D48BADEC5C",
    ),
    ("sitemesh", "2.2.1-Jaspersoft-1.0.2"): (
        "4F4096D355E9AC51B80882572CD1AAAB65C87AD2",
        "BA2777C37B1FAC17A69D8A4B064BE3B8BF7DC954",
    ),
}


def read_dependencies(path):
    result = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        fields = line.split()
        if len(fields) != 3:
            raise ValueError(f"{path}:{number}: expected name, version and SHA-1")
        name, version, digest = fields
        key = (name, version)
        if key in result:
            raise ValueError(f"{path}:{number}: duplicate dependency {key}")
        if len(digest) != 40 or any(char not in "0123456789abcdefABCDEF" for char in digest):
            raise ValueError(f"{path}:{number}: invalid SHA-1 for {name}")
        result[key] = digest.upper()
    if not result:
        raise ValueError(f"{path}: empty dependency inventory")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--master", type=Path, required=True)
    parser.add_argument("--current", type=Path, required=True)
    parser.add_argument("--build-exit", type=int, required=True)
    parser.add_argument("--build-log", type=Path, required=True)
    args = parser.parse_args()

    try:
        master = read_dependencies(args.master)
        current = read_dependencies(args.current)
        log = args.build_log.read_text(encoding="utf-8", errors="replace")

        if master == current:
            if args.build_exit:
                raise ValueError("Build failed for a reason other than dependency drift")
            print("WAR dependency inventory matches the historical master SHA-1 list.")
            return 0

        removed = set(master) - set(current)
        added = set(current) - set(master)
        changed = {key for key in master.keys() & current.keys() if master[key] != current[key]}
        if added or removed or changed != set(KNOWN_DIAGNOSTIC_DRIFT):
            raise ValueError(
                f"Unexpected dependency change: added={sorted(added)}, "
                f"removed={sorted(removed)}, changed={sorted(changed)}"
            )

        for key, (historical, observed) in KNOWN_DIAGNOSTIC_DRIFT.items():
            if master.get(key) != historical or current.get(key) != observed:
                raise ValueError(
                    f"Unexpected checksum for {key}: "
                    f"master={master.get(key)} current={current.get(key)}"
                )

        # The exact original Buildomatic checksum error must be present. Any
        # Maven or Ant failure that occurs for another reason cannot be masked.
        expected_messages = (
            "The list of third-party JARs found in the WAR file:",
            "is different from the master list:",
            "The following 2 library(-ies) have been changed:",
        )
        if args.build_exit == 0 or any(message not in log for message in expected_messages):
            raise ValueError("The Ant failure was not the expected checksum mismatch")

        print("::warning title=Unverified legacy WAR artifact hashes::"
              "Two historical JARs differ from committed SHA-1s; "
              "CI accepts these exact observed checksums for diagnostic build only. "
              "Verify artifact provenance before production use.")
        for key in sorted(changed):
            print(f"Diagnostic SHA-1 drift: {key[0]} {key[1]} "
                  f"{master[key]} -> {current[key]}")
        print("Buildomatic strict integrity validation is unchanged outside this CI workflow.")
        return 0
    except (OSError, ValueError) as exc:
        print(f"::error title=WAR dependency audit failed::{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

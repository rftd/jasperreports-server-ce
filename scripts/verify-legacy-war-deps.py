#!/usr/bin/env python3
"""Validate exactly the known migration WAR differences in diagnostic CI only.

The historical Buildomatic SHA-1 inventory remains unchanged. The strict Ant
check still runs and fails on a changed WAR outside this CI-only diagnostic gate.
This script NEVER authorizes production distribution: hashes observed in CI
must be independently provenance-verified before promotion.
"""

import argparse
import sys
from pathlib import Path

# Observed in migration PR #2, run 39718805105. Match every coordinate AND
# exact SHA-1 (not just names or versions). No wildcard drift is permitted.
# These changes are expected consequences of moving JasperReports 6 -> 7.
KNOWN_MIGRATION_ADDITIONS = {
    ("commons-beanutils2", "2.0.0-M2"): "51A76B49BE5C57072805E46901ADF152E368336F",
    ("commons-collections4", "4.5.0"): "E5CF89F0C6E132FC970BD9A465FDCB8DBE94F75A",
    ("javax.xml.soap-api", "1.4.0"): "667EF2EEE594CA7E05A1CBE0B37A428F7B57778F",
    ("xmpcore", "6.1.11"): "852F14101381E527E6D43339D7DB1698C970436C",
    # Required by JR7 PDF API, verified against Jaspersoft's published SHA-1.
    ("openpdf", "1.3.43.jaspersoft.2"): "2583544F268D26889F2A5E3DA40F2AC5900B5602",
}

KNOWN_MIGRATION_REMOVALS = {
    # Replaced by newer transitive dependencies during JasperReports migration.
    ("commons-collections4", "4.2"): "54EBEA0A5B653D3C680131E73FE807BB8F78C4ED",
    # Replaced by the exact OpenPDF Classic artifact required by JR 7.1.0.
    ("openpdf", "1.3.30.jaspersoft.2"): "D78A17A6E9C09C18A0F7787F8D3F4BCCABF3FC12",
    # Proprietary MongoDB adapter deliberately removed from Community.
    ("js-mongodb-datasource", "3.10.2"): "53A6D012198EABE5C1583EABEB4EFA691DB7B17C",
}

KNOWN_LEGACY_CHECKSUM_DRIFT = {
    # Existing historical SiteMesh discrepancy: observed, NOT provenance-verified.
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
            print("WAR inventory exactly matches historical baseline.")
            return 0

        removed = set(master) - set(current)
        added = set(current) - set(master)
        changed = {key for key in master.keys() & current.keys() if master[key] != current[key]}
        if (added != set(KNOWN_MIGRATION_ADDITIONS)
                or removed != set(KNOWN_MIGRATION_REMOVALS)
                or changed != set(KNOWN_LEGACY_CHECKSUM_DRIFT)):
            raise ValueError(
                f"Unexpected dependency change: added={sorted(added)}, "
                f"removed={sorted(removed)}, changed={sorted(changed)}"
            )

        for key, expected_sha in KNOWN_MIGRATION_ADDITIONS.items():
            if current.get(key) != expected_sha:
                raise ValueError(f"Unexpected added artifact digest {key}: {current.get(key)}")
        for key, expected_sha in KNOWN_MIGRATION_REMOVALS.items():
            if master.get(key) != expected_sha:
                raise ValueError(f"Unexpected historical artifact digest {key}: {master.get(key)}")
        for key, (historical, observed) in KNOWN_LEGACY_CHECKSUM_DRIFT.items():
            if master.get(key) != historical or current.get(key) != observed:
                raise ValueError(f"Unrecognized artifact checksum for {key}: "
                                 f"master={master.get(key)} current={current.get(key)}")

        # Only the original Ant checksum failure is eligible for this
        # diagnostic continuation; compilation, resolution and other failures
        # are never masked.
        expected_messages = (
            "The list of third-party JARs found in the WAR file:",
            "is different from the master list:",
            "The following 3 library(-ies) have been added:",
            "The following 1 library(-ies) have been removed:",
            "The following 3 library(-ies) have been changed:",
        )
        if args.build_exit == 0 or any(message not in log for message in expected_messages):
            raise ValueError("Ant failed for a reason other than the exact expected checksum drift")

        print("::warning title=Unverified migration WAR inventory::"
              "Exact hashes match the diagnosed migration inventory and the official JR7 OpenPDF artifact. "
              "Diagnostic CI may continue, but this does not authorize production. "
              "Independently verify all new artifacts and the SiteMesh fork.")
        print("Expected additions:", ", ".join(sorted(x[0] + ":" + x[1] for x in added)))
        print("Expected removals:", ", ".join(sorted(x[0] + ":" + x[1] for x in removed)))
        for key in sorted(changed):
            print(f"Observed SiteMesh SHA-1: {key[0]} {master[key]} -> {current[key]}")
        print("Original Buildomatic strict WAR checksum gate remains unchanged.")
        return 0
    except (OSError, ValueError) as exc:
        print(f"::error title=WAR dependency audit failed::{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

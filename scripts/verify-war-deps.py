#!/usr/bin/env python3
"""Verify the strict inventory and reviewed SHA-256 pins against the actual WAR."""

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import sys
from zipfile import BadZipFile, ZipFile


def digest(value, length):
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-fA-F]{%d}" % length, value):
        raise ValueError(f"Invalid {length * 4}-bit digest: {value!r}")
    return value.lower()


def coordinate(value):
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_.+-]+", value):
        raise ValueError(f"Invalid artifact name/version: {value!r}")
    return value


def read_dependencies(path):
    result = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        fields = line.split()
        if len(fields) != 3:
            raise ValueError(f"{path}:{number}: expected name, version and SHA-1")
        name, version, sha1 = fields
        key = (coordinate(name), coordinate(version))
        if key in result:
            raise ValueError(f"{path}:{number}: duplicate dependency {key}")
        result[key] = digest(sha1, 40)
    if not result:
        raise ValueError(f"{path}: empty dependency inventory")
    return result


def verify(master_path, current_path, war_path, provenance_path, project_version):
    master = read_dependencies(master_path)
    current = read_dependencies(current_path)
    if master != current:
        added = sorted(current.keys() - master.keys())
        removed = sorted(master.keys() - current.keys())
        changed = sorted(k for k in master.keys() & current.keys() if master[k] != current[k])
        raise ValueError(f"WAR inventory mismatch: added={added}, removed={removed}, changed={changed}")

    provenance = json.loads(provenance_path.read_text(encoding="utf-8"))
    if not isinstance(provenance, dict) or provenance.get("schemaVersion") != 1:
        raise ValueError("Unsupported dependency provenance schema")
    expected_inventory_sha256 = digest(provenance["inventorySha256"], 64)
    if hashlib.sha256(master_path.read_bytes()).hexdigest() != expected_inventory_sha256:
        raise ValueError("Inventory differs from the reviewed provenance manifest")

    inventory_files = {f"{name}-{version}.jar": sha1 for (name, version), sha1 in master.items()}
    if len(inventory_files) != len(master):
        raise ValueError("Ambiguous dependency filenames in inventory")
    artifacts = provenance["artifacts"]
    if not isinstance(artifacts, list) or not artifacts:
        raise ValueError("Empty artifact provenance list")
    pins = {}
    engine_files = set()
    for artifact in artifacts:
        name = coordinate(artifact["artifactId"])
        version = coordinate(artifact["version"])
        filename = f"{name}-{version}.jar"
        if artifact["file"] != filename or filename in pins:
            raise ValueError(f"Invalid or duplicate provenance filename: {filename}")
        sha1 = digest(artifact["sha1"], 40)
        sha256 = digest(artifact["sha256"], 64)
        in_inventory = artifact["buildomaticInventory"]
        if not isinstance(in_inventory, bool) or in_inventory != (filename in inventory_files):
            raise ValueError(f"Inconsistent inventory membership: {filename}")
        if in_inventory:
            if inventory_files[filename] != sha1:
                raise ValueError(f"Provenance disagrees with inventory: {filename}")
        else:
            if artifact["groupId"] != "net.sf.jasperreports":
                raise ValueError(f"Only explicitly pinned JR modules may be outside inventory: {filename}")
            engine_files.add(filename)
        pins[filename] = (sha1, sha256)

    local_artifacts = provenance["projectArtifacts"]
    if not isinstance(local_artifacts, list) or not local_artifacts:
        raise ValueError("Empty source-built artifact list")
    project_version = coordinate(project_version)
    local_files = {f"{coordinate(name)}-{project_version}.jar" for name in local_artifacts}
    if len(local_files) != len(local_artifacts) or local_files & (inventory_files.keys() | engine_files):
        raise ValueError("Duplicate or overlapping source-built artifacts")
    expected_files = inventory_files.keys() | engine_files | local_files

    with ZipFile(war_path) as war:
        entries = [name for name in war.namelist()
                   if name.startswith("WEB-INF/lib/") and name.endswith(".jar")]
        duplicates = [name for name, count in Counter(entries).items() if count != 1]
        if duplicates:
            raise ValueError(f"Duplicate WAR libraries: {duplicates}")
        filenames = {name[len("WEB-INF/lib/"):] for name in entries}
        if filenames != expected_files:
            raise ValueError(f"Unexpected WAR libraries: added={sorted(filenames - expected_files)}, "
                             f"missing={sorted(expected_files - filenames)}")
        for filename in sorted(inventory_files.keys() | engine_files):
            sha1 = hashlib.sha1()
            sha256 = hashlib.sha256()
            with war.open("WEB-INF/lib/" + filename) as stream:
                for block in iter(lambda: stream.read(65536), b""):
                    sha1.update(block)
                    sha256.update(block)
            expected_sha1 = inventory_files.get(filename) or pins[filename][0]
            if sha1.hexdigest() != expected_sha1:
                raise ValueError(f"WAR SHA-1 mismatch: {filename}")
            if filename in pins and sha256.hexdigest() != pins[filename][1]:
                raise ValueError(f"WAR SHA-256 mismatch: {filename}")
    return {"inventory": len(master), "sha256": len(pins), "source_built": len(local_files)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--master", type=Path, required=True)
    parser.add_argument("--current", type=Path, required=True)
    parser.add_argument("--war", type=Path, required=True)
    parser.add_argument("--project-version", required=True)
    parser.add_argument("--provenance", type=Path, default=Path(__file__).resolve().parents[1]
                        / "jasperserver/deps/war-deps-provenance.json")
    args = parser.parse_args()
    try:
        counts = verify(args.master, args.current, args.war, args.provenance, args.project_version)
        print(f"Strict WAR verification PASSED: {counts['inventory']} inventory entries, "
              f"{counts['sha256']} SHA-256 pins, {counts['source_built']} source-built libraries. "
              "No diagnostic exceptions.")
        return 0
    except (OSError, ValueError, KeyError, TypeError, BadZipFile) as exc:
        print(f"WAR dependency verification FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

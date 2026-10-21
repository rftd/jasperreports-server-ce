#!/usr/bin/env python3
"""Use the checked-in Yarn 1 mirrors of the historical private Jaspersoft Git packages.

No network access or third-party Python packages are needed.
Run --prepare before installing the legacy frontend, and --restore afterwards.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
import tarfile
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "jasperserver-ui" / "ce"
MIRROR = UI / "yarn-offline-mirror"
WORK = UI / ".legacy-private-deps"
PACKAGES = WORK / "packages"
BACKUPS = WORK / "original-manifests"
MODULES = ("js-sdk", "bi-report", "bi-repository", "jrs-ui")
PRIVATE_GIT_PREFIX = "git+ssh://git@git.jaspersoft.com:"

# Keep these prefixes explicit: they identify the exact, checked-in archives,
# not arbitrary content fetched from an untrusted Git server.
ARCHIVE_PREFIXES = {
    "dragdropextra": "dragdropextra.git-",
    "eslint-plugin-xss": "eslint-plugin-xss.git-",
    "iscroll": "iscroll-",
    "jCryption": "jCryption-",
    "jquery-ui-touch-punch": "jquery-ui-touch-punch-",
    "jquery.selection": "jquery.selection-",
    "jquery.urldecoder": "jquery.urldecoder-",
    "jqueryui-timepicker-addon": "jQuery-Timepicker-Addon-",
    "prototype": "prototype.git-",
    "scriptaculous": "scriptaculous.git-",
}
DEPENDENCY_GROUPS = ("dependencies", "devDependencies", "optionalDependencies")


def manifest_path(module: str) -> Path:
    return UI / module / "package.json"


def private_dependencies(document: dict) -> set[str]:
    result: set[str] = set()
    for group in DEPENDENCY_GROUPS:
        for name, value in document.get(group, {}).items():
            if isinstance(value, str) and value.startswith(PRIVATE_GIT_PREFIX):
                result.add(name)
    return result


def archive_for(name: str) -> Path:
    prefix = ARCHIVE_PREFIXES.get(name)
    if prefix is None:
        raise ValueError(f"Unmapped private Git dependency: {name}")
    matches = [
        candidate for candidate in MIRROR.glob(prefix + "*")
        if candidate.is_file()
        and re.fullmatch(re.escape(prefix) + r"[0-9a-f]{40}", candidate.name)
    ]
    if len(matches) != 1:
        raise ValueError(
            f"Expected one archived snapshot for {name}, found {len(matches)}"
        )
    return matches[0]


def safe_members(archive: tarfile.TarFile) -> list[tarfile.TarInfo]:
    members = archive.getmembers()
    for member in members:
        parts = PurePosixPath(member.name).parts
        if not parts or member.name.startswith("/") or ".." in parts:
            raise ValueError(f"Unsafe path inside archive: {member.name}")
        if not (member.isfile() or member.isdir()):
            raise ValueError(f"Unsupported tar entry: {member.name}")
    return members


def validate_archive(path: Path) -> None:
    with tarfile.open(path, "r:") as archive:
        members = safe_members(archive)
        manifest = next(
            (entry for entry in members if entry.name == "package.json" and entry.isfile()),
            None,
        )
        if manifest is None:
            raise ValueError(f"No package.json in archive: {path.name}")
        stream = archive.extractfile(manifest)
        if stream is None:
            raise ValueError(f"Unreadable package.json in {path.name}")
        doc = json.load(stream)
        if not isinstance(doc, dict) or not doc.get("name"):
            raise ValueError(f"Invalid package.json in {path.name}")


def unpack_archive(source: Path, destination: Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    with tarfile.open(source, "r:") as archive:
        for member in safe_members(archive):
            target = destination.joinpath(*PurePosixPath(member.name).parts)
            # Refuse links, absolute paths and parent traversal instead of
            # using extractall() on a historical vendor archive.
            if not target.resolve().is_relative_to(destination.resolve()):
                raise ValueError(f"Archive escaped destination: {member.name}")
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                stream = archive.extractfile(member)
                if stream is None:
                    raise ValueError(f"Unreadable archive member: {member.name}")
                with stream, target.open("wb") as output:
                    shutil.copyfileobj(stream, output)


def check() -> set[str]:
    required: set[str] = set()
    for module in MODULES:
        document = json.loads(manifest_path(module).read_text(encoding="utf-8"))
        required |= private_dependencies(document)
    if not required:
        raise ValueError("No private Git dependencies found. Were manifests already prepared?")
    print(f"Found {len(required)} distinct private Git dependencies across {len(MODULES)} manifests.")
    for name in sorted(required):
        archive = archive_for(name)
        validate_archive(archive)
        print(f"  OK {name}: {archive.name}")
    return required


def prepare() -> None:
    if any(BACKUPS.glob("*.json")):
        raise ValueError("Existing manifest backups found; run --restore first.")
    required = check()
    for name in sorted(required):
        unpack_archive(archive_for(name), PACKAGES / name)
    BACKUPS.mkdir(parents=True, exist_ok=True)
    for module in MODULES:
        path = manifest_path(module)
        document = json.loads(path.read_text(encoding="utf-8"))
        replacements = 0
        for group in DEPENDENCY_GROUPS:
            for name, value in document.get(group, {}).items():
                if isinstance(value, str) and value.startswith(PRIVATE_GIT_PREFIX):
                    document[group][name] = f"file:../.legacy-private-deps/packages/{name}"
                    replacements += 1
        if replacements:
            (BACKUPS / f"{module}.json").write_bytes(path.read_bytes())
            path.write_text(json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
            print(f"Prepared {module}: replaced {replacements} Git dependency references.")
    print("Next: cd jasperserver-ui/ce && yarn install --no-lockfile")
    print("After the build: python3 scripts/prepare-legacy-frontend.py --restore (from repository root).")


def restore() -> None:
    restored = 0
    for module in MODULES:
        backup = BACKUPS / f"{module}.json"
        if backup.exists():
            manifest_path(module).write_bytes(backup.read_bytes())
            backup.unlink()
            restored += 1
    print(f"Restored {restored} original package.json manifests.")
    if not restored:
        print("No backups found; nothing to restore.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    operation = parser.add_mutually_exclusive_group(required=True)
    operation.add_argument("--check", action="store_true")
    operation.add_argument("--prepare", action="store_true")
    operation.add_argument("--restore", action="store_true")
    args = parser.parse_args()
    try:
        if args.restore:
            restore()
        elif args.prepare:
            prepare()
        else:
            check()
    except (ValueError, OSError, tarfile.TarError, json.JSONDecodeError) as exc:
        print(f"Legacy frontend preflight failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

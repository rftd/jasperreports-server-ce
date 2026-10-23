import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import warnings
from zipfile import ZipFile


SCRIPT = Path(__file__).resolve().parents[1] / "verify-war-deps.py"
spec = importlib.util.spec_from_file_location("war_dependencies", SCRIPT)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class WarDependenciesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.master = self.root / "master.txt"
        self.current = self.root / "current.txt"
        self.manifest = self.root / "provenance.json"
        self.war = self.root / "server.war"
        self.files = {"ordinary-1.0.jar": b"unchanged third party fixture",
                      "audited-2.0.jar": b"reviewed migration fixture",
                      "jasperreports-7.1.0.jar": b"reviewed engine fixture",
                      "openjrs-ci.jar": b"source build fixture"}
        inventory = "".join(f"{name} {version} {hashlib.sha1(self.files[name + '-' + version + '.jar']).hexdigest()}\n"
                            for name, version in [("ordinary", "1.0"), ("audited", "2.0")])
        self.master.write_text(inventory)
        self.current.write_text(inventory)
        self.provenance = {"schemaVersion": 1,
                           "inventorySha256": hashlib.sha256(self.master.read_bytes()).hexdigest(),
                           "projectArtifacts": ["openjrs"], "artifacts": []}
        for name, version, in_inventory in [("audited", "2.0", True), ("jasperreports", "7.1.0", False)]:
            filename = name + "-" + version + ".jar"
            self.provenance["artifacts"].append({
                "artifactId": name, "groupId": "fixture" if in_inventory else "net.sf.jasperreports",
                "version": version, "file": filename, "buildomaticInventory": in_inventory,
                "sha1": hashlib.sha1(self.files[filename]).hexdigest(),
                "sha256": hashlib.sha256(self.files[filename]).hexdigest()})
        self.write_manifest()
        self.write_war()

    def write_manifest(self):
        self.manifest.write_text(json.dumps(self.provenance))

    def write_war(self, extra_entries=()):
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with ZipFile(self.war, "w") as archive:
                archive.writestr("WEB-INF/web.xml", b"<web-app/>")
                for name, data in list(self.files.items()) + list(extra_entries):
                    archive.writestr("WEB-INF/lib/" + name, data)

    def verify(self):
        return guard.verify(self.master, self.current, self.war, self.manifest, "ci")

    def test_accepts_exact_inventory_and_reviewed_war(self):
        self.assertEqual({"inventory": 2, "sha256": 2, "source_built": 1}, self.verify())

    def test_rejects_changed_generated_inventory(self):
        self.current.write_text(self.current.read_text().replace("ordinary 1.0", "ordinary 1.1"))
        with self.assertRaisesRegex(ValueError, "inventory mismatch"):
            self.verify()

    def test_rejects_empty_inventory(self):
        self.current.write_text("")
        with self.assertRaisesRegex(ValueError, "empty dependency inventory"):
            self.verify()

    def test_rejects_duplicate_inventory_coordinates(self):
        self.current.write_text(self.current.read_text() * 2)
        with self.assertRaisesRegex(ValueError, "duplicate dependency"):
            self.verify()

    def test_rejects_unreviewed_master_and_current_update(self):
        for path in (self.master, self.current):
            path.write_text(path.read_text().replace("ordinary 1.0", "ordinary 1.1"))
        with self.assertRaisesRegex(ValueError, "reviewed provenance manifest"):
            self.verify()

    def test_rejects_tampered_unchanged_library_even_when_inventory_files_match(self):
        self.files["ordinary-1.0.jar"] += b"tampered"
        self.write_war()
        with self.assertRaisesRegex(ValueError, "WAR SHA-1 mismatch: ordinary"):
            self.verify()

    def test_rejects_tampered_engine_excluded_by_legacy_ant_scanner(self):
        self.files["jasperreports-7.1.0.jar"] += b"tampered"
        self.write_war()
        with self.assertRaisesRegex(ValueError, "WAR SHA-1 mismatch: jasperreports"):
            self.verify()

    def test_rejects_wrong_sha256_even_when_sha1_matches(self):
        self.provenance["artifacts"][0]["sha256"] = "0" * 64
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "WAR SHA-256 mismatch: audited"):
            self.verify()

    def test_rejects_extra_library_masquerading_as_project_output(self):
        self.write_war([("jasperserver-unknown-ci.jar", b"unexpected")])
        with self.assertRaisesRegex(ValueError, "Unexpected WAR libraries"):
            self.verify()

    def test_rejects_unlisted_engine_module(self):
        self.write_war([("jasperreports-unknown-7.1.0.jar", b"unexpected")])
        with self.assertRaisesRegex(ValueError, "Unexpected WAR libraries"):
            self.verify()

    def test_rejects_missing_reviewed_library(self):
        del self.files["audited-2.0.jar"]
        self.write_war()
        with self.assertRaisesRegex(ValueError, "missing=.*audited"):
            self.verify()

    def test_rejects_missing_source_built_library(self):
        del self.files["openjrs-ci.jar"]
        self.write_war()
        with self.assertRaisesRegex(ValueError, "missing=.*openjrs"):
            self.verify()

    def test_rejects_duplicate_zip_library_entries(self):
        self.write_war([("audited-2.0.jar", self.files["audited-2.0.jar"])])
        with self.assertRaisesRegex(ValueError, "Duplicate WAR libraries"):
            self.verify()

    def test_rejects_duplicate_provenance_entries(self):
        self.provenance["artifacts"].append(self.provenance["artifacts"][0])
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "duplicate provenance filename"):
            self.verify()

    def test_rejects_wrong_project_version(self):
        with self.assertRaisesRegex(ValueError, "Unexpected WAR libraries"):
            guard.verify(self.master, self.current, self.war, self.manifest, "other")

    def test_rejects_non_engine_artifact_outside_inventory(self):
        self.provenance["artifacts"][1]["groupId"] = "unreviewed"
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "Only explicitly pinned JR modules"):
            self.verify()

    def test_command_fails_closed_on_malformed_provenance(self):
        self.manifest.write_text("{}")
        result = subprocess.run([sys.executable, str(SCRIPT), "--master", str(self.master),
                                 "--current", str(self.current), "--war", str(self.war),
                                 "--provenance", str(self.manifest), "--project-version", "ci"],
                                text=True, capture_output=True)
        self.assertEqual(1, result.returncode)
        self.assertIn("verification FAILED", result.stderr)


if __name__ == "__main__":
    unittest.main()

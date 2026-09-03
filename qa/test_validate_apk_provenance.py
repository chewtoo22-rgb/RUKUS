#!/usr/bin/env python3
import hashlib
import json
import pathlib
import tempfile
import unittest

from validate_apk_provenance import ProvenanceError, validate

SHA = "a" * 40
APK = b"PK\x03\x04synthetic-rukus-apk"


def evidence(apk_path: pathlib.Path) -> dict:
    return {
        "schema_version": 1,
        "product": "RUKUS",
        "source_sha": SHA,
        "application_id": "com.ruckus.agent",
        "version_code": 1,
        "apk_filename": apk_path.name,
        "apk_size": len(APK),
        "apk_sha256": hashlib.sha256(APK).hexdigest(),
        "signed": False,
    }


class ApkProvenanceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        self.apk = self.root / "app-release-unsigned.apk"
        self.apk.write_bytes(APK)
        self.prov = self.root / "provenance.json"

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, value):
        self.prov.write_text(json.dumps(value), encoding="utf-8")

    def assertRejected(self, value, expected_sha=SHA):
        self.write(value)
        with self.assertRaises(ProvenanceError):
            validate(self.prov, self.apk, expected_sha)

    def test_valid_exact_head_evidence(self):
        value = evidence(self.apk)
        self.write(value)
        self.assertEqual(validate(self.prov, self.apk, SHA)["apk_sha256"], value["apk_sha256"])

    def test_rejects_source_sha_drift(self):
        value = evidence(self.apk)
        value["source_sha"] = "b" * 40
        self.assertRejected(value)

    def test_rejects_hash_drift(self):
        value = evidence(self.apk)
        value["apk_sha256"] = "0" * 64
        self.assertRejected(value)

    def test_rejects_size_drift(self):
        value = evidence(self.apk)
        value["apk_size"] += 1
        self.assertRejected(value)

    def test_rejects_wrong_application_id(self):
        value = evidence(self.apk)
        value["application_id"] = "com.example.fake"
        self.assertRejected(value)

    def test_rejects_signed_state_drift(self):
        value = evidence(self.apk)
        value["signed"] = True
        self.assertRejected(value)

    def test_rejects_unknown_fields(self):
        value = evidence(self.apk)
        value["extra"] = "nope"
        self.assertRejected(value)

    def test_rejects_filename_traversal(self):
        value = evidence(self.apk)
        value["apk_filename"] = "../app-release-unsigned.apk"
        self.assertRejected(value)

    def test_rejects_symlinked_apk(self):
        target = self.root / "real.apk"
        target.write_bytes(APK)
        self.apk.unlink()
        self.apk.symlink_to(target)
        self.assertRejected(evidence(self.apk))

    def test_rejects_symlinked_evidence(self):
        real = self.root / "real.json"
        real.write_text(json.dumps(evidence(self.apk)), encoding="utf-8")
        self.prov.symlink_to(real)
        with self.assertRaises(ProvenanceError):
            validate(self.prov, self.apk, SHA)


if __name__ == "__main__":
    unittest.main()

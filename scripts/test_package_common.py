#!/usr/bin/env python3
"""Tests for packaging helper logic."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import package_common


class PackageCommonTest(unittest.TestCase):
    def write_release(self, app_root: Path, relative_path: str, version: str = "21.0.4") -> Path:
        release = app_root / relative_path
        release.parent.mkdir(parents=True, exist_ok=True)
        release.write_text(f'JAVA_VERSION="{version}"\\n', encoding="utf-8")
        return release

    def test_parse_java_major(self) -> None:
        self.assertEqual(21, package_common.parse_java_major('openjdk version "21.0.4"'))
        self.assertEqual(17, package_common.parse_java_major('17.0.8'))
        self.assertEqual(8, package_common.parse_java_major('java version "1.8.0_402"'))

    def test_find_runtime_release_linux_layout(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            app_root = Path(tmp) / "Breath"
            expected = self.write_release(app_root, "lib/runtime/release")
            self.assertEqual(expected, package_common.find_runtime_release(app_root))

    def test_find_runtime_release_windows_layout(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            app_root = Path(tmp) / "Breath"
            expected = self.write_release(app_root, "runtime/release")
            self.assertEqual(expected, package_common.find_runtime_release(app_root))

    def test_find_runtime_release_macos_layout(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            app_root = Path(tmp) / "Breath.app"
            expected = self.write_release(app_root, "Contents/runtime/Contents/Home/release")
            self.assertEqual(expected, package_common.find_runtime_release(app_root))

    def test_verify_runtime_rejects_non_java21(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            app_root = Path(tmp) / "Breath"
            self.write_release(app_root, "runtime/release", "17.0.8")
            with self.assertRaises(SystemExit):
                package_common.verify_runtime_is_java21(app_root)

    def test_next_build_version_increments_shared_counter(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            version_file = Path(tmp) / "build-version.properties"
            version_file.write_text("baseVersion=2.4\nbuildNumber=8\n", encoding="utf-8")

            self.assertEqual("2.4.9", package_common.next_build_version(version_file))
            self.assertEqual(("2.4", 9), package_common.read_build_version(version_file))
            self.assertEqual("2.4.10", package_common.next_build_version(version_file))
            self.assertEqual(("2.4", 10), package_common.read_build_version(version_file))

    def test_next_build_version_creates_missing_counter(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            version_file = Path(tmp) / "build-version.properties"

            self.assertEqual("1.0.1", package_common.next_build_version(version_file))
            self.assertEqual(("1.0", 1), package_common.read_build_version(version_file))

    def test_versioned_zip_stem_includes_app_version(self) -> None:
        self.assertEqual("Breath-linux-64-1.0.12", package_common.versioned_zip_stem("Breath-linux-64", "1.0.12"))

    def test_write_app_version_marker(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            app_root = Path(tmp) / "Breath"
            app_root.mkdir()

            marker = package_common.write_app_version_marker(app_root, "1.0.12")

            self.assertEqual(app_root / "VERSION.txt", marker)
            self.assertEqual("Breath 1.0.12\n", marker.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()

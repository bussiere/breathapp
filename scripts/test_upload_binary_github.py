#!/usr/bin/env python3
"""Tests for GitHub binary upload helper logic that does not touch the network."""

from __future__ import annotations

import os
import tempfile
import unittest
from pathlib import Path

import upload_binary_github


class UploadBinaryGithubTest(unittest.TestCase):
    def test_parse_github_url_from_readme_text(self) -> None:
        text = "GitHub repository: https://github.com/bussiere/breathapp\n"

        self.assertEqual(
            ("bussiere", "breathapp", "https://github.com/bussiere/breathapp"),
            upload_binary_github.parse_github_url(text),
        )

    def test_read_github_repo_from_readme(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            readme = Path(tmp) / "README.md"
            readme.write_text("Repo: https://github.com/bussiere/breathapp\n", encoding="utf-8")

            self.assertEqual(
                ("bussiere", "breathapp", "https://github.com/bussiere/breathapp"),
                upload_binary_github.read_github_repo_from_readme(readme),
            )

    def test_read_current_version_requires_existing_build(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            version_file = Path(tmp) / "build-version.properties"
            version_file.write_text("baseVersion=1.2\nbuildNumber=7\n", encoding="utf-8")

            self.assertEqual("1.2.7", upload_binary_github.read_current_version(version_file))

    def test_find_distribution_zips(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            dist = Path(tmp) / "dist"
            linux = dist / "linux-64"
            windows = dist / "windows-64"
            linux.mkdir(parents=True)
            windows.mkdir(parents=True)
            linux_zip = linux / "Breath-linux-64-1.0.1.zip"
            windows_zip = windows / "Breath-windows-64-1.0.2.zip"
            linux_zip.write_bytes(b"linux")
            windows_zip.write_bytes(b"windows")

            self.assertEqual([linux_zip, windows_zip], upload_binary_github.find_distribution_zips(dist))


    def test_env_value_accepts_lowercase_and_uppercase_names(self) -> None:
        old_lower = os.environ.get("github_name")
        old_upper = os.environ.get("GITHUB_NAME")
        try:
            os.environ.pop("github_name", None)
            os.environ["GITHUB_NAME"] = "bussiere"
            self.assertEqual("bussiere", upload_binary_github.env_value("github_name", "GITHUB_NAME"))

            os.environ["github_name"] = "local-name"
            self.assertEqual("local-name", upload_binary_github.env_value("github_name", "GITHUB_NAME"))
        finally:
            if old_lower is None:
                os.environ.pop("github_name", None)
            else:
                os.environ["github_name"] = old_lower
            if old_upper is None:
                os.environ.pop("GITHUB_NAME", None)
            else:
                os.environ["GITHUB_NAME"] = old_upper

    def test_existing_assets_by_name(self) -> None:
        release = {
            "assets": [
                {"name": "Breath-linux-64-1.0.1.zip", "id": 10},
                {"name": "ignored-no-id"},
            ]
        }

        self.assertEqual({"Breath-linux-64-1.0.1.zip": 10}, upload_binary_github.existing_assets_by_name(release))


if __name__ == "__main__":
    unittest.main()

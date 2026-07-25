#!/usr/bin/env python3
"""Build a Linux 64-bit standalone app image and zip with jpackage."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

from package_common import APP_NAME, PROJECT_ROOT, package_app

DIST_DIR = PROJECT_ROOT / "dist" / "linux-64"
ZIP_STEM = "Breath-linux-64"
APP_ROOT = DIST_DIR / APP_NAME
EXECUTABLE = APP_ROOT / "bin" / APP_NAME


def main() -> None:
    archive = package_app(
        system="Linux",
        label="Linux 64 bits",
        machines={"x86_64", "amd64", "aarch64", "arm64"},
        dist_dir=DIST_DIR,
        zip_stem=ZIP_STEM,
        gradlew_windows=False,
        app_root=APP_ROOT,
        executable=EXECUTABLE,
    )
    print(f"\nExecutable Linux 64 bits cree: {EXECUTABLE}")
    print(f"Archive distribuable autonome creee: {archive}")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as exc:
        sys.exit(exc.returncode)
    except KeyboardInterrupt:
        sys.exit(130)

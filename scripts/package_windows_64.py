#!/usr/bin/env python3
"""Build a Windows 64-bit standalone app image and zip with jpackage."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

from package_common import APP_NAME, PROJECT_ROOT, package_app

DIST_DIR = PROJECT_ROOT / "dist" / "windows-64"
ZIP_STEM = "Breath-windows-64"
APP_ROOT = DIST_DIR / APP_NAME
EXECUTABLE = APP_ROOT / f"{APP_NAME}.exe"


def main() -> None:
    archive = package_app(
        system="Windows",
        label="Windows 64 bits",
        machines={"amd64", "x86_64"},
        dist_dir=DIST_DIR,
        zip_stem=ZIP_STEM,
        gradlew_windows=True,
        app_root=APP_ROOT,
        executable=EXECUTABLE,
    )
    print(f"\nExecutable Windows 64 bits cree: {EXECUTABLE}")
    print(f"Archive distribuable autonome creee: {archive}")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as exc:
        sys.exit(exc.returncode)
    except KeyboardInterrupt:
        sys.exit(130)

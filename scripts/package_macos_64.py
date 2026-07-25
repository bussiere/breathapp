#!/usr/bin/env python3
"""Build a macOS 64-bit standalone app image and zip with jpackage."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

from package_common import APP_NAME, PROJECT_ROOT, package_app

DIST_DIR = PROJECT_ROOT / "dist" / "macos-64"
ZIP_STEM = "Breath-macos-64"
APP_ROOT = DIST_DIR / f"{APP_NAME}.app"
EXECUTABLE = APP_ROOT / "Contents" / "MacOS" / APP_NAME


def main() -> None:
    archive = package_app(
        system="Darwin",
        label="macOS 64 bits",
        machines={"x86_64", "arm64", "aarch64"},
        dist_dir=DIST_DIR,
        zip_stem=ZIP_STEM,
        gradlew_windows=False,
        app_root=APP_ROOT,
        executable=EXECUTABLE,
        jpackage_extras=["--mac-package-identifier", "com.customjv.breath"],
    )
    print(f"\nApplication macOS 64 bits creee: {APP_ROOT}")
    print(f"Archive distribuable autonome creee: {archive}")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as exc:
        sys.exit(exc.returncode)
    except KeyboardInterrupt:
        sys.exit(130)

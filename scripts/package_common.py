#!/usr/bin/env python3
"""Shared helpers for native jpackage distributions."""

from __future__ import annotations

import os
import platform
import re
import shutil
import struct
import subprocess
import tempfile
import zipfile
from pathlib import Path


APP_NAME = "Breath"
DEFAULT_BASE_VERSION = "1.0"
MAIN_CLASS = "org.example.App"
MAIN_JAR = "app.jar"
REQUIRED_JAVA_MAJOR = 21
PROJECT_ROOT = Path(__file__).resolve().parents[1]
INSTALL_DIR = PROJECT_ROOT / "app" / "build" / "install" / "app"
BUILD_VERSION_FILE = PROJECT_ROOT / "build-version.properties"
VERSION_PATTERN = re.compile(r"^[0-9]+(?:\.[0-9]+){1,2}$")


def run(command: list[str], *, env: dict[str, str] | None = None) -> None:
    print("+ " + " ".join(map(str, command)))
    subprocess.run(command, cwd=PROJECT_ROOT, check=True, env=env)


def require_platform(expected_system: str, allowed_machines: set[str], label: str) -> None:
    if platform.system() != expected_system:
        raise SystemExit(f"Ce script doit etre lance sous {label}.")
    if struct.calcsize("P") * 8 != 64:
        raise SystemExit("Python doit etre lance en 64 bits.")
    machine = platform.machine().lower()
    if machine not in allowed_machines:
        raise SystemExit(f"Architecture non supportee: {platform.machine()} (64 bits requis).")


def java_home_bin(name: str) -> Path | None:
    java_home = os.environ.get("JAVA_HOME")
    if not java_home:
        return None
    suffix = ".exe" if platform.system() == "Windows" else ""
    candidate = Path(java_home) / "bin" / f"{name}{suffix}"
    return candidate if candidate.exists() else None


def tool_path(name: str) -> Path:
    from_home = java_home_bin(name)
    if from_home is not None:
        return from_home
    found = shutil.which(name)
    if found is None:
        raise SystemExit(f"Commande introuvable: {name}. Installez un JDK 21 complet 64 bits et definissez JAVA_HOME.")
    return Path(found)


def command_output(command: list[str]) -> str:
    completed = subprocess.run(command, cwd=PROJECT_ROOT, text=True, capture_output=True, check=True)
    return (completed.stdout + completed.stderr).strip()


def parse_java_major(version_output: str) -> int | None:
    for token in version_output.replace('"', ' ').replace("'", " ").split():
        if token[0:1].isdigit():
            head = token.split(".", 1)[0]
            if head == "1" and "." in token:
                parts = token.split(".")
                return int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else None
            return int(head) if head.isdigit() else None
    return None


def require_jdk21() -> tuple[Path, Path, dict[str, str]]:
    java = tool_path("java")
    jpackage = tool_path("jpackage")
    java_major = parse_java_major(command_output([str(java), "-version"]))
    jpackage_major = parse_java_major(command_output([str(jpackage), "--version"]))
    if java_major != REQUIRED_JAVA_MAJOR or jpackage_major != REQUIRED_JAVA_MAJOR:
        raise SystemExit(
            "Java 21 est obligatoire pour compiler et packager. "
            f"java={java_major}, jpackage={jpackage_major}. "
            "Installez un JDK 21 complet et pointez JAVA_HOME dessus."
        )

    java_home = java.parent.parent
    env = os.environ.copy()
    env["JAVA_HOME"] = str(java_home)
    env["PATH"] = str(java.parent) + os.pathsep + env.get("PATH", "")
    return java, jpackage, env


def require_gradlew(windows: bool) -> Path:
    gradlew = PROJECT_ROOT / ("gradlew.bat" if windows else "gradlew")
    if not gradlew.exists():
        raise SystemExit(f"{gradlew.name} est introuvable a la racine du projet.")
    return gradlew


def run_gradle_test_and_install(gradlew: Path, env: dict[str, str]) -> None:
    run([str(gradlew), ":app:clean", ":app:test", ":app:installDist"], env=env)


def verify_app_jar() -> None:
    jar_path = INSTALL_DIR / "lib" / MAIN_JAR
    if not jar_path.exists():
        raise SystemExit(f"Jar applicatif introuvable: {jar_path}")
    with zipfile.ZipFile(jar_path) as jar:
        names = set(jar.namelist())
        if "test_sprite/chips.png" not in names:
            raise SystemExit("Resource test_sprite/chips.png absente du jar applicatif.")
        offenders = []
        for name in names:
            lowered = name.lower()
            if "chunli" in lowered:
                offenders.append(name)
                continue
            data = jar.read(name)
            if b"chunli" in data.lower():
                offenders.append(name)
        if offenders:
            raise SystemExit("Ancienne reference chunli trouvee dans le jar: " + ", ".join(sorted(offenders)[:10]))


def prepare_dist(dist_dir: Path) -> None:
    if dist_dir.exists():
        shutil.rmtree(dist_dir)
    dist_dir.mkdir(parents=True, exist_ok=True)


# Keep the counter in one repo-level file so every OS build consumes the same
# release sequence instead of drifting into platform-specific version numbers.
def read_build_version(version_file: Path = BUILD_VERSION_FILE) -> tuple[str, int]:
    base_version = DEFAULT_BASE_VERSION
    build_number = 0
    if not version_file.exists():
        # A missing counter should not block first-time builds; the first package
        # becomes 1.0.1 and materializes the file for future builds.
        return base_version, build_number

    for line in version_file.read_text(encoding="utf-8").splitlines():
        clean = line.strip()
        if not clean or clean.startswith("#") or "=" not in clean:
            continue
        key, value = clean.split("=", 1)
        key = key.strip()
        value = value.strip()
        if key == "baseVersion":
            base_version = value
        elif key == "buildNumber":
            build_number = int(value)

    # jpackage expects numeric dotted versions on all target OSes, so fail before
    # spending time on a native image that would carry invalid metadata.
    if not VERSION_PATTERN.match(base_version):
        raise SystemExit(f"baseVersion invalide dans {version_file}: {base_version}")
    if build_number < 0:
        raise SystemExit(f"buildNumber invalide dans {version_file}: {build_number}")
    return base_version, build_number


def write_build_version(base_version: str, build_number: int, version_file: Path = BUILD_VERSION_FILE) -> None:
    version_file.write_text(
        f"baseVersion={base_version}\n"
        f"buildNumber={build_number}\n",
        encoding="utf-8",
    )


def next_build_version(version_file: Path = BUILD_VERSION_FILE) -> str:
    base_version, build_number = read_build_version(version_file)
    build_number += 1
    write_build_version(base_version, build_number, version_file)
    return f"{base_version}.{build_number}"


def versioned_zip_stem(zip_stem: str, app_version: str) -> str:
    return f"{zip_stem}-{app_version}"


def write_app_version_marker(app_root: Path, app_version: str) -> Path:
    # Zip files often get renamed after upload or mirroring; keeping the version
    # inside the app image gives support/debugging a stable source of truth.
    marker = app_root / "VERSION.txt"
    marker.write_text(f"{APP_NAME} {app_version}\n", encoding="utf-8")
    return marker


def jpackage_command(jpackage: Path, dist_dir: Path, app_version: str, extras: list[str] | None = None) -> list[str]:
    command = [
        str(jpackage),
        "--type",
        "app-image",
        "--name",
        APP_NAME,
        "--app-version",
        app_version,
        "--vendor",
        "CustomJV",
        "--input",
        str(INSTALL_DIR / "lib"),
        "--main-jar",
        MAIN_JAR,
        "--main-class",
        MAIN_CLASS,
        "--dest",
        str(dist_dir),
        "--java-options",
        "-Dfile.encoding=UTF-8",
    ]
    if extras:
        command.extend(extras)
    return command


def runtime_release_candidates(app_root: Path) -> list[Path]:
    return [
        app_root / "lib" / "runtime" / "release",
        app_root / "runtime" / "release",
        app_root / "Contents" / "runtime" / "Contents" / "Home" / "release",
        app_root / "Contents" / "PlugIns" / "runtime" / "Contents" / "Home" / "release",
    ]


def find_runtime_release(app_root: Path) -> Path | None:
    for candidate in runtime_release_candidates(app_root):
        if candidate.exists():
            return candidate
    for candidate in app_root.rglob("release"):
        try:
            text = candidate.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        if "JAVA_VERSION" in text:
            return candidate
    return None


def verify_runtime_is_java21(app_root: Path) -> None:
    release_file = find_runtime_release(app_root)
    if release_file is None:
        candidates = ", ".join(str(path) for path in runtime_release_candidates(app_root))
        raise SystemExit(f"Fichier release du runtime introuvable. Chemins testes: {candidates}")
    text = release_file.read_text(encoding="utf-8", errors="replace")
    marker = f'JAVA_VERSION="{REQUIRED_JAVA_MAJOR}.'
    if marker not in text and f'JAVA_VERSION="{REQUIRED_JAVA_MAJOR}"' not in text:
        first_line = text.splitlines()[0] if text.splitlines() else "runtime inconnu"
        raise SystemExit(f"Runtime embarque non Java 21 ({release_file}): {first_line}")


def smoke_export(executable: Path, env: dict[str, str]) -> None:
    if not executable.exists():
        raise SystemExit(f"Executable introuvable apres jpackage: {executable}")
    with tempfile.TemporaryDirectory(prefix="breath-package-smoke-") as tmp:
        out = Path(tmp)
        run([str(executable), "--export-demo", str(out)], env=env)
        expected = [
            out / "chips_breath_spritesheet.png",
            out / "chips_breath_apng.png",
            out / "chips_breath.gif",
            out / "frames" / "breath_000.png",
        ]
        missing = [str(path) for path in expected if not path.exists()]
        if missing:
            raise SystemExit("Smoke test package incomplet, fichiers absents: " + ", ".join(missing))


def package_app(
        *,
        system: str,
        label: str,
        machines: set[str],
        dist_dir: Path,
        zip_stem: str,
        gradlew_windows: bool,
        app_root: Path,
        executable: Path,
        jpackage_extras: list[str] | None = None) -> Path:
    require_platform(system, machines, label)
    _java, jpackage, env = require_jdk21()
    gradlew = require_gradlew(gradlew_windows)
    run_gradle_test_and_install(gradlew, env)
    verify_app_jar()
    # Consume the version after cheap preflight checks, but before jpackage, so
    # app metadata, VERSION.txt, and the zip filename all describe the same build.
    app_version = next_build_version()
    prepare_dist(dist_dir)
    print(f"Version build: {app_version}")
    run(jpackage_command(jpackage, dist_dir, app_version, jpackage_extras), env=env)
    write_app_version_marker(app_root, app_version)
    verify_runtime_is_java21(app_root)
    smoke_export(executable, env)
    archive = shutil.make_archive(str(dist_dir / versioned_zip_stem(zip_stem, app_version)), "zip", root_dir=dist_dir, base_dir=app_root.name)
    return Path(archive)

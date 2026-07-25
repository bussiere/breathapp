#!/usr/bin/env python3
"""Upload distribution zip assets to the GitHub Release configured in README.md."""

from __future__ import annotations

import getpass
import json
import os
import re
import sys
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen


PROJECT_ROOT = Path(__file__).resolve().parents[1]
README_FILE = PROJECT_ROOT / "README.md"
DIST_DIR = PROJECT_ROOT / "dist"
BUILD_VERSION_FILE = PROJECT_ROOT / "build-version.properties"
GITHUB_API = "https://api.github.com"
GITHUB_UPLOADS = "https://uploads.github.com"
GITHUB_URL_PATTERN = re.compile(r"https://github\.com/(?P<owner>[A-Za-z0-9_.-]+)/(?P<repo>[A-Za-z0-9_.-]+)")


class GitHubApiError(RuntimeError):
    pass


def read_current_version(version_file: Path = BUILD_VERSION_FILE) -> str:
    base_version = "1.0"
    build_number = 0
    if version_file.exists():
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
    if build_number <= 0:
        raise SystemExit("Aucun build versionne a uploader. Lancez d'abord un script build_*_standalone.")
    return f"{base_version}.{build_number}"


def find_distribution_zips(dist_dir: Path = DIST_DIR) -> list[Path]:
    if not dist_dir.exists():
        raise SystemExit("Dossier dist introuvable. Lancez d'abord les scripts de build standalone.")
    zips = sorted(path for path in dist_dir.glob("*/*.zip") if path.is_file())
    if not zips:
        raise SystemExit("Aucun zip de distribution trouve dans dist/*/*.zip.")
    return zips


def parse_github_url(text: str) -> tuple[str, str, str] | None:
    match = GITHUB_URL_PATTERN.search(text)
    if not match:
        return None
    owner = match.group("owner")
    repo = match.group("repo").removesuffix(".git")
    return owner, repo, f"https://github.com/{owner}/{repo}"


def read_github_repo_from_readme(readme_file: Path = README_FILE) -> tuple[str, str, str]:
    if not readme_file.exists():
        raise SystemExit(f"README introuvable: {readme_file}")
    parsed = parse_github_url(readme_file.read_text(encoding="utf-8"))
    if parsed is None:
        raise SystemExit("URL GitHub introuvable dans README.md. Ajoutez https://github.com/<owner>/<repo> dans la doc.")
    return parsed


def prompt_non_empty(label: str, default: str | None = None) -> str:
    prompt = f"{label} [{default}]: " if default else f"{label}: "
    value = input(prompt).strip()
    if value:
        return value
    if default:
        return default
    raise SystemExit(f"Valeur obligatoire: {label}")


def env_value(*names: str) -> str | None:
    for name in names:
        value = os.environ.get(name)
        if value:
            return value.strip()
    return None


def prompt_token() -> str:
    token = env_value("github_token", "GITHUB_TOKEN")
    if token:
        return token
    token = getpass.getpass("GitHub token: ").strip()
    if not token:
        raise SystemExit("Token GitHub obligatoire.")
    return token


def github_request(
        method: str,
        url: str,
        token: str,
        *,
        payload: dict[str, object] | None = None,
        data: bytes | None = None,
        content_type: str = "application/json") -> dict[str, object]:
    body = data if data is not None else (json.dumps(payload).encode("utf-8") if payload is not None else None)
    request = Request(url, data=body, method=method)
    request.add_header("Authorization", f"Bearer {token}")
    request.add_header("Accept", "application/vnd.github+json")
    request.add_header("X-GitHub-Api-Version", "2022-11-28")
    if body is not None:
        request.add_header("Content-Type", content_type)
    try:
        with urlopen(request) as response:
            raw = response.read()
    except HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        raise GitHubApiError(f"GitHub API {method} {url} -> HTTP {exc.code}: {details}") from exc
    if not raw:
        return {}
    return json.loads(raw.decode("utf-8"))


def get_release(owner: str, repo: str, tag: str, token: str) -> dict[str, object] | None:
    url = f"{GITHUB_API}/repos/{owner}/{repo}/releases/tags/{quote(tag, safe='')}"
    try:
        return github_request("GET", url, token)
    except GitHubApiError as exc:
        if "HTTP 404" in str(exc):
            return None
        raise


def create_release(owner: str, repo: str, tag: str, title: str, token: str) -> dict[str, object]:
    # A release is the stable public container for binary assets; direct branch
    # uploads would mix large generated files with source history.
    url = f"{GITHUB_API}/repos/{owner}/{repo}/releases"
    return github_request(
        "POST",
        url,
        token,
        payload={
            "tag_name": tag,
            "name": title,
            "body": "Standalone distribution zips generated by the local packaging scripts.",
            "draft": False,
            "prerelease": False,
        },
    )


def delete_existing_asset(owner: str, repo: str, asset_id: int, token: str) -> None:
    url = f"{GITHUB_API}/repos/{owner}/{repo}/releases/assets/{asset_id}"
    github_request("DELETE", url, token)


def existing_assets_by_name(release: dict[str, object]) -> dict[str, int]:
    assets = release.get("assets", [])
    if not isinstance(assets, list):
        return {}
    found: dict[str, int] = {}
    for asset in assets:
        if isinstance(asset, dict) and isinstance(asset.get("name"), str) and isinstance(asset.get("id"), int):
            found[asset["name"]] = asset["id"]
    return found


def upload_asset(owner: str, repo: str, release_id: int, zip_path: Path, token: str) -> dict[str, object]:
    # The upload host is separate from the REST API host; constructing it here
    # avoids relying on the templated upload_url returned by older clients.
    url = (
        f"{GITHUB_UPLOADS}/repos/{owner}/{repo}/releases/{release_id}/assets"
        f"?name={quote(zip_path.name)}"
    )
    return github_request("POST", url, token, data=zip_path.read_bytes(), content_type="application/zip")


def main() -> None:
    zips = find_distribution_zips()
    version = read_current_version()
    owner, repo, repo_url = read_github_repo_from_readme()

    print(f"Repo GitHub lu depuis README.md: {repo_url}")
    print("Zips trouves:")
    for zip_path in zips:
        print(f"- {zip_path.relative_to(PROJECT_ROOT)}")
    print()

    github_name = env_value("github_name", "GITHUB_NAME") or prompt_non_empty("Nom GitHub", owner)
    if github_name != owner:
        raise SystemExit(f"Le README cible {owner}/{repo}, mais le nom saisi est {github_name}.")
    tag = prompt_non_empty("Tag de release", f"v{version}")
    title = prompt_non_empty("Titre de release", f"Breath {version}")
    token = prompt_token()

    release = get_release(owner, repo, tag, token)
    if release is None:
        print(f"Creation de la release {tag} sur {owner}/{repo}...")
        release = create_release(owner, repo, tag, title, token)
    else:
        print(f"Release existante trouvee: {tag}")

    release_id = release.get("id")
    if not isinstance(release_id, int):
        raise SystemExit("Reponse GitHub invalide: release id manquant.")

    existing = existing_assets_by_name(release)
    for zip_path in zips:
        if zip_path.name in existing:
            print(f"Remplacement de l'asset existant: {zip_path.name}")
            delete_existing_asset(owner, repo, existing[zip_path.name], token)
        print(f"Upload: {zip_path.name}")
        upload_asset(owner, repo, release_id, zip_path, token)

    print(f"Upload termine: {repo_url}/releases/tag/{tag}")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
    except GitHubApiError as exc:
        raise SystemExit(str(exc))

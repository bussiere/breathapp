#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  # SDKMAN exports JAVA_HOME/PATH for the configured Java candidate in this shell.
  # The Python packaging script still verifies that java and jpackage are Java 21.
  # shellcheck source=/dev/null
  set +u
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  set -u
fi

python3 scripts/package_linux_64.py

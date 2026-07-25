#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
output_dir="${1:-export}"
mkdir -p "${output_dir}"
output_dir="$(cd "${output_dir}" && pwd)"
./gradlew -q run --args="--export-demo ${output_dir}"
printf "Generated demo animation files in %s\n" "${output_dir}"
printf -- "- %s\n" \
  "${output_dir}/chips_breath_spritesheet.png" \
  "${output_dir}/chips_breath_apng.png" \
  "${output_dir}/chips_breath.gif" \
  "${output_dir}/frames/breath_000.png ..."

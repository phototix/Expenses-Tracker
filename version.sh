#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
INDEX_FILE="$ROOT_DIR/index.html"
VERSION_FILE="$ROOT_DIR/.asset-version"

if [[ ! -f "$INDEX_FILE" ]]; then
  echo "index.html not found: $INDEX_FILE"
  exit 1
fi

current_version=0
if [[ -f "$VERSION_FILE" ]]; then
  raw_version="$(tr -d '[:space:]' < "$VERSION_FILE")"
  if [[ "$raw_version" =~ ^[0-9]+$ ]]; then
    current_version="$raw_version"
  fi
fi

new_version=$((current_version + 1))
printf '%s\n' "$new_version" > "$VERSION_FILE"

perl -0pi -e "s{href=\"styles\\.css(?:\\?v=\\d+)?\"}{href=\"styles.css?v=${new_version}\"}g" "$INDEX_FILE"
perl -0pi -e "s{src=\"apps\\.js(?:\\?v=\\d+)?\"}{src=\"apps.js?v=${new_version}\"}g" "$INDEX_FILE"

echo "Asset version bumped to v${new_version}"
echo "Updated: styles.css?v=${new_version}, apps.js?v=${new_version}"

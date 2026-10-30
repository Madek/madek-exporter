#!/usr/bin/env bash
# Clear Gatekeeper quarantine on madek-exporter.app (beside this script).
# Usage (from this folder): bash ./remove-quarantine-lock.sh
set -euo pipefail

DIR="$(cd -- "$(dirname "$0")" && pwd)"
APP="${DIR}/madek-exporter.app"

if [[ ! -d "$APP" ]]; then
  echo "ERROR: madek-exporter.app not found next to this script:" >&2
  echo "  expected: $APP" >&2
  exit 1
fi

# Bundled JRE ships many files as mode 444; xattr needs write access.
chmod -R u+w "$APP"
xattr -dr com.apple.quarantine "$APP"

echo "Quarantine attribute cleared on:"
echo "  $APP"
echo
echo "You can open the app now (double-click madek-exporter.app in Finder),"
echo "or run:"
echo "  open \"$APP\""
echo "  # or: \"$APP/Contents/MacOS/madek-exporter\""

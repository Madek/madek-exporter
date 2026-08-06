#!/usr/bin/env bash
# Rename the Electron binary and install packaging/linux/madek-exporter as the
# public entrypoint so --no-sandbox / X11 flags are on argv before Chromium starts.
#
# Usage (from repo root, after electron-packager):
#   source bin/env/wrap-linux-electron.bash
#   wrap_linux_electron "$OUTPUT_DIR"

_wrap_linux_electron_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_wrap_linux_electron_root="$(cd -- "${_wrap_linux_electron_dir}/../.." && pwd)"

wrap_linux_electron() {
  local dir="${1:?package directory required}"
  local wrapper_src="${_wrap_linux_electron_root}/packaging/linux/madek-exporter"
  local desktop_src="${_wrap_linux_electron_root}/packaging/linux/madek-exporter.desktop"
  local app="${dir}/madek-exporter"
  local bin="${dir}/madek-exporter.bin"
  local desktop="${dir}/madek-exporter.desktop"

  if [[ ! -f "$wrapper_src" ]]; then
    echo "ERROR: missing Linux launcher at ${wrapper_src}" >&2
    return 1
  fi
  if [[ ! -f "$desktop_src" ]]; then
    echo "ERROR: missing Linux desktop entry at ${desktop_src}" >&2
    return 1
  fi
  if [[ ! -e "$app" ]]; then
    echo "ERROR: missing Electron binary at ${app}" >&2
    return 1
  fi
  if [[ -e "$bin" ]]; then
    echo "ERROR: ${bin} already exists; refusing to overwrite" >&2
    return 1
  fi

  mv "$app" "$bin"
  cp "$wrapper_src" "$app"
  cp "$desktop_src" "$desktop"
  chmod 755 "$bin" "$app"
  chmod 644 "$desktop"
}

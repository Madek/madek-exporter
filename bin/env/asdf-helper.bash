function mtime-comps() {
  case "$(uname -s)" in
    Linux*) date -Iseconds --date="$(stat -c '@%Y' "$1")" ;;
    Darwin) stat -f %Sm -t "%Y-%m-%dT%H:%M:%S%z" "$1" ;;
  esac
}


function command-exists() {
  command -v "$1" >/dev/null 2>&1
}


function tool-name-for-mise() {
  case "$1" in
    nodejs) echo "node" ;;
    lein) echo "leiningen" ;;
    *) echo "$1" ;;
  esac
}


function cache-needs-update() {
  local cache_file="$1"
  local ref_file

  if [[ ! -e "$cache_file" ]]; then
    return 0
  fi

  for ref_file in "$PROJECT_DIR/.tool-versions" "$PROJECT_DIR/mise.toml"; do
    if [[ -e "$ref_file" ]] && [[ "$(mtime-comps "$cache_file")" < "$(mtime-comps "$ref_file")" ]]; then
      return 0
    fi
  done

  return 1
}


function tool-version-from-tool-versions() {
  local plugin_name="$1"
  local tool_versions_file="$PROJECT_DIR/.tool-versions"

  if [[ -f "$tool_versions_file" ]]; then
    awk -v plugin_name="$plugin_name" '$1 == plugin_name { print $2; exit }' "$tool_versions_file"
  fi
}


function tool-version-from-mise-toml() {
  local tool_name="$1"
  local mise_file="$PROJECT_DIR/mise.toml"

  if [[ -f "$mise_file" ]]; then
    sed -nE "s/^[[:space:]]*${tool_name}[[:space:]]*=[[:space:]]*\"([^\"]+)\"[[:space:]]*$/\1/p" "$mise_file" | head -n 1
  fi
}


function mise-install-target() {
  local plugin_name="$1"
  local tool_name="$2"
  local version

  version="$(tool-version-from-mise-toml "$tool_name")"
  if [[ -z "$version" ]]; then
    version="$(tool-version-from-tool-versions "$plugin_name")"
  fi

  if [[ -n "$version" ]]; then
    echo "${tool_name}@${version}"
  else
    echo "$tool_name"
  fi
}


function asdf-update-plugin () {
  CACHE_ID="${PLUGIN}_${PROJECT}"
  TMPDIR=${TMPDIR:-/tmp/}
  CACHE_FILE=""

  if command-exists asdf; then
    echo "# ${CACHE_ID} env check via asdf $(asdf --version)"
    CACHE_FILE="${TMPDIR}asdf_cache_${CACHE_ID}"

    if cache-needs-update "$CACHE_FILE"; then
      if asdf plugin list | grep -q "$PLUGIN"; then
        echo "asdf $PLUGIN found: updating"
        asdf plugin update "$PLUGIN"
      else
        echo "asdf $PLUGIN NOT found: installing"
        asdf plugin add "$PLUGIN" "${PLUGIN_URL}"
      fi
      cd "$PROJECT_DIR"
      asdf install "$PLUGIN"
      touch "$CACHE_FILE"
      echo "# ${CACHE_ID} env is up to date"
    else
      echo "# ${CACHE_ID} env skipped update; touch .tool-versions/mise.toml or remove ${CACHE_FILE} to force update"
    fi
    return 0
  fi

  if command-exists mise; then
    local mise_tool
    local install_target
    mise_tool="$(tool-name-for-mise "$PLUGIN")"
    install_target="$(mise-install-target "$PLUGIN" "$mise_tool")"
    echo "# ${CACHE_ID} env check via mise $(mise --version | head -n 1)"
    CACHE_FILE="${TMPDIR}mise_cache_${CACHE_ID}"

    if cache-needs-update "$CACHE_FILE"; then
      cd "$PROJECT_DIR"
      echo "mise ${install_target}: installing"
      if ! MISE_AUTO_INSTALL=${MISE_AUTO_INSTALL:-0} mise install "$install_target"; then
        echo "mise install for ${install_target} failed; retrying with ${mise_tool}"
        MISE_AUTO_INSTALL=${MISE_AUTO_INSTALL:-0} mise install "$mise_tool"
      fi
      touch "$CACHE_FILE"
      echo "# ${CACHE_ID} env is up to date"
    else
      echo "# ${CACHE_ID} env skipped update; touch .tool-versions/mise.toml or remove ${CACHE_FILE} to force update"
    fi
    return 0
  fi

  echo "Neither asdf nor mise is available in PATH." >&2
  return 1
}
# vi: ft=sh

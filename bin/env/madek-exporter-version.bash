#!/usr/bin/env bash
# Source from packaging scripts. Sets MADEK_EXPORTER_VERSION from releases.yml
# (same format as bin/prepare-release-infos).
#
# Also provides substitute_madek_exporter_version_in_readme <path> to replace
# __MADEK_EXPORTER_VERSION__ in a packaged README.txt.

_madek_exporter_version_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_madek_exporter_version_root="$(cd -- "${_madek_exporter_version_dir}/../.." && pwd)"

MADEK_EXPORTER_VERSION="$(
  ruby -ryaml -e '
    release = YAML.load_file(ARGV[0]).first
    version = "#{release["version_major"]}.#{release["version_minor"]}.#{release["version_patch"]}"
    version += "-#{release["version_pre"]}" if release["version_pre"]
    version += "+#{release["version_build"]}" if release["version_build"]
    puts version
  ' "${_madek_exporter_version_root}/releases.yml"
)"

if [[ -z "${MADEK_EXPORTER_VERSION}" ]]; then
  echo "ERROR: could not read Madek Exporter version from ${_madek_exporter_version_root}/releases.yml" >&2
  return 1 2>/dev/null || exit 1
fi

substitute_madek_exporter_version_in_readme() {
  local readme="$1"
  sed -i.bak "s/__MADEK_EXPORTER_VERSION__/${MADEK_EXPORTER_VERSION}/g" "$readme"
  rm -f "${readme}.bak"
  if grep -q '__MADEK_EXPORTER_VERSION__' "$readme"; then
    echo "ERROR: version placeholder not substituted in $readme" >&2
    return 1
  fi
}

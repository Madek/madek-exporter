#!/usr/bin/env bash
# Source from packaging scripts so electron-packager temp lives on the same
# filesystem as --out (Cider uses /ramdisk workdir + /tmp → rename ENOENT).
#
# Use a per-process directory: packager ensureTempDir() does rm -rf on
# $TMPDIR/electron-packager, and CI runs linux/mac/win/arm64 in parallel.
PACKAGER_TMPDIR="${PWD}/target/electron-packager-tmp/pid-$$"
mkdir -p "$PACKAGER_TMPDIR"
export TMPDIR="$PACKAGER_TMPDIR"
export TEMP="$PACKAGER_TMPDIR"
export TMP="$PACKAGER_TMPDIR"

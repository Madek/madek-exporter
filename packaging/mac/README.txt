Madek Exporter — macOS package
==============================

If macOS says madek-exporter.app is "damaged" and cannot be opened, that is
usually Gatekeeper quarantine on an unsigned download — not a corrupt zip.

Fix (Terminal)
--------------

1. Open Terminal and change into this folder (the one that contains
   madek-exporter.app, README.txt, and remove-quarantine-lock.sh).

2. Run:

     bash ./remove-quarantine-lock.sh

3. Open madek-exporter.app (double-click in Finder, or open it from Terminal).

Architecture
------------

Use the matching zip for your Mac:

  uname -m
  arm64   → Madek-Exporter_Mac-OS-ARM64.zip  (Apple Silicon)
  x86_64  → Madek-Exporter_Mac-OS-x64.zip    (Intel)

Java
----

This app bundles its own Temurin Java 21 under
madek-exporter.app/Contents/Resources/jre/. A system Java install is not
required to start the app.

Longer-term
-----------

Apple codesign + notarization is the lasting fix so downloads open without
this workaround. Until then, use remove-quarantine-lock.sh after each download.

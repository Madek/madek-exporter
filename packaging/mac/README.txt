Fix (Gatekeeper)
----------------

If macOS says madek-exporter.app is "damaged" and cannot be opened, that is
usually Gatekeeper quarantine on an unsigned download — not a corrupt zip.

1. Double-click remove-quarantine-lock.sh (in this folder, beside
   madek-exporter.app and README.txt).

2. Open madek-exporter.app.

Otherwise — Fix (Terminal)
--------------------------

1. Open Terminal and change into this folder (the one that contains
   madek-exporter.app, README.txt, and remove-quarantine-lock.sh).

2. Run:

     bash ./remove-quarantine-lock.sh

3. Open madek-exporter.app (double-click in Finder, or open it from Terminal).

Longer-term
-----------

Apple codesign + notarization is the lasting fix so downloads open without
this workaround. Until then, use remove-quarantine-lock.sh after each download.

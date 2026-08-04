Madek Exporter
==============

Version: __MADEK_EXPORTER_VERSION__

GitHub
------

  Repository:  https://github.com/Madek/madek-exporter
  Releases:    https://github.com/Madek/madek-exporter/releases
  Issues:      https://github.com/Madek/madek-exporter/issues

Log files
---------

Runtime logs are written under the Madek user-data directory:

  macOS:    ~/Library/Application Support/Madek/logs/app.log
  Linux:    ~/.config/Madek/logs/app.log
  Windows:  %APPDATA%\Madek\logs\app.log

Rotated backup (when app.log exceeds about 2MB): app.log.1

Architecture
------------

Use the zip that matches your machine:

  macOS:    uname -m
            arm64   → Madek-Exporter_Mac-OS-ARM64.zip  (Apple Silicon)
            x86_64  → Madek-Exporter_Mac-OS-x64.zip    (Intel)

  Linux:    uname -m
            x86_64  → Madek-Exporter_Linux-x64.zip
            aarch64 → Madek-Exporter_Linux-ARM64.zip

  Windows:  Madek-Exporter_Windows.zip  (x64)

Java
----

This app bundles its own Temurin Java 21. A system Java install is not
required to start the app.

  macOS:     madek-exporter.app/Contents/Resources/jre/
  Linux:     resources/jre/
  Windows:   resources\jre\

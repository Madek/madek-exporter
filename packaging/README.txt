Madek Exporter
==============

Version: __MADEK_EXPORTER_VERSION__

GitHub
------

  Repository:  https://github.com/Madek/madek-exporter
  Releases:    https://github.com/Madek/madek-exporter/releases/tag/__MADEK_EXPORTER_VERSION__
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

Use the artefact that matches your machine:

  macOS:    uname -m
            arm64   → Madek-Exporter_Mac-OS-ARM64.zip  (Apple Silicon)
            x86_64  → Madek-Exporter_Mac-OS-x64.zip    (Intel)

  Linux:    uname -m
            x86_64  → Madek-Exporter_Linux-x64.deb (preferred on Debian/Ubuntu)
                      or Madek-Exporter_Linux-x64.zip
            aarch64 → Madek-Exporter_Linux-ARM64.deb (preferred on Debian/Ubuntu)
                      or Madek-Exporter_Linux-ARM64.zip

  Windows:  Madek-Exporter_Windows.zip  (x64)

Java
----

This app bundles its own Temurin Java 21. A system Java install is not
required to start the app.

  macOS:     madek-exporter.app/Contents/Resources/jre/
  Linux:     resources/jre/
  Windows:   resources\jre\

Linux
-----

Debian/Ubuntu (.deb)
~~~~~~~~~~~~~~~~~~~~

Install the matching .deb, then start Madek Exporter from the applications
menu or with `madek-exporter`:

  sudo apt install ./Madek-Exporter_Linux-x64.deb
  # or: sudo apt install ./Madek-Exporter_Linux-ARM64.deb

(Alternatively: `sudo dpkg -i ./Madek-Exporter_Linux-*.deb` then
`sudo apt-get install -f` if dependencies are missing.)

The package installs under /opt/madek-exporter, sets chrome-sandbox
permissions in postinst, and adds a menu entry.

Portable zip
~~~~~~~~~~~~

./madek-exporter is a shell launcher that starts madek-exporter.bin with
--no-sandbox and --ozone-platform=x11. Zip installs cannot ship a working
setuid chrome-sandbox, and forcing X11 keeps the window visible on Ubuntu
Wayland sessions. From a terminal, the launcher detaches (setsid) and
returns immediately; the GUI keeps running and should appear on the first
launch.

On Debian/Ubuntu file managers, prefer the madek-exporter.desktop file
(right-click → Allow Launching, then double-click), or use the .deb above.

If the app still fails to start, check:

  ~/.config/Madek/logs/app.log
  ~/.config/Madek/logs/launcher.log

Advanced alternative (if you run madek-exporter.bin directly from a zip):

  sudo chown root:root chrome-sandbox
  sudo chmod 4755 chrome-sandbox
  ./madek-exporter.bin


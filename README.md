# Madek Exporter

## Current Stack

- Electron: `43.1.1`
- Node.js: `22.12.0` (via `mise`)
- Java: `temurin-21.0.11+10.0.LTS` (via `mise`)
- Ruby: `3.3.8` (via `mise`, used by release helper scripts)
- osv-scanner: `2.4.0` (via `mise`, dependency vulnerability scans)
- Build tooling: `lein`, `npm`, `@electron/packager`
- Vendor assets/runtime: git submodule `vendor/`

## One-Time Setup

```zsh
cd /Users/mradl/2026_02/madek-exporter

# Toolchain (versions from mise.toml)
MISE_AUTO_INSTALL=0 mise install

# Vendor assets/JRE
git submodule update --init --recursive vendor

# JS dependencies
MISE_AUTO_INSTALL=0 mise exec node@22.12.0 -- npm install
```

## Production Build and Run

### 1) Clean old processes (recommended)

```zsh
cd /Users/mradl/2026_02/madek-exporter
pkill -f "madek-exporter.app/Contents/MacOS/madek-exporter" || true
pkill -f "jvm-main.jar" || true
```

### 2) Build production app

```zsh
cd /Users/mradl/2026_02/madek-exporter
MISE_AUTO_INSTALL=0 mise exec java@temurin-21.0.11+10.0.LTS -- npm run build:prod
```

Artifacts are written to:

- `app/prod/jvm-main.jar`
- `app/prod/css/site.css`
- `target/packages/madek-exporter-darwin-arm64/madek-exporter.app` (Apple Silicon)
- `target/packages/madek-exporter-darwin-x64/madek-exporter.app` (Intel)

`npm run build:prod` packages **both macOS arches**. For Linux / Windows / macOS artefacts (manual testing), see below.

### Build all platforms (manual testing)

`npm run build:all:prod` builds **5** platform packages and zips:

1. `Madek-Exporter_Mac-OS-x64.zip` (Intel Mac)
2. `Madek-Exporter_Mac-OS-ARM64.zip` (Apple Silicon)
3. `Madek-Exporter_Linux-x64.zip`
4. `Madek-Exporter_Linux-ARM64.zip`
5. `Madek-Exporter_Windows.zip`

It builds shared Electron + JVM prod outputs first, then packages each platform. Requires `vendor/` submodules (including `vendor/jre_linux_x64` and `vendor/jre_win_x64`). Runs `build:clean` first, so old package/zip artefacts are removed automatically.

```zsh
cd /Users/mradl/2026_02/madek-exporter
MISE_AUTO_INSTALL=0 mise exec java@temurin-21.0.11+10.0.LTS -- \
  mise exec node@22.12.0 -- npm run build:all:prod
```

Outputs (packages under `target/packages/`, zips under `target/dist/`):

| # | Platform | Directory | Zip |
|---|---|---|---|
| 1 | macOS Intel | `madek-exporter-darwin-x64/` | `Madek-Exporter_Mac-OS-x64.zip` |
| 2 | macOS Apple Silicon | `madek-exporter-darwin-arm64/` | `Madek-Exporter_Mac-OS-ARM64.zip` |
| 3 | Linux x64 | `madek-exporter-linux-x64/` | `Madek-Exporter_Linux-x64.zip` |
| 4 | Linux ARM64 | `madek-exporter-linux-arm64/` | `Madek-Exporter_Linux-ARM64.zip` |
| 5 | Windows | `madek-exporter-win32-x64/` | `Madek-Exporter_Windows.zip` |

Single-platform scripts write the same package directories under `target/packages/`:
- `npm run build:mac:x64:prod` / `build:mac:prod` -> `darwin-x64`
- `npm run build:mac:arm64:prod` -> `darwin-arm64`
- `npm run build:linux:prod` -> `linux-x64`
- `npm run build:win:prod` -> `win32-x64`

Individual packaging (after a shared prod build):

| Script | Command | With zip |
|---|---|---|
| macOS Intel | `npm run build:mac:x64:prod` | `npm run build:mac:x64:zip:prod` |
| macOS Apple Silicon | `npm run build:mac:arm64:prod` | `npm run build:mac:arm64:zip:prod` |
| Linux | `npm run build:linux:prod` | `npm run build:linux:zip:prod` |
| Linux ARM64 | `npm run build:linux:arm64:prod` | `npm run build:linux:arm64:zip:prod` |
| Windows | `npm run build:win:prod` | `npm run build:win:zip:prod` |

The `:zip:` variants package the platform and create a zip in `target/dist/`.
Linux ARM64 and macOS ARM64 builds download and verify a pinned Temurin 21 ARM64 JRE in `target/vendor-cache/`; subsequent builds reuse the cached archive.

**Package `README.txt`:** every platform package/zip includes `README.txt` at the
package root (GitHub links, log paths, Architecture, Java, and Version from
`releases.yml`). Template: `packaging/README.txt`.

**macOS artefacts:** each Mac package/zip (`madek-exporter-darwin-x64/`,
`madek-exporter-darwin-arm64/`) also includes, **beside** `madek-exporter.app`
(not inside the bundle):

- Gatekeeper Fix / Longer-term sections appended into `README.txt` from
  `packaging/mac/README.txt`
- `remove-quarantine-lock.sh` — run **manually** after unzip (double-click in
  Finder, or `bash ./remove-quarantine-lock.sh`); it is not auto-executed when
  opening the app

Mac packaging is done by `bin/build-mac-os` and `bin/build-mac-os-arm64`. Full
steps: [Debug startup from Terminal (macOS)](#debug-startup-from-terminal-macos).

Optional check: `npm run check:artifacts`.

Optional cleanup:

```zsh
rm -rf target/packages target/dist
```

### 3) Run production app

On Apple Silicon use the ARM64 package (native; avoids Rosetta). On Intel use x64.

Recommended (`npm run run:prod` picks arch via `uname -m`):

```zsh
cd /Users/mradl/2026_02/madek-exporter
npm run run:prod
```

Or explicitly:

```zsh
# Apple Silicon
./target/packages/madek-exporter-darwin-arm64/madek-exporter.app/Contents/MacOS/madek-exporter

# Intel
./target/packages/madek-exporter-darwin-x64/madek-exporter.app/Contents/MacOS/madek-exporter
```

### 4) Quick verify (optional)

Check bundled runtime version (should be Java 21+):

```zsh
cd /Users/mradl/2026_02/madek-exporter
cat target/packages/madek-exporter-darwin-arm64/madek-exporter.app/Contents/Resources/jre/release
# or: …/darwin-x64/…
```

Or launch as regular macOS app:

```zsh
open -na /Users/mradl/2026_02/madek-exporter/target/packages/madek-exporter-darwin-arm64/madek-exporter.app
```

## Development

### Build dev artifacts

```zsh
cd /Users/mradl/2026_02/madek-exporter
./bin/build-electron-dev
```

This writes dev outputs to `app/dev/` (Electron main/front JS, CSS, and `jvm-main.jar`).

### Run dev (two terminals)

Terminal 1: start JVM backend

```zsh
cd /Users/mradl/2026_02/madek-exporter
lein clean
lein run -m madek.exporter.main server
```

Terminal 2: start Electron app

```zsh
cd /Users/mradl/2026_02/madek-exporter
npx electron app/dev
```

Notes:

- In dev, Electron expects local backend at `http://localhost:8383`.
- The backend password defaults to `secret`.

### Rebuild individual parts

| What | Command |
|---|---|
| Electron main (ClojureScript) | `./bin/build-electron-main-dev` |
| Electron frontend (ClojureScript) | `./bin/build-electron-front-dev` |
| Stylesheets | `./bin/build-stylesheets-dev` |
| JVM jar | `./bin/build-jvm-main-dev` |
| Everything | `./bin/build-electron-dev` |

## Security Scanning

Dependency checks against [OSV](https://osv.dev/) (plus npm audit). Requires `osv-scanner` from `mise install` (or `brew install osv-scanner`).

```zsh
cd /Users/mradl/2026_02/madek-exporter

# Full audit: npm audit + Clojure/JVM + package-lock.json + Gemfile.lock
./bin/security-audit

# Clojure/JVM only (Leiningen resolved deps, direct + transitive)
./bin/clj-osv-scan
```

Suppressions / accepted risks go in `osv-scanner/osv-scanner.toml`. Longer-term dependency work is tracked in `SECURITY-PLAN.md`.

## Troubleshooting

### Log files (macOS / Linux / Windows)

Packaged builds write runtime logs under Electron's per-user data directory
(`app.getPath("userData")` → `logs/`). The app name used for that directory is
**Madek** (see `app/prod/package.json`).

| OS | Log directory | Current log | Previous (rotated) |
|---|---|---|---|
| macOS | `~/Library/Application Support/Madek/logs/` | `app.log` | `app.log.1` |
| Linux | `~/.config/Madek/logs/` | `app.log` | `app.log.1` |
| Windows | `%APPDATA%\Madek\logs\` (usually `C:\Users\<you>\AppData\Roaming\Madek\logs\`) | `app.log` | `app.log.1` |

Examples (current file):

- macOS: `~/Library/Application Support/Madek/logs/app.log`
- Linux: `~/.config/Madek/logs/app.log`
- Windows: `%APPDATA%\Madek\logs\app.log`

Open the folder quickly:

```zsh
# macOS
open "$HOME/Library/Application Support/Madek/logs"

# Linux
xdg-open "$HOME/.config/Madek/logs"
```

```bat
REM Windows (cmd)
explorer %APPDATA%\Madek\logs
```

Rotation: each file is capped at about **2MB**; when `app.log` would exceed that,
it is renamed to `app.log.1` (replacing any previous backup) and a new `app.log`
is started. The log contains Electron main-process events and the JVM backend
child process stdout/stderr.

The base directory may differ if the OS or launch environment overrides Electron's
`userData` path. Dev runs use the same `userData`/`logs` layout once the main
process has started.

**If no `app.log` appears**, Electron never reached the main-process logger (wrong
CPU arch, Gatekeeper quarantine, damaged unzip, or crash before JS). Debug from
Terminal instead of double-clicking the `.app` — see below.

### Debug startup from Terminal (macOS)

Use the **matching** zip: Apple Silicon → `Madek-Exporter_Mac-OS-ARM64.zip`,
Intel → `Madek-Exporter_Mac-OS-x64.zip`. Running the Intel build on Apple Silicon
needs Rosetta; a native ARM64 Mac with only the x64 zip often fails before any
log file is created.

Mac release zips include `README.txt` and `remove-quarantine-lock.sh` beside
`madek-exporter.app`. After unzip, if macOS reports the app as “damaged”,
double-click `remove-quarantine-lock.sh`, then open the app. Otherwise from
Terminal:

```zsh
cd /path/to/madek-exporter-darwin-arm64   # or darwin-x64
bash ./remove-quarantine-lock.sh
open ./madek-exporter.app
```

```zsh
# 1) Machine arch
uname -m
# arm64 = Apple Silicon, x86_64 = Intel

# 2) Point at the .app you copied (adjust path after unzip)
APP="/path/to/madek-exporter.app"
BIN="$APP/Contents/MacOS/madek-exporter"

# 3) Clear Gatekeeper quarantine (common after download/AirDrop/zip)
# Prefer: double-click remove-quarantine-lock.sh, or bash ./remove-quarantine-lock.sh
# chmod: bundled JRE has read-only files; xattr needs write access
chmod -R u+w "$APP"
xattr -dr com.apple.quarantine "$APP"

# 4) Sanity-check bundle contents
file "$BIN"
file "$APP/Contents/Resources/jre/bin/java"
ls -la "$APP/Contents/Resources/jvm-main.jar"
"$APP/Contents/Resources/jre/bin/java" -version

# Expected: BIN/java Mach-O type matches uname -m (arm64 vs x86_64)
# java -version should report 21.x

# 5) Run in the foreground — stdout/stderr stay in this Terminal
"$BIN"
```

Leave that Terminal open and watch for Electron/JVM errors. In another Terminal:

```zsh
# Did a log file appear after the binary actually started?
ls -la "$HOME/Library/Application Support/Madek/logs/"
tail -n 80 "$HOME/Library/Application Support/Madek/logs/app.log"

# Is anything still running?
pgrep -fa "madek-exporter|jvm-main.jar"
```

To capture everything to a file (useful when pasting a report):

```zsh
"$BIN" >"$HOME/Desktop/madek-exporter-console.log" 2>&1
# Ctrl-C to stop, then open the log on the Desktop
```

Do **not** use `open -a` for this debug path — that detaches from the Terminal
and hides early crash output. Prefer `"$BIN"` as above.

Linux / Windows (same idea: run the binary in a terminal, not via file manager):

```bash
# Linux (from the unzipped package dir)
./madek-exporter
# or: ./madek-exporter >~/madek-exporter-console.log 2>&1
```

```bat
REM Windows (cmd, from the unzipped package dir)
madek-exporter.exe
REM or: madek-exporter.exe > %USERPROFILE%\Desktop\madek-exporter-console.log 2>&1
```

```powershell
# Windows (PowerShell): use .\ so the local exe is found
.\madek-exporter.exe
```

### Citrix / VDI: `GPU process isn't usable` (Windows)

On Citrix and similar VDI sessions Chromium often fails to start its GPU
process (`GPU process launch failed: error_code=18`, then
`GPU process isn't usable. Goodbye.`).

Current builds **disable GPU acceleration by default** (`disableHardwareAcceleration`,
`--disable-gpu`, `--in-process-gpu`), so a normal double-click / `.\madek-exporter.exe`
start should work without extra flags. `app.log` records that GPU was disabled.

Prefer running from a **local** folder (e.g. `C:\Temp\...`) rather than a UNC
home path (`\\filer\...`) when possible — Electron child processes are less
reliable on network shares.

### `ClassNotFoundException: javax.xml.bind.DatatypeConverter`

This is a historical error from older builds/toolchains. Current setup is verified with Java 21.

If it appears, you are likely running an older build artifact.

```zsh
MISE_AUTO_INSTALL=0 mise exec java@temurin-21.0.11+10.0.LTS -- java -version
MISE_AUTO_INSTALL=0 mise exec java@temurin-21.0.11+10.0.LTS -- npm run build:prod
```

### `spawn ENOTDIR` in Electron main process

Use freshly rebuilt app bundle from the canonical build path. Current packaging copies `jvm-main.jar` and `jre` to `Contents/Resources/` and supports asar packaging.

### `NoClassDefFoundError: java/util/SequencedCollection`

This indicates the JVM runtime used by the app is too old (typically Java 11) while current dependencies require Java 21+.

Use this clean rebuild/start sequence:

```zsh
cd /Users/mradl/2026_02/madek-exporter
pkill -f "madek-exporter.app/Contents/MacOS/madek-exporter" || true
pkill -f "jvm-main.jar" || true
MISE_AUTO_INSTALL=0 mise exec java@temurin-21.0.11+10.0.LTS -- npm run build:prod
npm run run:prod
```

Optional check of bundled runtime version:

```zsh
cd /Users/mradl/2026_02/madek-exporter
cat target/packages/madek-exporter-darwin-arm64/madek-exporter.app/Contents/Resources/jre/release
# or darwin-x64 on Intel
```

### `Request failed with status 0 (local transport error)` on login

This usually means the local JVM backend is not reachable.

1) Verify backend process is running:

```zsh
pgrep -fa "madek.exporter.main|jvm-main.jar"
```

2) Verify local endpoint responds:

```zsh
curl -v -u ":secret" http://localhost:8383/
```

`404 Not Found` is acceptable here and indicates the server is reachable.

3) Test `/connect` directly:

```zsh
curl -v \
  -u ":secret" \
  -X POST http://localhost:8383/connect \
  -H "Content-Type: application/json" \
  -d '{"url":"https://manuel.madek.rubydev.zhdk.ch","login":"manuel.radl@zhdk.ch","password":"<PASSWORD>"}'
```

### CSS shows Sass error text in UI

Rebuild stylesheets:

```zsh
cd /Users/mradl/2026_02/madek-exporter
./bin/build-stylesheets-prod
```

Optional strict Sass warning mode (for migration work):

```zsh
cd /Users/mradl/2026_02/madek-exporter
SASS_STRICT_DEPRECATIONS=1 ./bin/build-stylesheets-prod
```

### Multiple app instances running

```zsh
pkill -f "madek-exporter.app/Contents/MacOS/madek-exporter" || true
open -na /Users/mradl/2026_02/madek-exporter/target/packages/madek-exporter-darwin-arm64/madek-exporter.app
```

### macOS: “kann aufgrund eines Problems nicht geöffnet werden” / missing `libffmpeg.dylib`

This happens when the `.app` was unpacked from a zip that dropped Electron framework symlinks (`Libraries` → `Versions/Current/Libraries`, etc.). The dyld crash looks like `Library not loaded: @rpath/libffmpeg.dylib`.

Use a zip built with the current `bin/zip-tree` (preserves symlinks via `zip -ry` / equivalent), or run the local package without re-zipping:

```zsh
open -na /Users/mradl/2026_02/madek-exporter/target/packages/madek-exporter-darwin-arm64/madek-exporter.app
```

After unzipping a release zip, confirm the framework links exist:

```zsh
ls -la madek-exporter.app/Contents/Frameworks/Electron\ Framework.framework/Libraries
# expect: Libraries -> Versions/Current/Libraries
```

## Release Checklist (short)

- [ ] `git submodule update --init --recursive vendor`
- [ ] `mise` toolchain installed (`node@22.12.0`, `java@temurin-21.0.11+10.0.LTS`, `ruby@3.3.8`, `osv-scanner@2.4.0`)
- [ ] `npm install`
- [ ] `./bin/security-audit` reviewed (triage findings; do not mass-suppress)
- [ ] `npm run build:prod` (macOS x64 + ARM64) or `npm run build:all:prod` (**5** platform zips)
- [ ] start app once and verify no main-process crash
- [ ] verify artifacts:
  - [ ] `app/prod/jvm-main.jar`
  - [ ] `app/prod/css/site.css`
  - [ ] `target/packages/madek-exporter-darwin-arm64/…/jre/bin/java` (Apple Silicon)
  - [ ] `target/packages/madek-exporter-darwin-x64/…/jre/bin/java` (Intel)
  - [ ] (if all-platforms) all 5 zips under `target/dist/`:
    `Madek-Exporter_Mac-OS-x64.zip`, `Madek-Exporter_Mac-OS-ARM64.zip`,
    `Madek-Exporter_Linux-x64.zip`, `Madek-Exporter_Linux-ARM64.zip`,
    `Madek-Exporter_Windows.zip`

## License

Madek is (C) Zuercher Hochschule der Kuenste (Zurich University of the Arts).

Madek is Free Software under the GNU General Public License (GPL) v3, see `LICENSE`.

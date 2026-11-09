# Madek Exporter

Desktop application for exporting data from Madek.

Export can use Madek `/api` (JSON-ROA), `/api-v2` (REST), or **both** (writes into `api/` and `api-v2/` subfolders). After login, the UI probes both APIs and lets you pick the export mode.

The **shipped app version** is the first entry in `releases.yml`. Run `bundle exec ./bin/update-releases` after editing it so `app/*/releases.json`, `package.json`, `package-lock.json`, and `project.clj` stay aligned.

## Setup

Requires `mise` (Java 21, Node 22, Ruby), Git, and npm. Leiningen is invoked via `./bin/lein` (mise does not manage `lein` itself).

```zsh
mise install
git submodule update --init --recursive vendor
npm install
bundle install
```

## Build and run

Build the production app for macOS (Intel and Apple Silicon):

```zsh
npm run build:prod
npm run run:prod
```

Oneliner
```
npm run build:clean && npm run build:prod && npm run run:prod
```

Build packages and zip files for all supported platforms:

```zsh
npm run build:all:prod
```

Build outputs are written to `target/packages/` and `target/dist/`.

## Development

One-click dev stack (Cursor terminals: Figwheel, CLJS main, CSS, Electron):

```zsh
./tmp/dev-madek-exporter/one-click-runner start
```

Fake Madek for local login / tests:

```zsh
./tmp/dev-madek-exporter/one-click-runner start-test
./tmp/dev-madek-exporter/one-click-runner test
```

See `tmp/dev-madek-exporter/README.md` for `stop` and `cleanup`.

Manual setup:

```zsh
./bin/build-electron-dev
npx electron app/dev
```

(Electron spawns the JVM backend on port 8383 with a local Basic Auth password; a separate `./bin/lein run -m madek.exporter.main server` is only needed without Electron.)

## Testing

Fake Madek API (v1 JSON-ROA + v2 REST) for local checks:

```zsh
npm run fake-madek-api
```

JVM connect/probe tests against the fake server:

```zsh
npm run test:login
```

Local JVM Basic Auth / path-sanitization unit tests:

```zsh
./bin/with-java21 lein with-profile test test :only madek.exporter.http-auth-test
```

Playwright Electron UI tests (starts fake server automatically; uses `app/dev`):

```zsh
./bin/build-electron-dev   # once, if app/dev artifacts are missing
npm run test:e2e
```

Optional smoke against advanced-compiled `app/prod` (not run in Cider-CI by default):

```zsh
npm run build:electron:prod && npm run build:jvm:prod
npm run test:e2e:prod
```

Dependency audit (npm + OSV; Cider-CI `security-audit` job fails on findings):

```zsh
npm run audit:security
```

Run login + default e2e:

```zsh
npm test
```

Login coverage (fake API, JVM connect, Playwright UI) mirrors Madek `/api`:

| Credentials | Result |
|-------------|--------|
| api-token only | ok |
| login / api-token | ok |
| email / api-token | ok |
| login / account password | rejected (401) |
| email / account password | rejected (401) |
| api-client / api-client password | ok |

Cider-CI runs both suites in the `tests` job ("All tests") (`cider-ci/test-login-api.yml`, `cider-ci/test-login-e2e.yml`); Playwright runs headless under `xvfb-run` there.

Plus JVM edge cases (missing URL, unreachable host, empty credentials, v1-only hosts via `FAKE_MADEK_API_V2_DISABLED=1`) and UI form validation / disconnect.

## Release

The packaged version is the first entry in `releases.yml` (`major.minor.patch`, plus optional `-pre` and `+build`). Add a new entry at the top, with a `description` of `Fixes and Features` and optional `Notes`, then regenerate the JSON the app ships:

```zsh
bundle exec ./bin/update-releases
```

That writes `app/prod/releases.json` and `app/dev/releases.json`, and syncs the top release version into `package.json`, `package-lock.json`, and `project.clj` (not the long release-notes `description`). Commit those with `releases.yml`. CI rejects the branch if they are stale.

Push the branch. CI builds Linux (x64, ARM64), macOS (x64, ARM64), and Windows, then `./bin/prepare-release-infos` copies the zips to a durable path on the executor and prints the version, notes, fetch command, and draft URL. Fetch as soon as those platform jobs finish — the CI working tree is cleaned shortly after:

```zsh
./bin/fetch-server-artefacts <user@host> /var/tmp/madek-exporter-artefacts-<job-id>
```

Zips land in `target/server-artefacts/`:

- `Madek-Exporter_Linux-x64.zip`
- `Madek-Exporter_Linux-ARM64.zip`
- `Madek-Exporter_Mac-OS-x64.zip`
- `Madek-Exporter_Mac-OS-ARM64.zip`
- `Madek-Exporter_Windows.zip`

Open the printed draft URL (`https://github.com/Madek/madek-exporter/releases/new?tag=<version>&title=<version>`), attach the five zips, and publish. Publishing creates the git tag. There is no automated GitHub release.

## macOS signing / notarization

CI builds unsigned zips for distribution inside ZHdK. For public Gatekeeper
installs, notarize a packaged `.app` with your Apple Developer ID after
packaging. Helper (requires `APPLE_ID`, `APPLE_TEAM_ID`,
`APPLE_APP_SPECIFIC_PASSWORD`):

```zsh
./bin/notarize-mac target/packages/madek-exporter-darwin-arm64/madek-exporter.app
```

## License

Copyright Zuercher Hochschule der Kuenste (Zurich University of the Arts).

Licensed under the GNU General Public License v3. See `LICENSE`.

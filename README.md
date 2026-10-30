# Madek Exporter

Desktop application for exporting data from Madek.

## Setup

Requires `mise`, Git, Leiningen, and npm.

```zsh
mise install
git submodule update --init --recursive vendor
npm install
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

Build development artifacts:

```zsh
./bin/build-electron-dev
```

Run the backend and Electron app in separate terminals:

```zsh
lein clean
lein run -m madek.exporter.main server
```

```zsh
npx electron app/dev
```

## Testing

Fake Madek API (v1 JSON-ROA + v2 REST) for local checks:

```zsh
npm run fake-madek-api
```

JVM connect/probe tests against the fake server:

```zsh
npm run test:login
```

Playwright Electron UI tests (starts fake server automatically):

```zsh
./bin/build-electron-dev   # once, if app/dev artifacts are missing
npm run test:e2e
```

Run both:

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

That writes `app/prod/releases.json` and `app/dev/releases.json`. Commit both with `releases.yml`. CI rejects the branch if `app/prod/releases.json` is stale.

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

## License

Copyright Zuercher Hochschule der Kuenste (Zurich University of the Arts).

Licensed under the GNU General Public License v3. See `LICENSE`.

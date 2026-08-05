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

## License

Copyright Zuercher Hochschule der Kuenste (Zurich University of the Arts).

Licensed under the GNU General Public License v3. See `LICENSE`.

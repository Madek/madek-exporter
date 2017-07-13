# The Madek Exporter


## Building the App for Production

We do this via our CI system, see the file [cider-ci.yml](cider-ci.yml).

## Development

See the files [project.clj](project.clj) and [.mux.yml](.mux.yml).

### First time preparation

`node` and `npm` must be installed and in the execution PATH. Run

    npm install

Download Electron for your platform. One way to do it is via grunt

    ./node_modules/grunt/bin/grunt download-electron


### Development targets

There are the following build targets:

1. JVM main, the code is located in `jvm_main/`.
2. Electron main, the code is located in `electron_main/`.
3. Electron front, also known as the "renderer", the code is located in `electron_front/`.
4. Stylesheets compiled from sass.

We build each one "continuously" in its own window when developing. A forth
window holds the electron app.

### JVM main

    rm -rf target
    lein repl
    # it will open a repl in the main namespace, where we can start the server with
    (-main)


### Electron main

    rm -rf app/dev/js/out_main app/dev/js/main.js
    lein cljsbuild auto electron-main-dev


### Electron front

    rm -rf app/dev/js/front.js app/dev/js/out_front.js
    lein descjop-figwheel

### Stylesheets

The Electron front includes stylesheets which we are building continuously with

    lein sass watch

### The Electron app

Wait until all the three previous ones are ready. Then e.g. on MacOS:

    ./electron/Electron.app/Contents/MacOS/Electron app/dev


### Running and Debugging the Production App

The electron parts can be build with
1. `bin/build-electron-prod` and the jvm with
2. `lein uberjar`.

The next steps depend on the OS. On MacOS:
3. `bin/build-mac-os`
4. `./madek-app-darwin-x64/madek-app.app/Contents/MacOS/madek-app`

# The Madek-Exporter


## Building the App for Production

We do this via our CI system, see the file [cider-ci.yml](cider-ci.yml).

### Requirements

Debian:

* task-desktop
* zip, unzip
* libgconf-2-4
* wine
* libxss1


## Development

See the files [project.clj](project.clj) and [.mux.yml](.mux.yml).

Most dependecies are managed via https://asdf-vm.com/



### Development targets

There are the following build targets:

1. JVM main, the code is located in `jvm_main/`.
2. Electron main, the code is located in `electron_main/`.
3. Electron front, also known as the "renderer", the code is located in `electron_front/`.
4. Stylesheets compiled from sass.


The watched dev builds are kind of broken now. Until we update the whole app use prod builds.

### JVM main

    ./bin/build-jvm-main-prod

    The jvm app can be started with _debug_ and _repl_ options for development ; see cli options.

### Electron main

    ./bin/build-electron-main-prod

### Electron front

    ./bin/build-electron-front-prod

### Stylesheets

The Electron front includes stylesheets which we are building continuously with

    lein sass watch

### The Electron app

    ./bin/build-stylesheets-prod



## Copyright and license

Madek is (C) Zürcher Hochschule der Künste (Zurich University of the Arts).

Madek is Free Software under the GNU General Public License (GPL) v3, see the included LICENSE file for license details.

Visit our main website at http://www.zhdk.ch and the IT center
at http://itz.zhdk.ch

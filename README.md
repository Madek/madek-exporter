# The Madek App


## Development

The setup of the project is based on
[descjop](https://github.com/karad/lein_template_descjop). It has been adjusted
and cleaned up in many ways. Descjop consists essentially of conventions
and a number of of `lein` aliases. See the file [project.clj](project.clj).


### Building the App for Production

We do this via our CI system, see the file [cider-ci.yml](cider-ci.yml).

### Development with Figwheel etc

See also the file [.mux.yml](.mux.yml).

We use 3 terminals. In terminal `figwheel`

      ./bin/build-electron-dev && lein do descjop-figwheel

In the terminal `repl` we run the java server part:

      lein repl
      # it will open a repl in the main namespace, wher we can start the server with
      (-main)

Once figwheel is ready and the server are ready we start electron with the dev environment,
e.g. on MacOS:

      ./electron/Electron.app/Contents/MacOS/Electron app/dev



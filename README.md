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

We use 2 terminals. In terminal `figwheel` we clean, prepare, prebuild, and run figwheel

      rm -rf app/dev/js/out_front && rm -rf app/dev/js/out_main
      && rm -rf app/prod/js/out_front && rm -rf app/prod/js/out_main && rm -rf target/*
      && lein do clean, descjop-init, descjop-externs, descjop-once, descjop-figwheel

Once figwheel is ready and waiting for connections we start electron with the dev environment,
e.g. on MacOS:

      ./electron/Electron.app/Contents/MacOS/Electron app/dev



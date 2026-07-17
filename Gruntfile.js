module.exports = function(grunt) {

    const pkg = grunt.file.readJSON('package.json');

    grunt.initConfig({
        pkg: pkg,
        "download-electron": {
            version: pkg.config.electronVersion,
            outputDir: "./electron",
            // if you want to download electron into project directory
            // downloadDir: ".electron-download",
            rebuild: true
        }
    });

    grunt.loadNpmTasks('grunt-download-electron');

};

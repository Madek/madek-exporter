const fs = require('fs');
const path = require('path');

const repoRoot = path.join(__dirname, '../..');

const required = [
  'app/dev/js/front.js',
  'app/dev/js/main.js',
  'app/dev/jvm-main.jar',
  'app/dev/css/site.css',
];

module.exports = async function globalSetup() {
  const missing = required.filter(
    (rel) => !fs.existsSync(path.join(repoRoot, rel))
  );
  if (missing.length > 0) {
    throw new Error(
      `E2E prerequisites missing:\n${missing.map((f) => `  - ${f}`).join('\n')}\nRun: ./bin/build-electron-dev`
    );
  }
};

const fs = require('fs');
const path = require('path');

const repoRoot = path.join(__dirname, '../..');
const appMode = process.env.MADEK_E2E_APP === 'prod' ? 'prod' : 'dev';

const required = [
  `app/${appMode}/js/front.js`,
  `app/${appMode}/js/main.js`,
  `app/${appMode}/jvm-main.jar`,
  `app/${appMode}/css/site.css`,
];

const buildHint =
  appMode === 'prod'
    ? './bin/build-electron-prod && ./bin/build-jvm-main-prod'
    : './bin/build-electron-dev';

module.exports = async function globalSetup() {
  const missing = required.filter(
    (rel) => !fs.existsSync(path.join(repoRoot, rel))
  );
  if (missing.length > 0) {
    throw new Error(
      `E2E prerequisites missing:\n${missing.map((f) => `  - ${f}`).join('\n')}\nRun: ${buildHint}`
    );
  }
};

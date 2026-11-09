const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');
const {
  launchMadekExporter,
  closeMadekExporter,
  attachPageDiagnostics,
  waitForFrontReady,
  getFakeBaseUrl,
  fakeToken,
} = require('./electron-app');
const { connectWithToken } = require('./fixtures');

const runProd = process.env.MADEK_E2E_PROD === '1';
const frontJs = path.join(__dirname, '../../app/prod/js/front.js');

test.describe('prod advanced-build smoke', () => {
  test.skip(!runProd, 'Set MADEK_E2E_PROD=1 (and MADEK_E2E_APP=prod) to run');

  test.beforeAll(() => {
    test.skip(
      !fs.existsSync(frontJs),
      'app/prod/js/front.js missing — run npm run build:electron:prod first'
    );
  });

  /** @type {import('@playwright/test').ElectronApplication} */
  let app;
  let jvmPort;
  /** @type {import('@playwright/test').Page} */
  let page;
  let dumpLogs;

  test.beforeAll(async () => {
    process.env.MADEK_E2E_APP = 'prod';
    ({ app, jvmPort } = await launchMadekExporter());
    page = await app.firstWindow();
    dumpLogs = attachPageDiagnostics(page);
    await waitForFrontReady(page, dumpLogs);
  });

  test.afterAll(async () => {
    if (app) await closeMadekExporter(app, jvmPort);
  });

  test('connects with token against fake Madek', async () => {
    await connectWithToken(page, fakeToken, getFakeBaseUrl());
    await expect(page.getByRole('link', { name: /^Export$/i })).toBeVisible({
      timeout: 60_000,
    });
  });
});

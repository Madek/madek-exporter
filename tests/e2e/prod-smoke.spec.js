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
  fakeMediaEntryId,
  createE2eExportDir,
  jvmFetch,
} = require('./electron-app');
const {
  connectWithToken,
  expectConnected,
  openExportPage,
  runExportWizard,
} = require('./fixtures');

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
    delete process.env.MADEK_EXPORTER_DEBUG;
    ({ app, jvmPort } = await launchMadekExporter());
    page = await app.firstWindow();
    dumpLogs = attachPageDiagnostics(page);
    await waitForFrontReady(page, dumpLogs);
    // Record instead of opening a real browser / Finder window.
    await app.evaluate(({ shell }) => {
      global.__shellCalls = [];
      shell.openExternal = async (url) => {
        global.__shellCalls.push(['openExternal', url]);
      };
      shell.openPath = async (dir) => {
        global.__shellCalls.push(['openPath', dir]);
        return '';
      };
    });
  });

  test.afterAll(async () => {
    if (app) await closeMadekExporter(app, jvmPort);
  });

  test.beforeEach(async () => {
    await app.evaluate(() => {
      global.__shellCalls = [];
    });
  });

  const shellCalls = () => app.evaluate(() => global.__shellCalls);

  test('sandboxed preload opens links and folders via main', async () => {
    const rejected = await page.evaluate(async () => {
      await window.madekBridge.openExternal('https://example.test/');
      await window.madekBridge.openPath('/tmp');
      const blocked = [];
      for (const call of [
        () => window.madekBridge.openExternal('file:///etc/passwd'),
        () => window.madekBridge.openPath('/tmp/../etc'),
      ]) {
        await call().then(
          () => blocked.push(false),
          () => blocked.push(true)
        );
      }
      return blocked;
    });
    expect(rejected).toEqual([true, true]);
    expect(await shellCalls()).toEqual([
      ['openExternal', 'https://example.test/'],
      ['openPath', '/tmp'],
    ]);
  });

  test('clicking an external link on the Help page opens it via shell', async () => {
    await page.getByRole('link', { name: /^Help$/i }).first().click();
    const link = page.locator('.page a[href^="https://"]').first();
    const href = await link.getAttribute('href');
    expect(href).toMatch(/^https:\/\//);
    await link.click();
    await expect.poll(shellCalls).toEqual([['openExternal', href]]);
    // The app window must not navigate away to the external page.
    expect(page.url()).toMatch(/^file:/);
    await page.getByRole('link', { name: /^Connection$/i }).first().click();
  });

  test('Debug page is available, Performance tab hidden without MADEK_EXPORTER_DEBUG', async () => {
    await page.getByRole('link', { name: /^Debug$/i }).first().click();
    const tabs = page.locator('.page.debug ul.nav-tabs');
    await expect(tabs.getByRole('link', { name: /^State$/i })).toBeVisible();
    await expect(tabs.getByRole('link', { name: /^Performance$/i })).toHaveCount(0);
    const resp = await jvmFetch(`http://127.0.0.1:${jvmPort}/debug/performance`);
    expect(resp.status).not.toBe(200);
    await page.getByRole('link', { name: /^Connection$/i }).first().click();
  });

  test('connects with token against fake Madek', async () => {
    await connectWithToken(page, fakeToken, getFakeBaseUrl());
    await expect(page.getByRole('link', { name: /^Export$/i })).toBeVisible({
      timeout: 60_000,
    });
  });

  test('"Open Finder" button opens the export target directory', async () => {
    const targetDir = createE2eExportDir();
    await expectConnected(page);
    await openExportPage(page);
    await runExportWizard(page, {
      entityUrl: `${getFakeBaseUrl()}/entries/${fakeMediaEntryId}`,
      targetDir,
      skipMediaFiles: true,
      skipConnect: true,
    });
    await page
      .getByRole('button', { name: /Open (Finder|Explorer|file manager)/i })
      .click();
    await expect.poll(shellCalls).toEqual([['openPath', targetDir]]);
  });
});

test.describe('prod advanced-build smoke with MADEK_EXPORTER_DEBUG=1', () => {
  test.skip(!runProd, 'Set MADEK_E2E_PROD=1 (and MADEK_E2E_APP=prod) to run');

  let app;
  let jvmPort;
  let page;

  test.beforeAll(async () => {
    test.skip(!fs.existsSync(frontJs), 'app/prod/js/front.js missing');
    process.env.MADEK_E2E_APP = 'prod';
    process.env.MADEK_EXPORTER_DEBUG = '1';
    try {
      ({ app, jvmPort } = await launchMadekExporter());
    } finally {
      delete process.env.MADEK_EXPORTER_DEBUG;
    }
    page = await app.firstWindow();
  });

  test.afterAll(async () => {
    if (app) await closeMadekExporter(app, jvmPort);
  });

  test('Performance tab and JVM /debug/performance are enabled', async () => {
    const resp = await jvmFetch(`http://127.0.0.1:${jvmPort}/debug/performance`, {
      headers: { accept: 'application/json' },
    });
    expect(resp.status).toBe(200);
    await page.getByRole('link', { name: /^Debug$/i }).first().click();
    const perfTab = page.locator('.page.debug ul.nav-tabs').getByRole('link', {
      name: /^Performance$/i,
    });
    await expect(perfTab).toBeVisible();
    await perfTab.click();
    await expect(page.locator('.page.debug li.active')).toHaveText('Performance');
  });
});

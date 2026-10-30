const fs = require('fs');
const path = require('path');
const base = require('@playwright/test');
const {
  getFakeBaseUrl,
  fakeToken,
  fakeMediaEntryId,
  fakeSetId,
  fakeSetMediaEntryIds,
  createE2eExportDir,
  launchMadekExporter,
  attachPageDiagnostics,
  waitForFrontReady,
  resetSession,
  closeMadekExporter,
} = require('./electron-app');

const { expect } = base;

/**
 * `exporter` is worker-scoped: one Electron app + JVM per worker (workers: 1),
 * shared by all spec files. `page` is that app's window, reset to a clean
 * login form before every test.
 */
const test = base.test.extend({
  exporter: [
    // eslint-disable-next-line no-empty-pattern
    async ({}, use) => {
      const { app, jvmPort } = await launchMadekExporter();
      const page = await app.firstWindow();
      await waitForFrontReady(page, attachPageDiagnostics(page));
      await use({ app, page, jvmPort });
      await closeMadekExporter(app, jvmPort);
    },
    { scope: 'worker', timeout: 240_000 },
  ],
  page: async ({ exporter }, use) => {
    await resetSession(exporter.page, exporter.jvmPort);
    await use(exporter.page);
  },
});

/** @param {import('@playwright/test').Page} page */
function connectButton(page) {
  return page.getByRole('button', { name: /^Connect$/i });
}

/** @param {import('@playwright/test').Page} page @param {string} url */
async function fillConnectionUrl(page, url) {
  const input = page.locator('input.url');
  await input.click();
  await input.fill('');
  await input.fill(url);
  await expect(input).toHaveValue(url);
}

/** @param {import('@playwright/test').Page} page */
async function useLoginTab(page) {
  await page.getByRole('link', { name: /Sign in with login and password/i }).click();
}

/** @param {import('@playwright/test').Page} page */
async function connectWithToken(page, token, url) {
  const baseUrl = url ?? getFakeBaseUrl();
  await fillConnectionUrl(page, baseUrl);
  await page.locator('input.password').fill(token);
  await connectButton(page).click();
}

/** @param {import('@playwright/test').Page} page @param {'api' | 'api-v2' | 'both'} mode */
async function setExportApiMode(page, mode) {
  const select = page.locator('#api-mode');
  await expect(select).toBeVisible();
  await Promise.all([
    page.waitForResponse(
      (r) =>
        r.url().includes('/connect/api-mode') &&
        ['PATCH', 'POST'].includes(r.request().method()) &&
        r.ok(),
      { timeout: 15_000 }
    ),
    select.selectOption(mode),
  ]);
}

/** @param {import('@playwright/test').Page} page */
async function connectWithLogin(page, login, password) {
  await useLoginTab(page);
  await fillConnectionUrl(page, getFakeBaseUrl());
  await page.locator('input.login').fill(login);
  await page.locator('input.password').fill(password);
  await connectButton(page).click();
}

/** @param {import('@playwright/test').Page} page */
function exportNavItem(page) {
  return page
    .getByRole('navigation')
    .getByRole('listitem')
    .filter({ has: page.getByRole('link', { name: 'Export', exact: true }) });
}

/** @param {import('@playwright/test').Page} page */
async function expectExportDisabled(page) {
  const item = exportNavItem(page);
  await expect(item).toHaveClass(/\bdisabled\b/);
  await expect(item.getByRole('link')).toHaveAttribute('href', '#');
  // Clicking must not leave the connection page.
  await item.getByRole('link').click({ force: true });
  await expect(page.locator('input.url')).toBeVisible();
  expect(page.url()).not.toMatch(/\/download$/);
}

/** @param {import('@playwright/test').Page} page */
async function expectExportEnabled(page) {
  const item = exportNavItem(page);
  await expect(item).not.toHaveClass(/\bdisabled\b/);
  await expect(item.getByRole('link')).toHaveAttribute('href', '/download');
}

/** @param {import('@playwright/test').Page} page */
function connectionErrorModal(page) {
  return page.locator('.modal-content.modal-danger');
}

/** @param {import('@playwright/test').Page} page */
async function dismissConnectionError(page) {
  await connectionErrorModal(page).getByRole('button', { name: /^Dismiss$/i }).click();
}

/** @param {import('@playwright/test').Page} page */
async function expectRejected(page) {
  const modal = connectionErrorModal(page);
  await expect(modal).toBeVisible({ timeout: 15_000 });
  await expect(modal.locator('.modal-header')).toContainText(/Connection failed/i);
  // Main alert only — probe lines also contain “Authentication failed (HTTP 401)”.
  await expect(modal.locator('.alert-danger')).toContainText(/Authentication failed/i);
  await expect(page.getByText(/Connected!/i)).not.toBeVisible();
  await dismissConnectionError(page);
  await expectExportDisabled(page);
}

/** @param {import('@playwright/test').Page} page */
async function expectConnected(page) {
  await expect(page.getByText(/Connected!/i)).toBeVisible();
  await expectExportEnabled(page);
}

/** @param {import('@playwright/test').Page} page */
async function openExportPage(page) {
  await exportNavItem(page).getByRole('link').click();
  await expect(page.getByRole('heading', { name: /Step 1/i })).toBeVisible();
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {{ entityUrl: string, targetDir: string, skipMediaFiles: boolean }} opts
 */
async function runExportWizard(page, { entityUrl, targetDir, skipMediaFiles, skipConnect }) {
  if (!skipConnect) {
    await connectWithToken(page, fakeToken);
    await expectConnected(page);
    await openExportPage(page);
  }

  await page.locator('section.pre-download-check input[type="url"]').fill(entityUrl);
  await page.locator('#target-directory').fill(targetDir);
  await page.getByRole('button', { name: /Continue to step 2/i }).click();
  await expect(page.getByRole('heading', { name: /Step 2/i })).toBeVisible({
    timeout: 30_000,
  });

  await expect(page.locator('.meta-key select')).toBeVisible({ timeout: 30_000 });
  await page.locator('.meta-key select').selectOption('');

  if (skipMediaFiles) {
    await page.locator('.skip-files input[type="checkbox"]').check();
  }

  await page.getByRole('button', { name: /Continue to step 3/i }).click();
  await expect(page.getByRole('heading', { name: /Step 3/i })).toBeVisible();

  await page.getByRole('button', { name: /Start Download\/Export!/i }).click();
  await expect(page.getByRole('heading', { name: /Download \/ Export Finished/i })).toBeVisible({
    timeout: 90_000,
  });
}

/**
 * Connect with token, run export steps 1–3, wait until finished.
 * @param {import('@playwright/test').Page} page
 * @param {{ skipMediaFiles?: boolean, targetDir?: string, apiMode?: 'api' | 'api-v2' | 'both' }} [opts]
 * @returns {Promise<{ targetDir: string, entryId: string }>}
 */
async function exportFakeMediaEntry(page, opts = {}) {
  const skipMediaFiles = opts.skipMediaFiles !== false;
  const targetDir = opts.targetDir ?? createE2eExportDir();
  const entryUrl = `${getFakeBaseUrl()}/entries/${fakeMediaEntryId}`;

  await connectWithToken(page, fakeToken);
  await expectConnected(page);
  if (opts.apiMode && opts.apiMode !== 'api') {
    await setExportApiMode(page, opts.apiMode);
  }
  await openExportPage(page);
  await runExportWizard(page, {
    entityUrl: entryUrl,
    targetDir,
    skipMediaFiles,
    skipConnect: true,
  });

  const metaDataPath =
    opts.apiMode === 'both'
      ? path.join(targetDir, 'api', fakeMediaEntryId, 'meta-data.json')
      : path.join(targetDir, fakeMediaEntryId, 'meta-data.json');
  await expect.poll(() => fs.existsSync(metaDataPath), { timeout: 90_000 }).toBe(true);

  return { targetDir, entryId: fakeMediaEntryId };
}

/**
 * Export the fake set containing three media entries (metadata only by default).
 * @param {import('@playwright/test').Page} page
 * @param {{ skipMediaFiles?: boolean, targetDir?: string }} [opts]
 */
async function exportFakeSet(page, opts = {}) {
  const skipMediaFiles = opts.skipMediaFiles !== false;
  const targetDir = opts.targetDir ?? createE2eExportDir();
  const setUrl = `${getFakeBaseUrl()}/sets/${fakeSetId}`;

  await runExportWizard(page, { entityUrl: setUrl, targetDir, skipMediaFiles });

  const setDir = path.join(targetDir, fakeSetId);
  await expect
    .poll(() => fs.existsSync(path.join(setDir, 'meta-data.json')), { timeout: 10_000 })
    .toBe(true);
  for (const entryId of fakeSetMediaEntryIds) {
    await expect
      .poll(() => fs.existsSync(path.join(setDir, entryId, 'meta-data.json')), {
        timeout: 10_000,
      })
      .toBe(true);
  }

  return { targetDir, setId: fakeSetId, entryIds: fakeSetMediaEntryIds };
}

/** @param {import('@playwright/test').Locator} toggle */
async function readExportDownloadLogCount(toggle) {
  const label = (await toggle.textContent()) ?? '';
  const match = label.match(/\((\d+)\)/);
  return match ? Number.parseInt(match[1], 10) : 0;
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {{ count: number, open?: boolean }} opts
 * @returns {Promise<number>}
 */
async function expectExportDownloadLogCount(page, opts) {
  const expected = opts.count;
  const open = opts.open !== false;
  const toggle = page.locator('.download-logs').getByRole('button', { name: /Logs/i });
  await expect(toggle).toContainText(/\(\d+\)/, { timeout: 15_000 });
  await expect
    .poll(async () => readExportDownloadLogCount(toggle), { timeout: 20_000 })
    .toBe(expected);
  const count = await readExportDownloadLogCount(toggle);
  if (open && !(await page.locator('.download-logs-text').isVisible())) {
    await toggle.click();
  }
  if (open) {
    const logText = (await page.locator('.download-logs-text').textContent()) ?? '';
    const lines = logText.split('\n').map((l) => l.trim()).filter(Boolean);
    expect(lines.length).toBe(count);
    expect(lines.every((l) => /^GET /i.test(l))).toBe(true);
  }
  return count;
}

module.exports = {
  test,
  expect,
  connectButton,
  fillConnectionUrl,
  useLoginTab,
  connectWithToken,
  connectWithLogin,
  expectExportDisabled,
  expectExportEnabled,
  dismissConnectionError,
  expectRejected,
  expectConnected,
  openExportPage,
  exportFakeMediaEntry,
  exportFakeSet,
  expectExportDownloadLogCount,
  setExportApiMode,
};

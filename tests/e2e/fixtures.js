const base = require('@playwright/test');
const {
  fakeBaseUrl,
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

/** @param {import('@playwright/test').Page} page */
async function useLoginTab(page) {
  await page.getByRole('link', { name: /Sign in with login and password/i }).click();
}

/** @param {import('@playwright/test').Page} page */
async function connectWithToken(page, token, url = fakeBaseUrl) {
  await page.locator('input.url').fill(url);
  await page.locator('input.password').fill(token);
  await connectButton(page).click();
}

/** @param {import('@playwright/test').Page} page */
async function connectWithLogin(page, login, password) {
  await useLoginTab(page);
  await page.locator('input.url').fill(fakeBaseUrl);
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
async function dismissConnectionError(page) {
  await page.getByRole('button', { name: /^Dismiss$/i }).click();
}

/** @param {import('@playwright/test').Page} page */
async function expectRejected(page) {
  await expect(page.getByText(/Connection failed/i)).toBeVisible();
  await expect(page.getByText(/Authentication failed/i)).toBeVisible();
  await expect(page.getByText(/Connected!/i)).not.toBeVisible();
  await dismissConnectionError(page);
  await expectExportDisabled(page);
}

/** @param {import('@playwright/test').Page} page */
async function expectConnected(page) {
  await expect(page.getByText(/Connected!/i)).toBeVisible();
  await expectExportEnabled(page);
}

module.exports = {
  test,
  expect,
  connectButton,
  useLoginTab,
  connectWithToken,
  connectWithLogin,
  expectExportDisabled,
  expectExportEnabled,
  dismissConnectionError,
  expectRejected,
  expectConnected,
};

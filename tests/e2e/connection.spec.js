const { test, expect } = require('@playwright/test');
const {
  fakeBaseUrl,
  fakeToken,
  fakeLogin,
  fakeEmail,
  fakePassword,
  fakeApiClient,
  fakeApiClientPassword,
  launchMadekExporter,
  attachPageDiagnostics,
  waitForFrontReady,
  gotoConnectionPage,
  resetSession,
  closeMadekExporter,
} = require('./electron-app');

test.describe.configure({ mode: 'serial' });

test.describe('login', () => {
  /** @type {import('@playwright/test').ElectronApplication} */
  let app;
  /** @type {import('@playwright/test').Page} */
  let page;
  let jvmPort;

  test.beforeAll(async () => {
    test.setTimeout(240_000);
    ({ app, jvmPort } = await launchMadekExporter());
    page = await app.firstWindow();
    const dumpLogs = attachPageDiagnostics(page);
    await waitForFrontReady(page, dumpLogs);
  });

  test.afterAll(async () => {
    if (app) await closeMadekExporter(app, jvmPort);
  });

  test.beforeEach(async () => {
    await resetSession(page, jvmPort);
  });

  async function useLoginTab() {
    await page.getByRole('link', { name: /Sign in with login and password/i }).click();
  }

  async function connectButton() {
    return page.getByRole('button', { name: /^Connect$/i });
  }

  async function connectWithLogin(login, password) {
    await useLoginTab();
    await page.locator('input.url').fill(fakeBaseUrl);
    await page.locator('input.login').fill(login);
    await page.locator('input.password').fill(password);
    await (await connectButton()).click();
  }

  function exportNavItem() {
    return page
      .getByRole('navigation')
      .getByRole('listitem')
      .filter({ has: page.getByRole('link', { name: 'Export', exact: true }) });
  }

  async function expectExportDisabled() {
    const item = exportNavItem();
    await expect(item).toHaveClass(/\bdisabled\b/);
    await expect(item.getByRole('link')).toHaveAttribute('href', '#');
    // Clicking must not leave the connection page.
    await item.getByRole('link').click({ force: true });
    await expect(page.locator('input.url')).toBeVisible();
    expect(page.url()).not.toMatch(/\/download$/);
  }

  async function expectExportEnabled() {
    const item = exportNavItem();
    await expect(item).not.toHaveClass(/\bdisabled\b/);
    await expect(item.getByRole('link')).toHaveAttribute('href', '/download');
  }

  async function expectRejected() {
    await expect(page.getByText(/Connection failed/i)).toBeVisible();
    await expect(page.getByText(/Authentication failed/i)).toBeVisible();
    await expect(page.getByText(/Connected!/i)).not.toBeVisible();
    await page.getByRole('button', { name: /^Dismiss$/i }).click();
    await expectExportDisabled();
  }

  async function expectConnected() {
    await expect(page.getByText(/Connected!/i)).toBeVisible();
    await expectExportEnabled();
  }

  test('app: Electron + JVM connected', async () => {
    await page.getByRole('link', { name: /^Debug$/i }).click();
    await expect(page.getByText(/Electron main connected!/i)).toBeVisible();
    await expect(page.getByText(/JVM main connected!/i)).toBeVisible();
    await gotoConnectionPage(page);
  });

  test('export: menu disabled before login', async () => {
    await expectExportDisabled();
  });

  test('token only: connects (api + api-v2)', async () => {
    await expectExportDisabled();
    await page.locator('input.url').fill(fakeBaseUrl);
    await page.locator('input.password').fill(fakeToken);
    await (await connectButton()).click();

    await expectConnected();
    await expect(page.locator('.connected')).toContainText(fakeBaseUrl);
    await expect(page.getByRole('link', { name: /Continue to export/i })).toBeVisible();
  });

  test('token: wrong token fails', async () => {
    await page.locator('input.url').fill(fakeBaseUrl);
    await page.locator('input.password').fill('wrong-token');
    await (await connectButton()).click();
    await expectRejected();
  });

  test('login/token: connects', async () => {
    await connectWithLogin(fakeLogin, fakeToken);
    await expectConnected();
    await expect(page.locator('.connected')).toContainText(fakeEmail);
  });

  test('email/token: connects', async () => {
    await connectWithLogin(fakeEmail, fakeToken);
    await expectConnected();
    await expect(page.locator('.connected')).toContainText(fakeEmail);
  });

  test('login/password: rejected (401)', async () => {
    await connectWithLogin(fakeLogin, fakePassword);
    await expectRejected();
  });

  test('email/password: rejected (401)', async () => {
    await connectWithLogin(fakeEmail, fakePassword);
    await expectRejected();
  });

  test('api-client/password: connects', async () => {
    await connectWithLogin(fakeApiClient, fakeApiClientPassword);
    await expectConnected();
    await expect(page.locator('.connected')).toContainText('ApiClient');
    await expect(page.locator('.connected')).toContainText(fakeApiClient);
  });

  test('api-client/wrong password: rejected (401)', async () => {
    await connectWithLogin(fakeApiClient, 'wrong');
    await expectRejected();
  });

  test('unreachable Madek URL fails', async () => {
    await page.locator('input.url').fill('http://127.0.0.1:59123');
    await page.locator('input.password').fill(fakeToken);
    await (await connectButton()).click();

    await expect(page.getByText(/Connection failed/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Connected!/i)).not.toBeVisible();
    await page.getByRole('button', { name: /^Dismiss$/i }).click();
    await expectExportDisabled();
  });

  test('disconnect shows login form, export disabled again', async () => {
    await page.locator('input.url').fill(fakeBaseUrl);
    await page.locator('input.password').fill(fakeToken);
    await (await connectButton()).click();
    await expectConnected();

    await page.getByRole('button', { name: /^Disconnect$/i }).click();
    await expect(page.getByText(/Connected!/i)).not.toBeVisible();
    await expect(await connectButton()).toBeVisible();
    await expectExportDisabled();
  });

  test('form: invalid URL disables Connect', async () => {
    await page.locator('input.url').fill('not-a-valid-url');
    await page.locator('input.password').fill(fakeToken);
    await expect(await connectButton()).toBeDisabled();
    await expectExportDisabled();
  });

  test('form: empty token disables Connect', async () => {
    await page.locator('input.url').fill(fakeBaseUrl);
    await expect(await connectButton()).toBeDisabled();
    await expectExportDisabled();
  });

  test('form: empty password disables Connect', async () => {
    await useLoginTab();
    await page.locator('input.url').fill(fakeBaseUrl);
    await page.locator('input.login').fill(fakeLogin);
    await expect(await connectButton()).toBeDisabled();
    await expectExportDisabled();
  });
});

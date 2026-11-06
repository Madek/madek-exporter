const {
  test,
  expect,
  connectWithToken,
  connectWithLogin,
  expectConnected,
  expectExportEnabled,
  exportNavItem,
  openApiModeMenu,
  apiModeItem,
  setExportApiMode,
} = require('./fixtures');
const { fakeToken, fakeLogin } = require('./electron-app');

/** @param {import('@playwright/test').Page} page @param {string} code @param {boolean} available */
async function expectProbe(page, code, available) {
  const item = page.locator('.api-probe-status-item').filter({
    has: page.locator('code', { hasText: new RegExp(`^${code}$`) }),
  });
  await expect(item).toBeVisible();
  await expect(item.locator(available ? '.glyphicon-ok' : '.glyphicon-remove')).toBeVisible();
  await expect(item).toContainText(available ? 'available' : 'unavailable');
}

test('both APIs up: api, api-v2, and both can be selected', async ({ page }) => {
  await connectWithToken(page, fakeToken);
  await expectConnected(page);
  await expect(page.locator('.connected')).toContainText(/Connected!/i);
  await expectProbe(page, '/api', true);
  await expectProbe(page, '/api-v2', true);
  await expect(page.locator('.api-mode-selection .alert-warning')).toHaveCount(0);

  await setExportApiMode(page, 'api-v2');
  await setExportApiMode(page, 'both');
  await expectExportEnabled(page);
  await expect(page.getByRole('link', { name: /Continue to export/i })).not.toHaveClass(
    /\bdisabled\b/
  );
});

test('one API down: api-v2 and both cannot be selected', async ({ page }) => {
  // Basic auth is accepted by /api and rejected by /api-v2.
  await connectWithLogin(page, fakeLogin, fakeToken);
  await expectConnected(page);
  await expectProbe(page, '/api', true);
  await expectProbe(page, '/api-v2', false);

  await openApiModeMenu(page);
  await expect(apiModeItem(page, 'api')).not.toHaveClass(/\bdisabled\b/);
  await expect(apiModeItem(page, 'api-v2')).toHaveClass(/\bdisabled\b/);
  await expect(apiModeItem(page, 'both')).toHaveClass(/\bdisabled\b/);

  await apiModeItem(page, 'both').locator('a').click({ force: true });
  await expect(page.locator('#api-mode')).toContainText(/^api \(\/api\)/);
  await expect(page.locator('.api-mode-selection .alert-warning')).toHaveCount(0);
  await expectExportEnabled(page);
});

test('both stays blocked after reconnecting with only /api', async ({ page }) => {
  await connectWithToken(page, fakeToken);
  await expectConnected(page);
  await setExportApiMode(page, 'both');

  await page.getByRole('button', { name: /^Disconnect$/i }).click();
  await expect(page.getByText(/Connected!/i)).not.toBeVisible();

  await connectWithLogin(page, fakeLogin, fakeToken);
  await expect(page.getByText(/Connected!/i)).toBeVisible();
  await expectProbe(page, '/api-v2', false);
  await expect(page.locator('#api-mode')).toContainText(/^both →/);
  await expect(page.locator('.api-mode-selection .alert-warning')).toBeVisible();
  await expect(page.getByRole('link', { name: /Continue to export/i })).toHaveClass(/\bdisabled\b/);
  const exportItem = exportNavItem(page);
  await expect(exportItem).toHaveClass(/\bdisabled\b/);
  await expect(exportItem.getByRole('link')).toHaveAttribute('href', '#');

  await openApiModeMenu(page);
  await expect(apiModeItem(page, 'both')).toHaveClass(/\bdisabled\b/);
  await expect(apiModeItem(page, 'api-v2')).toHaveClass(/\bdisabled\b/);

  await setExportApiMode(page, 'api');
  await expect(page.locator('.api-mode-selection .alert-warning')).toHaveCount(0);
  await expectExportEnabled(page);
});

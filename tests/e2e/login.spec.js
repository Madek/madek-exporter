const {
  test,
  expect,
  connectButton,
  fillConnectionUrl,
  useLoginTab,
  connectWithToken,
  connectWithLogin,
  expectExportDisabled,
  dismissConnectionError,
  expectRejected,
  expectConnected,
} = require('./fixtures');
const {
  getFakeBaseUrl,
  fakeToken,
  fakeLogin,
  fakeEmail,
  fakePassword,
  fakeApiClient,
  fakeApiClientPassword,
  defaultMadekPresetUrl,
} = require('./electron-app');

test('export: menu disabled before login', async ({ page }) => {
  await expectExportDisabled(page);
});

test('token only: connects (api + api-v2)', async ({ page }) => {
  await connectWithToken(page, fakeToken);
  await expectConnected(page);
  await expect(page.locator('.connected')).toContainText(getFakeBaseUrl());
  await expect(page.getByRole('link', { name: /Continue to export/i })).toBeVisible();
});

test('token: wrong token fails', async ({ page }) => {
  await connectWithToken(page, 'wrong-token');
  await expectRejected(page);
});

test('login/token: connects', async ({ page }) => {
  await connectWithLogin(page, fakeLogin, fakeToken);
  await expectConnected(page);
  await expect(page.locator('.connected')).toContainText(fakeEmail);
});

test('email/token: connects', async ({ page }) => {
  await connectWithLogin(page, fakeEmail, fakeToken);
  await expectConnected(page);
  await expect(page.locator('.connected')).toContainText(fakeEmail);
});

test('login/password: rejected (401)', async ({ page }) => {
  await connectWithLogin(page, fakeLogin, fakePassword);
  await expectRejected(page);
});

test('email/password: rejected (401)', async ({ page }) => {
  await connectWithLogin(page, fakeEmail, fakePassword);
  await expectRejected(page);
});

test('api-client/password: connects', async ({ page }) => {
  await connectWithLogin(page, fakeApiClient, fakeApiClientPassword);
  await expectConnected(page);
  await expect(page.locator('.connected')).toContainText('ApiClient');
  await expect(page.locator('.connected')).toContainText(fakeApiClient);
});

test('api-client/wrong password: rejected (401)', async ({ page }) => {
  await connectWithLogin(page, fakeApiClient, 'wrong');
  await expectRejected(page);
});

test('unreachable Madek URL fails', async ({ page }) => {
  await connectWithToken(page, fakeToken, 'http://127.0.0.1:59123');
  await expect(page.getByText(/Connection failed/i)).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText(/Connected!/i)).not.toBeVisible();
  await dismissConnectionError(page);
  await expectExportDisabled(page);
});

test('disconnect shows login form, export disabled again', async ({ page }) => {
  await connectWithToken(page, fakeToken);
  await expectConnected(page);

  await page.getByRole('button', { name: /^Disconnect$/i }).click();
  await expect(page.getByText(/Connected!/i)).not.toBeVisible();
  await expect(connectButton(page)).toBeVisible();
  await expectExportDisabled(page);
});

test('form: base URL empty until label clicked', async ({ page }) => {
  const urlInput = page.locator('input.url');
  await expect(urlInput).toHaveValue('');
  await expect(urlInput).toHaveAttribute('placeholder', defaultMadekPresetUrl);
  await page.locator('label[for="url"]').click();
  await expect(urlInput).toHaveValue(defaultMadekPresetUrl);
});

test('form: invalid URL disables Connect', async ({ page }) => {
  await fillConnectionUrl(page, 'not-a-valid-url');
  await page.locator('input.password').fill(fakeToken);
  await expect(connectButton(page)).toBeDisabled();
  await expectExportDisabled(page);
});

test('form: empty token disables Connect', async ({ page }) => {
  await fillConnectionUrl(page, getFakeBaseUrl());
  await expect(connectButton(page)).toBeDisabled();
  await expectExportDisabled(page);
});

test('form: empty password disables Connect', async ({ page }) => {
  await useLoginTab(page);
  await fillConnectionUrl(page, getFakeBaseUrl());
  await page.locator('input.login').fill(fakeLogin);
  await expect(connectButton(page)).toBeDisabled();
  await expectExportDisabled(page);
});

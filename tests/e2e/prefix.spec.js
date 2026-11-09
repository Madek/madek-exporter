const {
  test,
  expect,
  connectWithToken,
  expectConnected,
  openExportPage,
  setExportApiMode,
  clearFakeRequests,
  fakeRequestPaths,
  expectPathsForApi,
  catalogPaths,
} = require('./fixtures');
const {
  getFakeBaseUrl,
  fakeToken,
  fakeMediaEntryId,
  createE2eExportDir,
} = require('./electron-app');

test.describe.configure({ timeout: 120_000 });

async function openPrefixStep(page, { apiMode = 'api' } = {}) {
  const targetDir = createE2eExportDir();
  await connectWithToken(page, fakeToken);
  await expectConnected(page);
  if (apiMode !== 'api') {
    await setExportApiMode(page, apiMode);
  }
  await openExportPage(page);

  await page.locator('section.pre-download-check input[type="url"]').fill(
    `${getFakeBaseUrl()}/entries/${fakeMediaEntryId}`
  );
  await page.locator('#target-directory').fill(targetDir);
  await page.getByRole('button', { name: /Continue to step 2/i }).click();
  await expect(page.getByRole('heading', { name: /Step 2/i })).toBeVisible({
    timeout: 30_000,
  });

  const toggle = page.locator('.prefix-dropdown-toggle');
  await expect(toggle).toBeEnabled({ timeout: 30_000 });
  return toggle;
}

test('prefix picker: value, empty value, no prefix, and the value filter', async ({ page }) => {
  const toggle = await openPrefixStep(page);

  await expect(toggle).toContainText(/NO META-KEY PREFIX/i);

  const refresh = page.getByRole('button', { name: 'Refresh prefix options' });
  await refresh.click();
  await expect(toggle).toBeEnabled({ timeout: 30_000 });
  await expect(toggle).toContainText(/NO META-KEY PREFIX/i);

  await toggle.click();

  const menu = page.locator('.prefix-dropdown-menu');
  const onlyWithValue = menu.getByRole('checkbox', { name: /Only meta-keys with value/i });
  const filter = menu.locator('input[type="search"]');
  // Click the label text. Clicking the input itself toggles twice (input inside <label>).
  const onlyWithValueLabel = menu.getByText('Only meta-keys with value', { exact: true });
  async function setOnlyWithValue(checked) {
    if ((await onlyWithValue.isChecked()) !== checked) {
      await onlyWithValueLabel.click();
    }
    if (checked) await expect(onlyWithValue).toBeChecked();
    else await expect(onlyWithValue).not.toBeChecked();
  }
  await expect(onlyWithValue).toBeChecked();

  // Checked filter hides the empty subtitle and the “no prefix” row.
  await expect(menu.locator('tr', { hasText: 'madek_core:title' })).toBeVisible();
  await expect(menu.locator('tr', { hasText: 'madek_core:subtitle' })).toHaveCount(0);
  await expect(menu.locator('tr.no-prefix')).toHaveCount(0);
  await expect(menu).toContainText(/1 text meta-keys/i);

  await filter.fill('subtitle');
  await expect(menu.locator('tbody tr')).toHaveCount(0);
  await expect(menu).toContainText(/0 text meta-keys/i);

  await filter.fill('');
  await setOnlyWithValue(false);
  await expect(menu.locator('tr', { hasText: 'madek_core:subtitle' })).toContainText(/no value/i);
  await expect(menu.locator('tr', { hasText: 'madek_core:subtitle' })).toContainText(
    `_${fakeMediaEntryId}`
  );
  await expect(menu.locator('tr.no-prefix')).toBeVisible();
  await expect(menu.locator('tr.no-prefix code')).toHaveText(fakeMediaEntryId);

  await menu.locator('tr', { hasText: 'madek_core:title' }).click();
  await expect(toggle).toContainText('Fake export entry');
  await expect(page.locator('.prefix-example .prefix-part')).toHaveText([
    'Fake export entry_',
    'Fake export entry_',
  ]);

  await toggle.click();
  await setOnlyWithValue(false);
  await menu.locator('tr', { hasText: 'madek_core:subtitle' }).click();
  await expect(page.locator('.prefix-example .prefix-part')).toHaveText(['_', '_']);

  await toggle.click();
  await setOnlyWithValue(false);
  await menu.locator('tr.no-prefix').click();
  await expect(page.locator('.prefix-example .prefix-part')).toHaveCount(0);
  await expect(page.locator('.prefix-example')).toContainText(fakeMediaEntryId);
  await expect(page.locator('.prefix-example')).not.toContainText('Fake export entry_');
});

async function expectPrefixRefreshUsesApi(page, expectedApi) {
  await clearFakeRequests();
  const refresh = page.getByRole('button', { name: 'Refresh prefix options' });
  const prefixDone = page.waitForResponse(
    (r) =>
      r.url().includes('/download/prefix-options') &&
      r.request().method() === 'GET' &&
      r.ok(),
    { timeout: 30_000 }
  );
  await refresh.click();
  await prefixDone;
  const catalog = catalogPaths(await fakeRequestPaths());
  expectPathsForApi(catalog, expectedApi, 'prefix refresh');
  expect(catalog.some((p) => p.includes('meta-keys'))).toBe(true);
}

test('prefix refresh uses /api when mode is api', async ({ page }) => {
  await openPrefixStep(page, { apiMode: 'api' });
  await expectPrefixRefreshUsesApi(page, 'api');
});

test('prefix refresh uses /api-v2 when mode is api-v2', async ({ page }) => {
  await openPrefixStep(page, { apiMode: 'api-v2' });
  await expectPrefixRefreshUsesApi(page, 'api-v2');
});

test('prefix refresh uses /api when mode is both', async ({ page }) => {
  await openPrefixStep(page, { apiMode: 'both' });
  await expectPrefixRefreshUsesApi(page, 'api');
});

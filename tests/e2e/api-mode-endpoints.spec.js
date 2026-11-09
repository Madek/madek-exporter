const fs = require('fs');
const path = require('path');
const {
  test,
  expect,
  connectWithToken,
  expectConnected,
  openExportPage,
  exportNavItem,
  setExportApiMode,
  clearFakeRequests,
  fakeRequestPaths,
  expectPathsForApi,
  catalogPaths,
  isApiV1Path,
  isApiV2Path,
} = require('./fixtures');
const {
  getFakeBaseUrl,
  fakeToken,
  fakeMediaEntryId,
  createE2eExportDir,
} = require('./electron-app');

test.describe.configure({ timeout: 120_000 });

/**
 * @param {import('@playwright/test').Page} page
 * @param {'api' | 'api-v2' | 'both'} apiMode
 */
async function openPrefixStep(page, apiMode) {
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
  return { targetDir, toggle };
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {'api' | 'api-v2' | 'both'} expectedApi
 */
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
  expectPathsForApi(catalog, expectedApi === 'both' ? 'api' : expectedApi, 'prefix refresh');
  expect(catalog.some((p) => p.includes('meta-keys'))).toBe(true);
}

test.describe('prefix-options endpoints by API mode', () => {
  test('api → /api', async ({ page }) => {
    await openPrefixStep(page, 'api');
    await expectPrefixRefreshUsesApi(page, 'api');
  });

  test('api-v2 → /api-v2', async ({ page }) => {
    await openPrefixStep(page, 'api-v2');
    await expectPrefixRefreshUsesApi(page, 'api-v2');
  });

  test('both → /api', async ({ page }) => {
    await openPrefixStep(page, 'both');
    await expectPrefixRefreshUsesApi(page, 'api');
  });

  test('switching api → api-v2 reconnects and prefix uses /api-v2', async ({ page }) => {
    await openPrefixStep(page, 'api');
    await expectPrefixRefreshUsesApi(page, 'api');

    await page.getByRole('navigation').getByRole('link', { name: /^Connection$/i }).click();
    await expect(page.locator('#api-mode')).toBeVisible();
    await setExportApiMode(page, 'api-v2');
    await expectConnected(page);

    // Download wizard state survives reconnect (often still on step 2).
    await exportNavItem(page).getByRole('link').click();
    const step2 = page.getByRole('heading', { name: /Step 2/i });
    const step1 = page.getByRole('heading', { name: /Step 1/i });
    await expect(step1.or(step2)).toBeVisible({ timeout: 15_000 });
    if (await step1.isVisible()) {
      await page.locator('section.pre-download-check input[type="url"]').fill(
        `${getFakeBaseUrl()}/entries/${fakeMediaEntryId}`
      );
      await page.locator('#target-directory').fill(createE2eExportDir());
      await page.getByRole('button', { name: /Continue to step 2/i }).click();
    }
    await expect(step2).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('.prefix-dropdown-toggle')).toBeEnabled({ timeout: 30_000 });
    await expectPrefixRefreshUsesApi(page, 'api-v2');
  });
});

test.describe('export endpoints by API mode', () => {
  test('export api hits /api only', async ({ page }) => {
    await connectWithToken(page, fakeToken);
    await expectConnected(page);
    await openExportPage(page);

    const targetDir = createE2eExportDir();
    await page.locator('section.pre-download-check input[type="url"]').fill(
      `${getFakeBaseUrl()}/entries/${fakeMediaEntryId}`
    );
    await page.locator('#target-directory').fill(targetDir);
    await page.getByRole('button', { name: /Continue to step 2/i }).click();
    await expect(page.getByRole('heading', { name: /Step 2/i })).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.locator('.prefix-dropdown-toggle')).toBeEnabled({ timeout: 30_000 });
    await page.locator('.skip-files input[type="checkbox"]').check();
    await page.getByRole('button', { name: /Continue to step 3/i }).click();
    await expect(page.getByRole('heading', { name: /Step 3/i })).toBeVisible();

    await clearFakeRequests();
    await page.getByRole('button', { name: /Start Download\/Export!/i }).click();
    await expect(page.getByRole('heading', { name: /Download \/ Export Finished/i })).toBeVisible({
      timeout: 90_000,
    });

    const paths = await fakeRequestPaths();
    expectPathsForApi(paths, 'api', 'export api');
    expect(fs.existsSync(path.join(targetDir, fakeMediaEntryId, 'meta-data.json'))).toBe(true);
  });

  test('export api-v2 hits /api-v2 only', async ({ page }) => {
    await connectWithToken(page, fakeToken);
    await expectConnected(page);
    await setExportApiMode(page, 'api-v2');
    await openExportPage(page);

    const targetDir = createE2eExportDir();
    await page.locator('section.pre-download-check input[type="url"]').fill(
      `${getFakeBaseUrl()}/entries/${fakeMediaEntryId}`
    );
    await page.locator('#target-directory').fill(targetDir);
    await page.getByRole('button', { name: /Continue to step 2/i }).click();
    await expect(page.getByRole('heading', { name: /Step 2/i })).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.locator('.prefix-dropdown-toggle')).toBeEnabled({ timeout: 30_000 });
    await expectPrefixRefreshUsesApi(page, 'api-v2');

    await page.locator('.skip-files input[type="checkbox"]').check();
    await page.getByRole('button', { name: /Continue to step 3/i }).click();
    await clearFakeRequests();
    await page.getByRole('button', { name: /Start Download\/Export!/i }).click();
    await expect(page.getByRole('heading', { name: /Download \/ Export Finished/i })).toBeVisible({
      timeout: 90_000,
    });

    const paths = await fakeRequestPaths();
    expectPathsForApi(paths, 'api-v2', 'export api-v2');
    expect(paths.some((p) => p.includes('media-entries'))).toBe(true);
    expect(fs.existsSync(path.join(targetDir, fakeMediaEntryId, 'meta-data.json'))).toBe(true);
  });

  test('export both hits /api and /api-v2', async ({ page }) => {
    await connectWithToken(page, fakeToken);
    await expectConnected(page);
    await setExportApiMode(page, 'both');
    await openExportPage(page);

    const targetDir = createE2eExportDir();
    await page.locator('section.pre-download-check input[type="url"]').fill(
      `${getFakeBaseUrl()}/entries/${fakeMediaEntryId}`
    );
    await page.locator('#target-directory').fill(targetDir);
    await page.getByRole('button', { name: /Continue to step 2/i }).click();
    await expect(page.getByRole('heading', { name: /Step 2/i })).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.locator('.prefix-dropdown-toggle')).toBeEnabled({ timeout: 30_000 });
    await expectPrefixRefreshUsesApi(page, 'api');

    await page.locator('.skip-files input[type="checkbox"]').check();
    await page.getByRole('button', { name: /Continue to step 3/i }).click();
    await clearFakeRequests();
    await page.getByRole('button', { name: /Start Download\/Export!/i }).click();
    await expect(page.getByRole('heading', { name: /Download \/ Export Finished/i })).toBeVisible({
      timeout: 90_000,
    });

    const paths = await fakeRequestPaths();
    expectPathsForApi(paths, 'both', 'export both');
    expect(paths.some((p) => isApiV1Path(p) && p.includes('media-entries'))).toBe(true);
    expect(paths.some((p) => isApiV2Path(p) && p.includes('media-entries'))).toBe(true);
    expect(fs.existsSync(path.join(targetDir, 'api', fakeMediaEntryId, 'meta-data.json'))).toBe(
      true
    );
    expect(fs.existsSync(path.join(targetDir, 'api-v2', fakeMediaEntryId, 'meta-data.json'))).toBe(
      true
    );
  });
});

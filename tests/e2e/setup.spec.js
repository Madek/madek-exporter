const { test, expect, expectExportDisabled } = require('./fixtures');
const { indexUrl } = require('./electron-app');

const LOCALE_KEY = 'madek-exporter-locale';

// Navbar labels use :nav/* keys (see i18n/messages.cljs), not :connection/title.
const LOCALES = [
  { code: 'de', label: 'DE', connection: 'Verbindungen', help: 'Hilfe', export: 'Export' },
  { code: 'fr', label: 'FR', connection: 'Connexion', help: 'Aide', export: 'Export' },
  { code: 'it', label: 'IT', connection: 'Connessione', help: 'Aiuto', export: 'Esporta' },
  { code: 'es', label: 'ES', connection: 'Conexión', help: 'Ayuda', export: 'Exportar' },
  { code: 'zh', label: 'ZH', connection: '连接', help: '帮助', export: '导出' },
  { code: 'en', label: 'EN', connection: 'Connection', help: 'Help', export: 'Export' },
];

/** @param {import('@playwright/test').Page} page */
function mainNav(page) {
  return page.locator('nav.navbar ul.navbar-pages');
}

/** @param {import('@playwright/test').Page} page */
function languageMenu(page) {
  return page.locator('li.language-menu');
}

/** @param {import('@playwright/test').Page} page */
function burgerMenu(page) {
  return page.locator('li.burger-menu');
}

test('Electron main + JVM connected', async ({ page }) => {
  await page.getByRole('link', { name: /^Debug$/i }).click();
  await expect(page.getByText(/Electron main connected!/i)).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText(/JVM main connected!/i)).toBeVisible({ timeout: 60_000 });
});

test('menu: nav items available', async ({ page }) => {
  const nav = mainNav(page);
  for (const name of ['Connection', 'Export', 'Debug', 'Help']) {
    await expect(nav.getByRole('link', { name, exact: true })).toBeVisible();
  }
  await expect(nav.locator('li.active')).toHaveText('Connection');
  await expectExportDisabled(page);

  await nav.getByRole('link', { name: 'Help', exact: true }).click();
  await expect(nav.locator('li.active')).toHaveText('Help');
  await nav.getByRole('link', { name: 'Debug', exact: true }).click();
  await expect(nav.locator('li.active')).toHaveText('Debug');
  await nav.getByRole('link', { name: 'Connection', exact: true }).click();
  await expect(page.locator('input.url')).toBeVisible();
});

test('help: toc on the left shows the selected section', async ({ page }) => {
  await page.getByRole('link', { name: 'Help', exact: true }).click();

  const toc = page.locator('nav.toc');
  const content = page.locator('.help-content');
  await expect(toc.getByRole('heading', { name: 'Contents' })).toBeVisible();
  await expect(toc.getByRole('link', { name: 'About the Madek-Exporter' })).toBeVisible();
  await expect(toc.getByRole('link', { name: 'Version & supported operating systems' })).toHaveCount(0);
  await expect(content.getByRole('heading', { name: 'About the Madek-Exporter' })).toBeVisible();
  await expect(content.getByRole('heading', { name: 'Supported operating systems' })).toBeVisible();
  await expect(content.getByText('macOS (x64, ARM64)')).toBeVisible();

  await toc.getByRole('link', { name: 'References' }).click();
  await expect(content.getByRole('heading', { name: 'References' })).toBeVisible();
  await expect(content.getByRole('heading', { name: 'About the Madek-Exporter' })).toHaveCount(0);

  await toc.locator('button.toc-toggle').click();
  await expect(toc.getByRole('heading', { name: 'Contents' })).toHaveCount(0);
  await expect(content.getByRole('heading', { name: 'References' })).toBeVisible();
  await toc.locator('button.toc-toggle').click();
  await expect(toc.getByRole('heading', { name: 'Contents' })).toBeVisible();

  const sideToc = await toc.boundingBox();
  const sideContent = await content.boundingBox();
  expect(sideContent.x).toBeGreaterThan(sideToc.x + 40);

  await page.setViewportSize({ width: 680, height: 800 });
  const stackedToc = await toc.boundingBox();
  const stackedContent = await content.boundingBox();
  expect(stackedContent.y).toBeGreaterThan(stackedToc.y + stackedToc.height - 8);
  expect(stackedToc.width).toBeGreaterThan(stackedContent.width * 0.85);
  await page.setViewportSize({ width: 800, height: 600 });
});

test('menu: burger shows version + contact, no logout before login', async ({ page }) => {
  const burger = burgerMenu(page);
  await burger.locator('a.dropdown-toggle').click();
  await expect(burger).toHaveClass(/\bopen\b/);
  await expect(burger.getByText(/^Version: \S+/)).toBeVisible();
  await expect(burger.getByRole('link', { name: 'Contact', exact: true })).toBeVisible();
  await expect(burger.getByRole('link', { name: 'Logout', exact: true })).toHaveCount(0);

  await burger.locator('a.dropdown-toggle').click();
  await expect(burger).not.toHaveClass(/\bopen\b/);
});

test('menu: click outside closes burger and language menus', async ({ page }) => {
  const outside = page.locator('input.url');
  const burger = burgerMenu(page);
  const language = languageMenu(page);

  await burger.locator('a.dropdown-toggle').click();
  await expect(burger).toHaveClass(/\bopen\b/);
  await burger.locator('.burger-version').click();
  await expect(burger).toHaveClass(/\bopen\b/);
  await outside.click();
  await expect(burger).not.toHaveClass(/\bopen\b/);

  await language.locator('a.dropdown-toggle').click();
  await expect(language).toHaveClass(/\bopen\b/);
  await outside.click();
  await expect(language).not.toHaveClass(/\bopen\b/);
});

test('language: switch through all locales and back to EN', async ({ page }) => {
  const menu = languageMenu(page);
  const toggle = menu.locator('a.dropdown-toggle');
  const nav = mainNav(page);

  try {
    await expect(toggle.locator('.language-code')).toHaveText('EN');

    for (const loc of LOCALES) {
      await toggle.click();
      await expect(menu).toHaveClass(/\bopen\b/);
      await menu.locator('ul.dropdown-menu').getByRole('link', { name: loc.label, exact: true }).click();

      await expect(menu).not.toHaveClass(/\bopen\b/);
      await expect(toggle.locator('.language-code')).toHaveText(loc.label);
      await expect(page.locator('html')).toHaveAttribute('lang', loc.code);
      await expect(nav.getByRole('link', { name: loc.connection, exact: true })).toBeVisible();
      await expect(nav.getByRole('link', { name: loc.help, exact: true })).toBeVisible();
      await expect(nav.getByRole('link', { name: loc.export, exact: true })).toBeVisible();
      expect(await page.evaluate((k) => localStorage.getItem(k), LOCALE_KEY)).toBe(loc.code);
    }
  } finally {
    // Keep later tests (which match English texts) independent of this one.
    await page.evaluate((k) => localStorage.setItem(k, 'en'), LOCALE_KEY);
  }
});

test('menu: narrow width uses two rows', async ({ page }) => {
  const brand = page.locator('.navbar-header');
  const appName = page.getByRole('link', { name: 'Madek-Exporter', exact: true });
  const pages = page.locator('ul.navbar-pages');
  const burger = page.locator('li.burger-menu');
  const status = page.locator('li.connection-status');
  const language = page.locator('li.language-menu');

  await page.setViewportSize({ width: 900, height: 640 });
  const wideBrand = await brand.boundingBox();
  const widePages = await pages.boundingBox();
  const wideLanguage = await language.boundingBox();
  const wideBurger = await burger.boundingBox();
  expect(Math.abs(wideBrand.y - widePages.y)).toBeLessThan(20);
  await expect(appName).toBeVisible();
  expect(Math.abs(wideLanguage.y - wideBurger.y)).toBeLessThan(20);
  expect(wideBurger.x).toBeGreaterThan(wideLanguage.x);

  await page.setViewportSize({ width: 420, height: 640 });
  const help = pages.getByRole('link', { name: 'Help', exact: true });
  await expect.poll(async () => {
    const brandBox = await brand.boundingBox();
    const pagesBox = await pages.boundingBox();
    return pagesBox.y > brandBox.y + brandBox.height - 8;
  }).toBe(true);

  const boxes = {
    brand: await brand.boundingBox(),
    status: await status.boundingBox(),
    burger: await burger.boundingBox(),
    pages: await pages.boundingBox(),
    language: await language.boundingBox(),
    help: await help.boundingBox(),
  };
  expect(Math.abs(boxes.brand.y - boxes.burger.y)).toBeLessThan(24);
  expect(Math.abs(boxes.brand.y - boxes.language.y)).toBeLessThan(24);
  expect(Math.abs(boxes.brand.y - boxes.status.y)).toBeLessThan(24);
  expect(boxes.status.x).toBeGreaterThan(boxes.brand.x);
  expect(boxes.language.x).toBeGreaterThan(boxes.status.x);
  expect(boxes.burger.x).toBeGreaterThan(boxes.language.x);
  expect(boxes.pages.y).toBeGreaterThan(boxes.brand.y + boxes.brand.height - 8);
  await expect(appName).toBeVisible();
  expect(boxes.help.y).toBeGreaterThan(boxes.language.y);

  await page.setViewportSize({ width: 360, height: 640 });
  await expect(pages).toBeHidden();
  await expect(appName).toBeHidden();
  await expect(language).toBeVisible();
  await burger.locator('a.dropdown-toggle').click();
  const menu = burger.locator('ul.dropdown-menu');
  for (const name of ['Connection', 'Export', 'Debug', 'Help']) {
    await expect(menu.getByRole('link', { name, exact: true })).toBeVisible();
  }
  await burger.locator('a.dropdown-toggle').click();
  await page.setViewportSize({ width: 800, height: 600 });
});

test('language: choice survives a reload', async ({ page }) => {
  const menu = languageMenu(page);
  const toggle = menu.locator('a.dropdown-toggle');

  try {
    await toggle.click();
    await menu.locator('ul.dropdown-menu').getByRole('link', { name: 'DE', exact: true }).click();
    await expect(mainNav(page).getByRole('link', { name: 'Verbindungen', exact: true })).toBeVisible();

    // A plain reload hits the rewritten router path; restart from index.html.
    await page.goto(indexUrl);
    await page.waitForFunction(
      () => document.querySelector('input.url') != null,
      { timeout: 60_000 }
    );
    await expect(page.locator('html')).toHaveAttribute('lang', 'de');
    await expect(toggle.locator('.language-code')).toHaveText('DE');
    await expect(mainNav(page).getByRole('link', { name: 'Verbindungen', exact: true })).toBeVisible();
  } finally {
    await page.evaluate((k) => localStorage.setItem(k, 'en'), LOCALE_KEY);
  }
});

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
  return page.locator('nav.navbar ul.navbar-nav').first();
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
  await expect(page.getByText(/Electron main connected!/i)).toBeVisible();
  await expect(page.getByText(/JVM main connected!/i)).toBeVisible();
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

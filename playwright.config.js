// @ts-check
const path = require('path');

const FAKE_PORT = process.env.FAKE_MADEK_API_PORT || '9191';
const FAKE_TOKEN = process.env.FAKE_MADEK_API_TOKEN || 'test-token';

process.env.FAKE_MADEK_API_PORT = FAKE_PORT;
process.env.FAKE_MADEK_API_TOKEN = FAKE_TOKEN;
process.env.FAKE_MADEK_API_TEST_URL =
  process.env.FAKE_MADEK_API_TEST_URL || `http://127.0.0.1:${FAKE_PORT}`;
process.env.MADEK_E2E = process.env.MADEK_E2E || '1';

/** @type {import('@playwright/test').PlaywrightTestConfig} */
module.exports = {
  globalSetup: path.join(__dirname, 'tests/e2e/global-setup.js'),
  testDir: path.join(__dirname, 'tests/e2e'),
  timeout: 60_000,
  expect: { timeout: 5_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  outputDir: path.join(__dirname, 'test-results/playwright'),
  webServer: {
    command: `./bin/fake-madek-api`,
    url: `http://127.0.0.1:${FAKE_PORT}/api-v2/_export-stubs-ready`,
    env: {
      FAKE_MADEK_API_PORT: FAKE_PORT,
      FAKE_MADEK_API_TOKEN: FAKE_TOKEN,
    },
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
  use: {
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  // One project per scope; login only runs once the app shell itself works.
  projects: [
    { name: 'setup', testMatch: /setup\.spec\.js$/ },
    { name: 'login', testMatch: /login\.spec\.js$/, dependencies: ['setup'] },
    {
      name: 'export-from-api',
      testMatch: /export-from-api\.spec\.js$/,
      dependencies: ['setup'],
      timeout: 120_000,
    },
    {
      name: 'export-from-both-apis',
      testMatch: /export-from-both-apis\.spec\.js$/,
      dependencies: ['setup'],
      timeout: 120_000,
    },
  ],
};

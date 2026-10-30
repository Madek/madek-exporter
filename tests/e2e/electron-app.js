const fs = require('fs');
const net = require('net');
const path = require('path');
const { _electron: electron } = require('@playwright/test');

const repoRoot = path.join(__dirname, '../..');
const appDevDir = 'app/dev';
const userDataRoot = path.join(repoRoot, 'tmp/e2e-electron-user-data');
// The router rewrites the URL to e.g. file:///connection, so page.reload()
// would load a non-existent file; always go back to the real entry page.
const indexUrl = `file://${path.join(fs.realpathSync(repoRoot), appDevDir, 'index.html')}`;

/**
 * Dev Electron uses a fixed JVM port (8383) by default; a running dev app or a
 * leftover `lein run` JVM would otherwise answer the test UI.
 * @returns {Promise<number>}
 */
function freePort() {
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    srv.unref();
    srv.on('error', reject);
    srv.listen(0, '127.0.0.1', () => {
      const { port } = srv.address();
      srv.close(() => resolve(port));
    });
  });
}

/**
 * @param {number} port
 * @param {number} timeoutMs
 */
function waitForPort(port, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const tick = () => {
      const socket = net.connect(port, '127.0.0.1');
      socket.once('connect', () => {
        socket.destroy();
        resolve();
      });
      socket.once('error', () => {
        socket.destroy();
        if (Date.now() > deadline) {
          reject(new Error(`JVM did not listen on port ${port} within ${timeoutMs}ms`));
        } else {
          setTimeout(tick, 250);
        }
      });
    };
    tick();
  });
}

/**
 * @param {import('@playwright/test').Page} page
 */
function attachPageDiagnostics(page) {
  const logs = [];
  page.on('console', (msg) => {
    logs.push(`[${msg.type()}] ${msg.text()}`);
  });
  page.on('pageerror', (err) => {
    logs.push(`[pageerror] ${err.message}`);
  });
  return () => logs.slice(-40).join('\n');
}

/**
 * @returns {Promise<{app: import('@playwright/test').ElectronApplication, jvmPort: number}>}
 */
async function launchMadekExporter() {
  fs.mkdirSync(userDataRoot, { recursive: true });
  // Fresh profile per launch: no persisted locale / connection form state.
  const userDataDir = fs.mkdtempSync(path.join(userDataRoot, 'run-'));
  const jvmPort = await freePort();
  const electronPath = require('electron');
  const app = await electron.launch({
    executablePath: electronPath,
    // Match README: `npx electron app/dev` from repo root (main.js resolves ./app/dev/...).
    args: [
      appDevDir,
      `--user-data-dir=${userDataDir}`,
      '--lang=en-US',
      // CI containers have no usable setuid chrome-sandbox.
      ...(process.platform === 'linux' ? ['--no-sandbox'] : []),
    ],
    cwd: repoRoot,
    env: {
      ...process.env,
      MADEK_E2E: '1',
      MADEK_JVM_PORT: String(jvmPort),
      MADEK_USER_DATA_DIR: userDataDir,
      LANG: 'en_US.UTF-8',
      ELECTRON_DISABLE_SECURITY_WARNINGS: 'true',
    },
  });
  // The JVM cold-starts in the background; the UI can't reach it until then.
  await waitForPort(jvmPort, 150_000);
  return { app, jvmPort };
}

/**
 * `app.close()` can hang with the window still open; the JVM would then be
 * orphaned, so stop it explicitly and force-kill Electron after a grace period.
 * @param {import('@playwright/test').ElectronApplication} app
 * @param {number} jvmPort
 */
async function closeMadekExporter(app, jvmPort) {
  const proc = app.process();
  if (jvmPort) {
    await fetch(`http://127.0.0.1:${jvmPort}/shutdown`, { method: 'POST' }).catch(
      () => {}
    );
  }
  const exited = new Promise((resolve) => {
    if (proc.exitCode !== null || proc.signalCode !== null) resolve();
    else proc.once('exit', resolve);
  });
  // app.exit skips window close handling, which is what stalls app.close().
  app.evaluate(({ app: electronApp }) => electronApp.exit(0)).catch(() => {});
  await Promise.race([exited, new Promise((resolve) => setTimeout(resolve, 5_000))]);
  if (proc.exitCode === null && proc.signalCode === null) {
    proc.kill('SIGKILL');
  }
  await app.close().catch(() => {});
}

/**
 * Drop the JVM connection and reload the renderer so each test starts on a
 * clean login form.
 * @param {import('@playwright/test').Page} page
 * @param {number} jvmPort
 */
async function resetSession(page, jvmPort) {
  await fetch(`http://127.0.0.1:${jvmPort}/connect`, { method: 'DELETE' }).catch(
    () => {}
  );
  await page.goto(indexUrl);
  await page.waitForFunction(
    () => document.querySelector('input.url') != null,
    { timeout: 60_000 }
  );
  await gotoConnectionPage(page);
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {() => string} dumpLogs
 */
async function waitForFrontReady(page, dumpLogs) {
  try {
    await page.waitForFunction(
      () => document.querySelector('input.url') != null,
      { timeout: 90_000 }
    );
  } catch (err) {
    const appText = await page
      .locator('#app')
      .innerText()
      .catch(() => '(no #app)');
    throw new Error(
      `Front-end did not mount (still "${appText.trim().slice(0, 80)}").\n` +
        `Recent console:\n${dumpLogs()}`
    );
  }
  // Locale may come from the OS; tests assert English labels.
  await page.evaluate(() => {
    localStorage.setItem('madek-exporter-locale', 'en');
  });
  const isEnglish = await page
    .getByRole('link', { name: /^Connection$/i })
    .isVisible()
    .catch(() => false);
  if (!isEnglish) {
    await page.goto(indexUrl);
    await page.waitForFunction(
      () => document.querySelector('input.url') != null,
      { timeout: 90_000 }
    );
  }
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function gotoConnectionPage(page) {
  const connectionNav = page.getByRole('link', { name: /^Connection$/i });
  if (await connectionNav.isVisible().catch(() => false)) {
    await connectionNav.click();
  }
}

module.exports = {
  repoRoot,
  appDevDir: path.join(repoRoot, appDevDir),
  indexUrl,
  fakeBaseUrl: process.env.FAKE_MADEK_API_TEST_URL || 'http://127.0.0.1:9191',
  fakeToken: process.env.FAKE_MADEK_API_TOKEN || 'test-token',
  fakeLogin: 'fake-user',
  fakeEmail: 'fake-user@example.test',
  // Account password: Madek /api rejects it over Basic auth.
  fakePassword: 'fake-password',
  fakeApiClient: 'fake-api-client',
  fakeApiClientPassword: 'fake-api-client-password',
  launchMadekExporter,
  attachPageDiagnostics,
  waitForFrontReady,
  gotoConnectionPage,
  resetSession,
  closeMadekExporter,
};

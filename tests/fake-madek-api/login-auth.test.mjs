import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import http from 'node:http';
import { after, before, describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const serverPath = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  'server.mjs'
);
const TOKEN = process.env.FAKE_MADEK_API_TOKEN || 'test-token';
const LOGIN = 'fake-user';
const EMAIL = 'fake-user@example.test';
const PASSWORD = 'fake-password';
const API_CLIENT = 'fake-api-client';
const API_CLIENT_PASSWORD = 'fake-api-client-password';

const basic = (user, pw) =>
  `Basic ${Buffer.from(`${user}:${pw}`, 'utf8').toString('base64')}`;

let port;
let child;
let ownsServer = false;

function waitForUrl(url, timeoutMs = 10_000) {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const tick = () => {
      http
        .get(url, (res) => {
          res.resume();
          resolve();
        })
        .on('error', () => {
          if (Date.now() > deadline) reject(new Error(`timeout waiting for ${url}`));
          else setTimeout(tick, 50);
        });
    };
    tick();
  });
}

function waitForUrlWithAuth(url, timeoutMs, authorization) {
  const deadline = Date.now() + timeoutMs;
  const parsed = new URL(url);
  return new Promise((resolve, reject) => {
    const tick = () => {
      http
        .get(
          {
            hostname: parsed.hostname,
            port: parsed.port,
            path: parsed.pathname,
            headers: {
              Accept: 'application/json-roa+json',
              Authorization: authorization,
            },
          },
          (res) => {
            res.resume();
            if (res.statusCode === 200) resolve();
            else if (Date.now() > deadline) {
              reject(new Error(`timeout waiting for ${url} (${res.statusCode})`));
            } else setTimeout(tick, 50);
          }
        )
        .on('error', () => {
          if (Date.now() > deadline) reject(new Error(`timeout waiting for ${url}`));
          else setTimeout(tick, 50);
        });
    };
    tick();
  });
}

function get(pathname, authorization) {
  const headers = { Accept: 'application/json-roa+json' };
  if (authorization) headers.Authorization = authorization;
  return new Promise((resolve, reject) => {
    http
      .get(
        { hostname: '127.0.0.1', port, path: pathname, headers },
        (res) => {
          let body = '';
          res.on('data', (c) => {
            body += c;
          });
          res.on('end', () => resolve({ status: res.statusCode, body }));
        }
      )
      .on('error', reject);
  });
}

before(async () => {
  const external = process.env.FAKE_MADEK_API_TEST_URL;
  if (external) {
    port = new URL(external).port || 80;
    await waitForUrl(`${external.replace(/\/$/, '')}/api-v2/api-docs/openapi.json`);
    return;
  }
  ownsServer = true;
  port = 19191 + Math.floor(Math.random() * 1000);
  child = spawn(process.execPath, [serverPath], {
    env: {
      ...process.env,
      FAKE_MADEK_API_PORT: String(port),
      FAKE_MADEK_API_TOKEN: TOKEN,
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  await waitForUrl(`http://127.0.0.1:${port}/api-v2/api-docs/openapi.json`);
});

after(() => {
  if (ownsServer && child && !child.killed) child.kill('SIGTERM');
});

describe('fake-madek-api auth matrix', () => {
  it('v1 accepts valid token', async () => {
    const res = await get('/api/auth-info', `token ${TOKEN}`);
    assert.equal(res.status, 200);
    assert.match(res.body, /fake-user/);
  });

  it('v1 rejects invalid token', async () => {
    const res = await get('/api/auth-info', 'token bad');
    assert.equal(res.status, 401);
  });

  it('v1 Basic login/token ok', async () => {
    const res = await get('/api/auth-info', basic(LOGIN, TOKEN));
    assert.equal(res.status, 200);
    assert.equal(JSON.parse(res.body).type, 'User');
  });

  it('v1 Basic email/token ok', async () => {
    const res = await get('/api/auth-info', basic(EMAIL, TOKEN));
    assert.equal(res.status, 200);
    assert.equal(JSON.parse(res.body).login, LOGIN);
  });

  it('v1 Basic login/password rejected (401)', async () => {
    const res = await get('/api/auth-info', basic(LOGIN, PASSWORD));
    assert.equal(res.status, 401);
    assert.match(
      JSON.parse(res.body).message,
      /Neither User nor ApiClient exists for \{:login-or-email-address "fake-user"\}/
    );
  });

  it('v1 Basic email/password rejected (401)', async () => {
    const res = await get('/api/auth-info', basic(EMAIL, PASSWORD));
    assert.equal(res.status, 401);
    assert.match(
      JSON.parse(res.body).message,
      /Neither User nor ApiClient exists for \{:login-or-email-address "fake-user@example\.test"\}/
    );
  });

  it('v1 Basic api-client/api-client-password ok', async () => {
    const res = await get('/api/auth-info', basic(API_CLIENT, API_CLIENT_PASSWORD));
    assert.equal(res.status, 200);
    const body = JSON.parse(res.body);
    assert.equal(body.type, 'ApiClient');
    assert.equal(body.login, API_CLIENT);
  });

  it('v1 Basic api-client with wrong password rejected (401)', async () => {
    const res = await get('/api/auth-info', basic(API_CLIENT, 'wrong'));
    assert.equal(res.status, 401);
  });

  it('v2 accepts valid token', async () => {
    const res = await get('/api-v2/auth-info/', `token ${TOKEN}`);
    assert.equal(res.status, 200);
    assert.match(res.body, /fake-user/);
  });

  it('v2 rejects invalid token', async () => {
    const res = await get('/api-v2/auth-info/', 'token bad');
    assert.equal(res.status, 401);
  });

  it('v2 rejects Basic even when credentials are valid on v1', async () => {
    const res = await get('/api-v2/auth-info/', basic(LOGIN, TOKEN));
    assert.equal(res.status, 401);
  });

  it('both reject missing Authorization', async () => {
    assert.equal((await get('/api/auth-info')).status, 401);
    assert.equal((await get('/api-v2/auth-info/')).status, 401);
  });
});

describe('fake-madek-api v1-only (FAKE_MADEK_API_V2_DISABLED=1)', () => {
  let v1Port;
  let v1Child;

  before(async () => {
    v1Port = 20191 + Math.floor(Math.random() * 1000);
    v1Child = spawn(process.execPath, [serverPath], {
      env: {
        ...process.env,
        FAKE_MADEK_API_PORT: String(v1Port),
        FAKE_MADEK_API_TOKEN: TOKEN,
        FAKE_MADEK_API_V2_DISABLED: '1',
      },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    await waitForUrlWithAuth(
      `http://127.0.0.1:${v1Port}/api/auth-info`,
      10_000,
      `token ${TOKEN}`
    );
  });

  after(() => {
    if (v1Child && !v1Child.killed) v1Child.kill('SIGTERM');
  });

  function getV1(pathname, authorization) {
    const headers = { Accept: 'application/json-roa+json' };
    if (authorization) headers.Authorization = authorization;
    return new Promise((resolve, reject) => {
      http
        .get(
          { hostname: '127.0.0.1', port: v1Port, path: pathname, headers },
          (res) => {
            let body = '';
            res.on('data', (c) => {
              body += c;
            });
            res.on('end', () => resolve({ status: res.statusCode, body }));
          }
        )
        .on('error', reject);
    });
  }

  it('v1 still accepts token', async () => {
    const res = await getV1('/api/auth-info', `token ${TOKEN}`);
    assert.equal(res.status, 200);
  });

  it('v2 auth-info is not served (404)', async () => {
    const res = await getV1('/api-v2/auth-info/', `token ${TOKEN}`);
    assert.equal(res.status, 404);
  });
});

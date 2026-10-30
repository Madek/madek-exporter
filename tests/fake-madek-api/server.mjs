#!/usr/bin/env node
/**
 * Standalone stub for Madek `/api` (JSON-ROA) and `/api-v2` (REST JSON).
 *
 * Usage: node tests/fake-madek-api/server.mjs
 * Env:   FAKE_MADEK_API_PORT (default 9191)
 *        FAKE_MADEK_API_TOKEN (default test-token)
 */
import http from 'node:http';

const PORT = Number(
  process.env.FAKE_MADEK_API_PORT ?? process.env.FAKE_MADEK_PORT ?? 9191
);
const EXPECTED_TOKEN =
  process.env.FAKE_MADEK_API_TOKEN ?? process.env.FAKE_MADEK_TOKEN ?? 'test-token';
/** When `1`, v2 routes are absent (simulates Madek without /api-v2). */
const V2_DISABLED = process.env.FAKE_MADEK_API_V2_DISABLED === '1';
const BASE = `http://127.0.0.1:${PORT}`;
const LOG = '[fake-madek-api]';

const CT_JSON_ROA = 'application/json-roa+json; charset=utf-8';
const CT_JSON = 'application/json; charset=utf-8';

const FAKE_USER = {
  login: 'fake-user',
  email: 'fake-user@example.test',
  // Account password: valid for the web UI only, rejected by /api Basic auth.
  password: 'fake-password',
};
const FAKE_API_CLIENT = {
  login: 'fake-api-client',
  password: 'fake-api-client-password',
};

function userAuthInfo(method) {
  return {
    type: 'User',
    id: '00000000-0000-4000-8000-000000000001',
    login: FAKE_USER.login,
    email_address: FAKE_USER.email,
    created_at: '2020-01-01T00:00:00Z',
    'authentication-method': method,
  };
}

function apiClientAuthInfo() {
  return {
    type: 'ApiClient',
    id: '00000000-0000-4000-8000-000000000002',
    login: FAKE_API_CLIENT.login,
    created_at: '2020-01-01T00:00:00Z',
    'authentication-method': 'password',
  };
}

function authInfoFields() {
  return userAuthInfo('token');
}

function roaEnvelope(relations, data = {}) {
  return {
    ...data,
    '_json-roa': {
      version: '1.0.0',
      name: relations.name ?? 'Resource',
      relations: relations.relations ?? relations,
    },
  };
}

function apiV1Root() {
  return roaEnvelope({
    name: 'Root',
    relations: {
      'auth-info': {
        name: 'Authentication-Info',
        href: `${BASE}/api/auth-info`,
      },
      'media-entry': {
        name: 'Media-Entry',
        href: `${BASE}/api/media-entries/{id}`,
      },
    },
  });
}

function apiV1AuthInfo(identity) {
  return roaEnvelope(
    {
      name: 'Root',
      relations: {
        root: { name: 'Root', href: `${BASE}/api/` },
      },
    },
    identity
  );
}

function notFoundForBasic(loginOrEmail) {
  return `Neither User nor ApiClient exists for {:login-or-email-address "${loginOrEmail}"}`;
}

/**
 * v1 auth, mirroring Madek /api:
 * - `token <secret>`
 * - Basic <user login|email>:<api-token>
 * - Basic <api-client login>:<api-client password>
 * A user's account password is not accepted over Basic.
 * @returns {{ok: true, identity: object} | {ok: false, message: string}}
 */
function authenticateV1(req) {
  const h = req.headers.authorization;
  if (!h) return { ok: false, message: 'Authentication failed' };
  if (h.startsWith('token ')) {
    return h === `token ${EXPECTED_TOKEN}`
      ? { ok: true, identity: userAuthInfo('token') }
      : { ok: false, message: 'Authentication failed' };
  }
  if (h.startsWith('Basic ')) {
    const decoded = Buffer.from(h.slice(6), 'base64').toString('utf8');
    const sep = decoded.indexOf(':');
    const username = sep < 0 ? decoded : decoded.slice(0, sep);
    const password = sep < 0 ? '' : decoded.slice(sep + 1);
    const isUser = username === FAKE_USER.login || username === FAKE_USER.email;
    if (isUser && password === EXPECTED_TOKEN) {
      return { ok: true, identity: userAuthInfo('token') };
    }
    if (username === FAKE_API_CLIENT.login && password === FAKE_API_CLIENT.password) {
      return { ok: true, identity: apiClientAuthInfo() };
    }
    return { ok: false, message: notFoundForBasic(username) };
  }
  return { ok: false, message: 'Authentication failed' };
}

/** v2: token only (matches real api-v2; Basic is rejected) */
function authorizedV2(req) {
  const h = req.headers.authorization;
  return h === `token ${EXPECTED_TOKEN}`;
}

function sendJson(res, status, body, contentType = CT_JSON) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': contentType,
    'Content-Length': Buffer.byteLength(payload),
  });
  res.end(payload);
}

function notFound(res, path) {
  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end(`not found: ${path}`);
}

function handleApiV1(req, res, path) {
  if (req.method === 'GET' && (path === '/api' || path === '/api/')) {
    const auth = authenticateV1(req);
    if (!auth.ok) {
      sendJson(res, 401, { message: auth.message }, CT_JSON_ROA);
    } else {
      sendJson(res, 200, apiV1Root(), CT_JSON_ROA);
    }
    return true;
  }

  if (req.method === 'GET' && path === '/api/auth-info') {
    const auth = authenticateV1(req);
    if (!auth.ok) {
      sendJson(res, 401, { message: auth.message }, CT_JSON_ROA);
    } else {
      sendJson(res, 200, apiV1AuthInfo(auth.identity), CT_JSON_ROA);
    }
    return true;
  }

  return false;
}

function handleApiV2(req, res, path) {
  if (V2_DISABLED) return false;
  // Trailing slash is required on real Madek; accept both.
  if (
    req.method === 'GET' &&
    (path === '/api-v2/auth-info' || path === '/api-v2/auth-info/')
  ) {
    if (!authorizedV2(req)) {
      sendJson(res, 401, { message: 'Not authorized' });
    } else {
      sendJson(res, 200, authInfoFields());
    }
    return true;
  }

  if (req.method === 'GET' && path === '/api-v2/api-docs/openapi.json') {
    sendJson(res, 200, {
      openapi: '3.0.3',
      info: { title: 'Fake Madek API v2', version: '0.0.0' },
      paths: {
        '/auth-info/': {
          get: { summary: 'Authentication info (stub)' },
        },
      },
    });
    return true;
  }

  return false;
}

const server = http.createServer((req, res) => {
  const path = req.url?.split('?')[0] ?? '';
  console.log(`${LOG} ${req.method} ${path}`);

  if (handleApiV1(req, res, path)) return;
  if (handleApiV2(req, res, path)) return;

  notFound(res, path);
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`${LOG} listening on ${BASE}`);
  console.log(
    `${LOG} v1 /api/ + /api/auth-info — token, Basic <login|email>:<token>, ` +
      `Basic ${FAKE_API_CLIENT.login}:<api-client password>`
  );
  if (V2_DISABLED) {
    console.log(`${LOG} v2 disabled (FAKE_MADEK_API_V2_DISABLED=1)`);
  } else {
    console.log(`${LOG} v2 /api-v2/auth-info/ — token only (${EXPECTED_TOKEN})`);
  }
});

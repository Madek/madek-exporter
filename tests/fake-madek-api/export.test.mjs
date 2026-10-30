import assert from 'node:assert/strict';
import http from 'node:http';
import { describe, it } from 'node:test';
import {
  FAKE_MEDIA_ENTRY_ID,
  FAKE_MEDIA_ENTRY_TITLE,
  FAKE_SET_ID,
  FAKE_SET_MEDIA_ENTRY_IDS,
  exportPaths,
} from './export-fixtures.mjs';

const TOKEN = process.env.FAKE_MADEK_API_TOKEN || 'test-token';
const base =
  process.env.FAKE_MADEK_API_TEST_URL ||
  `http://127.0.0.1:${process.env.FAKE_MADEK_API_PORT || '9191'}`;

function get(pathname) {
  return new Promise((resolve, reject) => {
    http
      .get(
        `${base}${pathname}`,
        {
          headers: {
            Accept: 'application/json-roa+json',
            Authorization: `token ${TOKEN}`,
          },
        },
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

function getV2(pathname) {
  return new Promise((resolve, reject) => {
    http
      .get(
        `${base}${pathname}`,
        {
          headers: {
            Accept: 'application/json',
            Authorization: `token ${TOKEN}`,
          },
        },
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

describe('fake-madek-api export fixtures', () => {
  it('serves media entry and title meta-datum', async () => {
    const paths = exportPaths(base);
    const entry = await get(`/api/media-entries/${FAKE_MEDIA_ENTRY_ID}/`);
    assert.equal(entry.status, 200);

    const meta = await get(`/api/media-entries/${FAKE_MEDIA_ENTRY_ID}/meta-data/`);
    assert.equal(meta.status, 200);
    assert.match(meta.body, /00000000-0000-4000-8000-000000000012/);

    const md = await get(new URL(paths.metaDatumTitle).pathname);
    assert.equal(md.status, 200);
    const parsed = JSON.parse(md.body);
    assert.equal(parsed.value, FAKE_MEDIA_ENTRY_TITLE);
  });

  it('serves vocabularies and meta-keys for schema export', async () => {
    const voc = await get('/api/vocabularies/');
    assert.equal(voc.status, 200);
    const parsed = JSON.parse(voc.body);
    const memberHref =
      parsed['_json-roa'].collection.relations['0'].href;
    assert.match(memberHref, /\/api\/vocabularies\/madek_core\/$/);
    const vocabulary = await get(new URL(memberHref).pathname);
    assert.equal(vocabulary.status, 200);
    const metaKeysHref = JSON.parse(vocabulary.body)['_json-roa'].relations['meta-keys'].href;
    const keys = await get(new URL(metaKeysHref).pathname);
    assert.equal(keys.status, 200);
  });

  it('serves set with three child media entries', async () => {
    const collection = await get(`/api/collections/${FAKE_SET_ID}/`);
    assert.equal(collection.status, 200);
    const children = await get(
      `/api/media-entries/?collection_id=${FAKE_SET_ID}&me_get_full_size=true`
    );
    assert.equal(children.status, 200);
    for (const id of FAKE_SET_MEDIA_ENTRY_IDS) {
      assert.match(children.body, new RegExp(id));
    }
  });
});

describe('fake-madek-api v2 export fixtures', () => {
  it('serves media entry and meta-data-related for dual export', async () => {
    const entry = await getV2(`/api-v2/media-entries/${FAKE_MEDIA_ENTRY_ID}/`);
    assert.equal(entry.status, 200);

    const related = await getV2(
      `/api-v2/media-entries/${FAKE_MEDIA_ENTRY_ID}/meta-data-related/`
    );
    assert.equal(related.status, 200);
    const rows = JSON.parse(related.body);
    assert.ok(Array.isArray(rows));
    assert.equal(rows[0].meta_data.string, FAKE_MEDIA_ENTRY_TITLE);
  });

  it('serves paginated vocabularies and meta-keys', async () => {
    const voc = await getV2('/api-v2/vocabularies/?page=1&size=100');
    assert.equal(voc.status, 200);
    const keys = await getV2('/api-v2/meta-keys/?page=1&size=100');
    assert.equal(keys.status, 200);
    assert.match(keys.body, /madek_core:title/);
  });

  it('serves meta-keys list without pagination query', async () => {
    const keys = await getV2('/api-v2/meta-keys/');
    assert.equal(keys.status, 200);
    assert.match(keys.body, /madek_core:title/);
  });

  it('advertises export stub readiness', async () => {
    const res = await getV2('/api-v2/_export-stubs-ready');
    assert.equal(res.status, 200);
    const body = JSON.parse(res.body);
    assert.equal(body.ready, true);
    assert.ok(body.stubs.includes('meta-keys'));
  });
});

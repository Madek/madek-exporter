/**
 * Minimal /api-v2 JSON fixtures for dual export (api + api-v2).
 */
import {
  FAKE_MEDIA_ENTRIES,
  FAKE_MEDIA_ENTRY_ID,
  FAKE_MEDIA_ENTRY_TITLE,
  FAKE_SET_ID,
  FAKE_SET_TITLE,
} from './export-fixtures.mjs';

export function vocabularyMadekCoreV2() {
  return {
    id: 'madek_core',
    label: 'Madek Core',
    position: 0,
    labels: { en: 'Madek Core', de: 'Madek Core' },
  };
}

export function metaKeyTitleV2() {
  return {
    id: 'madek_core:title',
    vocabulary_id: 'madek_core',
    label: 'Title',
    position: 0,
    labels: { en: 'Title', de: 'Title' },
    meta_datum_object_type: 'MetaDatum::Text',
  };
}

/** @param {{ id: string, title: string }} entry */
export function mediaEntryV2(entry) {
  return {
    id: entry.id,
    get: { full: { size: 1 } },
  };
}

/** @param {{ id: string, title: string }} entry */
export function metaDataRelatedForMediaEntry(entry) {
  return [
    {
      meta_data: {
        id: entry.metaDatumTitleId,
        meta_key_id: 'madek_core:title',
        type: 'MetaDatum::Text',
        media_entry_id: entry.id,
        string: entry.title,
      },
    },
  ];
}

export function collectionV2() {
  return { id: FAKE_SET_ID };
}

export function metaDataRelatedForCollection() {
  return [
    {
      meta_data: {
        id: '00000000-0000-4000-8000-000000000023',
        meta_key_id: 'madek_core:title',
        type: 'MetaDatum::Text',
        collection_id: FAKE_SET_ID,
        string: FAKE_SET_TITLE,
      },
    },
  ];
}

/** @param {string} key @param {unknown[]} items @param {import('node:http').IncomingMessage} req */
export function listBody(key, items, req) {
  const url = new URL(req.url ?? '/', 'http://127.0.0.1');
  const page = url.searchParams.get('page');
  const size = url.searchParams.get('size');
  if (page != null || size != null) {
    return {
      data: items,
      pagination: { page: Number(page) || 1, size: Number(size) || items.length, total_pages: 1 },
    };
  }
  return { [key]: items };
}

export { FAKE_MEDIA_ENTRY_ID, FAKE_MEDIA_ENTRY_TITLE };

/**
 * Minimal JSON-ROA fixtures for Madek Exporter export (v1 /api).
 */

export const FAKE_MEDIA_FILE_BYTES = Buffer.from('fake-madek-media-file-bytes\n');

export const FAKE_SET_ID = '00000000-0000-4000-8000-000000000020';
export const FAKE_SET_TITLE = 'Fake export set';
export const FAKE_SET_META_DATUM_TITLE_ID = '00000000-0000-4000-8000-000000000023';

/** @type {readonly { id: string, title: string, metaDatumTitleId: string, mediaFileId: string, filename: string }[]} */
export const FAKE_MEDIA_ENTRIES = [
  {
    id: '00000000-0000-4000-8000-000000000010',
    title: 'Fake export entry',
    metaDatumTitleId: '00000000-0000-4000-8000-000000000012',
    mediaFileId: '00000000-0000-4000-8000-000000000011',
    filename: 'fake-export.bin',
  },
  {
    id: '00000000-0000-4000-8000-000000000021',
    title: 'Fake set entry 2',
    metaDatumTitleId: '00000000-0000-4000-8000-000000000024',
    mediaFileId: '00000000-0000-4000-8000-000000000025',
    filename: 'fake-set-entry-2.bin',
  },
  {
    id: '00000000-0000-4000-8000-000000000022',
    title: 'Fake set entry 3',
    metaDatumTitleId: '00000000-0000-4000-8000-000000000026',
    mediaFileId: '00000000-0000-4000-8000-000000000027',
    filename: 'fake-set-entry-3.bin',
  },
];

/** Media entries contained in {@link FAKE_SET_ID}. */
export const FAKE_SET_MEDIA_ENTRY_IDS = FAKE_MEDIA_ENTRIES.map((e) => e.id);

/** @deprecated use {@link FAKE_MEDIA_ENTRIES}[0] — kept for existing tests */
export const FAKE_MEDIA_ENTRY_ID = FAKE_MEDIA_ENTRIES[0].id;
export const FAKE_MEDIA_FILE_ID = FAKE_MEDIA_ENTRIES[0].mediaFileId;
export const FAKE_META_DATUM_TITLE_ID = FAKE_MEDIA_ENTRIES[0].metaDatumTitleId;
export const FAKE_MEDIA_ENTRY_TITLE = FAKE_MEDIA_ENTRIES[0].title;

/** @param {string} id */
export function findMediaEntry(id) {
  return FAKE_MEDIA_ENTRIES.find((e) => e.id === id);
}

/** @param {string} mediaFileId */
export function findMediaEntryByFileId(mediaFileId) {
  return FAKE_MEDIA_ENTRIES.find((e) => e.mediaFileId === mediaFileId);
}

/** @param {string} metaDatumId */
export function findMetaDatum(metaDatumId) {
  if (metaDatumId === FAKE_SET_META_DATUM_TITLE_ID) {
    return setTitleMetaDatum();
  }
  const entry = FAKE_MEDIA_ENTRIES.find((e) => e.metaDatumTitleId === metaDatumId);
  return entry ? titleMetaDatumForEntry(entry) : null;
}

/** @param {string} base @param {{ id: string }} entry */
export function mediaEntryPaths(base, entry) {
  const root = `${base}/api/media-entries/${entry.id}`;
  return {
    entry: root,
    entryMetaData: `${root}/meta-data/`,
    mediaFile: `${root}/media-file/`,
    mediaFileDataStream: `${base}/api/media-files/${entry.mediaFileId}/data-stream/`,
    metaDatumTitle: `${base}/api/meta-data/${entry.metaDatumTitleId}/`,
  };
}

/** @param {string} base */
export function exportPaths(base) {
  const ep = mediaEntryPaths(base, FAKE_MEDIA_ENTRIES[0]);
  return {
    ...ep,
    vocabularies: `${base}/api/vocabularies/`,
    vocabularyMadekCore: `${base}/api/vocabularies/madek_core/`,
    vocabularyMetaKeys: `${base}/api/vocabularies/madek_core/meta-keys/`,
    metaKeys: `${base}/api/meta-keys/`,
    metaKeyTitle: `${base}/api/meta-keys/madek_core:title/`,
  };
}

/** @param {string} base */
export function collectionPaths(base) {
  const root = `${base}/api/collections/${FAKE_SET_ID}`;
  return {
    collection: root,
    metaData: `${root}/meta-data/`,
    setMetaDatumTitle: `${base}/api/meta-data/${FAKE_SET_META_DATUM_TITLE_ID}/`,
  };
}

/** @param {string} base */
export function roaResource(name, relations, data = {}) {
  return {
    ...data,
    '_json-roa': {
      version: '1.0.0',
      name,
      relations,
    },
  };
}

/** @param {string} name @param {{ name: string, href: string }[]} members */
export function roaCollection(name, members) {
  /** @type {Record<string, { name: string, href: string }>} */
  const relations = {};
  members.forEach((member, idx) => {
    relations[String(idx)] = member;
  });
  return {
    '_json-roa': {
      version: '1.0.0',
      name,
      collection: { relations },
    },
  };
}

/** @param {string} base */
export function apiV1RootExportRelations(base) {
  return {
    'auth-info': {
      name: 'Authentication-Info',
      href: `${base}/api/auth-info`,
    },
    'media-entry': {
      name: 'Media-Entry',
      href: `${base}/api/media-entries/{id}`,
    },
    collection: {
      name: 'Collection',
      href: `${base}/api/collections/{id}`,
    },
    'media-entries': {
      name: 'Media-Entries',
      href: `${base}/api/media-entries/{?collection_id,me_get_full_size,public_get_full_size}`,
    },
    vocabularies: {
      name: 'Vocabularies',
      href: `${base}/api/vocabularies/`,
    },
    'meta-keys': {
      name: 'Meta-Keys',
      href: `${base}/api/meta-keys/`,
    },
  };
}

/** @param {string} base @param {{ id: string, title: string, metaDatumTitleId: string, mediaFileId: string }} entry */
export function mediaEntryResource(base, entry) {
  const p = mediaEntryPaths(base, entry);
  return roaResource(
    'Media-Entry',
    {
      'meta-data': { name: 'Meta-Data', href: p.entryMetaData },
      'media-file': { name: 'Media-File', href: p.mediaFile },
    },
    { id: entry.id, created_at: '2020-01-01T00:00:00Z' }
  );
}

/** @param {string} base @param {{ metaDatumTitleId: string }} entry */
export function metaDataCollectionForEntry(base, entry) {
  const p = mediaEntryPaths(base, entry);
  return roaCollection('Meta-Data', [{ name: 'MetaDatum', href: p.metaDatumTitle }]);
}

/** @param {{ title: string, metaDatumTitleId: string }} entry */
export function titleMetaDatumForEntry(entry) {
  return roaResource(
    'MetaDatum',
    {},
    {
      id: entry.metaDatumTitleId,
      type: 'MetaDatum::Text',
      meta_key_id: 'madek_core:title',
      value: entry.title,
    }
  );
}

export function setTitleMetaDatum() {
  return roaResource(
    'MetaDatum',
    {},
    {
      id: FAKE_SET_META_DATUM_TITLE_ID,
      type: 'MetaDatum::Text',
      meta_key_id: 'madek_core:title',
      value: FAKE_SET_TITLE,
    }
  );
}

/** @param {string} base @param {{ mediaFileId: string, filename: string }} entry */
export function mediaFileResource(base, entry) {
  const p = mediaEntryPaths(base, entry);
  return {
    id: entry.mediaFileId,
    filename: entry.filename,
    content_type: 'application/octet-stream',
    size: FAKE_MEDIA_FILE_BYTES.length,
    '_json-roa': {
      version: '1.0.0',
      name: 'Media-File',
      relations: {
        'data-stream': { name: 'Data-Stream', href: p.mediaFileDataStream },
      },
      collection: { relations: {} },
    },
  };
}

/** @param {string} base */
export function collectionResource(base) {
  const p = collectionPaths(base);
  return roaResource(
    'Collection',
    {
      'meta-data': { name: 'Meta-Data', href: p.metaData },
      collections: {
        name: 'Collections',
        href: `${p.collection}/collections/`,
      },
    },
    { id: FAKE_SET_ID, created_at: '2020-01-01T00:00:00Z' }
  );
}

/** @param {string} base */
export function collectionMetaDataCollection(base) {
  const p = collectionPaths(base);
  return roaCollection('Meta-Data', [{ name: 'MetaDatum', href: p.setMetaDatumTitle }]);
}

/** @param {string} base */
export function setMediaEntriesCollection(base) {
  return roaCollection(
    'Media-Entries',
    FAKE_MEDIA_ENTRIES.map((entry) => ({
      name: 'Media-Entry',
      href: `${base}/api/media-entries/${entry.id}/`,
    }))
  );
}

/** @param {string} base */
export function emptyChildCollections(base) {
  return roaCollection('Collections', []);
}

/** @param {string} base — backward-compatible aliases */
export function metaDataCollection(base) {
  return metaDataCollectionForEntry(base, FAKE_MEDIA_ENTRIES[0]);
}

export function titleMetaDatum(base) {
  return titleMetaDatumForEntry(FAKE_MEDIA_ENTRIES[0]);
}

/** @param {string} base */
export function vocabulariesCollection(base) {
  const p = exportPaths(base);
  return roaCollection('Vocabularies', [{ name: 'Vocabulary', href: p.vocabularyMadekCore }]);
}

/** @param {string} base */
export function vocabularyMadekCore(base) {
  const p = exportPaths(base);
  return roaResource(
    'Vocabulary',
    { 'meta-keys': { name: 'Meta-Keys', href: p.vocabularyMetaKeys } },
    { id: 'madek_core', label: 'Madek Core', position: 0 }
  );
}

/** @param {string} base */
export function vocabularyMetaKeysCollection(base) {
  const p = exportPaths(base);
  return roaCollection('Meta-Keys', [{ name: 'Meta-Key', href: p.metaKeyTitle }]);
}

/** @param {string} base */
export function metaKeysCollection(base) {
  const p = exportPaths(base);
  return roaCollection('Meta-Keys', [{ name: 'Meta-Key', href: p.metaKeyTitle }]);
}

/** @param {string} base */
export function metaKeyTitle(base) {
  return roaResource(
    'Meta-Key',
    {},
    {
      id: 'madek_core:title',
      label: 'Title',
      meta_datum_object_type: 'MetaDatum::Text',
      position: 0,
    }
  );
}

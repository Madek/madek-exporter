const fs = require('fs');
const path = require('path');
const {
  test,
  expect,
  exportFakeMediaEntry,
  exportFakeSet,
  expectExportDownloadLogCount,
} = require('./fixtures');
const { fakeMediaEntryTitle, fakeExportLogCounts } = require('./electron-app');

test.describe.configure({ timeout: 120_000 });

test('login and export media-entry metadata via fake API', async ({ page }) => {
  const { targetDir, entryId } = await exportFakeMediaEntry(page, {
    skipMediaFiles: true,
  });

  const metaRaw = fs.readFileSync(path.join(targetDir, entryId, 'meta-data.json'), 'utf8');
  const meta = JSON.parse(metaRaw);
  expect(meta.some((m) => m.meta_key_id === 'madek_core:title' && m.value === fakeMediaEntryTitle)).toBe(
    true
  );
  expect(fs.existsSync(path.join(targetDir, 'meta-data_schema.json'))).toBe(true);

  await expectExportDownloadLogCount(page, {
    count: fakeExportLogCounts.mediaEntryMetadata,
  });
});

test('login and export media-entry including media file', async ({ page }) => {
  const { targetDir, entryId } = await exportFakeMediaEntry(page, {
    skipMediaFiles: false,
  });

  const mediaFilePath = path.join(
    targetDir,
    entryId,
    'media-files',
    '00000000-0000-4000-8000-000000000011',
    'fake-export.bin'
  );
  expect(fs.existsSync(mediaFilePath)).toBe(true);
  expect(fs.readFileSync(mediaFilePath, 'utf8')).toContain('fake-madek-media-file-bytes');
  await expectExportDownloadLogCount(page, {
    count: fakeExportLogCounts.mediaEntryWithFile,
  });
});

test('login and export set with three media entries (metadata)', async ({ page }) => {
  const { targetDir, setId, entryIds } = await exportFakeSet(page, {
    skipMediaFiles: true,
  });

  const setMeta = JSON.parse(
    fs.readFileSync(path.join(targetDir, setId, 'meta-data.json'), 'utf8')
  );
  expect(
    setMeta.some((m) => m.meta_key_id === 'madek_core:title' && m.value === 'Fake export set')
  ).toBe(true);

  expect(entryIds).toHaveLength(3);
  for (const entryId of entryIds) {
    const entryMeta = JSON.parse(
      fs.readFileSync(path.join(targetDir, setId, entryId, 'meta-data.json'), 'utf8')
    );
    expect(entryMeta.some((m) => m.meta_key_id === 'madek_core:title')).toBe(true);
  }
  await expectExportDownloadLogCount(page, { count: fakeExportLogCounts.setMetadata });
});

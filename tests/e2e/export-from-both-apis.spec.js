const fs = require('fs');
const path = require('path');
const { test, expect, exportFakeMediaEntry, expectExportDownloadLogCount } = require('./fixtures');
const { fakeExportLogCounts } = require('./electron-app');

test.describe.configure({ timeout: 120_000 });

test('login and export media-entry with both api and api-v2', async ({ page }) => {
  const { targetDir, entryId } = await exportFakeMediaEntry(page, {
    skipMediaFiles: true,
    apiMode: 'both',
  });

  expect(fs.existsSync(path.join(targetDir, 'api', entryId, 'meta-data.json'))).toBe(true);
  expect(fs.existsSync(path.join(targetDir, 'api-v2', entryId, 'meta-data.json'))).toBe(true);
  expect(fs.existsSync(path.join(targetDir, 'api', 'meta-data_schema.json'))).toBe(true);
  expect(fs.existsSync(path.join(targetDir, 'api-v2', 'meta-data_schema.json'))).toBe(true);

  await expectExportDownloadLogCount(page, {
    count: fakeExportLogCounts.mediaEntryBothApis,
  });
});

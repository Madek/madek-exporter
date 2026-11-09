/**
 * contextBridge API exposed as window.madekBridge (see app/prod/preload.js).
 * Kept separate so `lein externs` can overwrite front_externs.js safely.
 */
var madekBridge = {
  chooseDirectory: function () {},
  openExternal: function () {},
  openPath: function () {},
  onJvmSyncInit: function () {},
  onNavigate: function () {},
  onDbFull: function () {},
  onDbPatch: function () {},
  getVersions: function () {},
  platform: function () {}
};

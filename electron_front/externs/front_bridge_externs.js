/**
 * contextBridge API exposed as window.madek (see app/prod/preload.js).
 * Kept separate so `lein externs` can overwrite front_externs.js safely.
 */
var madek = {
  chooseDirectory: function () {},
  openExternal: function () {},
  openPath: function () {},
  onJvmSyncInit: function () {},
  onNavigate: function () {},
  onDbFull: function () {},
  onDbPatch: function () {},
  getVersions: function () {}
};

'use strict';

/**
 * Minimal bridge for the renderer. Keep nodeIntegration off and expose only
 * the IPC/shell helpers the ClojureScript UI needs.
 *
 * IMPORTANT: do not expose as `window.madek` — that collides with the CLJS
 * namespace root `madek.app.front.*` and breaks goog.provide under :none.
 */
const { contextBridge, ipcRenderer } = require('electron');

function on(channel, handler) {
  const listener = (_event, ...args) => handler(...args);
  ipcRenderer.on(channel, listener);
  return () => ipcRenderer.removeListener(channel, listener);
}

// CLJS `:none` bootstraps `process.env` (assigns NODE_ENV). With
// nodeIntegration off there is no Node `process` in the page — provide a
// tiny mutable stub so goog.require("process.env") does not throw.
contextBridge.exposeInMainWorld('process', {
  env: {
    NODE_ENV: process.env.NODE_ENV || 'development',
    MADEK_E2E: process.env.MADEK_E2E || '',
  },
  platform: process.platform,
});

contextBridge.exposeInMainWorld('madekBridge', {
  chooseDirectory(defaultPath) {
    return ipcRenderer.invoke('madek:choose-directory', defaultPath || '');
  },
  // `shell` is unavailable in sandboxed preloads (prod); main validates and opens.
  openExternal(url) {
    return ipcRenderer.invoke('madek:open-external', url);
  },
  openPath(dir) {
    return ipcRenderer.invoke('madek:open-path', dir);
  },
  onJvmSyncInit(handler) {
    return on('madek:jvm-sync:init', handler);
  },
  onNavigate(handler) {
    return on('madek:navigate', handler);
  },
  onDbFull(handler) {
    return on('madek:db:full', handler);
  },
  onDbPatch(handler) {
    return on('madek:db:patch', handler);
  },
  getVersions() {
    return {
      electron: process.versions.electron,
      chrome: process.versions.chrome,
      node: process.versions.node,
    };
  },
  platform() {
    return process.platform;
  },
});

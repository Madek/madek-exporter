'use strict';

/**
 * Minimal bridge for the renderer. Keep nodeIntegration off and expose only
 * the IPC/shell helpers the ClojureScript UI needs.
 */
const { contextBridge, ipcRenderer, shell } = require('electron');

function on(channel, handler) {
  const listener = (_event, ...args) => handler(...args);
  ipcRenderer.on(channel, listener);
  return () => ipcRenderer.removeListener(channel, listener);
}

contextBridge.exposeInMainWorld('madek', {
  chooseDirectory(defaultPath) {
    return ipcRenderer.invoke('madek:choose-directory', defaultPath || '');
  },
  openExternal(url) {
    if (typeof url === 'string' && /^(https?:|mailto:)/i.test(url)) {
      return shell.openExternal(url);
    }
    return Promise.reject(new Error('Blocked non-http(s) openExternal'));
  },
  openPath(dir) {
    if (typeof dir === 'string' && dir.length > 0) {
      return shell.openPath(dir);
    }
    return Promise.reject(new Error('Blocked empty openPath'));
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
      versions: { ...process.versions },
    };
  },
});

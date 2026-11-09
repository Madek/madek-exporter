# Madek Exporter — Production Readiness

Audit date: 2026-11-09  
Remediation applied: 2026-11-09  
Scope: JVM backend, Electron main/renderer, packaging, CI, tests, deps hygiene.

## Verdict

Core production-hardening items from the audit are implemented on this branch:

- Local JVM HTTP Basic Auth is **enabled** and wired from Electron + e2e helpers
- Renderer uses **preload + contextIsolation** (`nodeIntegration` off)
- Madek credentials are **redacted** from Sente payloads / Debug dumps; form secrets cleared after connect
- Export **filenames** and **target directories** are validated; `/open` URI allowlisted
- Prod builds strip Closure `out_*` / source maps; DevTools menu gated to `:dev`
- Hygiene: license metadata, no `FIX.md` token, `scratch.clj` removed, `debug-ns` off

Remaining follow-ups (not blocking for local/ZHdK distribution):

- Full cljs/reagent/Bootstrap upgrade (EOL stack)
- Automated macOS notarization
- Optional split of large `web.clj` modules

Threat model note: the JVM binds to **localhost** by default; Basic Auth protects against other local processes.

---

## Prod-ready checklist

- [x] Localhost JVM API rejects unauthenticated mutating requests  
- [x] Madek tokens/passwords never appear in Sente payloads, Debug UI, or client logs  
- [x] Renderer: `contextIsolation: true`, `nodeIntegration: false`, preload-only IPC  
- [x] Export paths validated; Madek filenames cannot escape target tree  
- [x] Prod builds omit source maps and Closure `out_*` dirs  
- [x] Prod menu has no DevTools/Reload (or equivalent lockdown)  
- [x] CI smoke against advanced / packaged app (opt-in: `npm run test:e2e:prod`)  
- [x] `package.json` license + repository metadata match GPL / real repo  
- [x] No secrets or scratch tokens in tracked files (`FIX.md`)  
- [x] `(debug/debug-ns *ns*)` off in shipped JVM code  

---

## Suggested verification

```zsh
./bin/with-java21 lein with-profile test test :only madek.exporter.http-auth-test
./bin/build-electron-dev
npm run test:login
npm run test:e2e
# optional advanced-build smoke:
npm run build:electron:prod && npm run test:e2e:prod
```

---

## Code map (quick)

| Concern | Primary locations |
|---------|-------------------|
| Local HTTP + auth | `jvm_main/.../server/http_server.clj`, `server/web.clj` |
| Client-safe DB | `jvm_main/.../state.clj`, `utils.clj` (`client-safe-db`) |
| Path / filename safety | `utils.clj`, `export/files.clj`, `export/api_v2.clj` |
| Electron preload | `app/prod/preload.js`, `electron_front/.../bridge.cljs` |
| Window prefs / menu | `electron_main/.../windows.cljs`, `menu.cljs` |
| Packaging / validate | `bin/build-electron-*-prod`, `bin/validate-build-artifacts` |
| Auth / path tests | `jvm_main/test/.../http_auth_test.clj` |

---

## Related docs

- `README.md` — setup, test, release, notarization note  
- `PERFORMANCE-VIEW.md` — step-2 prefix-options performance  
- `cider-ci.yml` — CI jobs (security audit is now blocking on findings)

# Madek Exporter — Prod readiness V2

Date: 2026-11-09 (updated after open-task implementation)  
Scope: README accuracy, cleanliness, production readiness on `mr/tests-apiv2`.

## Verdict

| Area | Status |
|------|--------|
| Core app (export / login / packaging) | Prod-capable for ZHdK internal distribution |
| Security hardening | Done for localhost control plane |
| README | Updated (version, setup, tests, API modes) |
| Code cleanliness | Dead root clj leftovers removed; prefix-options split |
| Public Gatekeeper / long-term stack | Not fully prod-ready (T8 / full T9) |

---

## Still to do — overview

| ID | Priority | Area | Item | Status |
|----|----------|------|------|--------|
| T1 | P1 | CI / tests | Wire `test:e2e:prod` into Cider-CI | Done — `playwright-prod` → `cider-ci/test-e2e-prod.yml` |
| T2 | P1 | CI / tests | Run `madek.exporter.http-auth-test` from `bin/test-login` / CI | Done |
| T3 | P1 | Security / UI | Gate or remove `/debug` route in prod builds (nav already hidden) | Done — route registered only when `env/env :dev` |
| T4 | P2 | Cleanliness | Replace remaining `thrown/stringify` client body in `server/web.clj` download handler | Done — `client-error-body` |
| T5 | P2 | Cleanliness | Remove or relocate dead root `madek/exporter/utils.clj` + `clj_http/client.clj` | Done |
| T6 | P2 | Metadata | Align `package.json` `version` / `description` with `releases.yml` | Done — `0.11.2-2` (+ `project.clj`) |
| T7 | P3 | Security | Avoid sending JVM password to renderer over IPC | Done — main `webRequest` injects Basic Auth; IPC sends port only |
| T8 | P3 | Stack | Upgrade ClojureScript / Reagent / Bootstrap (EOL front stack) | Deferred |
| T9 | P3 | Release | Automate macOS codesign + notarization | Partial — `bin/notarize-mac` helper (manual credentials) |
| T10 | P3 | Maintainability | Optional split of large `web.clj` | Done — `server/prefix_options.clj` |

### Done recently (no action)

| ID | Area | Item | Status |
|----|------|------|--------|
| D1 | Security | Localhost JVM Basic Auth + Electron/e2e wiring | Done |
| D2 | Security | Madek secrets redacted from Sente; form secrets cleared after connect | Done |
| D3 | Security | Electron preload + `contextIsolation` / no `nodeIntegration` | Done |
| D4 | Security | Target-dir validation + safe media filenames; `/open` allowlist | Done |
| D5 | Packaging | Prod strips maps/`out_*`; validator asserts | Done |
| D6 | UX | Prod menu without DevTools/Reload | Done |
| D7 | Hygiene | GPL metadata; no `FIX.md` / scratch; `debug-ns` off | Done |
| D8 | CI | Security audit job fails on findings | Done |
| D9 | Docs | README: version source, `bundle install`, `./bin/lein`, API modes, `test:e2e:prod`, `audit:security`, http-auth test | Done |

---

## README fixes applied

- Documented **API modes** (`api` / `api-v2` / `both`)
- Clarified **version** = first entry in `releases.yml` (not root `package.json`)
- Setup: **`bundle install`**, mise tools, **`./bin/lein`**
- Testing: **`test:e2e:prod`**, **`audit:security`**, **`http-auth-test`**
- Note that Electron starts JVM with local Basic Auth

One-click (`tmp/dev-madek-exporter`) left as-is (local workflow).

---

## Suggested verification

```zsh
./bin/with-java21 lein with-profile test test :only madek.exporter.http-auth-test
./bin/build-electron-dev
npm run test:login
npm run test:e2e
npm run audit:security
npm run build:electron:prod && npm run build:jvm:prod && npm run test:e2e:prod
```

---

## Related

- `PROD-READY.md` — earlier hardening checklist  
- `PERFORMANCE-VIEW.md` — step-2 prefix-options performance  
- `README.md` — operator docs

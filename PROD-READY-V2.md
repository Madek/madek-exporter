# Madek Exporter — Prod readiness V2

Date: 2026-11-09 (fresh audit + remediation)  
Scope: README accuracy, cleanliness, production readiness on `mr/tests-apiv2` @ `0.11.2-3`.

## Verdict

| Audience | Status |
|----------|--------|
| ZHdK internal Electron distribution | **Prod-ready** after today’s fixes |
| Public macOS Gatekeeper | Not ready (unsigned; notarization manual) |
| Long-term stack (cljs / reagent / Bootstrap) | Deferred (EOL front stack) |

| Area | Status |
|------|--------|
| Core app (export / login / packaging) | Prod-capable for ZHdK internal distribution |
| Security hardening | Localhost control plane OK; secrets no longer stored in client-db requests |
| Packaging strip (`out_*` / maps) | Fixed in `bin/build-electron-prod` + Cider-CI cljs steps |
| README | Corrected prod e2e / audit notes |
| Public Gatekeeper / long-term stack | Not fully prod-ready (T8 / full T9) |

---

## Still to do — overview

| ID | Priority | Area | Item | Status |
|----|----------|------|------|--------|
| T1 | P1 | CI / tests | Wire `test:e2e:prod` into Cider-CI | Done — `playwright-prod` |
| T2 | P1 | CI / tests | Run `madek.exporter.http-auth-test` from `bin/test-login` / CI | Done |
| T3 | P1 | Security / UI | Gate `/debug` route in prod builds | Done — front route `:dev` only |
| T4 | P2 | Cleanliness | Replace `thrown/stringify` in download handler | Done — `client-error-body` |
| T5 | P2 | Cleanliness | Remove dead root leftovers | Done |
| T6 | P2 | Metadata | Align versions with `releases.yml` | Done — `0.11.2-3` |
| T7 | P3 | Security | JVM password not sent to renderer | Done — webRequest inject |
| T8 | P3 | Stack | Upgrade ClojureScript / Reagent / Bootstrap | Deferred |
| T9 | P3 | Release | Automate macOS codesign + notarization | Partial — `bin/notarize-mac` |
| T10 | P3 | Maintainability | Split large `web.clj` | Done — `prefix_options.clj` |
| T11 | P1 | Security | Do not store Madek secrets in client-db `:requests` | Done — store redacted copy |
| T12 | P1 | Packaging | Strip `out_*` / maps on all prod cljs paths | Done — `build-electron-prod` + CI |
| T13 | P2 | Security | Gate JVM `/debug/performance` | Done — `MADEK_EXPORTER_DEBUG=1` |
| T14 | P2 | Security | Harden `openPath` + target-dir prefix blocklist | Done |

---

## Suggested verification

```zsh
./bin/with-java21 lein with-profile test test :only madek.exporter.http-auth-test
./bin/build-electron-dev
npm run test:login
npm run test:e2e
npm run audit:security
npm run build:electron:prod && npm run build:jvm:prod && npm run check:artifacts
npm run test:e2e:prod
```

---

## Related

- `PROD-READY.md` — earlier hardening checklist  
- `PERFORMANCE-VIEW.md` — step-2 prefix-options performance  
- `README.md` — operator docs

# Performance — Schritt 2 (View): Präfix-Optionen

The “Verzeichnisse & Files mit Präfix versehen” dropdown loads **all text meta-keys** from Madek, joined with values on the step-1 entity. Which Madek API is used follows export **API mode** (UI sends `?api-mode=`):

| Export mode | Prefix fetch |
|-------------|--------------|
| `api`       | `/api` (ROA) |
| `api-v2`    | `/api-v2` (REST) |
| `both`      | `/api` (ROA) |

For `/api`, member GETs run **in parallel** (up to 6) over the shared HTTP connection pool. For `/api-v2`, catalog lists are paginated plain JSON.

## Verdict

| Layer | Async? | Notes |
|-------|--------|-------|
| Electron UI → local JVM | **Yes** | One non-blocking XHR; spinner while waiting |
| JVM → Madek | **Partly** | Handler waits for the full walk; ROA member GETs are parallel |
| Parallelism (ROA) | **Up to 6** | `parallel-mapv` for vocabularies, meta-data, meta-keys |
| Connection reuse | **Yes** | `state/connection-http-options` + shared pool |
| Scope | **Full text meta-key catalog** | Empty-value keys included (`_{id}` folder preview) |
| API used | **Follows export mode** | `api-v2` → `/api-v2`; `api` / `both` → `/api` |

## UI flow

```http
GET http://localhost:<jvm-port>/download/prefix-options
```

Cached per entity until Refresh. Leaving step 2 aborts the Madek walk.

## JVM walk (`/api`)

1. API root  
2. All **vocabularies** (list + parallel member GETs)  
3. Step-1 **entity**  
4. Entity **meta-data** (list + parallel member GETs) → text values map  
5. All **meta-keys** (list + parallel member GETs) → keep `MetaDatum::Text`, join values, folder names  

## JVM walk (`/api-v2`)

1. Paginated **vocabularies**  
2. Entity **meta-data** list (`:string` for text)  
3. Paginated **meta-keys** → keep `MetaDatum::Text`, join values, folder names  

## Approx. Madek call count (`/api`)

```text
~4 + V + M + K  (+ paging)
```

With concurrency 6 and keep-alive, wall time is roughly those calls / 6 plus list/page latency — not the sum of every sequential round-trip.

## Debug

Debug → **Performance** lists each Madek / local request with duration (ms). Filter to “Madek API” while loading step 2 to verify parallel catalog fetch cost.

## Code map

| Piece | Location |
|-------|----------|
| Step 2 UI | `electron_front/.../download/step2.cljs` |
| Walk + parallel | `jvm_main/.../server/web.clj` (`prefix-options`, `roa-collection-data`, `prefix-options-via-api-v2`) |
| Timings store | `jvm_main/.../perf_log.clj` |
| Debug tab | `electron_front/.../debug.cljs` |

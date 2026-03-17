# ExtJS Bundling to `app.js` via Maven + Sencha Cmd

## Summary
Introduce a deterministic Sencha-based frontend compile step in Maven to generate bundled JS for both entrypoints:
- `buddilive/app.js` for authenticated app
- `authentication/app/app.js` for login/auth flow

Default runtime will load minified bundles for faster startup; unminified debug bundles will also be produced for troubleshooting.

## Implementation Changes
- Add a Maven-driven UI build step (new execution in build lifecycle) that:
  1. Resolves `extjs.sdk.dir` from `-Dextjs.sdk.dir=...` (or env-backed property).
  2. Fails fast with a clear error if missing.
  3. Runs Sencha compile with classpaths: ExtJS SDK source + `src/main/webapp/buddilive` + `src/main/resources/.../resource/ui/extjs`.
  4. Produces unminified bundles first, then minifies with `sencha fs minify`.
- Generate these artifacts into `target/generated-webapp` so source files stay unchanged:
  - `buddilive/app-debug.js` and `buddilive/app.js`
  - `authentication/app/app-debug.js` and `authentication/app/app.js`
- Update template loading in `src/main/webapp/index.html` to reference bundled outputs:
  - Logged-in branch: `buddilive/app.js` instead of `buddilive/Application.js`
  - Logged-out branch: `authentication/app/app.js` instead of `authentication/app/Application.js`
- Keep `Ext.Loader` enabled in both apps for compatibility with runtime-injected auth extensions; bundle will include all current static classes and current configured cross-app auth extras.
- Add a small build note in docs describing:
  - required `extjs.sdk.dir`
  - local command examples
  - where debug bundles are emitted

## Public Interfaces / Build Contract Changes
- New build input: Maven property `extjs.sdk.dir` (required for bundle generation).
- New generated frontend artifacts expected at runtime:
  - `buddilive/app.js`
  - `authentication/app/app.js`
- Existing entrypoint script filenames (`Application.js`) are no longer referenced by `index.html` for normal page load.

## Test Plan
- Build verification:
  - Run `mvn -Dextjs.sdk.dir=<path> -DskipTests package` and confirm both bundle files are present in WAR output.
- Runtime verification (manual):
  - Logged-out page loads and authentication panels render.
  - Logged-in app boots fully (viewport, controllers, reports, forms).
  - Confirm JS request count drops (no per-class fetch burst from `buddilive/*` and `authentication/*` on initial load).
- Regression checks:
  - Auth flow still supports injected extra controllers/views/models via `__authConfig`.
  - Core screens (accounts, budget, transactions, reports, preferences) initialize without missing-class errors.

## Assumptions
- ExtJS SDK source is available externally and provided through `extjs.sdk.dir` in CI/local builds.
- Default runtime uses minified bundles; `*-debug.js` exists for manual debugging but is not auto-selected by template logic.
- No broader frontend framework migration is included; this is a minimal build-process enhancement only.

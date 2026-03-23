# E2E Testing Guide

## Running Tests

```bash
mvn verify -Pe2etest
```

The `e2etest` Maven profile starts an embedded Spring Boot server with a Derby database, runs all integration tests, and shuts down.

## Architecture

- **BaseIT** — manages Spring Boot server lifecycle (start once before all tests, stop after)
- **TestHelper** — HTTP helpers (OkHttp), cookie-based auth, JDBC email bypass for registration
- **BrowserBaseIT** — Selenium Chrome headless base with ExtJS component interaction helpers
- API tests live in `src/test/java/.../e2e/api/`
- Browser tests live in `src/test/java/.../e2e/ui/`

## Key Lessons and Pitfalls

### Derby Compatibility

- **Y/N CHAR(1) boolean mapping**: Derby's `ResultSet.getBoolean()` only handles `"true"/"false"/"1"/"0"`, not `"Y"/"N"`. Any MyBatis query using `resultType` (auto-mapping) on a Y/N column will silently return wrong values. Fix: use a `resultMap` with `typeHandler="ca.digitalcave.buddi.live.db.handler.BooleanHandler"` for all Y/N columns.

- **Null parameters require jdbcType**: Derby throws an exception when MyBatis passes a null parameter without an explicit `jdbcType`. Fix: use `#{param,jdbcType=VARCHAR}` for any nullable parameter.

### Cookie Authentication

- **IP lock mismatch**: The cookie stores the client IP. If login uses `127.0.0.1` but subsequent requests use `0:0:0:0:0:0:0:1` (IPv6 loopback), cookie validation fails silently — returns null user. Fix: always pass `disableIpLock=on` in the login form.

- **twoFactorRequired false positive**: With the Derby Y/N bug above, `use_two_factor='N'` maps to `twoFactorRequired=true`. The verifier then requires 2FA validation that never happened, so it never sets the User on the request — even though primary auth succeeds. This manifests as 401 on every request after login.

- **Cookie accumulation causes 413**: If `CookieJar.saveFromResponse()` appends cookies without replacing existing ones with the same name/domain/path, the Cookie header grows until it exceeds the server's 8KB header limit. Fix: replace matching cookies in the jar.

### ExtJS API Responses

- **No ID in creation responses**: Account, category, and scheduled transaction creation endpoints return `{"success":true}` without the created entity's ID. Tests must query the list endpoint to find the ID by name after creation.

- **Transaction ID must be a string**: The `Transaction(JSONObject)` constructor calls `json.getString("id")`, which throws `JSONException` if the ID is a numeric JSON value. Fix: put the ID as `String.valueOf(id)` in the JSON.

- **ScheduledTransaction requires end/lastCreatedDate**: The constructor calls `json.getString("end")` and `json.getString("lastCreatedDate")` — these throw if missing. Pass empty strings (they parse to null dates via `parseDateInternal`).

### Browser (Selenium) Tests with ExtJS

- **SelfDocumentingField wrapping**: Fields in dialogs (account editor, etc.) are wrapped in `selfdocumentingfield`. The `itemId` is passed through to the inner field, so query directly: `accounteditor textfield[itemId=name]`, not through the wrapper.

- **Source combo store text has non-breaking spaces**: The `data/sources/from` endpoint prepends `\u00a0\u00a0` to account names and replaces spaces with `\u00a0`. Use `findBy` with `indexOf` instead of exact `findRecord('text', 'Chequing')`.

- **Tree store findRecord doesn't search nested nodes**: `TreeStore.findRecord()` only searches top-level records. To find an account node, use `root.cascadeBy()` to traverse the full tree hierarchy.

- **Ext.data.Connection doesn't fire from executeScript**: AJAX requests initiated via `Ext.data.Connection` or `Ext.Ajax` inside Selenium's `executeScript` do not fire. Use plain `XMLHttpRequest` instead, with async callbacks and a `window.__done` flag to poll completion.

- **Account creation triggers page reload**: The account editor's OK handler calls `location.reload()` after success. Tests must wait for the full app to reinitialize after the reload.

- **BufferedStore needs source param**: The transaction list uses a `BufferedStore` that requires a `source` (account ID) proxy param. This is set by the tree selection controller. If the tree selection didn't fire properly (e.g., `findRecord` returned null), the store loads with no params and returns 0 results.

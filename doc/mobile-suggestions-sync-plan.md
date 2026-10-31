# Mobile suggestions synchronization with stable hash buckets

Status: implemented. The backend and Android client use protocol version 1. Focused bucket, repository, API, and offline startup checks cover the implementation described below.

## Scope and intended behavior

Optimize Android transaction description suggestions while preserving offline entry and the desktop API. Server computation and local cache processing are assumed inexpensive; minimize transferred data and network round trips.

| Trigger | Suggestions behavior |
| --- | --- |
| First preparation or unusable cache | Download the complete compact suggestions dataset in one response. |
| Startup, resume, reconnect, periodic refresh, or manual refresh | Send bucket hashes once; receive only changed buckets, or the full dataset if smaller. |
| Create, edit, or delete on this device | Update the local projection immediately; upload the mutation and apply a small authoritative suggestion patch from its response. Make no separate suggestions request. |
| Pending mutation fails or is retried | Preserve confirmed suggestions and pending local changes; reconcile after acknowledgment. |

The server reconstructs only the current dataset for each request. It retains no historical snapshots, summaries, change log, or per-device synchronization state. Bucket hashes describe current content; they are not database modification timestamps.

This work does not change account/transaction downloads, balance calculation, SQL pagination, connectivity probes, desktop suggestion behavior, or the general offline transaction contract.

## Current implementation points

- `DescriptionsController.get()` builds the latest transaction template for each exact description from `Transactions.selectDescriptions()`.
- `BuddiRepository.synchronize()` always calls `fetchTransactionDescriptionResponse(forceNetwork = true)` after uploading transactions and refreshing account data.
- `addOrUpdateTransactionDescriptionTemplate()` changes in-memory templates but does not persist an authoritative suggestions update.
- `mergeDescriptionTemplates()` lets the entire previous in-memory list override remote values. Bucket replacements must instead replace confirmed data, with only actual pending mutations overlaid afterward.
- `OfflineStore` already supports owner-scoped JSON cache values and atomic transactions. Reuse this storage; no database schema migration is required.

## 1. Define the bucket and content contract

Use protocol version `1` with exactly **256 buckets**. Keep this fixed within a protocol version; changing it requires a full refresh.

- Identity: the exact decrypted description string, matching the existing backend grouping. Trimming and case-insensitive matching remain UI projection concerns and must not change stored identities or hashes.
- Assignment: `bucket_id = SHA-256(UTF-8(description))[0] & 0xff`.
- Contents: one authoritative suggestion per description, selected using the existing latest-transaction ordering.
- Compact suggestion fields: `description` and `splits`; each split contains `amountNumber`, `fromId`, `toId`, `fromType`, and `toType`.
- Keep all splits. Omit duplicated descriptions, duplicated formatted amounts, transaction numbers, and desktop presentation fields from the new mobile response.
- Sort suggestions by Java/Kotlin ordinal string order within each bucket. Preserve the existing deterministic split order because the mobile UI uses the first split.

Specify identical canonical hashing in Java and Kotlin, independent of JSON serialization:

1. Encode protocol version, bucket ID, and record count as four-byte big-endian integers.
2. For each sorted record, encode its description as a four-byte UTF-8 byte length followed by its bytes, then encode the split count.
3. For each split, encode the canonical decimal amount as a length-prefixed string, source IDs as four-byte integers, and source types as length-prefixed strings. Use length `-1` for nullable strings so null differs from empty.
4. Normalize decimal values with `stripTrailingZeros().toPlainString()`; normalize every numeric zero to `0`. Never convert money through floating point.
5. Hash the encoded bucket with SHA-256, including empty buckets.

Both sides compute hashes from their actual canonical contents. In particular, the app must not adopt a server bucket hash unless its local contents represent that complete bucket.

Create shared golden fixture vectors covering Unicode, case and whitespace differences, empty buckets, multiple splits, null handling, and decimal normalization. Verify the expected bucket IDs and hashes in both languages.

## 2. Add a single-exchange reconciliation endpoint

Add authenticated `POST /data/transactions/descriptions/sync`. Keep the existing GET endpoint and its source filtering available to desktop and existing apps.

Request:

```json
{
  "version": 1,
  "bucketHashes": "base64-of-256-concatenated-sha256-digests"
}
```

`bucketHashes` is omitted for bootstrap. A complete manifest is 8,192 bytes before Base64 and 10,924 bytes after Base64, plus the small JSON envelope. Packing avoids a separate JSON object or string wrapper for every hash.

The server builds one current suggestions projection, partitions it, compares its hashes, and returns one of these modes:

- `unchanged`: `{ "success": true, "version": 1, "mode": "unchanged" }`.
- `buckets`: include only mismatched buckets as `{ "id": 7, "hash": "base64-sha256", "items": [...] }` in a `buckets` array.
- `full`: include the complete compact dataset in `items`; this replaces every bucket, including buckets that become empty.

Bucket responses are complete replacements, not lists of added entries. An empty `items` array explicitly clears a previously populated bucket. This handles renames and deletions without tombstone storage.

For a changed dataset, serialize both candidate responses and choose the smaller response using gzip size when compression is negotiated, otherwise raw size. Use the existing HTTP response compression to send the selected JSON. Bootstrap always returns `full`; there is no separate manifest fetch or follow-up bucket request.

Validate supported protocol version and exact decoded manifest length. Reject malformed manifests with `400` and an unsupported version with an explicit unsupported-version response. A missing manifest requests a full refresh. Apply ordinary authenticated-user filtering; accept no client-supplied user ID, and prevent shared caching of personalized responses.

Tradeoff: version 1 deliberately spends about 11 KiB of upload on each external reconciliation to guarantee one request/response even when data changed. A root-hash preflight would add a round trip on changes and is outside this implementation.

## 3. Implement the backend projection and endpoint

Extract the current description selection into a small `TransactionSuggestionsService` used by the new endpoint and the transaction mutation patch. Keep the existing GET response shape and grouping semantics intact; share selection logic where it avoids duplication without rewriting its streaming/error handling.

- Reuse `Transactions.selectDescriptions()` and encryption helpers.
- Generate one compact current projection per reconciliation request; derive hashes and response contents from that same projection.
- Build hashes on demand. Do not add persisted server bucket metadata or instrument all database writers.
- Reuse the same projection rules for full responses, bucket responses, and mutation patches.
- Source metadata edits, scheduled transactions, restores, and changes from desktop automatically appear on the next comparison because hashes cover the current resulting suggestion contents.

## 4. Persist and apply the Android cache

Add a versioned canonical suggestions cache value using the existing owner-scoped `OfflineStore`. Store the compact records and protocol version; recompute bucket hashes from the records when reconciling.

- Load cached suggestions immediately on startup and while offline.
- Keep old-format cached suggestions readable until the first successful version-1 full response; an old cache has no valid version-1 manifest and therefore requests bootstrap.
- Track whether the canonical cache is complete. Applying a mutation patch before bootstrap must not mark a partial dataset complete or allow it to send a valid-looking full manifest.
- For `buckets`, remove all confirmed entries belonging to each returned bucket and insert that bucket's replacement items. Preserve all other buckets.
- For `full`, replace the entire confirmed dictionary.
- Verify bucket IDs, entry placement, uniqueness, and returned hashes before committing any replacements. Reject the whole response if invalid, keep the old cache, and request a full repair on the next external refresh.
- Persist replacements atomically before notifying observers. A truncated response, cancellation, or failed write must leave the previous valid cache usable.
- Preserve the existing owner/session guard and serialize confirmed suggestion changes with the existing sync mutex. Discard responses for a different active owner.
- Derive displayed templates from confirmed data plus the actual pending outbox. Replace the previous broad merge of all old in-memory values so remote updates and deletions can take effect.
- Ordinary display reads must use local data and must not initiate legacy suggestions downloads behind the new endpoint.

The canonical cache remains separate from pending local overlays. Pending edits made while a bucket request is in flight remain visible after its confirmed replacements are committed.

## 5. Make local writes independent of suggestions reconciliation

Distinguish a local-mutation wake-up from an external-refresh wake-up in the existing repository scheduler. Keep the current scheduler and add only the state needed to retain both reasons.

- Local saves and outbox edits/deletes request upload work with suggestions reconciliation disabled.
- Startup/resume/reconnect, manual refresh, and the existing periodic refresh request external reconciliation.
- Coalesce reasons by union: a local-save signal must not discard an already requested external refresh.
- Requests arriving during a cycle remain pending for the next cycle. Preserve requested work across retries.
- Track the periodic external-refresh deadline independently so frequent local writes cannot postpone it indefinitely.
- Other existing synchronization steps remain unchanged in this task.

To handle representative-template changes correctly without a separate suggestions request, add an opt-in `includeSuggestions` query parameter to `POST /data/transactions` for the new Android client. Return a transaction-specific response DTO preserving `success`, `id`, and `uuid`, with an optional `suggestions` patch containing `upserts` and `deletes`.

The patch contains only affected description keys:

- Insert: the submitted description and the actual existing description when an insert UUID is replayed.
- Update: the previous and resulting descriptions.
- Delete: the previous description; the result may restore an older representative or remove the suggestion.

Collect known affected keys on the client and include them as optional `suggestionKeys` in opted-in mutation requests. Preserve the key set in pending sync state across edits and lost-response retries, including the original description before a rename. The server adds keys discovered from the actual stored pre-mutation and resulting transaction, and derives their final authoritative values from the current projection.

Capturing the actual pre-mutation description belongs inside the existing transactional service. Add a user-scoped transaction-by-ID lookup for update/delete if needed. Request key hints only select records from the authenticated user's projection and should have bounded count and length.

This keeps rename retries correct without server history: a replay after a lost response still asks for the original key even if the server transaction already has its new description.

After acknowledgment, apply and persist the patch alongside the existing pending mutation acknowledgment, then refresh the displayed projection. The server must determine whether an older transaction edit actually changes a suggestion; do not assume the submitted transaction becomes the representative. Keep newer queued local edits overlaid and retain existing stable-UUID replay behavior.

Recompute local bucket hashes from the resulting confirmed records. Do not copy hashes of whole server buckets into a cache updated with only a few keys: that could hide unrelated changes from another device in the same bucket. The next external comparison will still find those changes.

The local-write path sends no hash manifest and makes no reconciliation request. The only additional downloaded suggestion content is the few affected entries in the mutation acknowledgment.

## 6. Compatibility and rollout

Deploy the backend before the Android update. Existing desktop and Android clients continue to use the original GET response and ordinary transaction success responses.

For a new Android client reaching an older backend, detect an unsupported sync route once per backend session and use the legacy GET only for external refreshes. If a mutation acknowledgment has no suggestions patch, retain the local projection and reconcile on the next external refresh; do not add an immediate full download to the save path.

An unsupported protocol or unreadable local version triggers a version-1 bootstrap when supported. Preserve pending transactions and owner isolation during every migration or fallback.

## Implementation sequence and files

| Step | Files | Result |
| --- | --- | --- |
| 1. Characterize current behavior | `BuddiRepositoryTest.kt`; new focused backend suggestions tests | Reproduce the full download after a local save and stale in-memory values overriding remote changes. Run these regressions first and confirm failure before fixing behavior. |
| 2. Define and test canonical buckets | New focused Java/Kotlin bucket helpers and DTOs under the existing `api`/`service` and mobile `data` packages; shared fixture JSON in `src/test/resources` | Matching deterministic hashes and compact version-1 records. Point the Android test resources at the same fixture file. |
| 3. Serve current bucket differences | `DescriptionsController.java`; new `TransactionSuggestionsService`; focused `DescriptionsSyncIT.java` | One-response unchanged, changed-bucket, and full modes; old GET preserved. |
| 4. Add durable mobile reconciliation | `BuddiApi.kt`, `ApiModels.kt`, `BuddiRepository.kt`; `BuddiRepositoryTest.kt` | Atomic owner-scoped replacements, migration, offline reads, and pending overlays using existing `OfflineStore` operations. |
| 5. Return affected suggestions with writes | `TransactionsController.java`, `TransactionsTransactionalService.java`, transaction request/response DTOs, `Transactions.java` and its mapper XML; mobile mutation DTOs/pending state | Correct small authoritative patches, including rename/delete and lost-response replay. |
| 6. Separate local and external triggers | `BuddiRepository.kt`, `TransactionsViewModel.kt`; existing test fakes in unit and offline instrumentation tests | No suggestions requests after local-only saves; external requests coalesce without being lost. |
| 7. Document and verify | `mobile/android/README.md`; focused API/unit tests and `OfflineStartupTest` | Explain the new preparation/refresh behavior and verify the existing offline workflow. |

Prefer a transaction-specific response DTO over extending the shared `SuccessResponseDto` used by unrelated controllers. Update fake `BuddiApi` implementations for the new method and response. Keep new abstractions limited to the canonical bucket algorithm and shared suggestion projection.

## Focused verification

Protocol/API tests:

- Bootstrap followed by the same manifest returns `unchanged` and no suggestion records.
- Insert/update affects the expected bucket; multiple changed buckets appear together in one response; unchanged buckets are absent.
- Rename across buckets replaces both; deleting the final entry returns an empty replacement; deleting the latest representative restores the previous template.
- Editing a transaction that does not affect the final projection leaves hashes unchanged.
- Large differences select the smaller full response; test a size-based decision rather than a bucket-count threshold, and measure request plus compressed response bytes.
- Hashes cover amount, source IDs, source types, and split order; memo/number edits that leave the suggestion projection unchanged do not affect them.
- Authentication, user isolation, malformed manifests, protocol versions, legacy GET, and optional mutation responses retain their contracts.
- Mutation patches survive a lost response and rename replay, and correctly handle backdated edits and concurrent changes from another device.

Repository/offline tests:

- A completed bootstrap followed by create/edit/delete makes zero additional suggestions reconciliation or legacy GET requests on the local-only save cycle.
- Acknowledged suggestion changes survive restart; pending/failed transactions remain usable offline.
- A mutation acknowledged before first preparation does not turn a partial cache into a completed bootstrap.
- Remote bucket updates/deletions replace old values; only actual pending edits override confirmed results.
- A mutation patch plus an unseen external edit in the same bucket still produces a hash mismatch at the next external refresh.
- Partial responses, bad hashes, cancellation, failed persistence, logout, and owner changes retain the previous valid cache.
- Coalescing preserves an external refresh alongside local saves; periodic refresh still occurs during sustained local activity.
- Existing lost-insert-response and concurrent outbox-edit/delete regressions continue to pass.

Run only the relevant checks during implementation:

```bash
mvn -Dtest=TransactionSuggestionBucketsTest test
mvn verify -Pe2etest -Dit.test=DescriptionsSyncIT,TransactionsIT
```

From `mobile/android`:

```bash
./gradlew :app:testDebugUnitTest --tests '*TransactionSuggestionBucketsTest' --tests '*BuddiRepositoryTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ca.digitalcave.buddilive.mobile.OfflineStartupTest
```

Use event/condition waits for concurrency and instrumentation tests. Finish with `git diff --check` and review new Java/Kotlin/SQL against the project conventions. Protocol fixtures and byte-count assertions provide bandwidth evidence without a production timing benchmark or unrelated test suites.

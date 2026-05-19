# Offline Mode

Android offline mode uses a small local SQLite store behind `OfflineStore`.

- `offline_cache` stores successful API responses as JSON by cache key.
- `pending_transactions` is an outbox for locally created transactions.
- Repository reads try the network first and fall back to cached responses on network failure.
- New transactions are saved to the outbox with a local UUID, shown immediately, and synced asynchronously.
- Pending transactions are projected into account balances and transaction lists until sync removes them.
- Sync sends each pending row as an insert with its UUID. Transient failures keep the row pending, authorization failures stop sync, and validation failures mark the row failed.
- The server includes transaction UUIDs in transaction responses and returns inserted transaction id/UUID data while preserving the existing `success` response shape.

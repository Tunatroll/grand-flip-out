# Config and Data Migration Strategy

Grand Flip Out uses a simple versioning approach so we can evolve persisted data and plugin behavior without breaking existing users.

## Plugin config schema version

- A single internal key is stored under the main config group: `grandflipout.configSchemaVersion` (string, e.g. `"1"`).
- This is **not** exposed in the RuneLite config UI; it is used only by the plugin on startup.
- On startup, the plugin reads this value. If it is missing or `"0"`, the plugin sets it to the current schema version (e.g. `"1"`).
- Future plugin versions can check `configSchemaVersion` and run one-time migrations (e.g. rename keys, transform data) before upgrading the stored version.

## Data persistence versioning

- **Trade history** and **flip logs** use versioned JSON envelopes (`TradeHistorySnapshot`, `FlipLogSnapshot`) with a `schemaVersion` field.
- When loading, the plugin supports the current schema and can support older schema versions for backward compatibility.
- When saving, the plugin always writes the current schema version so the next load uses the latest format.

## Adding a new migration

1. Bump the current schema version constant in code (e.g. `CURRENT_CONFIG_SCHEMA_VERSION = 2`).
2. In plugin startup, after loading managers, run something like:
   - `if (savedVersion < 2) { runMigrationFrom1To2(); setConfigSchemaVersion(2); }`
3. Keep migrations small and idempotent where possible so re-runs are safe.
4. Document the change in this file and in CHANGELOG.

## What we do not do

- We do **not** remove or overwrite user data without a clear migration path.
- We do **not** use the schema version for feature flags; it is only for one-time data/config shape changes.

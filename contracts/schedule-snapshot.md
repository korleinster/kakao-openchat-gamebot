# Schedule snapshot contract — proposed v0.1

This is a design contract, not an implemented API. The empty example is deliberately disabled and is not a valid production schedule source until timestamps and source details are supplied.

## Envelope

- `schema_version`: supported integer version; reject unknown versions.
- `source_id`: identifies one source; its revisions are compared only within that source.
- `source_revision`: monotonically increasing nonnegative integer assigned by the source adapter.
- `generated_at`, `valid_until`: ISO 8601 UTC timestamps; required before production import. Reject invalid or reversed validity intervals.
- `schedules`, `collections`: full-snapshot arrays; validation and replacement are atomic.

## Schedule

A schedule has a stable ID, original name, category (`world_boss` or `recurring_content`), timezone, enabled flag, recurrence rule or explicit occurrences, overrides, provenance, and last-verification timestamp. Define an explicit authority scope before treating absence from a snapshot as deletion. Invalid snapshots must not cancel existing schedules.

Occurrence identity is stable across edits of the same logical cycle. A schedule revision is not a new occurrence identity. Store source IDs or durable local mappings; never derive identity from a mutable display name alone.

## Collection

A collection uses either explicit renewal dates or an anchored calendar-month cycle. A monthly cycle requires the anchor timestamp and a positive interval. Keep the original anchor when clamping month ends. Explicit official overrides take precedence. Reminder offsets remain unset until the owner provides the actual policy.

## Apply outcome

Return one of `APPLIED`, `STALE_REVISION`, `UNSUPPORTED_SCHEMA`, `INVALID_INPUT`, or `EXPIRED_SOURCE`. No partial writes on failure. Cancel or update pending deliveries for changed occurrences and revalidate current state immediately before dispatch.

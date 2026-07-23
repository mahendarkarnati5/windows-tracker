# Tracker Server v2.2.7

Fixes Render/TiDB startup failures introduced by the v2.2.6 five-second Hikari connection timeout.

- Restores Hikari connection acquisition timeout to 30 seconds.
- Adds Flyway database connection retries (10 attempts, 5-second interval).
- Keeps the v2.2.6 batching, lock reduction, projection uniqueness, and duplicate-repair disablement changes.
- No agent or React changes are required.

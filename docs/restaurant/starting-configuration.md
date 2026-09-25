# Starting restaurant configuration

V25 records the dashboard settings approved on 25 September 2026:

- Restaurant timezone: Australia/Melbourne (existing V21 default).
- Opening windows: every day, 09:00–22:00.
- Lunch Special: published and active; daily cutoff 14:30 (existing V22 default).
- Lunch weekly windows: every day, 11:00–14:30.
- Staff ordering pause: off on fresh databases (existing V24 default).

Tuesday and Thursday now both finish at 14:30. The production corrections were
coordinated with V25 checksum repairs and replacement deployments. Restaurant
hours and the 14:30 daily cutoff still apply.
`ONLINE_ORDERING_ENABLED` remains an environment setting, not a database seed.

Schedules are inserted only when the corresponding schedule is empty. Existing
settings and unrelated schedule rows are retained. V25 also corrects the exact
captured development row IDs/values affected by legacy time conversion; it does
not globally shift times or overwrite later edits. The captured Lunch Special
cutoff is corrected only at the captured version (4).

Schedule TIME columns now use direct JDBC LocalTime mapping. Previously,
Hibernate's UTC JDBC calendar combined with a Melbourne JVM converted a stored
23:00 to 09:00 when reading. Store the intended local time itself, regardless of
JVM timezone. UTC mapping of audit timestamps is unchanged. Deploy the mapping
changes and V25 together. Other databases with manually edited legacy time rows
need their values checked against intended local times before this deployment;
the correction targets only the known development snapshot.

V26 inserts one full-access `ADMIN` account, username `admin`, password `admin`,
using a bcrypt hash. It does not overwrite an existing `admin` account, password,
role or enabled state. No FOH/kitchen accounts or sessions are copied. Existing
accounts remain, and optional environment bootstrap settings continue to work.
Change this known starting password before exposing a fresh installation publicly.
Normal dashboard password validation is unchanged.

Flyway applies these migrations once on backend startup. Subsequent staff edits
remain database state; restarting does not reset them to these defaults.

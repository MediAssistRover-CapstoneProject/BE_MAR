# BE laptop + MySQL Aiven

BE/Pi HTTPS remains `https://192.168.1.61`. Pi talks to BE/MQTT, never directly
to Aiven. The Aiven CA authenticates MySQL to BE and is different from the LAN
BE CA installed on Pi. Do not replace the Pi CA with the Aiven CA.

The target from the supplied connection screen is:

- Host: `mysql-2c54a41d-fpt-9265.i.aivencloud.com`
- Port: `12636`; database: `defaultdb`; user: `avnadmin`
- Password: prompt only; use a new password after exposure in the screenshot.
- Download the project's CA certificate from Aiven, save as
  `C:\Users\LENOVO\Downloads\aiven-ca.pem`.

No cloud connection or import runs merely by adding these scripts. Prerequisites:
Docker Desktop running, Python 3 on PATH, Java/keytool installed (the project's
existing JDK download is also detected), outbound access to Aiven and its allowed
IP rules permitting this laptop. Use the latest BE source with the aiven profile.

## 1. Generate the SQL and preserve IDs/files

Save/end robot tasks and stop the Pi stack first. Close any navigation session.
The export refuses active/recovery Jobs, pending physical commands or open sessions.
Run in PowerShell on the BE laptop:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
$project = 'C:\Users\LENOVO\Downloads\MAR_EMQX_Demo\BE_MAR'
$migration = Join-Path $env:USERPROFILE ('Downloads\MAR_Aiven_' + (Get-Date -Format 'yyyyMMdd_HHmmss'))
& "$project\deploy\Export-MarCloudDb.ps1" -ProjectDirectory $project -OutputDirectory $migration -FreezeSource
```

This stops ONLY the existing backend container, leaves the source MySQL data and
map volume intact, and generates `01_mar_to_aiven.sql`, `manifest.json` and a
`map-artifacts` backup. The backend remains stopped for cutover. SQL contains
schema AND data INCLUDING `flyway_schema_history`, binary UUIDs and device hashes.
Treat it as private; never commit it to Git. A snapshot made without FreezeSource
is for inspection only and the importer will refuse it for cutover.

To keep only the latest recovered map and its endpoints/zones, add
`-KeepMapId c342d669-cfbe-447a-a36d-f28b542f61e7` to Export-MarCloudDb.ps1.
This restores into an isolated temporary local DB, removes history/old maps and
old workflow versions there, validates every foreign key (including composites),
then writes the clean SQL. It retains the admin account, robot/device identity,
hospital/floor for this map, system settings, Flyway history and the latest
published mapping/MOVE/speech workflows. Jobs/requests/commands/sessions/logs and
old maps are absent from the resulting export. The original local DB is never
pruned. Only this imported map's YAML/PGM files remain in the backup. Use a new
output folder for each export. Open a fresh navigation session for the retained
map before launching saved-map navigation on Pi; old session IDs are not reused.

There is no CREATE DATABASE/USE/backend_robot_v1 or DROP TABLE in the generated
dump. Select the empty target `defaultdb`, not a new local database name. Do not
first boot BE/Flyway on that empty target then restore on top of its tables.
The SQL closes copied live sessions, marks robots OFFLINE and clears transient
authorizations/profile ACKs on the DESTINATION only. Historical Jobs/results and
catalog IDs remain. A new Pi HELLO/navigation session is required afterward.

## 2. Import into the empty Aiven database

```powershell
& "$project\deploy\Import-MarCloudDb.ps1" -ProjectDirectory $project `
  -SqlFile "$migration\01_mar_to_aiven.sql" `
  -CaCertificate "$env:USERPROFILE\Downloads\aiven-ca.pem"
```

The script prompts for the DB password, uses the Docker MySQL client with
VERIFY_IDENTITY + Aiven CA, and refuses a nonempty target. It imports once,
compares key table counts, Flyway history, robot IDs and map hashes, then creates
a Java PKCS12 truststore and private `.env.aiven`. It never drops an existing
cloud database. If import partially fails, keep BE stopped, inspect/backup that
target; do not retry blindly into existing tables.

Alternatively run the generated SQL in MySQL Workbench against an EMPTY
defaultdb using its TLS connection. Then run Import-MarCloudDb.ps1 with the
same arguments PLUS `-VerifyExisting`: this verifies the exact imported snapshot
and creates the JDBC config without importing again. Do not run both imports.
`deploy/verify-aiven.sql` is read-only.

On Docker Desktop/Windows, MySQL can warn that a bind-mounted client.cnf is
world-writable and ignore it, then try a local Unix socket. The importer copies
the options into a private Linux temporary file with mode 0600 inside each
short-lived client container and explicitly selects TCP. The password never
appears in process arguments; only the public CA remains read from the mount.

## 3. Start the same laptop BE on Aiven, initially without robot dispatch

```powershell
& "$project\deploy\Use-MarCloudDb.ps1" -ProjectDirectory $project
```

This builds the current source, replaces the SAME `mar-emqx-demo-backend-1`
service, keeps the existing LAN reverse proxy/CA and local map volume, selects
`prod,aiven`, and uses Flyway validation/migration. The base Compose DB_URL is
not edited; application-aiven.properties explicitly selects the Aiven URL.
MQTT and operation schedulers are OFF for inspection. Log in to the HTML with
the existing account, check the recovered map, endpoints, zones and workflow.
The first-start Admin bootstrap skips the existing account; original .env JWT,
DEVICE_JWT and MQTT credentials remain in use.

```powershell
& "$project\deploy\Use-MarCloudDb.ps1" -ProjectDirectory $project -EnableDispatch
```

EnableDispatch persists the three cloud flags and restarts that same backend.
Keep the Pi stack stopped during this change. Then start Pi normally, wait for
session ready, open a NEW saved-map navigation session and initialize position.
Test a short MOVE and zone entry/exit before longer workflows. No robot ID,
device secret, MQTT host or Pi HTTPS URL change is required for this architecture.

## Robot control and cloud latency

The Pi never queries MySQL. A MOVE dispatch starts a local Nav2 action; the Pi
checks localization, scan, zones and its velocity gate in the local 20 Hz loop.
Changing the DB does not require changing StepType inputs or sending wheel
commands from the BE. Other hardware steps retain their operation identity and
acknowledgement/recovery contracts.

Cloud round trips can delay admission, dispatch, durable acknowledgements, UI
snapshots and transitions between steps. Link heartbeat scheduling has a dedicated
executor so durable DB replay or other scheduled work cannot queue ahead of it.
A heartbeat transaction rolled back by a transient lock conflict is retried at most
twice in a new transaction, with the same event identity. Physical commands are
not replayed by this retry. The heartbeat still validates the persisted session; this does not make DB outages
invisible or bypass the Pi's communication-loss stop.

The live-map PNG includes `X-Map-Metadata` (base64 UTF-8 JSON without raster data),
containing geometry and pose from the very same frame revision. Clients must pair
that metadata with that PNG instead of combining independently fetched revisions.
The HTML labels a delayed position grey for up to 10 seconds; this is display-only
and does not authorize MOVE from stale localization. A saved/closed mapping job
does not show a live SLAM robot marker; open a navigation session on its saved map.

## Operations and rollback

Keep using BOTH Compose files and BOTH env files after cutover; plain
`docker compose up backend` uses the local DB again. The source local DB is a
rollback snapshot, not a synchronized replica. Never run a second backend against
the old DB on the same robot MQTT lane. Stop the backend before future cutovers.
To return to local DB before any new cloud work, start the original Compose
backend with `.env` only. After cloud has new Jobs/catalog edits, export that
data back with a deliberate migration first; simply switching would lose state.

The map binary files stay in the laptop Docker volume. A cloud DB backup alone
cannot recover them; retain the map-artifacts backup and later map-file backups.
This procedure is a one-time migration, not bidirectional synchronization.
The importer verifies Aiven data/identities, and BE startup validates its schema and
TLS connection. These checks do not validate physical MOVE performance; test local
localization, obstacle handling and zone entry/exit on the robot after cutover.

References: Aiven dump/restore:
https://aiven.io/docs/products/mysql/howto/migrate-database-mysqldump ; Connector/J
CA/truststore configuration:
https://dev.mysql.com/doc/connector-j/en/connector-j-server-authentication.html .

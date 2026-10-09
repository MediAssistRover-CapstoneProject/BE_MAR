# Phase 21 — Backend acceptance

Updated: 2026-10-09. Scope is Server/API/database only; UI, Pi code, ROS/Nav2, hardware and AI model acceptance remain outside this conclusion.

## Result

Phase 15–21 Backend scope is complete. No P1 backend gap remains in the approved scope. Phase 20 delivers ten canonical S4 workflow codes as DRAFT-safe catalog data and completes the missing Server-owned request-to-Job entry points.

| Area | Evidence | Result |
|---|---|---|
| Compile/package | Java 21, `mvn -DskipTests package` | PASS |
| Regression | 115 tests discovered; 114 executed, 0 failure/error; one Docker-only V16→V17 isolation test explicitly skipped locally | PASS |
| Fresh database | MySQL 8.0 local isolated schema, Flyway V1→V18, Hibernate validate | PASS |
| Upgrade isolation | `Phase19MigrationTest`, fixed to stop at V17 and configured to run in CI with Docker | CI-covered; local Docker unavailable |
| Workflow catalog | `Phase20WorkflowCatalogIntegrationTest`: 10 canonical codes, five added graphs compile, operational entry points validate source ownership | PASS |
| Authorization/idempotency | Runtime, delivery, event/ACK, request and Phase 20 integration suites | PASS |
| API/OpenAPI | Architecture/Auth/Workflow/Request integration suites; 401/403/error contract | PASS |
| Bounded load smoke | 500 health requests, concurrency 25, 500 success, 0 failure, 800 ms (624.3 req/s on this workstation) | PASS as smoke only; not an SLA |
| Backup/restore | `mysqldump --single-transaction`, restore to isolated schema | PASS: 47/47 tables, 17/17 Workflow rows, Flyway version 18 |
| ShedLock/retention | Phase 13 integration and dry-run behavior | PASS |

## Reproduction

- Regression: set `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`, then run `mvn test` (Docker is required for the isolated migration test; without it use the remaining suite and the fresh-schema check).
- Load smoke: `deploy/phase21-load-smoke.ps1 -BaseUri http://127.0.0.1:8080 -Requests 500 -Concurrency 25`.
- Restore smoke: set `DB_PASSWORD`, then run `deploy/phase21-backup-restore-smoke.ps1 -SourceDatabase <test_db> -RestoreDatabase <name>_restore_smoke`.

The measured load number is environment-specific and must not be presented as a production SLA. Real Pi/hardware acceptance remains tracked separately in `pi-contract-and-hardware-acceptance.md`.

# Phase 20 — Workflow source matrix

Updated: 2026-10-09. Canonical source: S4, contract protocol 1.0 / graph 3 / schema 2.

| Canonical workflow | Backend entry point | Catalog result |
|---|---|---|
| WF_PATIENT_ESCORT | Escort/Navigation request | Existing template retained |
| WF_INFORMATION_ASSISTANCE | Information request | Existing template retained |
| WF_SECURE_MEDICAL_DELIVERY | Delivery request | Existing template retained |
| WF_CROWD_MONITORING | Operational request or Schedule | Existing template retained |
| WF_EMERGENCY_SOS_RESPONSE | Emergency incident | Existing template retained |
| WF_FALL_DETECTION_RESPONSE | Operational request from verified detection | Canonical DRAFT v3 copied from historical `WF_FALL_RESPONSE` |
| WF_RESTRICTED_AREA_MONITORING | Operational request or Schedule | Canonical DRAFT v3 copied from historical `WF_RESTRICTED_MONITORING` |
| WF_AUTO_CHARGING | Operational request | New DRAFT v3; Staff charging only, no auto-docking claim |
| WF_NAVIGATION_RECOVERY | Operational request after reconciliation; requires `recovery_of_job_id` for a reconciled terminal Job on the same IDLE robot | New DRAFT v3; links `Job.recovery_of_job_id` and performs one MOVE retry after PATH_CLEARED |
| WF_ROBOT_STARTUP_STANDBY | Admin operational request or Schedule | New DRAFT v3; finite readiness check, no infinite standby WAIT |

`WF_NAVIGATION` remains as a historical/backend-specific template and is not counted among the ten S4 workflows. Old alias rows remain immutable history. V18 creates only DRAFT rows; an Admin must validate and publish them.

The common Staff/Admin API is `POST /api/v1/operational-requests`. Device clients still cannot create Jobs directly. The Server validates type-specific input and creates the ServiceRequest and Job atomically. Tests in `Phase20WorkflowCatalogIntegrationTest` compile all five added canonical graphs and cover idempotency and startup authorization.

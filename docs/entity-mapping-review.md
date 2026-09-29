# Entity mapping review

Source of truth: `docs/logicalDiagram.md`, explicitly confirmed by the user in place of `database_schema.md`.

The checklist was established before entity implementation. Number of FK counts the 110 single-column FKs in Foreign Key Summary. Additional composite constraints do not introduce business relationships.

| Table | Java Entity | PK | Number of FK | Special Mapping |
| --- | --- | --- | --- | --- |
| Account | Account | account_id | 0 | Conditional unique via MySQL NULL-distinct tuple |
| StaffProfile | StaffProfile | staff_profile_id | 1 | 1:1 with single-column UK |
| Shift | Shift | shift_id | 2 | Standard mapping |
| Slot | Slot | slot_id | 1 | Standard mapping |
| HospitalInformation | HospitalInformation | hospital_information_id | 3 | 1 generic JSON |
| AuditLog | AuditLog | audit_log_id | 1 | 2 generic JSON |
| SystemSetting | SystemSetting | system_setting_id | 1 | 1 generic JSON |
| VoiceInteraction | VoiceInteraction | voice_interaction_id | 2 | Standard mapping |
| VoiceCommand | VoiceCommand | voice_command_id | 2 | 1 generic JSON |
| Map | Map | map_id | 0 | Standard mapping |
| Zone | Zone | zone_id | 1 | 2 generic JSON |
| Room | Room | room_id | 1 | Standard mapping |
| Endpoint | Endpoint | endpoint_id | 3 | Additional fully specified composite FK |
| EndpointGroup | EndpointGroup | endpoint_group_id | 1 | Standard mapping |
| EndpointGroupMember | EndpointGroupMember | endpoint_group_id, endpoint_id | 3 | Composite PK; incomplete composite FK targets: TODO |
| StepType | StepType | step_type_code | 0 | String PK; 3 generic JSON |
| Workflow | Workflow | workflow_id | 2 | 6 generic JSON |
| Task | Task | task_id | 1 | 1 generic JSON |
| Step | Step | step_id | 3 | Additional fully specified composite FK; 3 generic JSON |
| StepTransition | StepTransition | transition_id | 3 | Incomplete composite FKs; conditional default unique: TODO; 1 generic JSON |
| Robot | Robot | robot_id | 1 | 1 generic JSON |
| RobotStatusHistory | RobotStatusHistory | robot_status_history_id | 2 | Standard mapping |
| RobotLocationHistory | RobotLocationHistory | robot_location_history_id | 3 | Standard mapping |
| DetectionEvent | DetectionEvent | detection_event_id | 3 | 1 generic JSON |
| EmergencyIncident | EmergencyIncident | incident_id | 6 | Additional fully specified composite FK; Conditional unique via MySQL NULL-distinct tuple |
| Alert | Alert | alert_id | 5 | 2 generic JSON |
| AlertRecipient | AlertRecipient | alert_recipient_id | 2 | Standard mapping |
| CrowdDensityRecord | CrowdDensityRecord | crowd_density_record_id | 4 | 1 generic JSON |
| HeatmapRecord | HeatmapRecord | heatmap_record_id | 1 | 1 generic JSON |
| ServiceRequest | ServiceRequest | service_request_id | 3 | 1 generic JSON |
| NavigationRequest | NavigationRequest | service_request_id | 6 | Shared PK/FK; @MapsId + @OneToOne |
| EscortRequest | EscortRequest | service_request_id | 8 | Shared PK/FK; @MapsId + @OneToOne |
| DeliveryRequest | DeliveryRequest | service_request_id | 10 | Shared PK/FK; @MapsId + @OneToOne |
| DeliveryItem | DeliveryItem | delivery_item_id | 1 | Standard mapping |
| AuthVerification | AuthVerification | verification_id | 4 | 1:1 with single-column UK |
| Schedule | Schedule | schedule_id | 3 | 1 generic JSON |
| Job | Job | job_id | 8 | Conditional unique via MySQL NULL-distinct tuple; row_version seed/ownership: TODO; 3 generic JSON |
| JobTask | JobTask | job_task_id | 2 | 1 generic JSON |
| JobStep | JobStep | job_step_id | 2 | 3 generic JSON |
| JobEvent | JobEvent | job_event_id | 5 | 1 generic JSON |

Confirmed inventory: **40 entities, 550 database fields, 110 single-column FKs, 37 JSON fields; 39 simple primary keys and one two-column composite primary key.**

## Mapping decisions

- Explicit table/column names preserve the source spelling. The standard physical naming strategy prevents Spring Boot from converting table names to snake_case.
- FK-owning associations only: 104 many-to-one and 6 one-to-one relationships; no inverse collections, cascading deletes, or inferred relationships.
- Three subtype associations use `@MapsId` over their existing `service_request_id`; their association properties do not add columns.
- At the user's request, composite FK mapping now uses `@JoinColumns` directly on `Endpoint.zone`, `Step.task`, and `EmergencyIncident.endpoint`. No custom Hibernate mapping contributor or ServiceLoader registration is used.
- The existing `zone_id`, `task_id`, and `endpoint_id` columns are writable UUID properties. Their composite associations are read-only views of those same columns and the shared Map/Workflow FK, avoiding duplicate column writers. Set the UUID and Map/Workflow association to write the keys; reload/refresh to read the updated composite association. No database columns or business relationships are added.
- These three composite FKs replace separate single-column declarations for those same associations. The generated metadata now has 107 single-column FKs plus 3 composite FKs, covering all 110 source relationships. The stronger constraints include the original target ID check; EmergencyIncident's existing CHECK requires map_id whenever endpoint_id is present. The original checklist above counts source FK fields, not generated constraint objects.
- `EndpointGroupMemberId` contains only the two UUID key components. The entity remains a business association entity.
- UUID IDs are assigned, not generated. UTC datetimes use `Instant`; decimals retain precision/scale; text and fixed char types are explicit.
- JSON uses raw `String` with Hibernate `@JdbcTypeCode(SqlTypes.JSON)`, preserving objects, arrays, scalars and JSON null without inventing DTOs or schemas. SQL NULL remains Java null.
- Enum constants come only from the database field descriptions. `RobotStatusHistory` references the explicitly defined `RobotStatus`; varchar registries/snapshots remain String. Contract-only values do not expand database enums.
- Field comments retain source descriptions. Class comments retain all source Constraints and source notes. Explicit single-row checks and numeric bounds are mapped with Hibernate `@Check`; policy/transaction rules remain documented requirements.
- All occurrences of `Unclear from ERD` in mapping-relevant field metadata are marked `// TODO: Clarify from physical database design`.

## Deliberately unresolved source requirements

- Conditional UQ on Account(auth_provider, external_subject), EmergencyIncident(detection_event_id, incident_type), and Job(schedule_id, schedule_fire_at, schedule_item_key) uses composite `@UniqueConstraint` with the current MySQL NULL-distinct semantics. Rows excluded by each source predicate already contain a NULL key component and therefore do not conflict. This preserves the predicate without inventing generated fields or individual-column uniqueness. Other nullable components remain nullable; no extra NOT NULL rules are assumed. Revisit this mapping if switching to a dialect with different NULL uniqueness semantics.
- At most one default StepTransition per (from_step_id, outcome), active/recovery Job robot ownership, overlapping Shift/Slot, same-map/workflow/job consistency and subtype/item state requirements need physical constraints or transactional enforcement. JPA associations alone do not enforce them. No service layer is introduced.
- EndpointGroupMember composite FK target tuples and StepTransition composite FK source/target tuples remain unspecified. No tuples are guessed.
- Only the three fully specified composite FKs (Endpoint to Zone, Step to Task, EmergencyIncident to Endpoint) use `@JoinColumns`. Incomplete composite FK notes still remain TODO; no missing tuple is inferred.
- Seven documented defaults are preserved in comments, not implemented as Java initializers or SQL defaults: the source does not specify who supplies them.
- Job.row_version is a Long with the stated >= 1 check. Optimistic concurrency requires a seed/ownership decision: plain Hibernate `@Version` starts at 0, conflicting with the source. No automatic zero seed is introduced; optimistic locking is not yet activated.
- Generic JSON does not enforce the incomplete JSON schema. ERD Review Notes are retained as design warnings, not used to alter the schema.

## Verification

Completed with Java 17, Spring Boot 4.1.1 and Hibernate 7.4.5.Final:

- `mvnw.cmd -q -DskipTests compile`: passed.
- `mvnw.cmd -q -Dtest=EntitySchemaMappingTest test`: 7 tests passed, 0 failures/errors.
- Independent tests read the original Markdown and compare all 40 tables and 550 columns, exact PKs, nullability, Java/SQL types, lengths and decimal precision/scale.
- Actual Hibernate metadata contains exactly 107 single-column FKs and 3 fully specified composite FKs, covering all 110 source relationships with matching source/target column pairs. Hibernate may reorder both sides of a composite FK consistently.
- Single-column UKs (19), all explicit UQ tuples, cardinalities, optionality and enum vocabularies match the source. No inferred inverse/cascade relationships are present.
- Hibernate SessionFactory boots without a database; generic JSON round-trips object, array, string, number, boolean and JSON null without adding a schema.
- MySQL DDL generation was inspected without executing it; review output is `target/entity-schema-review.sql`.
- No live database was configured or modified. The existing application context smoke test requires a datasource and was not part of this focused mapping suite. These checks do not claim enforcement of the unresolved cross-row/transaction rules listed above.

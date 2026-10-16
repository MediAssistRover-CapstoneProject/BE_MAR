# Endpoint/Zone type migration

`V31__restrict_endpoint_and_zone_types.sql` changes only the database check
constraints. It deliberately does not rewrite or delete legacy rows.

Before deploying V31, inspect production data:

```sql
SELECT endpoint_type, COUNT(*)
FROM Endpoint
WHERE endpoint_type NOT IN ('WAYPOINT', 'CHARGER', 'PARK', 'STAGING')
GROUP BY endpoint_type;

SELECT zone_type, COUNT(*)
FROM Zone
WHERE zone_type NOT IN ('KEEP_OUT', 'SPEED', 'NO_STOP')
GROUP BY zone_type;
```

If either query returns rows, agree the semantic mapping with the product/data
owner and update those rows in a reviewed, environment-specific data migration.
There is no universally safe conversion: for example, legacy `WAITING` may mean
either `PARK` or `STAGING`, and legacy `RESTRICTED` is not necessarily a strict
`KEEP_OUT` zone.

V31 runs the same check through a temporary table before any persistent DDL.
MySQL therefore rejects the migration while legacy values exist, without
deleting data or changing either existing constraint. Once preflight succeeds,
each table swaps its old and new check constraint in one `ALTER TABLE` statement.

---
id: REQ-20260915-001
status: ready
owner: rokeyvvz0828
module: platform/infrastructure
---

# Default project migration prerequisite

Approved by the user in this task: "按此处理", following the append-only migration proposal.

## Requirements

- R1: An empty MySQL 8.4 database must migrate through the latest version without a manually inserted project.
- R2: Before V207/V209, each tenant with affected architecture data (and the bootstrap tenant 1) must have its missing RDDMP-PLATFORM project initialized with an active same-tenant admin, PM role, member, role assignment and standard project stages.
- R3: Existing projects, memberships, permissions and business data must be preserved. A successful V209 makes the compatibility migration a no-op. A deleted default project or missing/ambiguous administrator must fail before persistent writes.
- R4: A database stopped at V208 and a database with a failed V209 guard must upgrade using a documented, verified repair sequence. Published migrations must remain byte-for-byte unchanged.

## Implementation and acceptance plan

1. Add V206_20260915143000 before V207; extend the existing migration filename checker with an exact exception.
2. Reuse pm_project, PM membership and seven stage conventions. Generate IDs above each table's current maximum, with the application stopped and Flyway holding its migration lock. Wrap all permanent inserts in one transaction.
3. Exercise full empty replay, legacy data upgrades, existing project preservation, already-current out-of-order upgrade, multiple tenants, deleted defaults, missing admins and V209 failure recovery in Testcontainers MySQL 8.4 using Java 17.
4. Run governance checks and regression tests, review independently, then submit the patch to main through a PR and respect required checks.

## Scope and rollback

No frontend/API changes, no operational database connections, no changes to V207/V209 or other published scripts. Only this task's migration, focused tests, migration-check exception, recovery guide and evidence may change. A migrated database must not have its new project removed once architecture data references it. Prefer a forward fix; an isolated test database can be restored from its pre-upgrade backup.

## Risk

The low version is intentional; already-upgraded environments require outOfOrder to apply it. Inspect all pending migrations before enabling this option. Failed V209 histories require an explicit repair only after verifying that failure occurred before any permanent DDL. Out-of-order disabled is not a supported upgrade route for this backfill.

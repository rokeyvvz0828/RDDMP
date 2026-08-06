# Platform Theme, Layout, Components, and Follow-up Modules Implementation Plan

## Status and source

- Plan revision: 1
- Design revision: 1
- Design document: `docs/engineering-control/designs/2026-08-04-platform-theme-layout-components-v2-design.md`
- Status: ready
- Approval: user approved the design direction on 2026-08-04

## Goal and constraints

Deliver a reusable single-tenant platform foundation while preserving the existing authentication, refresh, logout, 401, 403, and disabled-account behavior. Keep the current stack. Do not expose AI secrets to the browser or load arbitrary backend component paths.

## File responsibility map

- `web/src/stores/theme.ts`: persistent appearance, palette, layout, sidebar, and density state.
- `web/src/components/ui/*`: typed visual primitives shared by business pages.
- `web/src/views/AppLayout.vue`: shell variants and recursive navigation rendering.
- `web/src/styles.css`: semantic design tokens and layout-specific styling.
- `web/src/views/system/*`: real system management pages.
- `web/src/api/*` and `web/src/types/*`: frontend API/query contracts.
- `server/src/modules/system/src/main/java/com/ccb/system/*`: system controllers, services, mappers, entities, DTOs, and tests.
- `server/src/platform/infrastructure/src/main/resources/db/migration/V4__system_module_support.sql`: schema support and seed corrections.
- `server/src/modules/workflow/src/main/java/com/ccb/workflow/*`: workflow definitions, versions, tasks, and approvals.
- `server/src/modules/ai/src/main/java/com/ccb/ai/*`: provider registry, routing, capability contracts, and audited execution.
- `docs/integration/*`: module integration and AI capability contracts.

## Dependencies and parallel strategy

T1 precedes T3 because system pages consume shared UI components and shell layout. T2 precedes T3 because pages consume system APIs. T4 and T5 depend on the common response, authorization, audit, and UI contracts from T1-T3. T6 depends on all previous tasks.

Parallel groups: `[T1]`, `[T2]`, `[T3]`, `[T4, T5]`, `[T6]`.

## Requirement coverage

- R9: T1, T6
- R10: T1, T6
- R11: T1, T3, T6
- R12: T1, T6
- R13: T2, T3, T6
- R14: T4, T6
- R15: T5, T6

## T1 - Theme, layout, dynamic menu, and shared UI foundation

Maps R9-R12. The current store only persists `light/dark/system`; the layout is fixed to an aside; `AppLayout.vue` reads only `auth.routes[0]`; there is no shared UI component directory.

Files:

- Create `web/src/types/ui.ts`, `web/src/components/ui/UiPageHeader.vue`, `UiToolbar.vue`, `UiDataTable.vue`, `UiStatusTag.vue`, `UiFormDrawer.vue`, `UiEmptyState.vue`, `ThemeSettingsDrawer.vue`.
- Modify `web/src/stores/theme.ts`, `web/src/views/AppLayout.vue`, `web/src/styles.css`, `web/src/types/auth.ts`, `web/src/router/index.ts`, `web/src/main.ts`.

Steps:

1. Add a failing build/type check for palette, layout, and component prop contracts.
2. Implement preference state with `appearance`, `palette`, `layout`, `sidebarCollapsed`, and `density`, applying document data attributes.
3. Replace duplicated colors with semantic CSS tokens and implement four palettes, three layout variants, and a settings drawer.
4. Extract shared UI primitives and migrate dashboard and at least two business pages.
5. Replace first-root menu assumptions with recursive side/top/mixed renderers and preserve active routes.
6. Run `npm run build` and browser checks for switching, refresh persistence, menus, and desktop/tablet widths.

Acceptance: all R9-R12 checks pass and existing auth reaches `/dashboard`.
Rollback: revert only T1 frontend files. Stop on auth regression, arbitrary component loading, or unreproducible preference state.

## T2 - System administration backend

Maps R13. `ccb-system` currently has only its Maven descriptor and no source implementation. V1 contains core system tables but no system CRUD endpoints.

Files:

- Create entities, mappers, services, controllers, request/response DTOs, validation, and tests under `server/src/modules/system/src/main/java/com/ccb/system/{model,repository,service,web}`.
- Create `server/src/platform/infrastructure/src/main/resources/db/migration/V4__system_module_support.sql` only for indexes, seed corrections, and fields required by implemented APIs.
- Expose authenticated endpoints under `/api/system/users`, `/roles`, `/orgs`, `/menus`, `/dicts`, and `/configs` with the existing `ApiResponse` and `PageQuery` contract.

Steps:

1. Add tests for list, duplicate validation, status change, and forbidden access.
2. Implement list and validation flows, then create/update/status flows with transaction boundaries and audit hooks.
3. Apply Flyway migration and verify repeatability against local MySQL.
4. Run module tests and authenticated HTTP checks for 200, 400, 401, and 403.

Acceptance: six domains support authenticated list/create/update/status flows with duplicate/tree validation and audit rows.
Rollback: retain V1-V3 and stop before destructive migration. Stop on schema failure, missing tenant scope, or response incompatibility.

## T3 - System administration frontend

Maps R11 and R13. `ModuleView.vue` is a placeholder and route labels are encoded incorrectly in source.

Files:

- Create `web/src/api/system.ts`, `web/src/types/system.ts`, and pages under `web/src/views/system/` for users, roles, organizations, menus, dictionaries, and configs.
- Modify `web/src/router/index.ts` and `web/src/views/AppLayout.vue` for real page registration and labels/icons.

Steps:

1. Add page contracts and a failing build check for missing views/API types.
2. Implement list/filter/pagination and status actions using shared page, toolbar, table, and drawer components.
3. Implement organization and menu tree editors with parent validation.
4. Implement role-menu assignment, dictionary items, and config editing with masked sensitive values.
5. Run `npm run build` and authenticated browser CRUD smoke checks.

Acceptance: six system pages have loading, empty, error, validation, and success states. Stop on mutation without confirmation, unhandled 403, or API field mismatch.

## T4 - Workflow definition and approval runtime

Maps R14. This is a new module; no workflow persistence currently exists.

Files: create `server/src/modules/workflow/pom.xml`, sources under `server/src/modules/workflow/src/main/java/com/ccb/workflow`, migration `V5__create_workflow_schema.sql`, frontend `web/src/views/workflow/*`, and `docs/integration/workflow-module-contract.md`.

Steps: test definition validation and task idempotency; implement drafts, published versions, nodes, transitions, instances, tasks, inbox, approve/reject; then run an end-to-end submit/approve/reject flow.

Acceptance: a published definition creates an instance and a task can be approved or rejected exactly once, with audit. Stop on duplicate completion, invalid transition, or missing audit.

## T5 - AI model registry and integration contract

Maps R15. No provider contract currently exists. Credentials remain server-side and capabilities are declarative and audited.

Files: create `server/src/modules/ai/pom.xml`, sources under `server/src/modules/ai/src/main/java/com/ccb/ai`, migration `V6__create_ai_schema.sql`, frontend `web/src/views/ai/*`, and `docs/integration/ai-module-contract.md`.

Steps: test redaction, capability validation, and route selection; implement metadata, routing, masked credentials, execution boundary, and configuration pages; verify no credential appears in browser payloads, logs, or exceptions.

Acceptance: a capability request selects a configured model route, rejects unsupported capabilities, and creates an execution audit row without exposing secrets. Stop on secret leakage or missing permission.

## T6 - Integrated verification

Maps R9-R15. Run `mvn test`, `npm run build`, authenticated browser flows for login, palette/layout persistence, menus, system CRUD, workflow actions, and AI redaction, plus database assertions for audit and task state.

Acceptance: every must requirement has executable evidence, P0/P1 regressions are zero, and auth invariants remain unchanged. Stop on any unverified requirement, migration failure, or open P0/P1.

## Control model seed

- Boundary: repository, MySQL migrations, backend modules, frontend shell, browser, and local services.
- State variables: backend/frontend build, migration version, auth, routes, UI preferences, workflow tasks, AI executions.
- Interfaces: REST response, route tree, UI props/events, workflow transitions, AI capability contract.
- Sensors: Maven tests, frontend build, HTTP checks, browser flow, DOM attributes, database assertions, redaction assertions.
- Actuators: source changes, migrations, service startup, browser actions, test commands.
- Disturbances: port conflicts, stale artifacts, database state, runtime versions, provider availability.

## Approval

Plan is approved by the user's confirmation of the design and requested implementation. Replan if a stop condition occurs.

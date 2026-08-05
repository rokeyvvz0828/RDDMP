# Platform Theme, Layout, Components, and Follow-up Modules Design

Status: awaiting-user-approval

## Objective

Turn the current single-tenant Vue shell into a reusable platform foundation. Theme selection must change the complete visual system, not only light and dark colors. The shell must support menu placement choices, and future system, workflow, and AI modules must consume common UI and API contracts.

## Scope and sequence

1. Theme and layout foundation.
2. Shared frontend component contracts and migration of the shell.
3. Real system-management APIs and pages: users, roles, organizations, menus, dictionaries, and configs.
4. Workflow definition, approval runtime, and approval inbox.
5. AI model registry, system model routing, module capability contract, and execution audit.

The first implementation slice covers items 1-3. Items 4-5 remain in the same architectural plan but are separate delivery slices with their own acceptance checks.

## Requirements

| ID | Requirement | Acceptance | Priority |
| --- | --- | --- | --- |
| R9 | Theme presets | Four named color presets alter shell, menus, cards, controls, status colors, and page surfaces; each supports light, dark, and system appearance; the selection survives refresh. | must |
| R10 | Layout presets | Side, top, and mixed menu layouts are selectable, persist across refresh, preserve the active route, and render the same dynamic menu tree. | must |
| R11 | Shared UI components | Common page header, toolbar, table, status tag, form drawer, empty state, and destructive-action confirmation components have typed props/events and are used by at least two business pages. | must |
| R12 | Recursive dynamic menu | The frontend renders arbitrary menu depth from the backend route tree and does not assume the first root node is the system menu. | must |
| R13 | System administration | Users, roles, organizations, menus, dictionaries, and configs have authenticated list/create/update/status flows backed by REST APIs and the existing response contract. | must |
| R14 | Workflow foundation | Workflow definitions, versioned nodes/transitions, approval tasks, and an approval inbox have explicit persistence and REST contracts. | must |
| R15 | AI integration foundation | Model providers, model credentials metadata, routing policy, capability declarations, and execution audit have an explicit extension contract; secrets are never returned to the browser. | must |

## Invariants and constraints

- Single tenant remains the deployment mode; every persisted domain record retains `tenant_id`.
- Backend authorization remains authoritative. Frontend visibility is not security.
- Vue 3, TypeScript, Vite, Element Plus, Pinia, Vue Router, Spring Boot 3.x, JDK 17, MyBatis-Plus, MySQL 8, and Flyway remain the stack.
- Theme state is UI preference state and is independent from authentication and route permissions.
- Dynamic component paths from the backend are allowlisted; arbitrary code loading is forbidden.
- AI provider secrets are server-side only and are masked in all responses and logs.
- Existing login, refresh, logout, 401, 403, and disabled-account behavior must remain unchanged.

## Recommended architecture

Use one `ui-preferences` Pinia store with independent `appearance`, `palette`, `layout`, `sidebarCollapsed`, and `density` fields. Apply them to `document.documentElement` as data attributes. CSS variables define semantic tokens; palette presets only provide token values. Element Plus global variables are mapped to the same tokens. A `ThemeSettingsDrawer` exposes the controls and previews actual surfaces.

Use one `AppLayout` shell with layout-specific branches for side, top, and mixed navigation. A recursive menu renderer consumes the same `RouteNode` tree in every branch. The route registry maps backend component keys to local Vue components through an allowlist.

Keep shared UI components in `src/components/ui`. Components wrap Element Plus rather than replacing it and expose stable typed contracts. Business pages own data fetching and domain forms; UI components own visual consistency, loading, empty, error, and event presentation.

Backend system modules follow one bounded-module pattern: controller, application service, MyBatis-Plus mapper, entity, command/query DTOs, and tests. Cross-module access goes through application services, not direct mapper calls. The workflow and AI modules use the same pattern and publish their extension contracts under `docs/integration`.

## Alternatives considered

### A. CSS token presets plus one preference store (recommended)

The visual system remains centrally controlled, new palettes do not duplicate pages, and layout state is independent from business routes. It has moderate implementation cost and good observability through DOM attributes.

### B. Element Plus ConfigProvider only

This is smaller but does not cover custom surfaces, navigation geometry, shadows, or layout modes consistently. It would leave the current CSS split between framework defaults and handwritten values.

### C. Separate page shells per theme and layout

This gives visual freedom but duplicates navigation and route behavior, increases regression surface, and makes future modules harder to standardize. It is rejected.

## Verification

- `npm run build` passes after each frontend slice.
- Browser flow verifies all palette/appearance combinations, layout switching, refresh persistence, active menu state, and no horizontal overflow at desktop and tablet widths.
- API tests verify system CRUD, permission rejection, duplicate validation, and status changes.
- Workflow tests verify definition versioning, task assignment, approve/reject, and idempotent task completion.
- AI tests verify secret redaction, provider routing, capability validation, and audit rows.

## Risks and stop conditions

- Stop and remodel if backend menu payload cannot express arbitrary depth or component keys are not allowlistable.
- Stop and remodel if existing API responses require a breaking change to preserve CRUD behavior.
- Stop before AI implementation if a provider secret would need to enter browser state.
- Preserve current authentication behavior as the rollback boundary for frontend changes.

## Approval

The design is intentionally awaiting user approval. Implementation planning and code changes begin only after the user confirms the direction or specifies revisions.

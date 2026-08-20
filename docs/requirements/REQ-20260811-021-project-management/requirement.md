---
id: REQ-20260811-021
status: ready
owner: rokeyvvz0828
module: platform/system and frontend/application
---

# Project Management Workbench

## Goal

Add a single Chinese `项目管理` entry that opens a project workbench instead of four independent menus. A user sees only projects in which they are a member, while `SUPER_ADMIN` can see and maintain all projects.

## In Scope

- Project workbench with project cards and project summary.
- Project detail tabs for overview, plans, members, and project roles.
- Project fields: code, name, description, status, owner, planned dates, actual end date, and audit timestamps.
- Hierarchical project plans with parent task, owner, date range, progress, status, and sort order.
- Project members with user, one or more project roles, status, and join time.
- Project-local roles kept separate from system roles.
- Server-side project visibility and write authorization for every project, plan, member, and role endpoint.
- Menu entry and CRUD action permissions under the single project menu.
- Flyway migration with Chinese table and column comments.

## Data Scope

- `SUPER_ADMIN` can read, create, update, and delete all tenant projects.
- Other users can read projects where they are active members.
- Project owners and members with a project-management role can maintain project details, plans, members, and roles according to system action permissions.
- The backend must reject out-of-scope IDs even when a client sends them directly.

## Acceptance

- The left navigation contains only one project management menu.
- The project page has no four-menu duplication and opens with a card-based workbench.
- A normal user cannot obtain another user's project through list, detail, plan, member, role, update, or delete APIs.
- `SUPER_ADMIN` can see all tenant projects.
- Project detail tabs load and save plans, members, and roles with Chinese labels and explicit loading, empty, forbidden, and error states.
- Project menu action permissions are visible in the role permission catalog and are enforced by the backend.
- `mvn -pl :ccb-system -am test`, `npm --prefix web run build`, `node scripts/check-all-governance.mjs`, and `git diff --check` pass.

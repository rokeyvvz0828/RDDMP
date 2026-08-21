---
id: REQ-20260812-025
status: ready
owner: rokeyvvz0828
module: frontend/application, platform/system
---

# Project Workbench Polish

## Goal

Improve the existing project workbench without changing its business model.

## Scope

- Restore project overview facts beside the project description and keep the calendar beside them on desktop.
- Make calendar cells larger and plan labels compact enough for multiple plans per day.
- Keep fixed table operation columns opaque while horizontal scrolling.
- Ensure child plan number rules are used when creating a child plan, including projects created before V39.
- Keep the active project tab after plan, member, role, and project-settings operations.

## Invariants

- Existing tenant, permission, visibility, audit, date, and parent-plan validation remain unchanged.
- Main and child plan numbers remain server-generated; client-supplied plan codes remain ignored.
- No historical Flyway migration is modified.

## Acceptance

1. Overview shows description, phase, owner, dates, progress, member count, and plan count, with calendar in the adjacent desktop column.
2. Calendar plan labels are compact and cells can show more plans without clipping the date layout.
3. Fixed operation columns do not reveal status or other scrolled cells.
4. Creating a child plan returns a code extending its parent code, even when the project has a null legacy child rule.
5. Saving or deleting data in plans, members, roles, or settings keeps the current tab selected.

## Verification

- `mvn -Dnet.bytebuddy.experimental=true -pl :ccb-system -am test`
- `npm --prefix web run build`
- `git diff --check`

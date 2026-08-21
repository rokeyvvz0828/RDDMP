---
id: REQ-20260812-026
status: ready
owner: rokeyvvz0828
module: frontend/application, platform/system
---

# Project Workbench Numbering And Visual Fix

## Goal

Close the remaining project workbench feedback without changing project visibility, permission, date, or parent-plan rules.

## Scope

- Ensure the running backend loads V39 and server-generated child plan numbers extend the parent number.
- Reduce empty space in the project overview description area.
- Render calendar plans with circular markers instead of vertical bars.
- Keep the project plan operation column opaque while the table scrolls horizontally.

## Invariants

- Plan numbers remain server-generated; client-supplied plan codes remain ignored.
- Existing tenant, permission, visibility, audit, date, and parent-plan validation remain unchanged.
- Existing uncommitted worktree changes remain preserved.

## Acceptance

1. The local backend starts with schema version V39 and the project plan insert contract has matching columns, placeholders, and arguments.
2. The overview description does not stretch the summary card beyond its content and remains readable for longer text.
3. Each calendar plan has a circular marker and no left border marker.
4. The plan table operation column has an opaque themed background above scrolled cells.

## Verification

- `mvn "-Dnet.bytebuddy.experimental=true" -pl :ccb-system -am test`
- `mvn "-DskipTests" package`
- `npm --prefix web run build`
- `git diff --check`

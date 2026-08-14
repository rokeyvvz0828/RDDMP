---
id: REQ-20260813-029
status: ready
owner: rokeyvvz0828
module: frontend/application
---

# Project management detail polish

## Objective

Improve the project management detail experience without changing the project API or permission contract.

## Scope

- Move the project calendar into the upper-right area of the overview content.
- Keep the selected project in the URL so a browser refresh restores the project detail instead of the project list.
- Increase the plan number column width for long parent and child plan numbers.
- Ensure people and project-role selectors have loaded label options when editing so names, not numeric IDs, are shown.
- Present project facts in a readable information panel beside the calendar.
- Enter project detail without refreshing the project card list first.
- Show a clear loading overlay while project lists and project details are loading.
- Keep the project calendar prominent beside the overview summary on desktop and stacked cleanly on mobile.

## Non-goals

- No backend, database, migration, permission, or API changes.
- No changes to unrelated project-management features.

## Acceptance criteria

1. The overview shows the project calendar in the right column of the upper summary section.
2. Opening a project navigates to `/projects/{projectId}` and refreshing that URL reloads the same project detail.
3. The plan number column is wide enough for the existing parent/child number format.
4. Editing project owners, plan owners, members, and member roles displays labels from loaded options rather than IDs.
5. The overview information panel is visually separate from the calendar and presents project facts in labeled fields.
6. Clicking a project card navigates to its detail route without loading the project workbench list first.
7. Project list/detail loading states show a visible loading overlay with context text.
8. The calendar is visible in the desktop first viewport beside the overview summary and does not cause mobile horizontal overflow.

## Verification

- `npm --prefix web run build`
- `git diff --check`
- Local frontend HTTP reachability and focused route/source checks.

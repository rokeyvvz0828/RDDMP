---
id: REQ-20260812-023
status: ready
owner: rokeyvvz0828
module: platform/infrastructure, platform/system and frontend/application
---

# Project Plan Numbering And Calendar

## Goal

Extend the existing single-menu project workbench with server-generated plan numbers, project-level plan numbering settings, actual plan progress aggregation, and a project calendar.

## In Scope

- Add an optional plan number to every project plan. Existing plans remain valid when the number is empty.
- Add a project setting for the plan number rule. The default rule is `{PROJECT_CODE}-P{SEQ:3}`. Supported placeholders are `{PROJECT_CODE}`, `{SEQ}`, `{SEQ:n}`, `{YYYY}`, `{MM}`, and `{DD}`.
- Generate plan numbers on the server during plan creation. The client cannot submit or override the generated number. Number generation is tenant and project scoped and protected by a unique index.
- Keep project numbers manually entered and unchanged.
- Calculate project plan progress as the average actual progress of active plans, exposed as `plan_progress` and used by project cards and detail views.
- Add a Chinese monthly calendar to the project overview. Plans with dates are rendered on their scheduled days and can navigate to the plans tab.

## Invariants

- Existing project visibility, tenant predicates, permissions, audit records, and hierarchical plan behavior remain unchanged.
- Date validation remains server authoritative.
- No independent calendar table is introduced; the calendar is derived from plan dates.
- All database changes are append-only Flyway migrations with Chinese table and column comments.

## Acceptance

- Creating a plan returns a unique server-generated `plan_code` using the saved project rule; supplied client `plan_code` is ignored.
- Project settings can be read and saved by authorized project maintainers and are displayed in a new `项目设置` tab.
- Project cards display the rounded average of plan `progress`, including partial progress, rather than completed-plan count.
- Project overview displays a month calendar with each dated plan on its scheduled date, including status and progress.
- Old projects and plans remain readable when the new fields are null.
- Backend tests, frontend build, migration checks, and health/runtime checks pass.

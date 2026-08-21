---
id: REQ-20260812-024
status: ready
owner: rokeyvvz0828
module: platform/infrastructure, platform/system and frontend/application
---

# Project Overview And Plan Layout

## Goal

Refine the project workbench overview and plan numbering experience, and prevent fixed plan-table columns from exposing scrolled content.

## In Scope

- Keep only project description and project calendar in the project overview, arranged horizontally on desktop and stacked on narrow screens.
- Display the main-plan numbering rule and child-plan extension rule with Chinese explanations and examples.
- Generate child-plan numbers by extending the parent plan number; child sequences are independent for each parent plan.
- Make fixed plan-table columns opaque and layered above the scrollable table body.

## Invariants

- Existing project visibility, tenant predicates, permissions, audit records, date validation, and plan hierarchy remain unchanged.
- Existing main-plan numbering remains compatible with `{PROJECT_CODE}`, `{SEQ}`, `{SEQ:n}`, `{YYYY}`, `{MM}`, and `{DD}`.
- Flyway changes are append-only and include Chinese table and column comments.

## Acceptance

- The overview contains only the description and calendar sections, with a two-column desktop layout.
- Project settings explain both rules; a child plan created under `RDC-P001` can receive `RDC-P001-S001`, then `RDC-P001-S002`.
- Child sequence values do not affect another parent plan or the main-plan sequence.
- Horizontal scrolling in the plan table does not reveal cell content underneath the fixed operation column.

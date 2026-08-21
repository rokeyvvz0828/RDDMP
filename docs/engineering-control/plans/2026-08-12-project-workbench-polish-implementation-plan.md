# Project Workbench Polish Implementation Plan

## T1: Project detail refresh and overview

- Modify `web/src/views/ProjectView.vue` to add a refresh-only function and use it after plan, member, role, and settings mutations.
- Render project facts in the overview beside the description and keep the calendar in the desktop-side column.
- Verify `activeTab` is unchanged after each mutation.

## T2: Calendar and fixed-column presentation

- Modify `web/src/styles.css` for the overview grid, calendar cell sizing, compact plan labels, and opaque fixed columns.
- Verify the plan table remains usable at narrow widths and fixed operation cells cover scrolled content.

## T3: Child plan number compatibility and verification

- Make the existing server fallback treat null and blank child rules as the V39 default.
- Add or update focused service coverage for child plan code generation.
- Run Maven tests, frontend build, and `git diff --check`.

Rollback: revert only this requirement's code and ledger changes; no database compensation is needed.

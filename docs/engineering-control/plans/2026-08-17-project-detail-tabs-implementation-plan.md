# Project detail internal tabs implementation plan

## T1: Canonicalize shell tab identity

- Modify `web/src/views/AppLayout.vue` only.
- Add a project-detail tab path helper and use it for tab store opening and `UiTabs` current selection.
- Remove stale query-variant project tabs before opening the canonical detail tab.

## Verification

- Run frontend build and `git diff --check`.
- Use the running browser at `/projects/{projectId}` to click all internal tabs, inspect the global tab count, and refresh on a non-overview tab.

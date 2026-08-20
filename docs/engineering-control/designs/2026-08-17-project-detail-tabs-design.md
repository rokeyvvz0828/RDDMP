# Project detail internal tab design

## Decision

The global tab identity for a project detail route is its path (`/projects/{projectId}`), while the project sub-tab remains in the route query (`?tab=overview|plans|members|roles|settings`). `AppLayout` will use the canonical path only for the global tab bar and will remove any stale query-variant project detail entries from the in-memory tab store.

## Boundaries

- `ProjectView.vue` remains the owner of internal tab content and query synchronization.
- `UiTabs` remains unchanged and continues to navigate by its stored path.
- No API, data, permission, or backend behavior changes.

## States and verification

The shell must handle direct detail entry, internal tab query changes, refresh, leaving the project, and returning to another project. Loading, empty, error and permission states remain owned by `ProjectView.vue`.

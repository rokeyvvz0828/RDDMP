# Workflow List Pagination Design

The workflow module will expose `PageResult<T>` from the shared common module. Each list query performs a tenant-scoped count and a bounded `LIMIT/OFFSET` query with deterministic descending ordering.

The monitor query keeps all filters in the workflow service: business key, definition code/name, status, starter display name/username, and inclusive dates. The frontend sends empty filters as omitted values and resets the page to one after a filter change.

The existing workflow table and mobile card patterns remain in use. A shared `UiPagination` component will keep the page controls visually consistent across the four list modes.

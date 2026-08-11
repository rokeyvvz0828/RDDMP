---
id: REQ-20260811-019
status: ready
owner: rokeyvvz0828
module: platform/workflow
---

# Workflow List Pagination

## Goal

Add server-side pagination to workflow definitions, inbox, done, and monitor lists. Add monitor filters for business key, workflow name or code, status, starter, and start date range.

## In Scope

- Preserve tenant isolation, authorization, soft-delete behavior, and stable descending order.
- Return a shared page envelope with records, total, page, and size.
- Expose the same pagination behavior on desktop and mobile layouts.
- Reset to page one when the monitor filter changes or is reset.

## Acceptance

- Every workflow list request accepts `page` and `size` and returns a page envelope.
- Monitor requests apply all five filters in the backend query.
- Empty pages render a normal empty state without errors.
- Maven workflow/common tests and the frontend build pass.

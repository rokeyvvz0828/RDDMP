# Workflow List Pagination Implementation Plan

1. Add `PageResult<T>` and its unit test in `shared/common`.
2. Change workflow controller/service/monitor SQL contracts to accept `PageQuery` and return paged data.
3. Add typed pagination and monitor filter parameters to the workflow API client.
4. Add reusable pagination UI to `WorkflowView.vue` for desktop and mobile, including monitor filters.
5. Run Maven module tests, frontend build, governance checks, and browser smoke checks where the local service is available.

---
id: REQ-20260817-026
status: ready
owner: rokeyvvz0828
module: frontend/application
---

# Project detail internal tabs

## Objective

Keep project detail sub-tabs inside the single project detail application tab. Switching 项目概览、项目计划、项目成员、项目角色 and 项目设置 must not create additional global tabs.

## Scope

- Normalize the application tab identity for `/projects/{projectId}` while preserving the internal `tab` query for refresh and deep links.
- Remove stale query-variant project tabs from the in-memory tab list when the application shell observes a project detail route.
- Preserve existing tab context-menu behavior and all project APIs.

## Non-goals

- No backend, database, API, permission, or project business-rule changes.
- No changes to the reusable `UiTabs` contract or unrelated route query behavior.

## Acceptance criteria

1. Clicking each project detail sub-tab keeps exactly one global 项目详情 tab for that project.
2. The selected internal tab remains addressable through `route.query.tab` and survives browser refresh.
3. Existing global tabs and tab context-menu actions continue to work.
4. Frontend typecheck/build and browser checks pass at desktop and mobile viewports.

## Verification

- `npm --prefix web run build`
- `git diff --check`
- Browser route `/projects/{projectId}`: click all internal tabs, confirm global tab count, refresh and confirm active internal tab.

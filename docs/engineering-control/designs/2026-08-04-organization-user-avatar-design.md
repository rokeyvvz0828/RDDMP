# Organization, User Identity, and Avatar Design

Status: approved by user on 2026-08-04.

## Goal

Add hierarchical organization management, organization-aware user management, MinIO-backed user avatars, Chinese dynamic breadcrumbs, a reusable user identity component, and a component showcase page to the existing single-tenant Spring Boot 3 / Vue 3 platform.

## Decisions

- Organizations remain in `sys_org` with `parent_id`; the backend exposes a tree DTO rather than making the client infer hierarchy from a paged list.
- Organization UI is a tree with node actions. Selecting a node filters users; adding a user from a node preselects that organization.
- Users retain `org_id`; user list responses include organization name and avatar URL. User create/edit uses an organization tree selector.
- Avatars are uploaded through the backend to MinIO. The database stores only an object key. The backend returns a presigned URL or a controlled avatar URL; credentials never reach the browser.
- MinIO endpoint, credentials, bucket, and URL expiry are environment-configurable. Existing container endpoint defaults to `http://127.0.0.1:9000` and bucket defaults to `ccb-platform`.
- Breadcrumb labels are resolved from dynamic route menu names, with explicit Chinese static route metadata as fallback. Route codes are never displayed as labels.
- `UiUserIdentity` owns horizontal avatar/name layout and hover profile popover. It accepts a reusable user profile model and works in the header, tables, organization tree, and example page.
- The example page is a normal authenticated route and demonstrates all reusable UI primitives, including organization and user identity components.

## Boundaries

In scope: schema migration, MinIO storage adapter/configuration, org tree and avatar/user APIs, system management UI changes, route title resolution, reusable identity and org selector components, showcase page, seed menu.

Out of scope: multi-tenant changes, role redesign, direct browser-to-MinIO presigned upload, avatar crop editor, and bulk user import.

## Acceptance

- Organization management renders nested nodes and allows adding a user from a selected node.
- User create/edit can select an organization from a tree and displays organization name in the user list.
- A valid image can be uploaded to MinIO and the resulting avatar is visible in the header, user list, org tree, and hover profile.
- Invalid file type/size is rejected with a Chinese message; MinIO credentials are not present in browser responses.
- Breadcrumb displays Chinese menu names for dashboard, system, workflow, AI, and nested dynamic pages.
- Showcase route renders every reusable component without backend-only dependencies.
- Frontend production build, backend package, Flyway migration, and core browser flows pass.
# Organization, User Identity, and Avatar Implementation Plan

> Execute in the current workspace. Do not broaden into multi-tenancy or role redesign.

## Goal

Implement the approved design for hierarchical organizations, organization-aware users, MinIO avatars, Chinese breadcrumbs, reusable user identity UI, and a component showcase.

## Architecture

Use the existing generic system API for ordinary user CRUD, add dedicated organization tree and avatar endpoints where the generic resource contract is insufficient, and keep MinIO behind a backend storage service. Reuse Element Plus and the existing `components/ui` conventions.

## Tasks

### T1 - Database and storage foundation

Requirements: R1 organization tree, R2 user association, R4 avatar storage.

Files:
- Create `ccb-infrastructure/src/main/resources/db/migration/V10__user_avatar_support.sql`.
- Modify `ccb-infrastructure/pom.xml` and `ccb-boot/pom.xml` only if dependency ownership requires it.
- Create MinIO storage configuration/service under `ccb-infrastructure/src/main/java/com/ccb/infrastructure/storage/`.
- Modify `ccb-boot/src/main/resources/application-local.yml` with `ccb.storage.minio.*` environment-backed settings.

Contract:
- Add nullable `sys_user.avatar_object_key`.
- Storage service exposes upload, presigned URL, and delete operations.
- Bucket is created on demand; object keys are tenant/user scoped.

Verification: Flyway migrates from V9 to V10; backend context starts with storage disabled only when explicitly configured; upload service unit tests cover file type and size rejection.

Rollback: remove only V10 and storage code in a development database, or restore the pre-migration database snapshot.

### T2 - Organization and user backend APIs

Requirements: R1, R2, R4.

Files:
- Modify `ccb-system/src/main/java/com/ccb/system/service/SystemService.java` and `SystemController.java`.
- Create organization tree DTO/service/controller files under `ccb-system/src/main/java/com/ccb/system/`.
- Modify security auth models/repository/service/controller to include organization name and avatar URL.

Contract:
- `GET /api/system/orgs/tree` returns nested organization nodes with users.
- `GET /api/system/users?orgId=...` filters users and returns `orgName`, `avatarUrl`.
- `POST/PUT /api/system/users` accepts `org_id`.
- `POST /api/system/users/{id}/avatar` consumes multipart image and returns updated user profile.
- `DELETE /api/system/users/{id}/avatar` removes the object and clears the key.
- `GET /api/auth/me` includes `orgName` and `avatarUrl`.

Verification: authenticated API smoke tests cover nested orgs, user association, upload success, invalid media rejection, and tenant scoping.

### T3 - Frontend organization and user experience

Requirements: R1, R2, R3, R4.

Files:
- Modify `ccb-web/src/views/ModuleView.vue`, `src/api/system.ts`, `src/types/system.ts`, `src/types/auth.ts`, `src/stores/auth.ts`.
- Create `src/components/ui/UiOrgTree.vue` and `src/components/ui/UiOrgTreeSelect.vue`.
- Modify `src/views/AppLayout.vue` and add a menu-title resolver composable if needed.

Behavior:
- Organization section uses a tree and selected-node user view.
- User form uses the tree selector and avatar upload.
- Header displays the reusable user identity component.
- Breadcrumb resolves Chinese menu names from `auth.routes` by route path, with static Chinese fallback.

Verification: browser checks add child org, add user from org node, select org in user form, upload avatar, refresh session, and confirm Chinese breadcrumb.

### T4 - Reusable identity and showcase

Requirements: R5, R6.

Files:
- Create `ccb-web/src/components/ui/UiUserIdentity.vue`.
- Create `ccb-web/src/views/ComponentShowcaseView.vue`.
- Modify `ccb-web/src/router/index.ts`, `ccb-web/src/views/AppLayout.vue`, and menu seed migration if a visible menu entry is needed.

Behavior: horizontal avatar/name; hover/focus popover with username, organization, roles, and status; fallback initial when no image.

Verification: showcase route renders page header, toolbar, data table, status tag, form drawer, empty state, menu icon, org tree, org selector, and user identity.

### T5 - Regression verification

Commands:
- `npm run build` in `ccb-web`.
- `mvn -pl ccb-boot -am package -DskipTests` from workspace root.
- Start/restart backend with MinIO available and inspect `/actuator/health`.
- Browser smoke test at `http://127.0.0.1:5173/`.

Stop conditions: migration failure, storage credentials accidentally exposed to frontend, or existing login/menu/workflow/AI routes regress. Roll back the smallest task boundary and report the exact failure.
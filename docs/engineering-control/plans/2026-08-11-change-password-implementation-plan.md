# Change Password Implementation Plan

## Status and source

- Requirement: `docs/requirements/REQ-20260811-020-change-password/requirement.md`
- Design: `docs/engineering-control/designs/2026-08-11-change-password-design.md`
- Status: approved and ready for execution

## File map

- `ChangePasswordCommand.java`: validated request DTO.
- `AuthController.java`: authenticated HTTP endpoint.
- `AuthService.java`: old-password verification and BCrypt encoding orchestration.
- `AuthRepository.java`: tenant-scoped password update.
- `auth.ts`: authenticated API call and local session clear after success.
- `AppLayout.vue`: dropdown, dialog, field validation, and Chinese feedback for both headers.
- `styles.css`: only scoped user-menu/dialog adjustments if needed.

## Tasks

### T1 Backend password contract

Modify the security model, controller, service, and repository. Add focused tests for command validation and service outcomes. Run `mvn -pl :ccb-security -am test` and expect a successful reactor build.

Rollback: revert only the T1 security files listed in the task scope. Stop if the endpoint cannot enforce the authenticated user id and tenant id or if a migration appears necessary.

### T2 Frontend user menu

Add `changePassword` to the auth store. Replace both direct logout buttons with the same dropdown actions and dialog form. Keep the current shell layout and existing `UiUserIdentity` component. Verify cancel, validation, loading, failure preservation, success redirect, and mobile width behavior.

Rollback: revert only `auth.ts`, `AppLayout.vue`, and scoped styles. Stop if current logout or 401 redirect behavior regresses.

### T3 Integration checks

Run `npm --prefix web run build`, `git diff --check`, the security module tests, and browser smoke checks against the existing local frontend/backend. Record actual outputs and residual risks in the task ledger.

## Coverage

R1 is covered by T2, R2 and R3 by T1/T2, R4 by T2, and R5 by T3.

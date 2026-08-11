# Change Password Design

## Scope and decisions

The existing JWT-authenticated security module owns password verification and persistence. The browser shell owns the user menu, dialog state, validation feedback, session clearing, and navigation. The existing `sys_user.password_hash` column is reused; no migration or new API module is needed.

The new endpoint is `POST /api/auth/change-password`. It consumes `oldPassword`, `newPassword`, and `confirmPassword`, requires the current `AuthUser`, re-reads the user by id and tenant, verifies the old BCrypt hash, validates confirmation, and stores a newly encoded BCrypt hash. Error responses use the existing `ApiResponse` and Chinese business messages.

Both header variants in `AppLayout.vue` use one shared local menu/dialog state. The identity control opens `el-dropdown`; the dropdown contains `修改密码` and `退出登录`. Successful password change calls `auth.changePassword`, clears tokens and user state, closes the dialog, and routes to login. Logout remains confirmation-protected.

## Boundaries

- Inside: authenticated password-change contract, password hash update, shell dropdown/dialog.
- Outside: password reset, MFA, account recovery, migration changes, access-token revocation across other devices, user CRUD, workflow pagination.

## Failure behavior

- Empty or short fields are rejected in the dialog and by Bean Validation.
- Mismatched passwords return a bad-request error in Chinese.
- Incorrect old password returns a bad-request error without revealing stored hash details.
- A failed request keeps the dialog open and does not clear the session.
- A successful request clears local access and refresh tokens and redirects to login.

## Verification

Cover command validation, old-password rejection, BCrypt update invocation, frontend type-check/build, and browser inspection of the dropdown/dialog at desktop and mobile widths.

---
id: REQ-20260811-020
status: ready
owner: rokeyvvz0828
module: platform/security and frontend/application
---

# Change Password From User Menu

## Goal

Allow an authenticated user to open a user-menu dropdown from the top-right identity control and change the account password through a validated backend API.

## In Scope

- Show Chinese actions for change password and logout in both desktop shell header variants.
- Validate old password, new password, confirmation, and password length before submission.
- Verify the old BCrypt hash on the server and write a new BCrypt hash to the existing `sys_user.password_hash` column within the authenticated tenant.
- Clear the current browser session and redirect to the login route after a successful password change.
- Preserve existing logout behavior, authorization, tenant predicates, and workflow pagination changes.

## Acceptance

- Clicking the top-right user identity opens a dropdown instead of logging out immediately.
- The change-password dialog has old password, new password, confirmation, cancel, submit, loading, and Chinese validation/error states.
- An incorrect old password or mismatched confirmation is rejected without changing the stored hash.
- A valid request returns success, stores a BCrypt hash, clears both browser tokens, and navigates to `/login`.
- The backend security module tests and frontend build pass without modifying database migrations.

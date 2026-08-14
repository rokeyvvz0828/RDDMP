---
id: REQ-20260812-022
status: ready
owner: rokeyvvz0828
module: platform/system and frontend/application
---

# Project Management Enhancement

## Goal

Extend the existing project workbench with parameter-managed project and plan phases, project-manager role initialization, plan organization parties, and server-side date-range validation.

## In Scope

- Keep the project creation owner selector and default it to the current user.
- Create the project-local `PM` role named `项目负责人` during project creation and bind the creator as a project member with that role.
- Add project phase and plan phase values maintained through parameter management.
- Add one lead organization and multiple cooperating organizations to each plan.
- Require project and plan end dates to be greater than or equal to their start dates.
- Load phase and organization options through an authenticated project options endpoint.

## Acceptance

- Creating a project with the default owner creates the `PM/项目负责人` role and assigns it to the creator in the same transaction.
- The owner selector remains editable and defaults to the logged-in user.
- Project and plan phase selectors load active values from parameter data and show saved labels.
- Plan lead and cooperating organizations can be saved, edited, and displayed with organization names.
- Invalid project or plan date ranges are rejected by both the UI and API with Chinese validation messages.
- Existing project visibility, tenant boundaries, permissions, and hierarchical plans remain unchanged.

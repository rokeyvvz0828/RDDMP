# Workflow Node Visual Refresh Implementation Plan

> Execute within the approved workflow designer visual scope. Do not expand into backend behavior or data migration.

## Goal
Implement the approved BPMN-oriented node visual refresh while preserving four-side connection behavior.

## Constraints
- Vue 3, Vue Flow, and existing theme tokens only.
- Keep the current node type and edge model contracts.
- Preserve readable Chinese labels and existing readonly/selected states.

### T1: Refine node markup
Files:
- `ccb-web/src/components/workflow/WorkflowNode.vue`

Actions:
- Replace the permanent status dot with a compact type badge/icon treatment.
- Keep node-specific BPMN shapes and centered gateway symbols.
- Add explicit semantic classes for visible port state without changing handle IDs.

Verification:
- `npm run build` from `ccb-web`.
- DOM check for node type classes and four side positions.

### T2: Refine visual system
Files:
- `ccb-web/src/styles.css`

Actions:
- Reduce shadows, borders, and visual noise.
- Hide paired source/target handles by default while retaining hit areas.
- Reveal ports on node hover/selection and style selected nodes consistently.
- Ensure gateway rotation does not rotate handle visuals or text.

Verification:
- Local workflow designer screenshot/DOM inspection.
- Check light/dark theme variables continue to resolve.

### T3: Regression validation
Files:
- No new test file required for this narrow visual change.

Actions:
- Run `git diff --check`.
- Run `npm run build`.
- Open an existing flow and confirm it renders, can be selected, and retains saved edge handle IDs.

Rollback:
- Revert only T1/T2 changes if the visual check fails; leave prior workflow data untouched.
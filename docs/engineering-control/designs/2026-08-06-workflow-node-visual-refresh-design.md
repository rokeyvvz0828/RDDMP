# Workflow Node Visual Refresh Design

Status: approved

## Goal
Make the workflow designer visually readable and closer to BPMN conventions without changing workflow semantics or persisted graph contracts.

## Scope
- Refine `WorkflowNode.vue` structure and state presentation.
- Refine workflow node and handle styles in `ccb-web/src/styles.css`.
- Preserve four-direction source/target connectivity and existing handle IDs.
- Do not change backend validation, Flowable behavior, or graph persistence fields.

## Visual Rules
- Start and end events remain circular and use restrained status colors.
- Approval and CC nodes use compact task blocks with a type icon, title, and one-line rule summary.
- Condition, parallel split, and parallel join remain diamond gateways with centered BPMN symbols.
- Remove the decorative status dot and heavy shadow; use a thin type accent and a clear selected ring.
- Source and target handles remain available on all four sides but are visually merged into one small port per side and revealed on hover/selection.
- Edge arrows remain the primary flow-direction signal.

## Acceptance Criteria
1. Every node type is visually distinguishable at normal zoom.
2. Nodes do not show a permanent cluster of eight visible circles.
3. Hovering or selecting a node exposes four usable connection sides.
4. Start/end restrictions remain intact.
5. Existing saved graphs load without schema migration and preserve sourceHandle/targetHandle.
6. Frontend typecheck and production build pass.

## Out Of Scope
- Workflow validation rules.
- Backend APIs and database schema.
- Layout algorithm changes.
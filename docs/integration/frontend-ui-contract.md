# Frontend UI Integration Contract

Feature modules use the shared shell and components under `ccb-web/src/components/ui`.

## Page contract

- Use `UiPageHeader` for the page title and primary action.
- Use `UiToolbar` for filters and refresh actions.
- Use `UiDataTable` for tabular resources, including loading, empty, and footer states.
- Use `UiFormDrawer` for create and edit forms.
- Use `UiStatusTag` for lifecycle states instead of feature-specific status colors.
- Use `UiEmptyState` when a page has no table-shaped content.

## Theme and layout contract

Feature CSS consumes semantic variables such as `--page-bg`, `--panel-bg`, `--line`, `--text`, `--muted`, `--brand`, `--success`, `--warning`, and `--danger`. Do not hard-code palette colors in feature pages. Route menus must provide `routePath`, `menuName`, `icon`, and nested `children`; the shell supports side, top, and mixed layouts without feature changes.

## API contract

Every request uses `/api`, and every authenticated module receives the current tenant from the server-side JWT context. Frontend code must not send or persist provider secrets outside the server API boundary.

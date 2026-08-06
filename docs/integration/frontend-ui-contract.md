# Frontend UI Integration Contract

Feature modules use the shared shell and components under `web/src/components/ui`.

## Delivery showcase first

Business feature pages must first inspect and reuse the patterns in `web/src/modules/delivery-showcase/`, including page composition, list and form layouts, workflow presentation, charts, responsive behavior, and semantic theme usage. A new visual pattern is an exception: the requirement design or current `.ai-control` task record must state why the showcase cannot cover the business shape, which shared pieces are still reused, the limited scope of the new pattern, and its regression evidence.

## Page contract

- Use `UiPageHeader` for the page title and primary action.
- Use `UiToolbar` for filters and refresh actions.
- Use `UiDataTable` for tabular resources, including loading, empty, and footer states.
- Use `UiFormDrawer` for create and edit forms.
- Use `UiStatusTag` for lifecycle states instead of feature-specific status colors.
- Use `UiEmptyState` when a page has no table-shaped content.
- Use `UiFilePreview` for online document viewing. Obtain preview URLs from `api/file-preview.ts`; feature pages must not build kkFileView URLs or accept arbitrary remote source URLs.

## Theme and layout contract

Feature CSS consumes semantic variables such as `--page-bg`, `--panel-bg`, `--line`, `--text`, `--muted`, `--brand`, `--success`, `--warning`, and `--danger`. Do not hard-code palette colors in feature pages. Route menus must provide `routePath`, `menuName`, `icon`, and nested `children`; the shell supports side, top, and mixed layouts without feature changes.

## API contract

Every request uses `/api`, and every authenticated module receives the current tenant from the server-side JWT context. Frontend code must not send or persist provider secrets outside the server API boundary.

File preview capability and limits come from `GET /api/file-previews/capabilities`. Uploads use authenticated multipart requests to `POST /api/file-previews`; temporary objects are removed with `DELETE /api/file-previews/{previewId}`. The backend is the only owner of MinIO object keys and kkFileView URL encoding.

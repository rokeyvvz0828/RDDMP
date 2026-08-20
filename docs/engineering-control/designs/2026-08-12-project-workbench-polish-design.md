# Project Workbench Polish Design

The project detail page will use a two-column overview: the left column contains the description and fact grid, while the right column contains the calendar. On narrow screens the columns stack. Calendar cells get a stable minimum height and compact plan rows.

The project refresh path will be split from navigation. `openProject` continues to open a project and select overview; an internal refresh function reloads the selected project without changing `activeTab`. Mutating project tabs will use this refresh function.

Fixed Element Plus columns will receive explicit opaque backgrounds on the fixed wrapper, cells, pseudo-elements, and table body overlay, with a higher stacking level than the scroll wrapper.

The server will retain the V39-compatible child-number defaults when the project column is null or blank. Child numbering remains transactionally generated from the locked parent row and its independent sequence.

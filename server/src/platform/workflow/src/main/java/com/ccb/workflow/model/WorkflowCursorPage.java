package com.ccb.workflow.model;

import java.util.List;

/** Bounded forward-only workflow list response. */
public record WorkflowCursorPage<T>(List<T> records, String nextCursor, boolean hasMore) {
}

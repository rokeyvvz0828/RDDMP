package com.ccb.common.api;

import java.util.List;

/** Common server-side page envelope shared by platform modules. */
public record PageResult<T>(List<T> records, long total, long page, long size) {
}

package com.ccb.system.model;

import java.util.List;

public record SystemPage<T>(List<T> records, long total, long page, long size) {
}

package com.ccb.common.api;

public record PageQuery(long page, long size) {
    public PageQuery {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }
    }
}

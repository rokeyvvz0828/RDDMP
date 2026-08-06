package com.ccb.common.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageQueryTest {
    @Test
    void clampsInvalidPageValues() {
        PageQuery query = new PageQuery(0, 1000);

        assertEquals(1, query.page());
        assertEquals(100, query.size());
    }
}

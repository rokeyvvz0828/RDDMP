package com.ccb.common.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageResultTest {
    @Test
    void keepsPageMetadataAndRecords() {
        PageResult<String> result = new PageResult<>(List.of("one"), 21, 2, 20);

        assertEquals(List.of("one"), result.records());
        assertEquals(21, result.total());
        assertEquals(2, result.page());
        assertEquals(20, result.size());
    }
}

package com.feijimiao.xianyuassistant.service.impl;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderServiceImplTest {
    private final OrderServiceImpl orderService = new OrderServiceImpl();

    @Test
    void shouldRecognizeStringPaginationFlags() {
        assertTrue(orderService.resolveHasMore(Map.of("hasMore", "true"), 1, 50, 50));
        assertFalse(orderService.resolveHasMore(Map.of("hasMore", "false"), 1, 50, 50));
        assertFalse(orderService.resolveHasMore(Map.of("hasNext", "0"), 1, 50, 50));
    }

    @Test
    void shouldRejectUnknownPaginationFlag() {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.resolveHasMore(Map.of("hasMore", "unknown"), 1, 50, 50));
    }
}

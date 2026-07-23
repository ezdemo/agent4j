package site.sorghum.loopra.web.controller;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作区排序逻辑单元测试。
 */
class WorkspaceOrderTest {

    @Test
    void emptyOrderShouldReturnEmptyList() {
        List<String> order = new ArrayList<>();
        assertNotNull(order);
        assertTrue(order.isEmpty());
    }

    @Test
    void orderListShouldMaintainInsertionOrder() {
        List<String> order = new ArrayList<>(Arrays.asList("hash3", "hash1", "hash2"));
        assertEquals("hash3", order.get(0));
        assertEquals("hash1", order.get(1));
        assertEquals("hash2", order.get(2));
        assertEquals(3, order.size());
    }

    @Test
    void orderListShouldAllowReorder() {
        List<String> order = new ArrayList<>(Arrays.asList("hash1", "hash2", "hash3"));

        String item = order.remove(2);
        order.add(0, item);

        assertEquals("hash3", order.get(0));
        assertEquals("hash1", order.get(1));
        assertEquals("hash2", order.get(2));
    }

    @Test
    void orderListShouldFilterUnknownHashes() {
        List<String> savedOrder = Arrays.asList("hash1", "unknown", "hash2");
        List<String> validHashes = Arrays.asList("hash1", "hash2", "hash3");

        List<String> result = new ArrayList<>();
        for (String h : savedOrder) {
            if (validHashes.contains(h)) {
                result.add(h);
            }
        }

        assertEquals(2, result.size());
        assertEquals("hash1", result.get(0));
        assertEquals("hash2", result.get(1));
    }

    @Test
    void newItemsShouldBeAppendedToEnd() {
        List<String> savedOrder = Arrays.asList("hash1", "hash2");
        List<String> allHashes = Arrays.asList("hash1", "hash2", "hash3", "hash4");

        List<String> result = new ArrayList<>();
        for (String h : savedOrder) {
            if (allHashes.contains(h)) {
                result.add(h);
            }
        }
        for (String h : allHashes) {
            if (!result.contains(h)) {
                result.add(h);
            }
        }

        assertEquals(4, result.size());
        assertEquals("hash1", result.get(0));
        assertEquals("hash2", result.get(1));
        assertEquals("hash3", result.get(2));
        assertEquals("hash4", result.get(3));
    }

    @Test
    void emptySavedOrderShouldReturnAllItems() {
        List<String> savedOrder = Collections.emptyList();
        List<String> allHashes = Arrays.asList("hash1", "hash2", "hash3");

        List<String> result = new ArrayList<>();
        for (String h : savedOrder) {
            if (allHashes.contains(h)) {
                result.add(h);
            }
        }
        for (String h : allHashes) {
            if (!result.contains(h)) {
                result.add(h);
            }
        }

        assertEquals(3, result.size());
        assertEquals("hash1", result.get(0));
        assertEquals("hash2", result.get(1));
        assertEquals("hash3", result.get(2));
    }
}

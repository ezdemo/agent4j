package site.sorghum.loopra.web.controller;

import org.junit.jupiter.api.Test;
import site.sorghum.loopra.web.model.WorkspaceInfoDTO;
import site.sorghum.loopra.web.service.AgentService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作区排序合并逻辑单元测试。
 * <p>
 * 验证 {@link AgentService#applyWorkspaceOrder(List, List)}：
 * 已保存排序的 hash 按顺序在前，新工作区追加到末尾，已删除的 hash 被忽略。
 */
class WorkspaceOrderTest {

    private static WorkspaceInfoDTO workspace(String hash) {
        return new WorkspaceInfoDTO(hash, "ws-" + hash, "/path/" + hash, 0, 0, 0);
    }

    private static List<String> hashes(List<WorkspaceInfoDTO> workspaces) {
        List<String> result = new ArrayList<>();
        for (WorkspaceInfoDTO workspace : workspaces) {
            result.add(workspace.hash());
        }
        return result;
    }

    @Test
    void emptyOrderShouldReturnOriginalList() {
        List<WorkspaceInfoDTO> workspaces = Arrays.asList(workspace("h1"), workspace("h2"), workspace("h3"));
        assertEquals(workspaces, AgentService.applyWorkspaceOrder(workspaces, Collections.emptyList()));
        assertEquals(workspaces, AgentService.applyWorkspaceOrder(workspaces, null));
    }

    @Test
    void orderListShouldMaintainSavedOrder() {
        List<WorkspaceInfoDTO> workspaces = Arrays.asList(workspace("h1"), workspace("h2"), workspace("h3"));
        List<WorkspaceInfoDTO> result = AgentService.applyWorkspaceOrder(workspaces, Arrays.asList("h3", "h1", "h2"));
        assertEquals(Arrays.asList("h3", "h1", "h2"), hashes(result));
    }

    @Test
    void orderListShouldAllowReorder() {
        List<WorkspaceInfoDTO> workspaces = Arrays.asList(workspace("h1"), workspace("h2"), workspace("h3"));
        List<WorkspaceInfoDTO> result = AgentService.applyWorkspaceOrder(workspaces, Arrays.asList("h3", "h1", "h2"));
        assertEquals(Arrays.asList("h3", "h1", "h2"), hashes(result));
    }

    @Test
    void orderListShouldFilterUnknownHashes() {
        List<WorkspaceInfoDTO> workspaces = Arrays.asList(workspace("h1"), workspace("h2"), workspace("h3"));
        List<WorkspaceInfoDTO> result = AgentService.applyWorkspaceOrder(workspaces, Arrays.asList("h1", "unknown", "h2"));
        assertEquals(Arrays.asList("h1", "h2", "h3"), hashes(result));
    }

    @Test
    void newItemsShouldBeAppendedToEnd() {
        List<WorkspaceInfoDTO> workspaces = Arrays.asList(workspace("h1"), workspace("h2"), workspace("h3"), workspace("h4"));
        List<WorkspaceInfoDTO> result = AgentService.applyWorkspaceOrder(workspaces, Arrays.asList("h1", "h2"));
        assertEquals(Arrays.asList("h1", "h2", "h3", "h4"), hashes(result));
    }

    @Test
    void emptySavedOrderShouldReturnAllItems() {
        List<WorkspaceInfoDTO> workspaces = Arrays.asList(workspace("h1"), workspace("h2"), workspace("h3"));
        List<WorkspaceInfoDTO> result = AgentService.applyWorkspaceOrder(workspaces, Collections.emptyList());
        assertEquals(Arrays.asList("h1", "h2", "h3"), hashes(result));
    }
}

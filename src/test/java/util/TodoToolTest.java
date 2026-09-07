package util;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TodoTool 单元测试
 * 核心验证：基于 __sessionId 的多会话隔离（面试要求2 的 session 管理能力）
 */
class TodoToolTest {

    private final TodoTool tool = new TodoTool();

    @Test
    void nameShouldBeStable() {
        assertEquals("todo_manager", tool.getName());
    }

    @Test
    void shouldIsolateTodosBySessionId() {
        // 模拟 AgentRuntime 注入 __sessionId
        tool.execute(Map.of("action", "add", "content", "下午三点开会", "__sessionId", "window-1"));
        tool.execute(Map.of("action", "add", "content", "晚上写周报", "__sessionId", "window-2"));

        String list1 = tool.execute(Map.of("action", "list", "__sessionId", "window-1"));
        String list2 = tool.execute(Map.of("action", "list", "__sessionId", "window-2"));

        assertTrue(list1.contains("下午三点开会"), "窗口1 应包含自己的待办");
        assertFalse(list1.contains("晚上写周报"), "窗口1 不应看到窗口2 的待办（隔离生效）");

        assertTrue(list2.contains("晚上写周报"), "窗口2 应包含自己的待办");
        assertFalse(list2.contains("下午三点开会"), "窗口2 不应看到窗口1 的待办（隔离生效）");
    }

    @Test
    void shouldFallBackToDefaultWhenNoSessionId() {
        tool.execute(Map.of("action", "add", "content", "无会话ID的待办"));
        String result = tool.execute(Map.of("action", "list"));
        assertTrue(result.contains("无会话ID的待办"), "未注入 __sessionId 应落到 default 用户");
    }

    @Test
    void shouldRejectUnsupportedAction() {
        String result = tool.execute(Map.of("action", "delete", "__sessionId", "x"));
        assertEquals("不支持的操作类型", result);
    }

    @Test
    void schemaShouldRequireActionField() {
        Map<String, Object> schema = tool.getParametersSchema();
        @SuppressWarnings("unchecked")
        java.util.List<String> required = (java.util.List<String>) schema.get("required");
        assertTrue(required.contains("action"));
    }

    @Test
    void shouldRejectEmptyContentOnAdd() {
        // LLM 可能漏传 content，应友好返回错误而非写入 null
        String result = tool.execute(Map.of("action", "add", "__sessionId", "x"));
        assertTrue(result.contains("不能为空"), "空 content 应被拒绝，实际：" + result);
    }
}

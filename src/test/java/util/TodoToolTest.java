package util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TodoTool 单元测试
 * 核心验证：基于 __sessionId 的多会话隔离、结构化待办的增删改、文件持久化
 */
class TodoToolTest {

    @TempDir
    Path tempDir;

    private TodoTool newTool() {
        // 每个测试用独立文件，互不干扰，也不污染默认 data/todos.json
        return new TodoTool(tempDir.resolve("todos.json"));
    }

    private String extractId(String listResult) {
        Matcher m = Pattern.compile("id: (todo-\\w+)").matcher(listResult);
        assertTrue(m.find(), "list 结果应包含待办 id，实际：" + listResult);
        return m.group(1);
    }

    @Test
    void nameShouldBeStable() {
        assertEquals("todo_manager", newTool().getName());
    }

    @Test
    void shouldIsolateTodosBySessionId() {
        TodoTool tool = newTool();
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
        TodoTool tool = newTool();
        tool.execute(Map.of("action", "add", "content", "无会话ID的待办"));
        String result = tool.execute(Map.of("action", "list"));
        assertTrue(result.contains("无会话ID的待办"), "未注入 __sessionId 应落到 default 会话");
    }

    @Test
    void shouldRejectUnsupportedAction() {
        TodoTool tool = newTool();
        String result = tool.execute(Map.of("action", "dance", "__sessionId", "x"));
        assertTrue(result.contains("不支持"), "非法 action 应被拒绝，实际：" + result);
    }

    @Test
    void schemaShouldRequireActionField() {
        Map<String, Object> schema = newTool().getParametersSchema();
        @SuppressWarnings("unchecked")
        java.util.List<String> required = (java.util.List<String>) schema.get("required");
        assertTrue(required.contains("action"));
    }

    @Test
    void shouldRejectEmptyContentOnAdd() {
        TodoTool tool = newTool();
        String result = tool.execute(Map.of("action", "add", "__sessionId", "x"));
        assertTrue(result.contains("不能为空"), "空 content 应被拒绝，实际：" + result);
    }

    @Test
    void shouldMarkTodoComplete() {
        TodoTool tool = newTool();
        tool.execute(Map.of("action", "add", "content", "写周报", "__sessionId", "s"));
        String id = extractId(tool.execute(Map.of("action", "list", "__sessionId", "s")));

        String done = tool.execute(Map.of("action", "complete", "todo_id", id, "__sessionId", "s"));
        assertTrue(done.contains("已标记完成"), "complete 应成功，实际：" + done);

        String list = tool.execute(Map.of("action", "list", "__sessionId", "s"));
        assertTrue(list.contains("[x]"), "完成后应显示 [x]，实际：" + list);
    }

    @Test
    void shouldDeleteTodo() {
        TodoTool tool = newTool();
        tool.execute(Map.of("action", "add", "content", "待删除项", "__sessionId", "s"));
        String id = extractId(tool.execute(Map.of("action", "list", "__sessionId", "s")));

        String deleted = tool.execute(Map.of("action", "delete", "todo_id", id, "__sessionId", "s"));
        assertTrue(deleted.contains("已删除"), "delete 应成功，实际：" + deleted);

        String list = tool.execute(Map.of("action", "list", "__sessionId", "s"));
        assertFalse(list.contains("待删除项"), "删除后不应再出现");
    }

    @Test
    void shouldRejectCompleteWithoutId() {
        TodoTool tool = newTool();
        String result = tool.execute(Map.of("action", "complete", "__sessionId", "s"));
        assertTrue(result.contains("todo_id"), "缺 id 应提示，实际：" + result);
    }

    @Test
    void shouldPersistAcrossInstances() {
        Path file = tempDir.resolve("persist.json");
        TodoTool first = new TodoTool(file);
        first.execute(Map.of("action", "add", "content", "重启后还在的待办", "__sessionId", "s"));

        // 模拟进程重启：用同一文件新建实例，应能读回
        TodoTool second = new TodoTool(file);
        String list = second.execute(Map.of("action", "list", "__sessionId", "s"));
        assertTrue(list.contains("重启后还在的待办"), "新实例应从文件加载待办，实际：" + list);
    }
}

package util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TodoTool implements Tool {
    // 按用户存储待办，实际可持久化
    private final Map<String, String> userTodos = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "todo_manager";
    }

    @Override
    public String getDescription() {
        return "待办事项管理工具，可以添加、查看用户的待办清单";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> action = new HashMap<>();
        action.put("type", "string");
        action.put("description", "操作类型：add 添加，list 查看");
        properties.put("action", action);
        
        Map<String, Object> content = new HashMap<>();
        content.put("type", "string");
        content.put("description", "待办内容，add 操作时必填");
        properties.put("content", content);
        
        schema.put("properties", properties);
        schema.put("required", List.of("action"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String action = (String) params.get("action");
        // 从 AgentRuntime 注入的会话ID读取用户标识，实现多会话隔离
        String userId = (String) params.getOrDefault("__sessionId", "default");

        if ("list".equals(action)) {
            return "你的待办清单：\n" + userTodos.getOrDefault(userId, "暂无待办");
        } else if ("add".equals(action)) {
            String content = (String) params.get("content");
            String old = userTodos.getOrDefault(userId, "");
            userTodos.put(userId, old + "- " + content + "\n");
            return "已添加待办：" + content;
        }
        return "不支持的操作类型";
    }
}

package util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 待办事项管理工具。
 * - 结构化存储：每条待办是 {id, content, done}，支持 add/list/complete/delete/clear
 * - 按会话（__sessionId）隔离，互不影响
 * - 文件持久化：以 JSON 落盘（默认 data/todos.json，可用环境变量 TODO_STORE_PATH 覆盖），
 *   进程重启后待办不丢失；写入采用临时文件 + 原子替换，避免写坏文件
 */
public class TodoTool implements Tool {
    private static final Path DEFAULT_STORE = Paths.get(
            System.getenv().getOrDefault("TODO_STORE_PATH", "data/todos.json"));

    private final Path storeFile;
    private final ObjectMapper objectMapper;
    // sessionId -> 该会话的待办列表
    private final Map<String, List<Item>> todos;
    // 变更与落盘的锁
    private final Object lock = new Object();

    public TodoTool() {
        this(DEFAULT_STORE);
    }

    /** 测试可传入独立文件路径，避免污染默认存储 */
    public TodoTool(Path storeFile) {
        this.storeFile = storeFile;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.todos = load();
    }

    /** 待办条目，public 字段供 Jackson 直接序列化 */
    public static class Item {
        public String id;
        public String content;
        public boolean done;

        public Item() {
        }

        public Item(String id, String content) {
            this.id = id;
            this.content = content;
            this.done = false;
        }
    }

    @Override
    public String getName() {
        return "todo_manager";
    }

    @Override
    public String getDescription() {
        return "待办事项管理工具，支持添加、查看、标记完成、删除、清空待办，按会话隔离";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> action = new HashMap<>();
        action.put("type", "string");
        action.put("enum", List.of("add", "list", "complete", "delete", "clear"));
        action.put("description", "操作类型：add 添加，list 查看，complete 标记完成，delete 删除，clear 清空当前会话待办");
        properties.put("action", action);

        Map<String, Object> content = new HashMap<>();
        content.put("type", "string");
        content.put("description", "待办内容，add 操作时必填");
        properties.put("content", content);

        Map<String, Object> todoId = new HashMap<>();
        todoId.put("type", "string");
        todoId.put("description", "待办ID，complete/delete 操作时必填（值来自 list 返回的 id）");
        properties.put("todo_id", todoId);

        schema.put("properties", properties);
        schema.put("required", List.of("action"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String action = (String) params.get("action");
        // AgentRuntime 注入的会话ID，实现多会话隔离；未注入时落到 default
        String sessionId = (String) params.getOrDefault("__sessionId", "default");
        if (action == null) {
            return "操作失败：缺少 action 参数";
        }
        switch (action) {
            case "add":
                return add(sessionId, params);
            case "list":
                return list(sessionId);
            case "complete":
                return complete(sessionId, params);
            case "delete":
                return delete(sessionId, params);
            case "clear":
                return clear(sessionId);
            default:
                return "不支持的操作类型，支持：add / list / complete / delete / clear";
        }
    }

    private String add(String sessionId, Map<String, Object> params) {
        String content = (String) params.get("content");
        if (content == null || content.isBlank()) {
            return "添加失败：待办内容不能为空";
        }
        synchronized (lock) {
            List<Item> items = todos.computeIfAbsent(sessionId, k -> new ArrayList<>());
            String id = "todo-" + UUID.randomUUID().toString().substring(0, 8);
            items.add(new Item(id, content.trim()));
            persist();
        }
        return "已添加待办：" + content.trim();
    }

    private String list(String sessionId) {
        synchronized (lock) {
            List<Item> items = todos.get(sessionId);
            if (items == null || items.isEmpty()) {
                return "你的待办清单为空（暂无待办）";
            }
            StringBuilder sb = new StringBuilder("你的待办清单：\n");
            int index = 1;
            for (Item it : items) {
                sb.append(it.done ? "[x] " : "[ ] ")
                        .append(index++).append(". ").append(it.content)
                        .append("（id: ").append(it.id).append("）\n");
            }
            return sb.toString().stripTrailing();
        }
    }

    private String complete(String sessionId, Map<String, Object> params) {
        String id = (String) params.get("todo_id");
        if (id == null || id.isBlank()) {
            return "操作失败：请提供 todo_id（可先 list 查看）";
        }
        synchronized (lock) {
            Item item = findItem(sessionId, id);
            if (item == null) {
                return "未找到待办：" + id;
            }
            item.done = true;
            persist();
            return "已标记完成：" + item.content;
        }
    }

    private String delete(String sessionId, Map<String, Object> params) {
        String id = (String) params.get("todo_id");
        if (id == null || id.isBlank()) {
            return "操作失败：请提供 todo_id（可先 list 查看）";
        }
        synchronized (lock) {
            List<Item> items = todos.get(sessionId);
            if (items == null || !items.removeIf(it -> it.id.equals(id))) {
                return "未找到待办：" + id;
            }
            persist();
            return "已删除待办：" + id;
        }
    }

    private String clear(String sessionId) {
        synchronized (lock) {
            todos.remove(sessionId);
            persist();
        }
        return "已清空当前会话的全部待办";
    }

    private Item findItem(String sessionId, String id) {
        List<Item> items = todos.get(sessionId);
        if (items == null) {
            return null;
        }
        for (Item it : items) {
            if (it.id.equals(id)) {
                return it;
            }
        }
        return null;
    }

    // ============ 持久化 ============

    private Map<String, List<Item>> load() {
        try {
            if (Files.exists(storeFile)) {
                Map<String, List<Item>> data = objectMapper.readValue(
                        storeFile.toFile(), new TypeReference<Map<String, List<Item>>>() {});
                return new ConcurrentHashMap<>(data);
            }
        } catch (IOException e) {
            System.err.println("[TodoTool] 加载待办文件失败，将重新初始化：" + e.getMessage());
        }
        return new ConcurrentHashMap<>();
    }

    private void persist() {
        try {
            Path parent = storeFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // 先写临时文件再原子替换，避免中途崩溃写坏数据
            Path tmp = storeFile.resolveSibling(storeFile.getFileName() + ".tmp");
            objectMapper.writeValue(tmp.toFile(), todos);
            try {
                Files.move(tmp, storeFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
                Files.move(tmp, storeFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[TodoTool] 持久化失败：" + e.getMessage());
        }
    }
}

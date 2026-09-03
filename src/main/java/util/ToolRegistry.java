package util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {
    private final Map<String, Tool> toolMap = new HashMap<>();

    // 注册工具
    public void registerTool(Tool tool) {
        toolMap.put(tool.getName(), tool);
    }

    // 根据名称获取工具
    public Tool getTool(String name) {
        return toolMap.get(name);
    }

    // 获取所有工具的 Schema，传给 LLM
    public List<Map<String, Object>> getAllToolsSchema() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (Tool tool : toolMap.values()) {
            Map<String, Object> toolSchema = new HashMap<>();
            toolSchema.put("type", "function");
            Map<String, Object> function = new HashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.put("parameters", tool.getParametersSchema());
            toolSchema.put("function", function);
            schemas.add(toolSchema);
        }
        return schemas;
    }
}

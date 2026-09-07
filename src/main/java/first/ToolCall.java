package first;

/**
 * LLM 返回的工具调用指令
 */
public class ToolCall {
    // 调用ID，用于回传结果
    private String id;
    // 工具名称
    private String name;
    // 工具参数（JSON 字符串）
    private String arguments;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }
}

package frist;

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

    // getter/setter 省略
}

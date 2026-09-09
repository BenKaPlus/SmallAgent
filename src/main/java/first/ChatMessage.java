package first;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 聊天消息，严格按 OpenAI Chat Completions 协议建模。
 *
 * 协议要点：
 * - assistant 发起工具调用时，消息体携带 tool_calls 数组（含 id / function.name / function.arguments）
 * - 工具执行结果以 role=tool 的消息回传，必须用 tool_call_id 字段关联到对应调用
 * - 字段名必须是蛇形（tool_calls / tool_call_id），且为空的字段不应序列化进请求体
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {
    // 角色：system / user / assistant / tool
    private final String role;
    // 消息内容（assistant 发起工具调用时可为空串）
    private final String content;
    // assistant 发起的工具调用，序列化为 tool_calls
    private final List<Map<String, Object>> toolCalls;
    // tool 角色消息关联的调用 ID，序列化为 tool_call_id
    private final String toolCallId;

    /** 普通消息（system / user / 直接回复的 assistant） */
    public ChatMessage(String role, String content) {
        this(role, content, null, null);
    }

    /** tool 角色消息：content=工具结果，toolCallId=对应的工具调用 ID */
    public ChatMessage(String role, String content, String toolCallId) {
        this(role, content, null, toolCallId);
    }

    /** 携带工具调用的 assistant 消息 */
    public ChatMessage(String role, String content, List<Map<String, Object>> toolCalls) {
        this(role, content, toolCalls, null);
    }

    private ChatMessage(String role, String content,
                        List<Map<String, Object>> toolCalls, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    @JsonProperty("tool_calls")
    public List<Map<String, Object>> getToolCalls() {
        return toolCalls;
    }

    @JsonProperty("tool_call_id")
    public String getToolCallId() {
        return toolCallId;
    }
}

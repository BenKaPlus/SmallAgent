package first;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatMessage 协议序列化测试
 * 核心：发出的 JSON 必须符合 OpenAI Function Call 协议——
 * assistant 工具调用字段名是 tool_calls，tool 结果关联字段名是 tool_call_id，且不输出 null 字段
 */
class ChatMessageProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void assistantToolCallsShouldSerializeAsToolCalls() throws Exception {
        Map<String, Object> function = Map.of("name", "calculator", "arguments", "{\"expression\":\"1+1\"}");
        Map<String, Object> toolCall = Map.of("id", "call_1", "type", "function", "function", function);
        ChatMessage msg = new ChatMessage("assistant", "", List.of(toolCall));

        String json = objectMapper.writeValueAsString(msg);

        assertTrue(json.contains("\"tool_calls\""), "assistant 工具调用应序列化为 tool_calls：" + json);
        assertTrue(json.contains("call_1"), "应保留调用 id 供 tool 消息配对：" + json);
        assertFalse(json.contains("toolCallId"), "不应出现驼峰字段名：" + json);
        assertFalse(json.contains("\"tool_call_id\""), "assistant 消息不应带 tool_call_id：" + json);
    }

    @Test
    void toolMessageShouldSerializeAsToolCallId() throws Exception {
        ChatMessage msg = new ChatMessage("tool", "计算结果：2", "call_1");

        String json = objectMapper.writeValueAsString(msg);

        assertTrue(json.contains("\"tool_call_id\":\"call_1\""),
                "tool 结果必须用 tool_call_id 关联调用：" + json);
        assertFalse(json.contains("toolCallId"), "不应出现驼峰字段名：" + json);
        assertFalse(json.contains("tool_calls"), "tool 消息不应带 tool_calls：" + json);
    }

    @Test
    void plainMessageShouldOmitNullFields() throws Exception {
        ChatMessage msg = new ChatMessage("assistant", "你好");

        String json = objectMapper.writeValueAsString(msg);

        assertTrue(json.contains("\"content\":\"你好\""), json);
        assertFalse(json.contains("tool_calls"), "普通消息不应出现 tool_calls：" + json);
        assertFalse(json.contains("tool_call_id"), "普通消息不应出现 tool_call_id：" + json);
        assertFalse(json.contains("null"), "NON_NULL 生效，不应序列化 null 字段：" + json);
    }
}

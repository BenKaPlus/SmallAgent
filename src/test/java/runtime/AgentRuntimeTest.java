package runtime;

import llm.LlmClient;
import org.junit.jupiter.api.Test;
import session.SessionManager;
import util.MockSearchTool;
import util.ToolRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentRuntime 单元测试
 * 核心验证：循环上限保护（MAX_LOOP_COUNT）—— LLM 持续要求调用工具时不会死循环
 */
class AgentRuntimeTest {

    /**
     * Stub 版 LlmClient：chatCompletion 永远返回"调用 calculator 工具"
     * 用来触发 AgentRuntime 的循环上限保护
     */
    private static class StubLlmClient extends LlmClient {
        StubLlmClient() {
            // 真实构造参数不会用到，因为 chatCompletion 被重写
            super("stub-key", "https://stub.example.com/v1", "stub-model");
        }

        @Override
        public Map<String, Object> chatCompletion(List<first.ChatMessage> messages, List<Map<String, Object>> tools) {
            // 构造一个永远要求调用 calculator 的响应
            Map<String, Object> function = new HashMap<>();
            function.put("name", "web_search");
            function.put("arguments", "{\"query\":\"test\"}");

            Map<String, Object> toolCall = new HashMap<>();
            toolCall.put("id", "call-stub");
            toolCall.put("type", "function");
            toolCall.put("function", function);

            Map<String, Object> message = new HashMap<>();
            message.put("content", "我来搜索一下");
            message.put("tool_calls", List.of(toolCall));
            return message;
        }
    }

    @Test
    void shouldStopAtMaxLoopCountAndNotDeadlock() {
        LlmClient stub = new StubLlmClient();
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.registerTool(new MockSearchTool());
        SessionManager sessionManager = new SessionManager();
        AgentRuntime runtime = new AgentRuntime(stub, toolRegistry, sessionManager);

        // Stub 永远要求调工具，应被 MAX_LOOP_COUNT 拦下
        String reply = runtime.chat("test-session", "搜索测试");

        assertNotNull(reply);
        assertTrue(reply.contains("处理步骤过多") || reply.contains("换一种问法"),
                "应返回循环超限提示，实际：" + reply);
    }
}

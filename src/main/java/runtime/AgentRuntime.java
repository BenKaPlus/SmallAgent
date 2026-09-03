package runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import frist.ChatMessage;
import llm.LlmClient;
import session.AgentSession;
import session.SessionManager;
import util.ToolRegistry;
import java.util.List;
import java.util.Map;

public class AgentRuntime {
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;
    // 最大循环次数，防止死循环
    private static final int MAX_LOOP_COUNT = 10;

    public AgentRuntime(LlmClient llmClient, ToolRegistry toolRegistry, SessionManager sessionManager) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.sessionManager = sessionManager;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 处理用户输入，执行 Agent 主循环
     * @param sessionId 会话ID，用于隔离上下文
     * @param userInput 用户输入
     * @return 最终回复给用户的内容
     */
    @SuppressWarnings("unchecked")
    public String chat(String sessionId, String userInput) throws Exception {
        AgentSession session = sessionManager.getOrCreateSession(sessionId);
        // Step1：接收用户输入，加入上下文
        session.addMessage(new ChatMessage("user", userInput));

        int loopCount = 0;
        while (loopCount < MAX_LOOP_COUNT) {
            loopCount++;
            System.out.println("[Agent Trace] 第 " + loopCount + " 次循环，调用 LLM...");

            // Step2：调用 LLM，判断直接回复还是调用工具
            Map<String, Object> llmResponse = llmClient.chatCompletion(
                    session.getContext(),
                    toolRegistry.getAllToolsSchema()
            );

            String content = (String) llmResponse.get("content");
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) llmResponse.get("tool_calls");

            // 情况1：没有工具调用，直接返回结果，结束循环
            if (toolCalls == null || toolCalls.isEmpty()) {
                session.addMessage(new ChatMessage("assistant", content));
                System.out.println("[Agent Trace] LLM 直接回复，结束循环");
                return content;
            }

            // 情况2：有工具调用，先把助手回复加入上下文
            session.addMessage(new ChatMessage("assistant", content));
            System.out.println("[Agent Trace] LLM 决定调用工具，数量：" + toolCalls.size());

            // Step3：依次执行所有工具调用
            for (Map<String, Object> toolCall : toolCalls) {
                String callId = (String) toolCall.get("id");
                String toolName = (String) toolCall.get("function").get("name");
                String argumentsStr = (String) toolCall.get("function").get("arguments");

                System.out.println("[Agent Trace] 调用工具：" + toolName + "，参数：" + argumentsStr);

                // 解析工具参数
                Map<String, Object> params = objectMapper.readValue(
                        argumentsStr,
                        new TypeReference<Map<String, Object>>() {}
                );

                // 执行工具
                Tool tool = toolRegistry.getTool(toolName);
                String result;
                try {
                    result = tool.execute(params);
                } catch (Exception e) {
                    result = "工具执行异常：" + e.getMessage();
                    System.err.println("[Agent Error] 工具 " + toolName + " 执行失败：" + e.getMessage());
                }

                // Step4：工具结果加入上下文，继续循环
                session.addMessage(new ChatMessage("tool", result, callId));
                System.out.println("[Agent Trace] 工具执行结果：" + result);
            }

            // 回到循环开头，把工具结果喂给 LLM，继续判断
        }

        // 超过最大循环次数，强制结束
        return "抱歉，处理步骤过多，未能得到最终结果，请换一种问法。";
    }
}

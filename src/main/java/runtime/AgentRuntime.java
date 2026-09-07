package runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import exception.AgentException;
import first.ChatMessage;
import llm.LlmClient;
import session.AgentSession;
import session.SessionManager;
import util.Tool;
import util.ToolRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public String chat(String sessionId, String userInput) {
        AgentSession session = sessionManager.getOrCreateSession(sessionId);
        // 同一会话串行执行，避免并发撕裂上下文
        synchronized (session) {
            try {
                return doChat(session, sessionId, userInput);
            } catch (AgentException e) {
                throw e;
            } catch (Exception e) {
                throw new AgentException("Agent 执行失败：" + e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String doChat(AgentSession session, String sessionId, String userInput) throws Exception {
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

            // content 在工具调用场景下可能为 null（OpenAI 协议规定），用空串兜底
            String content = llmResponse.get("content") == null ? "" : (String) llmResponse.get("content");
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) llmResponse.get("tool_calls");

            // 提取思考过程（百炼 qwen3.8-max 思考模式开启时返回 reasoning_content 字段）
            // 思考过程仅打印 trace 供观察，不写入 context，避免上下文膨胀且不干扰后续决策
            Object reasoning = llmResponse.get("reasoning_content");
            if (reasoning instanceof String && !((String) reasoning).isBlank()) {
                String r = (String) reasoning;
                String preview = r.length() > 120 ? r.substring(0, 120) + "...(共" + r.length() + "字)" : r;
                System.out.println("[Agent Trace] LLM 思考过程：" + preview);
            }

            // 情况1：没有工具调用，直接返回结果，结束循环
            if (toolCalls == null || toolCalls.isEmpty()) {
                session.addMessage(new ChatMessage("assistant", content));
                System.out.println("[Agent Trace] LLM 直接回复，结束循环");
                return content;
            }

            // 情况2：有工具调用，先把助手回复加入上下文
            session.addMessage(new ChatMessage("assistant", content));
            System.out.println("[Agent Trace] LLM 决定调用工具，数量：" + toolCalls.size());

            // Step3：并行执行所有工具调用，结果按原顺序收集
            // executeOneTool 内不打印 trace，避免并行输出交错，统一在主线程按序打印
            List<ToolExecResult> execResults = toolCalls.parallelStream()
                    .map(toolCall -> executeOneTool(toolCall, sessionId))
                    .collect(Collectors.toList());

            // Step4：按顺序把工具结果加入上下文，并在主线程打印 trace（顺序清晰）
            for (ToolExecResult r : execResults) {
                session.addMessage(new ChatMessage("tool", r.result, r.callId));
                System.out.println("[Agent Trace] 调用工具：" + r.toolName + "，参数：" + r.argumentsStr);
                System.out.println("[Agent Trace] 工具执行结果：" + r.result);
            }

            // 回到循环开头，把工具结果喂给 LLM，继续判断
        }

        // 超过最大循环次数，强制结束。写入 assistant 消息保持 context 状态一致
        String fallback = "抱歉，处理步骤过多，未能得到最终结果，请换一种问法。";
        session.addMessage(new ChatMessage("assistant", fallback));
        return fallback;
    }

    /**
     * 执行单个工具调用，异常被吞为结果字符串，不中断并行流
     * 注意：此方法不打印 trace，trace 统一在主线程按序打印，避免并行输出交错
     */
    @SuppressWarnings("unchecked")
    private ToolExecResult executeOneTool(Map<String, Object> toolCall, String sessionId) {
        String callId = (String) toolCall.get("id");
        Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
        String toolName = (String) function.get("name");
        String argumentsStr = (String) function.get("arguments");

        // 解析工具参数
        Map<String, Object> params;
        try {
            params = objectMapper.readValue(
                    argumentsStr,
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            return new ToolExecResult(callId, toolName, argumentsStr, "工具参数解析异常：" + e.getMessage());
        }

        // 注入当前会话ID，供需要会话隔离的工具使用（不暴露给 LLM 的 schema）
        Map<String, Object> execParams = new HashMap<>(params);
        execParams.put("__sessionId", sessionId);

        // 执行工具
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            return new ToolExecResult(callId, toolName, argumentsStr, "未注册的工具：" + toolName);
        }
        try {
            String result = tool.execute(execParams);
            return new ToolExecResult(callId, toolName, argumentsStr, result);
        } catch (Exception e) {
            System.err.println("[Agent Error] 工具 " + toolName + " 执行失败：" + e.getMessage());
            return new ToolExecResult(callId, toolName, argumentsStr, "工具执行异常：" + e.getMessage());
        }
    }

    // 工具执行结果载体，便于并行收集后按顺序写回上下文
    private static final class ToolExecResult {
        final String callId;
        final String toolName;
        final String argumentsStr;  // 保存原始参数串，供主线程打印 trace
        final String result;

        ToolExecResult(String callId, String toolName, String argumentsStr, String result) {
            this.callId = callId;
            this.toolName = toolName;
            this.argumentsStr = argumentsStr;
            this.result = result;
        }
    }
}

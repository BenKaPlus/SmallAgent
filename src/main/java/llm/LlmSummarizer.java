package llm;

import first.ChatMessage;
import session.ContextSummarizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 LLM 的上下文摘要器：把较早的多轮对话压缩成简洁摘要。
 * 摘要失败（网络/限流等）时返回 null，由 AgentSession 回退为硬截断，不影响主流程。
 */
public class LlmSummarizer implements ContextSummarizer {
    private final LlmClient llmClient;

    public LlmSummarizer(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String summarize(List<ChatMessage> olderMessages) {
        StringBuilder conversation = new StringBuilder();
        for (ChatMessage m : olderMessages) {
            switch (m.getRole()) {
                case "user":
                    conversation.append("用户：").append(safe(m.getContent())).append('\n');
                    break;
                case "assistant":
                    conversation.append("助手：").append(safe(m.getContent()));
                    if (m.getToolCalls() != null) {
                        conversation.append("（调用了工具）");
                    }
                    conversation.append('\n');
                    break;
                case "tool":
                    conversation.append("工具结果：").append(safe(m.getContent())).append('\n');
                    break;
                default:
                    // system 消息不进入摘要
                    break;
            }
        }

        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(new ChatMessage("system",
                "你是对话摘要助手。请把下面的多轮对话压缩成简洁的中文摘要，"
                        + "保留关键事实、用户意图、工具得出的结论（如计算结果、天气、待办事项），"
                        + "不要丢失可用于后续追问的信息。控制在 200 字以内，直接输出摘要正文，不要加前缀。"));
        prompt.add(new ChatMessage("user", conversation.toString()));

        try {
            // 摘要请求不携带 tools，避免 LLM 在摘要阶段触发工具调用
            Map<String, Object> resp = llmClient.chatCompletion(prompt, null);
            Object content = resp.get("content");
            return content == null ? null : content.toString().trim();
        } catch (Exception e) {
            System.err.println("[Agent Warn] 上下文摘要失败，将回退为硬截断：" + e.getMessage());
            return null;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

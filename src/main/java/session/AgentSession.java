package session;

import frist.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个 Agent 会话，隔离不同窗口的上下文
 */
public class AgentSession {
    private final String sessionId;
    // 上下文消息列表
    private final List<ChatMessage> context;
    // 最大上下文消息数，超过则截断（基础压缩）
    private static final int MAX_CONTEXT_SIZE = 20;
    // 系统提示词
    private static final String SYSTEM_PROMPT = 
            "你是一个智能助手，可以使用工具来解决问题。\n" +
            "如果问题需要计算、搜索或管理待办，请调用对应的工具。\n" +
            "如果不需要工具，直接回答用户问题。";

    public AgentSession(String sessionId) {
        this.sessionId = sessionId;
        this.context = new ArrayList<>();
        // 初始化加入系统提示
        this.context.add(new ChatMessage("system", SYSTEM_PROMPT));
    }

    // 添加消息到上下文
    public void addMessage(ChatMessage message) {
        context.add(message);
        // 超过最大长度，保留系统提示 + 最近的消息
        if (context.size() > MAX_CONTEXT_SIZE) {
            ChatMessage system = context.get(0);
            List<ChatMessage> recent = context.subList(context.size() - (MAX_CONTEXT_SIZE - 1), context.size());
            context.clear();
            context.add(system);
            context.addAll(recent);
        }
    }

    public List<ChatMessage> getContext() {
        return context;
    }

    public String getSessionId() {
        return sessionId;
    }
}

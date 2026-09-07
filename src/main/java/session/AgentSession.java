package session;

import first.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
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
            "如果问题需要计算、搜索、天气查询或管理待办，请调用对应的工具。\n" +
            "注意：查询任何城市的天气必须调用 weather_query 工具获取真实数据，不要凭记忆回答，也不要用搜索代替。\n" +
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
            // 注意：subList 返回的是视图，clear 后该视图也会失效，必须先拷贝再清理
            int from = context.size() - (MAX_CONTEXT_SIZE - 1);
            List<ChatMessage> recent = new ArrayList<>(context.subList(from, context.size()));
            context.clear();
            context.add(system);
            context.addAll(recent);
        }
    }

    // 返回不可变视图，防止外部调用方修改内部 context 破坏一致性
    public List<ChatMessage> getContext() {
        return Collections.unmodifiableList(context);
    }

    public String getSessionId() {
        return sessionId;
    }
}

package session;

import first.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个 Agent 会话，隔离不同窗口的上下文。
 */
public class AgentSession {
    // 上下文消息数上限，超过则触发压缩
    private static final int MAX_CONTEXT_SIZE = 20;
    // 摘要压缩时，原样保留的最近消息条数（其余较早消息交给摘要器）
    private static final int KEEP_RECENT = 8;

    private final String sessionId;
    // 上下文消息列表
    private final List<ChatMessage> context;
    // 摘要器；为 null 时退化为硬截断（单元测试默认走这条路径）
    private final ContextSummarizer summarizer;
    // 最近一次活跃时间，供 SessionManager 清理空闲会话
    private volatile long lastActiveAt;

    // 系统提示词
    private static final String SYSTEM_PROMPT =
            "你是一个智能助手，可以使用工具来解决问题。\n" +
            "如果问题需要计算、搜索、天气查询或管理待办，请调用对应的工具。\n" +
            "注意：查询任何城市的天气必须调用 weather_query 工具获取真实数据，不要凭记忆回答，也不要用搜索代替。\n" +
            "如果不需要工具，直接回答用户问题。";

    public AgentSession(String sessionId) {
        this(sessionId, null);
    }

    public AgentSession(String sessionId, ContextSummarizer summarizer) {
        this.sessionId = sessionId;
        this.summarizer = summarizer;
        this.context = new ArrayList<>();
        // 初始化加入系统提示
        this.context.add(new ChatMessage("system", SYSTEM_PROMPT));
        this.lastActiveAt = System.currentTimeMillis();
    }

    // 添加消息到上下文
    public void addMessage(ChatMessage message) {
        context.add(message);
        this.lastActiveAt = System.currentTimeMillis();
        // 超过最大长度，触发压缩
        if (context.size() > MAX_CONTEXT_SIZE) {
            compact();
        }
    }

    /**
     * 上下文压缩：
     * - 配置了摘要器：system + 【历史摘要】+ 最近 KEEP_RECENT 条
     * - 未配置摘要器：硬截断，system + 最近 (MAX-1) 条
     * 两种方式都会丢弃开头"悬空"的 tool 结果（其 assistant.tool_calls 已被移除，
     * 若保留会导致 tool_call_id 找不到对应的 tool_calls，协议错乱）。
     */
    private void compact() {
        ChatMessage system = context.get(0);
        List<ChatMessage> compacted = new ArrayList<>();
        compacted.add(system);

        if (summarizer == null) {
            // 硬截断：保留 system + 最近 (MAX_CONTEXT_SIZE-1) 条
            int from = context.size() - (MAX_CONTEXT_SIZE - 1);
            List<ChatMessage> recent = new ArrayList<>(context.subList(from, context.size()));
            dropOrphanLeadingTools(recent);
            compacted.addAll(recent);
        } else {
            int recentFrom = context.size() - KEEP_RECENT;
            // 较早的消息（system 之后、最近窗口之前）交给摘要器
            if (recentFrom > 1) {
                List<ChatMessage> older = new ArrayList<>(context.subList(1, recentFrom));
                try {
                    String summary = summarizer.summarize(older);
                    if (summary != null && !summary.isBlank()) {
                        compacted.add(new ChatMessage("system", "【历史对话摘要】" + summary));
                    }
                } catch (Exception e) {
                    // 摘要失败时静默退化为硬截断（不插入摘要），保证主流程不受影响
                    System.err.println("[Agent Warn] 摘要异常，退化为硬截断：" + e.getMessage());
                }
            }
            List<ChatMessage> recent = new ArrayList<>(context.subList(recentFrom, context.size()));
            dropOrphanLeadingTools(recent);
            compacted.addAll(recent);
        }

        context.clear();
        context.addAll(compacted);
    }

    /**
     * 丢弃窗口开头悬空的 tool 消息：tool 消息必须紧随一条带 tool_calls 的 assistant，
     * 若该 assistant 已在截断/摘要中被移除，这条 tool 结果就是孤儿，必须一并丢弃。
     */
    private void dropOrphanLeadingTools(List<ChatMessage> messages) {
        while (!messages.isEmpty() && "tool".equals(messages.get(0).getRole())) {
            messages.remove(0);
        }
    }

    // 返回不可变视图，防止外部调用方修改内部 context 破坏一致性
    public List<ChatMessage> getContext() {
        return Collections.unmodifiableList(context);
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getLastActiveAt() {
        return lastActiveAt;
    }
}

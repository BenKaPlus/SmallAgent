package session;

import first.ChatMessage;

import java.util.List;

/**
 * 上下文摘要器：把较早的对话消息压缩成一段摘要文本。
 * AgentSession 在上下文超限时调用；实现不可用或返回 null 时回退为硬截断。
 */
@FunctionalInterface
public interface ContextSummarizer {
    String summarize(List<ChatMessage> olderMessages);
}

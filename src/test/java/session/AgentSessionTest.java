package session;

import first.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentSession 单元测试
 * 覆盖：上下文初始化（含 system prompt）、追加消息、超出上限后的截断压缩
 */
class AgentSessionTest {

    @Test
    void shouldInitializeWithSystemPrompt() {
        AgentSession session = new AgentSession("s1");
        List<ChatMessage> ctx = session.getContext();
        assertEquals(1, ctx.size());
        assertEquals("system", ctx.get(0).getRole());
        assertNotNull(ctx.get(0).getContent());
    }

    @Test
    void shouldAppendMessages() {
        AgentSession session = new AgentSession("s1");
        session.addMessage(new ChatMessage("user", "你好"));
        session.addMessage(new ChatMessage("assistant", "你好，有什么可以帮你？"));
        assertEquals(3, session.getContext().size());
    }

    @Test
    void shouldTruncateAndKeepSystemPlusRecent() {
        // MAX_CONTEXT_SIZE = 20，system 占 1，剩 19 个槽位
        AgentSession session = new AgentSession("s1");
        // 写入 25 条用户消息，触发多次截断
        for (int i = 0; i < 25; i++) {
            session.addMessage(new ChatMessage("user", "msg-" + i));
        }

        List<ChatMessage> ctx = session.getContext();
        // 截断后应保持上限 20
        assertEquals(20, ctx.size());

        // 首条仍是 system
        assertEquals("system", ctx.get(0).getRole());

        // 末条应是最新加入的 msg-24
        assertEquals("msg-24", ctx.get(ctx.size() - 1).getContent());
    }

    @Test
    void shouldNotTruncateBelowLimit() {
        AgentSession session = new AgentSession("s1");
        for (int i = 0; i < 10; i++) {
            session.addMessage(new ChatMessage("user", "msg-" + i));
        }
        // 1 system + 10 user = 11，未触发截断
        assertEquals(11, session.getContext().size());
    }

    @Test
    void hardTruncateShouldDropOrphanLeadingToolMessages() {
        // 构造边界场景：一条带 tool_calls 的 assistant 后紧跟 19 条 tool 结果，
        // 截断后若保留 tool 结果却丢弃了其 assistant，tool_call_id 会悬空——应把这些孤儿 tool 一并丢弃
        AgentSession session = new AgentSession("s1");
        Map<String, Object> function = Map.of("name", "calculator", "arguments", "{}");
        Map<String, Object> toolCall = Map.of("id", "call-x", "type", "function", "function", function);
        session.addMessage(new ChatMessage("assistant", "", List.of(toolCall)));
        for (int i = 0; i < 19; i++) {
            session.addMessage(new ChatMessage("tool", "result-" + i, "call-x"));
        }

        List<ChatMessage> ctx = session.getContext();
        // 不允许出现没有对应 assistant 的悬空 tool 消息
        assertTrue(ctx.stream().noneMatch(m -> "tool".equals(m.getRole())),
                "截断后不应残留悬空的 tool 消息，实际：" + ctx);
        assertEquals("system", ctx.get(0).getRole());
    }

    @Test
    void shouldSummarizeOlderMessagesWhenSummarizerProvided() {
        // 注入摘要器：较早消息应被压缩成一条摘要，最近消息原样保留
        AgentSession session = new AgentSession("s1", older -> "FIXED_SUMMARY");
        for (int i = 0; i < 25; i++) {
            session.addMessage(new ChatMessage("user", "msg-" + i));
        }

        List<ChatMessage> ctx = session.getContext();
        assertTrue(ctx.size() <= 20, "摘要后上下文应明显缩短，实际：" + ctx.size());
        assertTrue(ctx.stream().anyMatch(m -> m.getContent() != null && m.getContent().contains("FIXED_SUMMARY")),
                "应包含摘要消息");
        assertEquals("msg-24", ctx.get(ctx.size() - 1).getContent(), "最近消息应原样保留");
        assertEquals("system", ctx.get(0).getRole());
    }
}

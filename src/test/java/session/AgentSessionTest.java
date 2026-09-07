package session;

import first.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}

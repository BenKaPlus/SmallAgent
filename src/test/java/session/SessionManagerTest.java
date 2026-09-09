package session;

import first.ChatMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SessionManager 单元测试：空闲会话清理、摘要器透传
 */
class SessionManagerTest {

    @Test
    void shouldEvictIdleSessionsAndKeepFreshOnes() throws InterruptedException {
        SessionManager manager = new SessionManager();
        manager.getOrCreateSession("old-session");
        Thread.sleep(50);
        manager.getOrCreateSession("fresh-session");

        // 空闲阈值 1ms：old 已空闲 ~50ms 应被清理，fresh 刚活跃应保留
        int evicted = manager.evictIdle(Duration.ofMillis(1));

        assertEquals(1, evicted, "应清理 1 个空闲会话");
        assertEquals(1, manager.sessionCount(), "只保留 fresh 会话");
    }

    @Test
    void shouldKeepAllSessionsWhenIdleThresholdLarge() {
        SessionManager manager = new SessionManager();
        manager.getOrCreateSession("a");
        manager.getOrCreateSession("b");

        int evicted = manager.evictIdle(Duration.ofHours(24));

        assertEquals(0, evicted);
        assertEquals(2, manager.sessionCount());
    }

    @Test
    void shouldPassSummarizerToCreatedSessions() {
        SessionManager manager = new SessionManager(older -> "SUMMARY_MARKER");
        AgentSession session = manager.getOrCreateSession("s1");
        for (int i = 0; i < 25; i++) {
            session.addMessage(new ChatMessage("user", "msg-" + i));
        }
        assertTrue(session.getContext().stream()
                        .anyMatch(m -> m.getContent() != null && m.getContent().contains("SUMMARY_MARKER")),
                "会话应使用注入的摘要器做压缩");
    }
}

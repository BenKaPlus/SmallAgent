package session;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器，管理所有用户的所有会话，线程安全
 */
public class SessionManager {
    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();

    // 获取或创建会话
    public AgentSession getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, AgentSession::new);
    }

    // 清理不活跃会话（可定时调用）
    public void cleanExpiredSessions() {
        // 这里可以加过期时间判断，简化版先不实现
    }
}

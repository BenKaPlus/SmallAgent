package session;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 会话管理器，管理所有会话，线程安全。
 * 可选注入上下文摘要器（传给每个新建的 AgentSession），可选开启空闲会话定时清理。
 */
public class SessionManager {
    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();
    private final ContextSummarizer summarizer;
    private final ScheduledExecutorService cleaner;

    public SessionManager() {
        this(null, null);
    }

    public SessionManager(ContextSummarizer summarizer) {
        this(summarizer, null);
    }

    /**
     * @param summarizer 上下文摘要器，null 表示会话用硬截断
     * @param maxIdle    会话最大空闲时长，非 null 时启动守护线程定时清理
     */
    public SessionManager(ContextSummarizer summarizer, Duration maxIdle) {
        this.summarizer = summarizer;
        if (maxIdle != null) {
            this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "session-cleaner");
                t.setDaemon(true);
                return t;
            });
            // 清理周期取空闲时长的 1/4，至少 30 秒
            long periodSeconds = Math.max(30, maxIdle.getSeconds() / 4);
            this.cleaner.scheduleAtFixedRate(
                    () -> evictIdle(maxIdle), periodSeconds, periodSeconds, TimeUnit.SECONDS);
        } else {
            this.cleaner = null;
        }
    }

    // 获取或创建会话
    public AgentSession getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> new AgentSession(id, summarizer));
    }

    /**
     * 清理空闲超过 maxIdle 的会话，返回被清理的数量
     */
    public int evictIdle(Duration maxIdle) {
        long threshold = System.currentTimeMillis() - maxIdle.toMillis();
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> e.getValue().getLastActiveAt() < threshold);
        return before - sessions.size();
    }

    public int sessionCount() {
        return sessions.size();
    }
}

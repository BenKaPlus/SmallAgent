package exception;

/**
 * Agent 运行时异常，统一封装 LLM 调用、工具执行、会话处理中的异常
 * 使用 RuntimeException 避免 chat 方法签名上抛 checked Exception
 */
public class AgentException extends RuntimeException {

    public AgentException(String message) {
        super(message);
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}

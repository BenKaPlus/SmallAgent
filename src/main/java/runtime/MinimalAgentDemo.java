package runtime;

import llm.LlmClient;
import session.SessionManager;
import util.CalculatorTool;
import util.MockSearchTool;
import util.TodoTool;
import util.ToolRegistry;
import java.util.Map;

public class MinimalAgentDemo {
    public static void main(String[] args) throws Exception {
        // ========== 1. 初始化组件 ==========
        // 从环境变量读取配置，避免把 API Key 硬编码进源码
        // 优先读阿里云百炼的 DASHSCOPE_API_KEY，回退到通用 LLM_API_KEY
        // base_url 默认百炼华北2(北京) OpenAI 兼容入口；model 默认 qwen3.8-max
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("LLM_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("请先设置环境变量 DASHSCOPE_API_KEY（或 LLM_API_KEY）再运行本示例。");
            return;
        }
        String baseUrl = System.getenv().getOrDefault("LLM_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        String model = System.getenv().getOrDefault("LLM_MODEL", "qwen3.8-max");
        LlmClient llmClient = new LlmClient(apiKey, baseUrl, model);
        // 关闭百炼 qwen3.8-max 默认的思考模式，避免 Agent 在工具已返回结果后仍反复调用工具不收敛
        // 仅对百炼兼容入口生效，其他模型无此参数会被忽略
        llmClient.setExtraBody(Map.of("enable_thinking", false));

        // 注册工具
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.registerTool(new CalculatorTool());
        toolRegistry.registerTool(new MockSearchTool());
        toolRegistry.registerTool(new TodoTool());

        // 会话管理器
        SessionManager sessionManager = new SessionManager();

        // 核心运行时
        AgentRuntime agentRuntime = new AgentRuntime(llmClient, toolRegistry, sessionManager);

        // ========== 2. 测试场景 ==========
        System.out.println("===== 测试1：单轮工具调用（计算器） =====");
        String reply1 = agentRuntime.chat("session-window1", "帮我算一下 1234 * 5678 等于多少");
        System.out.println("Agent 回复：" + reply1);

        System.out.println("\n===== 测试2：多 Session 隔离 =====");
        // 窗口1 添加待办
        agentRuntime.chat("session-window1", "帮我添加待办：下午三点开会");
        // 窗口2 添加待办
        agentRuntime.chat("session-window2", "帮我添加待办：晚上写周报");
        
        // 各自查看，互不影响
        String todo1 = agentRuntime.chat("session-window1", "查看我的待办");
        String todo2 = agentRuntime.chat("session-window2", "查看我的待办");
        System.out.println("窗口1待办：\n" + todo1);
        System.out.println("窗口2待办：\n" + todo2);

        System.out.println("\n===== 测试3：多轮追问 =====");
        agentRuntime.chat("session-window3", "搜索一下广州的天气");
        String followUp = agentRuntime.chat("session-window3", "那深圳呢？");
        System.out.println("追问回复：" + followUp);
    }
}

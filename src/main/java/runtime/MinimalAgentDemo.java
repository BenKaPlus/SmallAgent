package runtime;

public class MinimalAgentDemo {
    public static void main(String[] args) throws Exception {
        // ========== 1. 初始化组件 ==========
        // 配置 LLM 客户端（换成你自己的 API Key 和接口地址）
        LlmClient llmClient = new LlmClient(
                "你的API-KEY",
                "https://api.deepseek.com/v1",
                "deepseek-chat"
        );

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

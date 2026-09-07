# SmallAgent

从零实现的最小可用 AI Agent。**不依赖任何 Agent 框架**（无 langgraph/openhands/openclaw/PI），核心 Agent Runtime 完全自行实现，仅用 JDK 11 内置 HttpClient + Jackson 做 JSON 序列化。

真实接入**阿里云百炼 qwen3.8-max**（OpenAI 兼容协议），支持 Function Call 工具调用、多会话隔离、上下文压缩、并行工具执行。

---

## 一、运行方式

### 1. 前置条件

- JDK 11+
- Maven 3.6+
- 阿里云百炼 API Key（[获取地址](https://help.aliyun.com/zh/model-studio/get-api-key)）

### 2. 配置环境变量

```powershell
# Windows PowerShell
$env:DASHSCOPE_API_KEY="sk-你的百炼key"

# Linux/macOS
export DASHSCOPE_API_KEY="sk-你的百炼key"
```

可选覆盖（不设则用默认值）：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DASHSCOPE_API_KEY` | （必填） | 百炼 API Key，回退读 `LLM_API_KEY` |
| `LLM_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | OpenAI 兼容入口 |
| `LLM_MODEL` | `qwen3.8-max` | 模型名 |

### 3. 编译运行

```bash
mvn clean package -DskipTests
java -cp "target/classes;$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" runtime.MinimalAgentDemo
```

或用 IDE 直接运行 `runtime.MinimalAgentDemo#main`。

### 4. 跑测试

```bash
mvn test
```

预期输出：`Tests run: 26, Failures: 0`，覆盖 calculator / todo / weather / session / runtime 循环上限（全部本地 stub，不消耗 API 额度）。

---

## 二、系统设计

### 1. 模块结构

```
SmallAgent/
└── src/main/java/
    ├── runtime/
    │   ├── AgentRuntime.java       Agent 主循环（核心）
    │   └── MinimalAgentDemo.java   演示入口
    ├── llm/LlmClient.java          OpenAI 兼容 HTTP 客户端
    ├── session/
    │   ├── AgentSession.java      单会话上下文 + 截断压缩
    │   └── SessionManager.java    多会话管理（ConcurrentHashMap）
    ├── first/ChatMessage.java      消息模型
    └── util/
        ├── Tool.java              工具接口
        ├── ToolRegistry.java      工具注册 + Schema 生成
        ├── CalculatorTool.java    计算器
        ├── MockSearchTool.java     模拟搜索
        └── TodoTool.java          待办管理
```

### 2. Agent 主循环（ReAct）

`AgentRuntime.chat()` 实现 ReAct 循环，对齐面试要求的四步：

```
Step1 接收用户输入 → session.addMessage(user)
        ↓
Step2 调 LLM，解析返回：
        ├─ 无 tool_calls → 写入 assistant 回复，返回（循环结束）
        └─ 有 tool_calls → 写入 assistant 回复，进入 Step3
        ↓
Step3 并行执行所有工具调用（parallelStream）
        ↓
Step4 工具结果按原顺序写入 context（role=tool）
        ↓
   回到 Step2，把工具结果喂给 LLM 继续判断
        ↓
   循环上限 MAX_LOOP_COUNT=10 兜底，防止死循环
```

### 3. 工具系统

- **接口**：`Tool` 定义 `getName / getDescription / getParametersSchema / execute`
- **注册**：`ToolRegistry.registerTool()`，`getAllToolsSchema()` 自动生成 OpenAI Function Call 格式
- **决策**：LLM 收到 schema 后自主决定调用，`tool_choice=auto`
- **并行**：一次循环内多个 tool_calls 用 `parallelStream` 并行执行，结果按原顺序写回 context
- **会话隔离**：AgentRuntime 调用工具时注入 `__sessionId`（不暴露给 LLM schema），TodoTool 据此实现按会话隔离待办

已实现三个工具：

| 工具 | 名称 | 能力 |
|------|------|------|
| 计算器 | `calculator` | 加减乘除、Math.pow 幂运算、sqrt 开方，带白名单注入防护 |
| 搜索 | `web_search` | Mock 实现，返回模拟搜索结果 |
| 待办 | `todo_manager` | add/list，按 `__sessionId` 隔离 |
| 天气 | `weather_query` | **真实 API 查询**（t.weather.itboy.net），内置 18 城市名→ID 映射，返回当前温度+今明两天预报 |

### 4. LLM 客户端

- 接入百炼 OpenAI 兼容入口 `/chat/completions`
- **超时**：连接 10s、请求 60s
- **重试**：429/5xx/IOException 指数退避重试，最多 3 次
- **非标参数**：`extraBody` 字段支持注入 `enable_thinking` 等供应商专属参数
- **思考模式**：默认 `enable_thinking=false`（避免 qwen3.8-max 反复调工具不收敛，实测死循环 → 2 次收敛）

### 5. Session 管理

- `SessionManager` 用 `ConcurrentHashMap<sessionId, AgentSession>`，多窗口天然隔离
- 同 sessionId 调 `chat()` 复用上下文，支持「接着窗口1继续聊」
- `AgentRuntime.chat()` 对同一 session 加 `synchronized`，防止并发撕裂 context

---

## 三、Memory 的召回时机与放置方式

### 1. Context（短期记忆）结构

每个 AgentSession 的 context 是一个有序消息列表，按 OpenAI 协议标注角色：

```
[
  {role: "system",    content: 系统提示}          ← 初始化时写入，永不丢失
  {role: "user",     content: 用户输入}          ← Step1 写入
  {role: "assistant",content: LLM 回复}          ← Step2 写入（含工具调用决策）
  {role: "tool",     content: 工具结果, toolCallId} ← Step4 写入
  ...
]
```

### 2. 召回时机

**全量召回**：每次调 LLM 前，把整个 context 作为 `messages` 参数发给模型。这是最简单也最稳妥的策略——LLM 拿到全部历史才能正确理解追问上下文。

```
chat() → doChat() → llmClient.chatCompletion(session.getContext(), ...) → 发给 LLM
```

### 3. 哪些信息塞入 context

| 信息 | 是否塞入 | 原因 |
|------|---------|------|
| 用户输入 | ✅ | 必需，理解意图 |
| LLM 最终回复 | ✅ | 必需，多轮对话依赖 |
| 工具执行结果 | ✅ | 必需，LLM 需基于结果继续判断 |
| LLM 思考过程（reasoning_content） | ❌ | 不塞入。思考是中间态，塞入会膨胀 context 且干扰后续决策；仅打印 trace 供观察 |
| 系统提示 | ✅ | 必需，定义 Agent 行为 |

### 4. Context 压缩策略

`AgentSession.MAX_CONTEXT_SIZE = 20`，超出后**保留 system + 最近 19 条**：

```java
if (context.size() > MAX_CONTEXT_SIZE) {
    ChatMessage system = context.get(0);
    List<ChatMessage> recent = new ArrayList<>(context.subList(from, context.size()));
    context.clear();
    context.add(system);
    context.addAll(recent);
}
```

**关键 bug 修复**：`subList` 返回的是原列表的视图，`clear()` 后视图失效。必须先用 `new ArrayList<>(subList)` 拷贝再清理，否则会导致旧消息被清空只剩 system。

### 5. 追问能力验证

- **纯对话追问**：测试3第二轮「那深圳呢？」——LLM 基于上一轮 context 理解"那"指代天气
- **带工具的追问**：LLM 理解上下文后自动搜深圳，且并行调用了 2 个 `web_search`（不同关键词）

---

## 四、异常处理与日志

### 1. 异常处理

- **工具异常**：`executeOneTool` 捕获异常转为字符串结果，不中断循环
- **LLM 异常**：429/5xx/IOException 重试，非重试范围抛 RuntimeException
- **空 Key 保护**：`MinimalAgentDemo` 检测 `DASHSCOPE_API_KEY` 未设则提示并退出
- **循环上限**：`MAX_LOOP_COUNT=10` 防止 LLM 反复调工具不收敛

### 2. Trace 日志

每步打印 `[Agent Trace]` 标识：

```
[Agent Trace] 第 1 次循环，调用 LLM...
[Agent Trace] LLM 思考过程：用户问的是 1234*5678...(共128字)
[Agent Trace] LLM 决定调用工具，数量：1
[Agent Trace] 调用工具：calculator，参数：{"expression": "1234 * 5678"}
[Agent Trace] 工具执行结果：计算结果：7006652
[Agent Trace] 第 2 次循环，调用 LLM...
[Agent Trace] LLM 直接回复，结束循环
```

---

## 五、测试用例

`src/test/java/` 下 4 个测试类共 15 个用例：

| 测试类 | 覆盖点 |
|--------|--------|
| `CalculatorToolTest` | 基础运算、Math.pow 幂运算、非法表达式容错、schema 规范 |
| `TodoToolTest` | **多会话隔离**（核心）、未注入 sessionId 回退、不支持操作拒绝 |
| `AgentSessionTest` | system 初始化、消息追加、超限截断压缩、未触发截断 |
| `AgentRuntimeTest` | **循环上限保护**——用 Stub LlmClient 模拟"永远调工具"，验证不死循环 |

---

## 六、技术选型说明

| 选型 | 决策 | 原因 |
|------|------|------|
| 框架 | 无 | 面试要求"从零实现"，仅用 JDK + Jackson |
| HTTP | JDK HttpClient | Java 11 内置，无需额外依赖 |
| JSON | Jackson | 业界标准，序列化 ChatMessage 需要 getter |
| LLM | 阿里云百炼 qwen3.8-max | OpenAI 兼容，国内可访问 |
| 测试 | JUnit 5 | 业界标准 |
| 思考模式 | 默认关闭 | qwen3.8-max 默认开启会导致 Agent 反复调工具不收敛 |

---

## 七、已知限制

- 搜索工具是 Mock 实现，未接真实搜索 API
- 待办仅内存存储，进程退出即丢失
- Context 压缩是简单截断，无摘要
- 无流式输出（SSE）
- 无 RAG / 长期记忆 / 多 Agent 协作

这些是**刻意保持的最小实现**，聚焦面试要求的 Agent 核心循环。

---

## 八、提交历史

```
[最新] feat: 新增真实天气查询工具（城市名→ID 映射 + 真实 API）
340fb53 fix: 实现细节加固（脚本注入防护、null 兜底、封装、异常分类）
6d7faf2 docs+test: 补全面试硬要求（README、JUnit 测试、思考过程解析、AI Prompt 记录）
d2069a9 feat: 接入阿里云百炼并修复计算器死循环
31aa1d3 feat: 健壮性增强（线程安全、超时重试、工具并行、截断修复）
6f2c56b chore: 清理死代码与工程卫生
e31f053 fix: 修复阻断运行的致命 Bug 并修正包名拼写
5599086 尝试的demo版本
```

开发过程记录见 [docs/AI-PROMPTS.md](docs/AI-PROMPTS.md)。

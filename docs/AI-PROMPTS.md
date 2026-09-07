# AI Prompt 与问题解决记录

记录开发 SmallAgent 过程中使用的 AI Prompt 与遇到的关键问题及解决方式。

---

## 一、使用的 AI Prompt

### 1. 项目初始分析

```
分析当前项目的代码和结构，查看该项目的作者的意图，并给出该项目需要完善扩展的内容
```

**目的**：理解作者意图（极简教学型 Agent），定位需要修复的 Bug 和扩展方向。

### 2. 修复与提交

```
开始吧，并且根据你的修改路线合理的提交gitee和github,你可以看看git文件，看看怎么提交到远程
```

**目的**：按修复路线分批提交到 gitee + github 两个远程。

### 3. 运行验证

```
现在运行是个什么样的情况？
```

**目的**：实测项目能否跑通，定位运行时问题。

### 4. 接入百炼

```
我不用deepseek的apikey,我用阿里云百炼平台的，他的apikey，我已经设置在环境变量里了，模型用qwen3.8-max
```

**目的**：切换 LLM 供应商到百炼，并指定模型。

### 5. 死循环修复

```
（AskUserQuestion 选择）修计算器死循环
```

**目的**：定位 qwen3.8-max 思考模式导致 Agent 反复调工具不收敛的问题。

### 6. 面试要求对照

```
这个项目是否满足面试官的以下要求，如果没有还差什么？
[面试要求全文]
```

**目的**：逐条对照面试要求，定位缺口。

---

## 二、关键问题与解决记录

### 问题1：ChatMessage 缺失 getter 导致 LLM 调用必然失败

**现象**：项目原始代码编译能通过，但运行时 LLM 收到的 messages 是空的。

**根因**：`ChatMessage` 类注释写「getter/setter 省略」，但 `LlmClient` 用 Jackson 把 `List<ChatMessage>` 序列化成 JSON 发给 LLM。Jackson 默认靠 getter 序列化，没有 getter 会发出空的 `{"role":null,"content":null}` 之类废数据，LLM 拿不到任何消息内容。

**解决**：补全 `getRole() / getContent() / getToolCallId()` 三个 getter。

**教训**：Jackson 默认用 getter 序列化，不是字段反射。教学项目「省略 getter」会让整个 demo 跑不起来。

---

### 问题2：包名拼写错误 `frist` → `first`

**现象**：包名是 `frist`。

**根因**：原作者手误。

**解决**：创建 `first` 包，迁移 `ChatMessage` 和 `ToolCall`，更新所有 import。同时发现 `ToolCall` 类完全未被使用（AgentRuntime 用裸 Map 解析 tool_calls），后清理删除。

---

### 问题3：CalculatorTool 的 `^` 替换 Bug

**现象**：原代码 `expression.replace("^", "**")`，期望支持幂运算。

**根因**：
1. Nashorn（JDK 11 的 js 引擎）**不支持 `**` 幂运算符**（ES2016 语法，Nashorn 不实现）
2. JavaScript 中 `^` 是**位异或**，不是幂运算
3. 原代码把 `^` 替换成 `**`，反而让表达式语法错误

**解决**：
- 去掉错误替换
- description 明确提示「幂运算请用 Math.pow(a, b)，不要用 ^」
- 容错 `engine == null`（JDK 15+ Nashorn 被移除）

**教训**：不要用 Nashorn 做严肃的数学计算，它的语义和数学期望不一致。

---

### 问题4：TodoTool 多会话隔离名存实亡

**现象**：演示中「多 Session 隔离」测试看起来通过了，但实际 TodoTool 没有真正隔离。

**根因**：TodoTool 按 `params.userId` 区分待办，但 `getParametersSchema` 里没有定义 `userId` 参数，LLM 永远不会传它，全部落到 `"default"`。演示中「隔离」只是上下文隔离，TodoTool 自身存储未隔离。

**解决**：
- AgentRuntime 调用工具时注入 `__sessionId`（不暴露给 LLM schema）
- TodoTool 从 `params.__sessionId` 读取会话标识

**教训**：会话隔离要贯通到工具层，不能只在上层 context 层面做。

---

### 问题5：AgentSession 上下文截断 Bug

**现象**：context 超过 20 条后，截断逻辑会让旧消息被清空只剩 system。

**根因**：
```java
List<ChatMessage> recent = context.subList(from, to);  // subList 返回的是视图
context.clear();                                        // clear 后视图也失效
context.addAll(recent);                                  // recent 已失效
```

**解决**：先拷贝再清理
```java
List<ChatMessage> recent = new ArrayList<>(context.subList(from, to));
context.clear();
context.addAll(recent);
```

**教训**：`List.subList()` 返回的是原列表的视图，不是拷贝。任何对原列表的结构性修改都会让视图行为未定义。

---

### 问题6：qwen3.8-max 思考模式导致 Agent 死循环

**现象**：接入百炼 qwen3.8-max 后，计算器测试反复调用同一工具 10 次后强制结束，无法收敛。

**根因**：
1. qwen3.8-max **默认开启思考模式**
2. 思考模式下，LLM 倾向于"既然有工具就再调一次试试"
3. 我们传的 `temperature=0.1` 会被百炼强制调整为 `0.6`（思考模式最低温度），让模型更发散

**解决**：
- LlmClient 增加 `extraBody` 字段，支持注入非标 OpenAI 参数
- MinimalAgentDemo 默认设置 `enable_thinking=false`

**实测对比**：
- 思考模式开启：10 次循环后强制结束，未给出答案
- 思考模式关闭：2 次循环收敛，答案 `1234 × 5678 = 7,006,652`

**教训**：不同 LLM 供应商的默认行为差异很大，Agent 框架要能注入供应商专属参数。

---

### 问题7：PowerShell 对 Java `-D` 参数的解析问题

**现象**：`java -Dfile.encoding=UTF-8 -cp ...` 在 PowerShell 里报错 `ClassNotFoundException: /encoding=UTF-8`。

**根因**：PowerShell 把 `-Dfile.encoding=UTF-8` 拆成了 `-Dfile` 和 `.encoding=UTF-8`。

**解决**：用单引号包裹：`java '-Dfile.encoding=UTF-8' -cp ...`

**教训**：在 PowerShell 里跑带 `-D` 参数的 java 命令，必须用单引号包裹整个参数。

---

## 三、AI 辅助开发的使用方式

### 1. 代码分析

用 AI Agent 工具的 `Explore` 子代理并行探索项目结构和核心代码，快速理解：
- 项目类型和技术栈（Java 11 + Maven + Jackson）
- 已实现的能力（LLM 对话、Function Call、多会话隔离）
- 作者意图（极简教学型 Agent）

### 2. 问题定位

AI Agent 通过阅读代码定位 Bug，并给出：
- 根因分析（Jackson getter 机制、subList 视图机制、Nashorn 语义）
- 修复方案（补 getter、拷贝再清理、去 `^` 替换）
- 验证方式（mvn compile + 运行实测）

### 3. 实测验证

AI Agent 真实运行项目，捕获运行时输出，定位：
- LLM 401（假 key）
- qwen3.8-max 死循环（真 key）
- 多会话隔离实际行为

### 4. 文档生成

基于开发对话记录，AI 自动生成 README 和问题解决记录，覆盖面试硬性要求。

---

## 四、未使用 AI 的部分

以下决策由人工完成，AI 仅提供选项：
- 选择阿里云百炼作为 LLM 供应商
- 选择默认关闭思考模式
- 选择 JUnit 5 作为测试框架
- 选择保留极简实现，不引入 Spring 等框架

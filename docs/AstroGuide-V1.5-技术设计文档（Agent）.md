# 🌌 AstroGuide – V1.5 技术设计文档（TDD / Agent 形态）

> 日期：2026-02-16  
> 范围：在 V1（RAG + Wikipedia 固定管线）基础上，将问答链路升级为 **真正的智能体（Agent Loop）**：引入 Tools（Function Calling）与多步推理循环；RAG 仍可作为 Advisor（稳定底座），Wikipedia/概念卡等能力升级为 Tool（按需调用）。  
> 关联文档：
> - [docs/AstroGuide-V1-技术设计文档.md](./AstroGuide-V1-技术设计文档.md)
> - [docs/V1-RAG-技术实现讨论.md](./V1-RAG-技术实现讨论.md)

---

## 0. 实现原则：仅用 Spring AI，无自研 Agent 循环

**项目内只保留一套工具调用体系：Spring AI 的 @Tool + ChatClient.tools()。**

- **Tool 定义与注册**：使用 Spring AI 的 `@Tool`、`@ToolParam` 将每个工具拆分成独立类（`WikipediaTool`、`KnowledgeBaseTool`、`ConceptCardTool`），通过 `ChatClient.prompt().tools(wikipediaTool, knowledgeBaseTool, conceptCardTool)` 注册给模型。
- **Tool 执行与多轮回填**：由 **Spring AI 的 ChatModel/ChatClient 内部机制托管**。模型返回 tool_calls → 框架解析并执行对应 `@Tool` 方法 → 将结果回填给模型 → 再调模型，直到模型输出最终 content。无需自研 JSON 协议、AgentOrchestrator 或 AgentToolRegistry。
- **配置**：通过 `app.ai.tools.enabled` 控制是否向模型注册 tools；关闭则退化为仅 Advisor（RAG + 可选 Wikipedia 固定步骤）。不保留 `app.agent.*` 等自研 agent 配置。
- **可观测性**：与 Spring AI 的 Tool 执行链路一致，便于与 Spring AI 生态的监控、日志对齐；当前实现不额外输出 tool_call/tool_result SSE 事件，如需可观测性增强，后续再评估基于日志/拦截器/响应扩展实现。

---

## 1. 目标与非目标

### 1.1 目标（V1.5 必须达成）

- **Agent Loop**：支持 LLM 在推理过程中发起 `tool_calls`，由 **Spring AI ChatModel/ChatClient** 执行 Tool 并将结果回填，再继续调用 LLM，直到得到最终回答（无自研 agent 循环）。
- **Tools 能力落地**：至少支持 2 类 Tool：Wikipedia（外部资料）+ 自有能力（如概念卡/知识库追加检索），均通过 Spring AI `@Tool` 声明并在 `AstroGuideToolset` 中实现。
- **与 V0/V1 兼容**：现有 REST/SSE URL 与基础协议不变（meta/delta/done/error）。
- **可观测与可控**：Tool 调用与多轮回填由 Spring AI 托管，配置与行为与官方文档一致；能在 done 中输出 citations（Advisor 来源；Tool 来源可基于 Spring AI 响应扩展）。

### 1.2 非目标（V1.5 明确不做）

- 复杂工作流可视化编排引擎（LangGraph/Dify 等）
- 全量多模态（图片/语音）
- 天文数据库 SIMBAD/NED（可作为后续 Tool 扩展，本文只给接口预留）

---

## 2. 核心概念：Advisor vs Tool（V1.5 推荐边界）

- **Advisor**：调用 LLM 前，由后端决定并注入的上下文（强调稳定、可控、每次都有）。
  - 推荐：RAG（知识库检索一次）、ChatMemory（对话历史注入）。
- **Tool**：由模型在推理中按需调用的能力（强调“模型决定是否调用/调用几次/参数是什么”）。
  - 推荐：Wikipedia 查询、知识库追加检索、概念卡查询、（可选）回答自检、（可选）对话总结。

设计原则：
- **底座能力（稳定性优先）留在 Advisor**；
- **探索性/按需能力（决定权交给模型）做 Tool**；
- **安全边界（鉴权、限流、DB 权威）永远由后端控制**。

---

## 3. V1.5 数据流（Agent Loop，由 Spring AI 托管）

### 3.1 单次问答（推荐链路）

Tool 执行与多轮回填由 **Spring AI ChatClient 内部** 完成，应用层仅注册独立工具类并调用 `prompt().tools(wikipediaTool, knowledgeBaseTool, conceptCardTool).stream().chatClientResponse()`。

```
用户问题
  ↓
鉴权/限流/会话校验（V0）
  ↓
[Advisors]
  - ChatMemory 注入历史
  - RAG：对当前问题检索一次，注入参考片段（可开关）
  ↓
构造 Prompt（System + 历史 + 当前问题 + [参考]）并注册 Tools（WikipediaTool / KnowledgeBaseTool / ConceptCardTool）
  ↓
ChatClient.stream().chatClientResponse()（Spring AI 内部）
  - 若模型返回 tool_calls → 框架执行 @Tool 方法 → 结果回填 → 再调模型（循环）
  - 若模型返回最终 content → 流式 delta → done
  ↓
SSE：meta → delta → done
  ↓
落库 assistant + usage
```

### 3.2 为什么仍保留“RAG 作为 Advisor”

- RAG 是产品质量底座：每次至少提供自家知识库的参考片段，降低回答波动。
- Tool 形式的 `search_knowledge_base` 作为“追加检索”，补充追问/澄清时的二次检索。

---

## 4. Tools 清单（V1.5 推荐）

V1.5 最小可用组合（强烈建议先实现这 3 个）：

1) `search_wikipedia`
2) `search_knowledge_base`（追加检索型 Tool）
3) `lookup_concept_card`

可选增强：

4) `verify_answer_with_citations`（自检/对齐引用）
5) `summarize_conversation`（长对话压缩记忆）

---

## 5. Tool Schema（建议定义）

以下 schema 使用 OpenAI-compatible tools/function calling 风格描述（示意），最终以 Spring AI 的 Tool/Function 注册方式为准。

### 5.1 Tool：search_wikipedia

用途：按需从 Wikipedia 官方 API 获取摘要/段落，补充外部资料来源。

参数：

```json
{
  "name": "search_wikipedia",
  "description": "Search Wikipedia via official API and return 1-2 relevant extracts for the given query.",
  "parameters": {
    "type": "object",
    "properties": {
      "query": {"type": "string", "description": "Search query"},
      "lang": {"type": "string", "enum": ["en", "zh"], "default": "en"},
      "maxResults": {"type": "integer", "minimum": 1, "maximum": 2, "default": 2}
    },
    "required": ["query"]
  }
}
```

返回（建议）：

```json
{
  "items": [
    {
      "title": "Type Ia supernova",
      "source": "Wikipedia: Type Ia supernova",
      "chunkId": "wiki_Type_Ia_supernova_0",
      "excerpt": "A Type Ia supernova is ..."
    }
  ]
}
```

实现映射：复用现有 WikipediaService，在 `WikipediaTool.searchWikipedia`（@Tool）内调用。

---

### 5.2 Tool：search_knowledge_base

用途：让模型在推理中触发“追加检索”，补充 RAG Advisor 一次检索之外的二次证据。

参数：

```json
{
  "name": "search_knowledge_base",
  "description": "Search AstroGuide knowledge base (VectorStore) and return top chunks with sources.",
  "parameters": {
    "type": "object",
    "properties": {
      "query": {"type": "string"},
      "topK": {"type": "integer", "minimum": 1, "maximum": 8, "default": 4}
    },
    "required": ["query"]
  }
}
```

返回（建议）：

```json
{
  "items": [
    {
      "source": "《书名》: 第12章",
      "chunkId": "book_astro_ch12_3",
      "excerpt": "Standard candles rely on..."
    }
  ]
}
```

实现映射：复用现有 RagService / VectorStore 查询能力（对外返回 citations 友好结构）。

---

### 5.3 Tool：lookup_concept_card

用途：按需查询术语/符号的解释卡片；模型可以先查再解释，或用于生成稳定 key（配合 `[[term:...|key=...]]`）。

参数：

```json
{
  "name": "lookup_concept_card",
  "description": "Lookup a concept card for a term or symbol (term/sym) in zh/en.",
  "parameters": {
    "type": "object",
    "properties": {
      "type": {"type": "string", "enum": ["term", "sym"]},
      "lang": {"type": "string", "enum": ["en", "zh"]},
      "key": {"type": "string", "description": "Stable key if known"},
      "text": {"type": "string", "description": "Raw text if key not known"}
    },
    "required": ["type", "lang"]
  }
}
```

返回（建议）：

```json
{
  "key": "chandrasekhar_limit",
  "title": "钱德拉塞卡极限",
  "short": "...",
  "details": [{"label": "定义", "value": "..."}],
  "seeAlso": ["白矮星", "电子简并压"]
}
```

实现映射：复用 ConceptCardService（`ConceptCardTool.lookupConceptCard`）。

---

### 5.4（可选）Tool：verify_answer_with_citations

用途：让模型自检“回答是否被 citations 支持”，降低编造风险。

参数/返回建议：

```json
{
  "name": "verify_answer_with_citations",
  "description": "Check whether the answer is supported by citations and identify unsupported claims.",
  "parameters": {
    "type": "object",
    "properties": {
      "answer": {"type": "string"},
      "citations": {"type": "array", "items": {"type": "object"}}
    },
    "required": ["answer", "citations"]
  }
}
```

返回（建议）：

```json
{
  "ok": false,
  "issues": [
    {"type": "unsupported_claim", "message": "...", "span": "..."}
  ]
}
```

实现建议：首版可由 LLM 自己做“反思式校验”（仍算 tool），后续再引入更强规则/检索。

---

### 5.5（可选）Tool：summarize_conversation

用途：长对话压缩记忆（将多轮历史总结为要点），用于长期 memory 或成本控制。

建议先做成系统策略触发；若做 Tool，需要严格限制调用条件与落库策略。

---

## 6. Agent Loop 的执行与边界（由 Spring AI 托管）

### 6.1 最大步数与调用上限（推荐强约束）

- `maxAgentSteps`：默认 4（含最终回答那一步）
- `maxToolCallsTotal`：默认 4
- `maxToolCallsByName`：
  - `search_wikipedia` ≤ 2
  - `search_knowledge_base` ≤ 2
  - `lookup_concept_card` ≤ 3

说明：当前实现选择“框架托管 Tool Calling”，不自研循环与限次状态机；因此以下上限更多是**产品层面建议**，需要在后续通过模型侧提示词、底层模型/供应商限制或框架扩展点来落实。

### 6.2 超时与失败策略

- 单次 Tool 超时：2–5 秒（可配）
- Tool 失败（网络/解析）：
  - 允许模型继续回答，但需在 system 提示“某工具失败，请基于已有信息作答并说明局限”。
- 全局超时：建议对整个请求设置 30–60 秒上限。

### 6.3 安全策略

- Tool 输入输出都必须做长度限制与清洗（尤其 Wikipedia excerpt）。
- Wikipedia API 必须设置 User-Agent；禁止网页爬取。
- 任何涉及鉴权、会话归属、限流的决策不交给模型。

---

## 7. SSE 事件扩展（推荐）

当前实现不输出 tool_call/tool_result SSE 事件（保持协议简单稳定）：
- `meta` / `delta` / `done` / `error`

如未来需要可视化与可追溯，可**选做**扩展两类事件（旧客户端可忽略；当前未实现）：

- `event: tool_call`
  - data：`{ "name": "search_wikipedia", "arguments": { ... }, "step": 2 }`
- `event: tool_result`
  - data：`{ "name": "search_wikipedia", "ok": true, "result": { ... }, "step": 2 }`

现有事件保持：
- `meta` / `delta` / `done` / `error`

`done.citations` 当前以 Advisor（RAG/Wikipedia Advisor）来源为主；Tool 来源 citations 若需要对前端透出，需后续做响应扩展或在 Tool 返回结构中约定并落库。

---

## 8. 后端模块拆分与落地建议（与现有重构对齐）

采用 **仅 Spring AI** 时，Tool 执行与多轮回填由 ChatModel/ChatClient 托管，无需自研 ToolExecutor 或 Agent 状态机。推荐分工：

- `ChatStreamOrchestrator`
  - 负责鉴权、参数组装、调用 `ChatStreamService.streamChatClientResponses(..., toolCallingEnabled)`，以及 SSE 组装（meta/delta/done）、citations 合并、落库
- `ChatStreamServiceImpl`
  - 负责 `prompt().advisors(...).tools(astroGuideToolset).stream().chatClientResponse()`；Advisor 与 Tool 注册在此完成，循环由 Spring AI 内部完成
- `WikipediaTool` / `KnowledgeBaseTool` / `ConceptCardTool`
  - 每个工具类各自承载一个 @Tool 方法，由 Spring AI 框架在生成过程中按需调用
- `ChatMemoryPrimer` / 对话历史
  - 负责对话历史 prime（增量/重建），通过 MessageChatMemoryAdvisor 注入
- citations 的构建与合并
  - 目前由 Orchestrator 从 Advisor 响应上下文合并；Tool 来源的 citations 可后续基于 Spring AI 响应扩展

---

## 9. 配置项（建议新增/扩展）

仅使用 Spring AI 时，Tool 执行与多轮由框架/模型托管，步数、超时等受 ChatModel 与底层 API 约束；应用层只控制「是否注册 tools」。

```yaml
app:
  ai:
    tools:
      enabled: true   # 是否向模型注册 AstroGuideToolset（@Tool）；false 则仅 Advisor

  rag:
    enabled: true
    top-k: 4

  chat:
    memory:
      max-messages: 16
```

---

## 10. 测试建议（V1.5）

- Agent Loop 基本路径：
  - 触发 1 次 `search_wikipedia` → 最终回答（delta/done 正常）。
- 追加检索路径：
  - 触发 `search_knowledge_base` 追加证据 → 最终回答（必要时再评估如何透出 Tool citations）。
- 概念卡路径：
  - 问术语/符号 → 触发 `lookup_concept_card` → 输出包含稳定 key 的 marker。
- 限制与降级：
  - 超出 tool call 上限时仍能完成回答（done 正常返回）。

---

## 11. 里程碑（建议）

- M1：使用 Spring AI `@Tool` 拆分为 3 个独立 Tool 类（Wikipedia/KB/ConceptCard），通过 `ChatClient.prompt().tools(wikipediaTool, knowledgeBaseTool, conceptCardTool)` 注册，跑通 Tool 调用与多轮回填（由 Spring AI 托管）。
- M2：流式输出（meta/delta/done）与 citations 合并（Advisor 来源）；如需可视化 tool 过程，再评估是否增加 tool_call/tool_result SSE。
- M3：配置统一为 `app.ai.tools.enabled`，移除自研 agent 相关配置与代码；回归测试 + 文档更新。

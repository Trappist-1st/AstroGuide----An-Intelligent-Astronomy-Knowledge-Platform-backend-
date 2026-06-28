# 🌌 AstroGuide – V2 技术设计文档（Agent 工程化）

> 日期：2026-02-23（架构更新：2026-06-28）  
> 范围：在 V1.5（Spring AI Tool Calling + RAG Advisor）基础上，补齐“严肃 Agent 产品”所需的工程化能力：可观测、可评测、可恢复、可治理、可扩展。  
> 关联文档：
> - [docs/AstroGuide-V1.5-技术设计文档（Agent）.md](./AstroGuide-V1.5-技术设计文档（Agent）.md)
> - [docs/AstroGuide-后端增强路线图（2026Q1）.md](./AstroGuide-后端增强路线图（2026Q1）.md)
> - [docs/AGENT_ARCHITECTURE_DEEP_SUMMARY.md](./AGENT_ARCHITECTURE_DEEP_SUMMARY.md)
> - **[docs/后端整体架构图.md](./后端整体架构图.md) — 当前实现权威参考**

---

## 0. 背景与问题定义

### 0.0 已实现能力（2026-06 Phase 3 快照）

| V2 目标 | 实现状态 | 对应模块 |
|---------|----------|----------|
| G1 可观测 | ✅ 部分完成 | SSE node/tool/route/review 事件；`agent_runs` 审计；Micrometer |
| G2 可评测 | ⏳ 待建 | — |
| G3 可治理 | ✅ 部分完成 | `ToolPolicyService`（预算/超时/限次）；Router/Reviewer |
| G4 可恢复 | ✅ 部分完成 | `MySqlCheckpointSaver` + `agent_checkpoints` |
| G5 可扩展 | ✅ 骨架完成 | LangGraph Workflow + Multi-Agent 节点 |

当前后端已具备：
- LangGraph4j Agent Runtime + Phase 3 Workflow（route/retrieve/plan/prepare/react/review/finalize）
- Spring AI `@Tool` + `ToolRegistry` + `ToolPolicyService`
- RAG（`RagRetrievalService`，可开关）
- Session Memory + Summary Memory（异步 Worker）
- SSE 流式（meta/delta/node/tool/route/review/done/error）
- Agent run 持久化（`agent_runs`）
- 基础限流与 usage 统计

仍待补齐的关键差距：

1. **评测闭环不足**：缺少离线评测集、回归基线、线上反馈回流与失败样本沉淀。
2. **追踪深度不足**：缺少 span 级 OTel 全链路追踪。
3. **分布式能力**：Redis 配额/缓存/多实例状态尚未引入。
4. **长链路能力**：子 Agent 委派、任务级异步编排仍待扩展。

V2 的目标不是推翻现有实现，而是按“最小风险演进”逐层补齐以上能力。

---

## 1. V2 目标与非目标

### 1.1 目标（必须达成）

- **G1 可观测**：建立 Agent 级 Metrics + Tracing + Structured Logging 三件套。
- **G2 可评测**：建立离线评测集与自动回归，覆盖工具调用路径、事实性与引用对齐。
- **G3 可治理**：引入工具预算、超时分级、失败降级、安全边界策略。
- **G4 可恢复**：实现请求级执行快照与可重放，支持问题定位与回归。
- **G5 可扩展**：引入“任务状态机 + 异步执行框架”的骨架，为子 agent/MCP 做铺垫。

### 1.2 非目标（V2 暂不做）

- 直接引入复杂可视化编排平台替换现有链路。
- 一次性全量改造为 LangGraph4j 多图系统。
- 全量多模态（图像理解/语音）与复杂 GUI agent。

---

## 2. 设计原则（借鉴 DeerFlow 的可复用模式）

1. **先边界，后能力**：优先建立清晰模块边界与状态边界，再叠加高级能力。
2. **先可观测，后优化**：没有可观测与评测，不做大规模 Prompt/Tool 优化。
3. **先单 Agent 工程化，再多 Agent 协作**：避免过早引入复杂并发编排。
4. **控制流优先于功能堆叠**：复制 DeerFlow 的“中间件/Advisor 链 + 状态机 + 异步更新”模式，而非逐个功能照搬。
5. **渐进兼容**：V2 必须保持现有 API 协议稳定，新增能力优先以可选开关方式接入。

---

## 3. V2 目标架构

> **与当前实现的关系**：§3.1 分层中 Orchestration / Intelligence 层已落地（见 [后端整体架构图](./后端整体架构图.md)）；§3.2 中 `ToolPolicyService`、`AgentRunStore`（→ `AgentRunService`）、`MemoryUpdateWorker`（→ `SummaryMemoryWorker`）已实现；`AgentExecutionCoordinator` 职责由 `LangGraphAgentRunner` + `ChatStreamOrchestrator` 分担；`AgentEvaluationService` 仍待建。

### 3.1 逻辑分层（目标 vs 当前）

```text
API Layer                                    [✅ 已实现]
  ├─ AIChatController / ConversationController
  └─ SSE Protocol (meta/delta/done/error + node/tool/route/review)

Orchestration Layer                          [✅ 已实现]
  ├─ ChatStreamOrchestrator
  ├─ LangGraphAgentRunner (≈ AgentExecutionCoordinator)
  ├─ Policy Chain (限流/预算/超时)
  └─ AgentRunService (≈ AgentRunStore)

Intelligence Layer                           [✅ 已实现]
  ├─ AstroGuideWorkflowRunner + ReAct Graph
  ├─ ToolRegistry & ToolPolicyService
  ├─ Memory Pipeline (Session + Summary)
  └─ Evaluation Hooks                         [⏳ 待建]

Infrastructure Layer                         [✅ 部分]
  ├─ MySQL (会话/消息/agent_runs/checkpoints/摘要)
  ├─ VectorStore (Qdrant)
  ├─ Redis                                      [⏳ 待引入]
  ├─ Metrics/Tracing (Micrometer)               [✅ 基础]
  └─ Async Executor (SummaryMemoryWorker)       [✅ 已实现]
```

### 3.2 核心新增模块（建议）

1. `AgentExecutionCoordinator`  
   - 职责：统一管理“单次 agent 执行”的生命周期（接受、执行、结束、失败、取消）。
2. `ToolPolicyService`  
   - 职责：工具总调用上限、按工具上限、预算与超时策略。
3. `AgentRunStore`  
   - 职责：保存每次运行的关键快照（模型参数、tool call 摘要、错误原因、耗时）。
4. `MemoryUpdateWorker`  
   - 职责：异步长记忆摘要与落库（不阻塞主对话链路）。
5. `AgentEvaluationService`  
   - 职责：离线评测执行与结果存档，输出质量基线报告。

---

## 4. 执行模型设计（V2）

### 4.1 单次执行生命周期

```text
RECEIVED -> VALIDATED -> RUNNING -> COMPLETED
                           ├-> FAILED
                           ├-> TIMEOUT
                           └-> CANCELLED
```

- 每次运行分配 `runId`（与 requestId/conversationId/messageId 关联）。
- `runId` 对应一条执行记录，包含：
  - 模型与配置快照
  - 工具调用统计与失败明细
  - token/成本/耗时
  - 终止原因

### 4.2 超时与预算（分层）

- **链路总超时**：默认 45s（可配）
- **模型调用超时**：默认 25s（可配）
- **工具调用超时**：默认 3s（可配）
- **预算限制**：
  - `maxToolCallsTotal`（默认 4）
  - `maxToolCallsByName`（按工具配置）
  - `maxInputTokens` / `maxOutputTokens`（按租户或用户级别）

超限策略：
- 优先降级为“禁用追加工具调用 + 保守回答 + 明确边界说明”。

### 4.3 计划对象（Planning）与多步更新

为将 Agent 从“问答”升级到“任务完成”，V2 引入显式计划对象 `AgentPlan`，由协调器维护并持久化：

```json
{
  "planId": "plan_xxx",
  "runId": "run_xxx",
  "goal": "用户目标",
  "status": "draft|active|completed|failed|cancelled",
  "tasks": [
   {
    "taskId": "t1",
    "title": "子任务标题",
    "dependsOn": [],
    "acceptance": "完成条件",
    "status": "todo|doing|done|failed|skipped",
    "retryCount": 0,
    "lastErrorCode": null
   }
  ],
  "updatedAt": "2026-02-23T00:00:00Z"
}
```

- 计划生成时机：`VALIDATED -> RUNNING` 期间创建初版计划。
- 计划更新时机：每次 tool 结果返回、任务失败重试、用户补充要求后增量更新。
- 计划对外暴露：SSE `meta` 可附带 `planSummary`（可选字段），便于前端展示任务进度。
- 完成判定：当所有 `required` 子任务 `done`，且 acceptance 条件满足时，进入 `COMPLETED`。

### 4.4 中断可恢复执行（跨请求）

V2 在 `AgentRunStore` 基础上补充 `resume` 语义，支持断线、超时、工具失败后的跨请求恢复：

1. 持久化检查点（checkpoint）
  - 记录 `runId/planId/currentTaskId/toolBudgetSnapshot/contextWindowHash`。
  - 每次状态迁移和关键 tool 返回后落 checkpoint。
2. 恢复入口
  - 新增恢复操作：`resume(runId)`（可由 API 或内部重试触发）。
  - 恢复时先校验会话归属、模型配置漂移、上下文版本。
3. 恢复策略矩阵
  - `tool timeout`：优先 `retry`（指数退避，最多 N 次），再 `skip`。
  - `provider transient error`：`retry`。
  - `policy rejected`：`rollback/skip` 并给出边界说明。
  - `invalid tool input`：`fail-fast`，回退到重规划（replan）。
4. 终止保证
  - 任一子任务超过重试上限后，`run` 可进入 `FAILED` 或 `COMPLETED_WITH_GAPS`（可选扩展状态，默认映射到 `COMPLETED` + warnings）。

---

## 5. 记忆体系设计（V2）

### 5.1 分层记忆

- **短期记忆（现有）**：ChatMemory + 最近对话消息。
- **总结记忆（新增）**：按会话窗口滚动摘要，存储在 `conversation_memory_summary`。
- **用户画像/偏好记忆（新增）**：偏好、禁忌、语言风格等，存储在 `user_profile_memory`。
- **任务记忆（新增）**：计划与任务上下文（`planId/taskId` 关联），存储在 `agent_task_memory`。
- **Episodic Memory（新增）**：按事件切片存储“发生过什么 + 证据来源 + 时间戳”，支持检索回放。
- **检索入口**：各层记忆通过 Advisor 或 Tool 按策略注入，优先任务记忆，再用户偏好，再总结记忆。

建议权限边界：
- 用户级：`user_profile_memory` 仅同用户可读。
- 会话级：`conversation_memory_summary` 仅同 conversation 可读。
- 任务级：`agent_task_memory` 仅同 `runId/planId` 可读。
- 审计级：episodic 原始片段仅审计角色可访问，在线推理默认使用脱敏摘要。

### 5.2 异步记忆更新流程

```text
after_agent hook -> MemoryQueue(防抖/聚合) -> MemoryUpdateWorker
-> LLM summarization -> schema validate -> DB upsert
```

- 失败不阻塞主链路。
- 记忆更新必须可审计（记录输入窗口、输出摘要、版本号）。

### 5.3 记忆压缩与淘汰

- 压缩策略：`recent window` 保留原文，历史消息按 `rolling summary` 压缩。
- 淘汰策略：按 `TTL + 优先级 + 最近访问时间` 进行分层淘汰。
- 反压策略：当记忆更新队列积压时，仅保留最新聚合窗口，避免写放大。
- 质量约束：摘要写入前执行 schema 校验 + 长度阈值校验。

---

## 5.4 工具编排策略增强（V2）

### 5.4.1 工具选择路由

- 引入 `ToolRouter`：基于意图分类、上下文信号、任务阶段（plan/execute/verify）路由候选工具。
- 路由结果包含：`selectedTools[]`、`reasonCode`、`confidence`。
- 当路由置信度低时，走“保守模式”（减少工具调用，优先给出边界说明）。

### 5.4.2 预算治理扩展

在现有调用次数预算外，新增：
- `maxToolCallsByStage`（按阶段预算）
- `maxToolTokensTotal`（工具调用总 token）
- `maxToolCostUsd`（单 run 估算成本上限）

### 5.4.3 失败降级与结构化融合

- 失败降级链：`retry -> fallback tool -> skip -> answer with uncertainty`。
- 工具结果统一映射为 `ToolEvidence` 结构，而非仅拼接 reference 文本：

```json
{
  "toolName": "search_knowledge_base",
  "status": "success|timeout|failed",
  "claims": ["..."],
  "citations": ["docId#chunkId"],
  "confidence": 0.0,
  "errorCode": null
}
```

- 在生成答案前执行 evidence merge：去重、冲突检测、引用完整性检查。

---

## 6. 可观测与审计设计

### 6.1 指标（Micrometer）

- `agent.run.total`（按状态标签）
- `agent.run.latency`（P50/P95/P99）
- `agent.tool.calls`（按工具名、成功/失败）
- `agent.tool.timeout`（工具超时次数）
- `agent.citation.coverage`（有引用回答占比）
- `agent.hallucination.flagged`（评测/人工标注结果）

### 6.2 链路追踪（OpenTelemetry）

span 建议：
- `agent.run`
- `advisor.rag.retrieve`
- `tool.search_wikipedia` / `tool.search_knowledge_base` / `tool.lookup_concept_card`
- `llm.generate`
- `memory.update.async`

### 6.3 审计日志

每次 `runId` 至少记录：
- 输入摘要与关键上下文来源
- 工具调用摘要（不落敏感原文）
- 终止原因、错误码、重试次数
- 关键配置快照（模型、温度、开关）

---

## 7. 评测体系设计

### 7.1 离线评测

建立 `eval` 数据集（建议首批 200~500 条），覆盖：
- 工具必须调用场景
- 工具不应调用场景
- 引用完整性场景
- 长上下文追问场景
- 边界/拒答与安全场景

输出指标：
- Tool Call Precision/Recall
- Citation Coverage
- Unsupported Claim Rate
- Answer Helpfulness（人工标注）

### 7.2 在线评测

- 采集用户反馈（thumb up/down + 原因标签）。
- 自动沉淀失败样本到回归集。
- 每周产出“质量波动报告”。

---

## 8. 安全与治理

1. **输入治理**：用户输入、RAG 文本、工具返回统一做长度限制与清洗。
2. **提示注入防护**：对外部文本做“数据区/指令区”隔离策略。
3. **工具权限边界**：工具白名单 + 参数校验 + 速率限制。
4. **数据合规**：敏感字段脱敏、可删除策略、审计留痕。
5. **异常降级**：工具故障时明确告知“证据不足区域”。
6. **记忆权限边界**：按 user/conversation/run/plan 四级作用域控制读取，默认最小权限。
7. **跨请求恢复权限**：`resume(runId)` 需校验操作者与 run 所属关系，防止越权恢复。

---

## 9. 演进路线（V2 里程碑）

### M1（2~3 周）：可观测与治理底座

- runId 生命周期 + AgentRunStore
- AgentPlan 数据模型 + 计划增量更新机制
- 工具预算与超时策略
- OTel tracing + Agent 指标集
- 标准化错误码与终止原因

### M2（3~4 周）：质量闭环

- 离线评测框架 + 首批数据集
- 引用对齐自检（`verify_answer_with_citations` 落地）
- 失败样本回流机制

### M3（3~4 周）：记忆与异步能力

- MemoryQueue + MemoryUpdateWorker
- 分层记忆（summary/profile/task/episodic）表结构与检索注入策略
- Ingest/重任务统一任务状态机

### M4（4 周+）：扩展能力（可选）

- 子 agent 委派（单一 subagent 先行）
- MCP/HTTP 外部工具统一注册
- 多实例部署下 Redis 状态共享

---

## 10. 配置草案（V2）

```yaml
app:
  agent:
    run:
      total-timeout-ms: 45000
      resume-enabled: true
      checkpoint-save-enabled: true
    tool:
      enabled: true
      max-calls-total: 4
      max-calls-by-stage:
        plan: 1
        execute: 3
        verify: 1
      max-tool-tokens-total: 6000
      max-tool-cost-usd: 0.03
      max-calls-by-name:
        search_wikipedia: 2
        search_knowledge_base: 2
        lookup_concept_card: 3
      timeout-ms: 3000
    planning:
      enabled: true
      max-tasks-per-plan: 8
      expose-plan-summary-in-sse: true
    memory:
      async-update-enabled: true
      debounce-ms: 3000
      tiered-enabled: true
      episodic-enabled: true
      profile-enabled: true
      task-memory-enabled: true
      policy-scope-enforcement-enabled: true
    evaluation:
      offline-enabled: true
      online-feedback-enabled: true
    observability:
      trace-enabled: true
      tool-event-log-enabled: true
```

---

## 11. 风险与权衡

1. **复杂度上升**：引入 run store、评测、异步 worker 后维护成本上升。  
   - 对策：按里程碑开关化落地，不一次性全开。
2. **性能开销**：trace/日志可能增加延迟。  
   - 对策：采样率控制 + 异步日志。
3. **质量评测成本**：标注与维护 eval 集需要持续投入。  
   - 对策：先做小规模高价值集，逐周迭代。

---

## 12. 与现有代码映射（落地入口）

- `ChatStreamOrchestrator`：接入 `runId`、状态机终态、统一错误语义。
- `ChatStreamServiceImpl`：接入 ToolPolicy、ToolRouter、ToolEvidence 融合与观测埋点拦截。
- `AgentExecutionCoordinator`：接入 AgentPlan 生命周期与 checkpoint 持久化。
- `WikipediaTool/KnowledgeBaseTool/ConceptCardTool`：补充参数校验、超时、错误分类。
- `application.yaml`：新增 `app.agent.*` V2 配置（与现有配置兼容）。
- `UsageService`：扩展到 run 维度成本核算。

---

## 13. 验收标准（V2 Exit Criteria）

满足以下条件可认为 V2 达标：

1. 90%+ 请求具备完整 `runId` 可追踪链路（日志+指标+trace）。
2. Tool 调用失败可分类归因，且超时/异常有统一降级策略。
3. 每次发布前可执行离线评测并形成报告（至少覆盖 5 类核心场景）。
4. Memory 异步更新不影响主链路可用性，且有失败重试与审计记录。
5. API 协议保持向后兼容，前端无需大改即可消费。
6. 80%+ 多步请求可生成可解释 `planSummary`，且任务完成条件可追踪。
7. 工具结果以结构化 evidence 融合，引用缺失率较 V1.5 下降（阈值按评测配置）。

---

## 14. 结论

V2 的核心不是“换框架”，而是把现有 Spring AI Agent 问答链路升级为 **工程化可运营系统**：
- 有状态（run 级）
- 有计划（task 级）
- 可治理（预算/超时/边界）
- 可观测（metrics/tracing/logging）
- 可评测（离线+在线闭环）
- 可扩展（异步任务与子 agent）

这一路线与 DeerFlow 总结中的关键模式一致：先复制架构边界与控制流，再扩展复杂能力。
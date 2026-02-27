# ✅ AstroGuide V2 TODO（Agent 工程化执行清单）

> 基于 `AstroGuide-V2-技术设计文档（Agent工程化）.md` 的执行版任务清单。  
> 日期：2026-02-23

---

## 0. 使用方式

- 状态：`TODO` / `DOING` / `DONE` / `BLOCKED`
- 优先级：`P0`（必须）/ `P1`（高）/ `P2`（可选）
- 每周更新：完成项、阻塞项、风险项

---

## 1. 里程碑总览

- **M1（2~3 周）**：可观测与治理底座
- **M2（3~4 周）**：质量闭环
- **M3（3~4 周）**：记忆与异步能力
- **M4（4 周+）**：扩展能力（子 agent / MCP）

---

## 2. M1：可观测与治理底座

### 2.1 Run 生命周期（P0）

- [ ] 设计并落地 `runId` 生成与透传（requestId/conversationId/messageId 关联）
- [ ] 新增 `agent_run` 数据表（状态、终止原因、耗时、token、成本）
- [ ] 在 `ChatStreamOrchestrator` 中接入状态迁移：RECEIVED/VALIDATED/RUNNING/COMPLETED/FAILED/TIMEOUT/CANCELLED
- [ ] 统一错误码与终止原因字典（含 tool timeout / provider error / policy rejected）
- [ ] 设计并落地 `AgentPlan` 数据模型（task/dependsOn/acceptance/status/retryCount）
- [ ] 增加 checkpoint 持久化与 `resume(runId)` 恢复入口
- [ ] 定义恢复策略矩阵（retry/rollback/skip/fail-fast）及重试上限

**验收**：可按 runId 从日志定位到完整一次执行。

### 2.2 Tool 治理（P0）

- [ ] 新增 `ToolPolicyService`（总调用上限、按工具上限、单工具超时）
- [ ] 在 `ChatStreamServiceImpl` 接入调用预算与降级策略
- [ ] 新增 `ToolRouter`（按意图/阶段路由候选工具）
- [ ] 三个 Tool 增加输入校验、长度限制、异常分类
- [ ] 增加预算维度：按阶段预算、总 tool token、总成本上限
- [ ] 工具结果统一为 `ToolEvidence` 结构并做融合（去重/冲突检测/引用校验）
- [ ] 配置化开关：`app.agent.tool.*`

**验收**：工具超限/超时/异常均可被稳定降级并记录。

### 2.3 可观测（P0）

- [ ] 增加指标：`agent.run.total`、`agent.run.latency`、`agent.tool.calls`、`agent.tool.timeout`
- [ ] 接入 OpenTelemetry spans（run、rag、tool、llm）
- [ ] 结构化日志字段标准化（runId/requestId/conversationId/messageId/toolName/status）

**验收**：Grafana/日志检索可看到 run 与 tool 维度指标。

---

## 3. M2：质量闭环

### 3.1 离线评测框架（P0）

- [ ] 新建 `eval` 数据集目录与样例格式（JSONL）
- [ ] 实现评测执行器（批量请求 + 结果判定 + 报告输出）
- [ ] 建立首批 200+ 样本（5 类场景）
- [ ] CI 增加“评测回归门禁”（阈值告警，不一定阻断）

**验收**：每次发布前可生成标准评测报告。

### 3.2 引用对齐与事实性（P1）

- [ ] 实现 `verify_answer_with_citations`（先规则/LLM 混合版）
- [ ] `done` 事件中增加引用完整性标识（可选字段）
- [ ] 建立 unsupported claim 统计指标

**验收**：可观测到引用缺失和不被支持陈述占比。

### 3.3 在线反馈闭环（P1）

- [ ] 设计反馈表结构（thumb up/down + reason）
- [ ] 建立失败样本自动回流机制（进入 eval 集候选池）
- [ ] 每周输出质量波动报告模板

**验收**：线上差评样本能在次周回归集中复测。

---

## 4. M3：记忆与异步能力

### 4.1 异步记忆更新（P1）

- [ ] 新增 `MemoryQueue`（防抖合并）
- [ ] 新增 `MemoryUpdateWorker`（异步摘要、schema 校验、落库）
- [ ] 长期记忆表结构设计（用户级/会话级，JSON 字段）
- [ ] 补齐记忆层次：summary/profile/task/episodic 四层
- [ ] 记忆注入优先级策略（task > profile > summary）
- [ ] 记忆压缩与淘汰策略（rolling summary + TTL/LRU）
- [ ] 记忆权限边界（user/conversation/run/plan）与越权审计
- [ ] 失败重试与审计日志

**验收**：主链路不因记忆更新失败而中断。

### 4.2 任务状态机（P1）

- [ ] 将 Ingest 重任务统一纳入任务状态机（queued/running/success/failed/retry）
- [ ] 增加任务查询与取消接口
- [ ] 增加任务维度指标与告警

**验收**：大文件任务可追踪、可重试、可审计。

---

## 5. M4：扩展能力（可选）

### 5.1 子 Agent 先行版（P2）

- [ ] 实现单一 `task` 风格委派工具（仅 1 个 subagent 类型）
- [ ] 子任务超时与并发上限
- [ ] 子任务结果回传与聚合规范

### 5.2 外部工具生态（P2）

- [ ] 设计 MCP/HTTP 工具注册接口（Tool Registry 扩展点）
- [ ] 工具能力门控（按环境、按租户、按模型）
- [ ] 工具安全审查清单（权限、审计、限流）

---

## 6. 测试清单（跨里程碑）

### 6.1 单元测试

- [ ] ToolPolicyService（预算/超时/边界）
- [ ] AgentRun 状态迁移
- [ ] AgentPlan 状态迁移与完成条件判定
- [ ] MemoryQueue 合并与防抖逻辑
- [ ] ToolRouter 路由决策与低置信降级

### 6.2 集成测试

- [ ] SSE 主链路：meta/delta/done/error + runId
- [ ] SSE `meta` 中 `planSummary` 增量更新
- [ ] Tool 调用失败降级路径
- [ ] RAG 命中与未命中路径
- [ ] 长记忆异步更新不阻塞主链路
- [ ] `resume(runId)` 断线恢复与权限校验路径

### 6.3 回归测试

- [ ] 离线评测集自动执行
- [ ] 核心指标阈值检查（tool timeout、citation coverage、latency、plan completion）

---

## 7. 数据与配置变更 TODO

- [ ] 新增表：`agent_run`
- [ ] 新增表：`agent_plan` / `agent_plan_task`
- [ ] 新增表：`agent_feedback`
- [ ] 新增表：`agent_memory_longterm`（或拆分 user/conversation）
- [ ] 新增表：`agent_memory_profile` / `agent_memory_task` / `agent_memory_episodic`
- [ ] 新增配置：`app.agent.run.*` / `app.agent.planning.*` / `app.agent.tool.*` / `app.agent.memory.*` / `app.agent.evaluation.*`
- [ ] 更新 `application.yaml` 示例与环境变量说明

---

## 8. 运维与发布 TODO

- [ ] 增加 dashboard：Agent Run / Tool / Latency / Error / Cost
- [ ] 发布灰度策略（按流量比例）
- [ ] 回滚预案（配置开关回退）
- [ ] 周报模板：稳定性、质量、成本三栏

---

## 9. 建议优先顺序（严格执行）

1. **先做 M1（P0）**：没有 run 可观测和 tool 治理，不进入 M2。  
2. **再做 M2（P0/P1）**：没有评测闭环，不做大规模 Prompt 调优。  
3. **然后 M3（P1）**：保证异步记忆与任务化稳定。  
4. **最后 M4（P2）**：在底座稳定后扩展子 agent 与外部工具。

---

## 10. 当前周建议开工包（可直接立项）

- [ ] A 包：runId + `agent_run`/`agent_plan` + 状态机/恢复入口落地
- [ ] B 包：ToolPolicyService + ToolRouter + ToolEvidence 融合 + 三个 Tool 参数/异常治理
- [ ] C 包：OTel spans + Agent metrics + Dashboard 初版 + plan 指标

> 若本周人力有限，优先 A+B；C 可并行推进基础埋点。
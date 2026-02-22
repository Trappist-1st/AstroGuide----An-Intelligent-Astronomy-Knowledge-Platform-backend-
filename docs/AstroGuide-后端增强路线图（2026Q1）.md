# 🌌 AstroGuide 后端增强路线图（2026Q1）

> 日期：2026-02-22  
> 适用范围：AstroGuide 后端（稳定性/可观测、RAG 效果、性能成本、用户体验）  
> 当前决策前提：短期不引入正式鉴权；部署形态待定（先按单机可落地，保留多实例扩展路径）

---

## 1. 目标与原则

### 1.1 本阶段目标

1. **稳定性与可观测**：让线上问题可发现、可定位、可追踪。
2. **RAG 效果优化**：提高检索命中质量，降低幻觉和无引用回答比例。
3. **性能与成本治理**：降低首包延迟与总时延，建立 token 成本看板基础。
4. **用户体验改进（后端侧）**：让回答链路对前端更可解释（来源、耗时、降级状态）。

### 1.2 实施原则

- 先做“**可运行**”，再做“**可扩展**”。
- 优先改造主链路，不一次性重构全量模块。
- 每项能力都要有可验证指标（Metrics / API 验收 / 回归测试）。

---

## 2. 当前现状（摘要）

- 已有能力：会话与消息管理、SSE 流式回答、基础限流、RAG（可开关）、Tool Calling、Ingest。
- 主要短板：请求追踪不统一、可观测弱、关键链路测试不足、RAG 链路仍有提效空间、Ingest 偏同步重任务。

---

## 3. 路线总览

- **里程碑 M1（0-6 周）**：稳定性与可观测底座 + RAG 提效第一阶段 + 用户体验元数据。
- **里程碑 M2（8-12 周）**：扩展与工程化（多实例准备、异步任务标准化、发布治理）。

---

## 4. M1（0-6周）执行计划

### Week 1-2：主链路可观测与故障分层

- 统一 requestId 追踪（请求头、响应头、错误响应、SSE meta）。
- 流式链路关键指标：请求数、结束状态（done/error/cancelled）、时延。
- 日志规范化：可按 requestId / conversationId / messageId 快速排障。
- 输出：主链路追踪闭环、基础 metrics 可查询。

### Week 3-4：RAG 提效与可解释性增强

- 评估并移除重复检索路径，减少不必要向量检索。
- done 事件补充可解释字段（如 citations 完整性、链路阶段耗时）。
- 统一“检索失败回退策略”（模型直答 + 边界说明）。
- 输出：回答质量稳定度提升、首包时间与总时延下降。

### Week 5：Ingest 稳定化（任务化）

- 将重型摄入流程任务化（queued/running/success/failed/retry）。
- 增加任务状态查询接口与失败重试机制。
- 输出：大文件导入稳定性提升，避免接口超时与阻塞。

### Week 6：测试与文档收口

- 为关键链路补齐测试（会话/消息/SSE/RAG 回退/限流/Ingest 任务状态）。
- 更新 API 与技术设计文档，保证实现与文档一致。
- 输出：可回归的质量基线和可交付文档。

---

## 5. M2（8-12周）季度路线

1. **多实例准备**
   - 限流从单机内存升级为分布式方案（如 Redis）。
   - 关键缓存与会话策略外部化（按演进需要逐步引入）。

2. **异步任务标准化**
   - Ingest 任务队列化，预留 MQ 对接能力。
   - 增强任务审计、重试、死信与告警策略。

3. **成本与模型策略深化**
   - 按场景分层模型路由（成本优先 vs 质量优先）。
   - usage 数据用于周报/告警阈值。

4. **发布治理与运维能力**
   - 多环境配置分层、灰度发布、快速回滚预案。

---

## 6. 验收指标（建议）

### 6.1 稳定性与可观测

- SSE 成功完成率（done / 总请求）
- SSE 中断率（cancelled/error）
- 可追踪请求占比（具备 requestId）
- 关键错误 MTTR（平均修复时间）

### 6.2 质量与体验

- 引用覆盖率（有 citations 的回答占比）
- 幻觉投诉率（人工抽样或运营标注）
- 回答可解释元数据覆盖率

### 6.3 性能与成本

- 首包时间 P50/P95
- 完整响应时延 P50/P95
- 单次请求平均 token 成本

---

## 7. 风险与依赖

- 部署拓扑未定：影响限流、缓存、任务化选型（先保持可单机落地）。
- 测试环境资源不足：可能影响 CI 稳定性与回归效率。
- 文档与实现漂移：需要每阶段同步更新。

---

## 8. 已落地改造（2026-02-22）

### 8.1 本次已实现

1. 统一 requestId 追踪
   - 新增请求过滤器与工具类，支持 `X-Request-Id` 透传/生成。
   - 错误响应统一回传 requestId。
   - SSE `meta/error` 统一携带 requestId。

2. 流式链路埋点
   - 增加流式请求、终态、时延指标。
   - 增加主链路日志（accepted/done/error/cancelled）。

3. 指标端点能力
   - 引入 Actuator，暴露 `health/info/metrics`。

### 8.2 关联文件

- `src/main/java/com/imperium/astroguide/config/RequestIdSupport.java`
- `src/main/java/com/imperium/astroguide/config/RequestIdFilter.java`
- `src/main/java/com/imperium/astroguide/controller/AIChatController.java`
- `src/main/java/com/imperium/astroguide/controller/ConversationController.java`
- `src/main/java/com/imperium/astroguide/controller/MessageController.java`
- `src/main/java/com/imperium/astroguide/controller/ConceptCardsController.java`
- `src/main/java/com/imperium/astroguide/controller/GlobalValidationExceptionHandler.java`
- `src/main/java/com/imperium/astroguide/ai/orchestrator/ChatStreamOrchestrator.java`
- `src/main/resources/application.yaml`
- `pom.xml`

---

## 9. 下一步建议（紧接执行）

1. 落地 RAG 双检索去重与阶段耗时分解（retrieval/generation）。
2. 为 SSE 主链路补齐集成测试，固定回归基线。
3. 将 Ingest 改为任务化状态机，先单机队列实现。

---

## 10. 备注

本路线图为执行版文档，建议每周例会更新“已完成/阻塞/风险”三栏，并在阶段结束后把验收指标回填到本文件。
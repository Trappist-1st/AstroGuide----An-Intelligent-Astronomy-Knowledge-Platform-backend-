# 🌌 AstroGuide

基于 Spring Boot、Spring AI 与 LangGraph4j 构建的天文知识智能平台，具备 Multi-Agent 路由、RAG、工具调用与 SSE 流式对话能力。

## 功能特性

- **LangGraph Agent Runtime**：Phase 3 工作流 — `route → retrieve → [plan] → prepare → react → review → finalize`
- **Multi-Agent 路由**：SIMPLE / COMPLEX 分流，复杂问题走 Planner 与 Answer Reviewer
- **ReAct 子图**：LangGraph4j agent ↔ tools 循环，Checkpoint 持久化到 MySQL
- **Tool Registry + Policy**：维基百科、知识库、概念卡 — 含预算、超时与审计
- **Context Engineering**：历史裁剪、RAG 注入、Summary Memory、Token 估算
- **Summary Memory**：异步滚动会话摘要，不阻塞 SSE 主链路
- **AI 聊天流式输出**：SSE 事件 — `meta` / `delta` / `node_*` / `tool_*` / `route` / `review` / `done`
- **RAG 集成**：Qdrant 向量检索，`done` 事件返回 citations
- **概念卡片**：交互式天文术语与符号解释
- **会话管理**：持久化对话、用量统计、Agent run 审计

## 技术栈

- **后端**：Spring Boot 3.4、Spring AI 1.1+、LangGraph4j 1.8
- **数据库**：MySQL（conversations、messages、agent_runs、checkpoints、memory summaries）
- **向量存储**：Qdrant（RAG）
- **AI 提供商**：OpenAI 兼容 API（DeepSeek、SiliconFlow 等）
- **可观测性**：Micrometer 指标、结构化 agent run 记录
- **构建工具**：Maven · **Java 21**

## 快速开始

### 前置条件

- Java 21 或更高版本
- Maven 3.6+
- MySQL 8.0+
- Qdrant（可选，通过 `app.rag.enabled=true` 启用）

### 安装

1. 克隆仓库：
   ```bash
   git clone https://github.com/your-username/astroguide.git
   cd astroguide
   ```

2. 在 `application.yaml` 或通过 `.env` 文件配置环境变量（参考 `.env.example`）：
   ```yaml
   spring:
     ai:
       openai:
         api-key: your-openai-api-key
         base-url: https://api.deepseek.com
     datasource:
       url: jdbc:mysql://localhost:3306/astroguide
       username: your-db-username
       password: your-db-password
   app:
     ai:
       tools:
         enabled: true
     rag:
       enabled: true
   ```

3. 初始化数据库：
   ```bash
   mysql -u root -p astroguide < database/schema-mysql.sql
   ```

4. 运行应用：
   ```bash
   mvn spring-boot:run
   ```

应用默认在 `http://localhost:8093` 启动。

## API 端点

- `GET /api/v0/conversations/{conversationId}/messages/{messageId}/stream` — AI 聊天流式输出（SSE）
- `GET /api/v0/conversations` — 列出对话
- `POST /api/v0/conversations` — 创建对话
- `GET /api/v0/concept-cards` — 概念卡查询

## 配置

`application.yaml` 中的关键选项：

| 配置项 | 说明 |
|--------|------|
| `app.ai.tools.enabled` | 是否为 ReAct Agent 注册工具 |
| `app.ai.runtime.recursion-limit` | ReAct 循环最大深度（默认 12） |
| `app.ai.runtime.emit-*-events` | 是否输出 node / tool / route SSE 事件 |
| `app.ai.router.enabled` | 启用 SIMPLE / COMPLEX 路由 |
| `app.ai.planner.enabled` | 复杂问题启用执行计划 |
| `app.ai.reviewer.enabled` | 启用回答审查步骤 |
| `app.ai.memory.summary-debounce-ms` | Summary Memory 防抖间隔 |
| `app.rag.enabled` | 工作流中启用 RAG 检索 |
| `app.rag.top-k` | 向量检索 Top-K |

## 项目结构

```
src/main/java/com/imperium/astroguide/
├── controller/          # REST 控制器
├── ai/
│   ├── orchestrator/    # SSE 编排（ChatStreamOrchestrator）
│   ├── runtime/         # AgentRuntime、LangGraphAgentRunner、流式事件
│   ├── graph/           # 工作流编排、ReAct 子图、Checkpoint
│   ├── context/         # 上下文组装、裁剪、Token 预算
│   ├── memory/          # Session + Summary Memory、异步 Worker
│   ├── multiagent/      # Router / Planner / Reviewer
│   ├── rag/             # RAG 检索服务
│   ├── tool/            # Tool 注册、策略、执行审计
│   └── tools/           # Wikipedia / KB / ConceptCard @Tool 实现
├── service/             # 业务服务（含 AgentRunService）
├── mapper/              # MyBatis 映射器
├── model/               # DTO 与实体
├── policy/              # 限流策略
└── config/              # Spring 配置（Async、Chat、VectorStore 等）
```

## 架构概览

```text
Frontend ──► ChatStreamOrchestrator ──► LangGraphAgentRunner
                                              │
                                              ▼
                                    AstroGuideWorkflowRunner
                                    route → retrieve → [plan]
                                    → prepare → react → review → finalize
                                              │
                                              ▼
                                    LangGraph ReAct（agent ↔ tools）
```

完整架构图、时序图、SSE 协议与数据库说明见 [后端整体架构图](docs/后端整体架构图.md)。

## 贡献

1. Fork 本仓库
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

本项目采用 MIT 许可证 — 查看 LICENSE 文件了解详情。

## 文档

- [后端整体架构图](docs/后端整体架构图.md) — **当前实现参考**
- [V1 技术设计文档](docs/AstroGuide-V1-技术设计文档.md) — RAG 基线
- [V1.5 Agent 设计](docs/AstroGuide-V1.5-技术设计文档（Agent）.md) — 工具调用
- [V2 Agent 工程化](docs/AstroGuide-V2-技术设计文档（Agent工程化）.md) — 路线图
- [数据库 Schema](database/schema-mysql.sql)

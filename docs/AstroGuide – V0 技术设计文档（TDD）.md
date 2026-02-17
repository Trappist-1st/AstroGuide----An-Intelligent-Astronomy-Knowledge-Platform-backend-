# 🌌 AstroGuide – V0 技术设计文档（TDD）

> 日期：2026-02-07  
> 范围：仅覆盖 V0（AI 天文问答 + 三档难度 + 双语切换 + SSE 流式 + Concept Cards 点读解释 + 基础观测与成本控制）。

---

## 0. 目标与非目标

### 0.1 目标（V0必须达成）

- Web 问答页：支持多轮对话、难度选择（Basic/Intermediate/Advanced）、语言切换（EN/中文）
- AI 回答：结构化输出模板稳定（结论→分层讲解→公式可选→误区→下一步建议）
- 流式输出：后端通过 SSE（Server-Sent Events）推送 token 增量
- Concept Cards：回答中关键术语/符号可点击，弹出概念卡片（1-3句定义 + 符号解释 + 可选数量级/条件）
- 可观测：请求日志、错误日志、耗时、token/费用估算
- 成本控制：限流、上下文裁剪、最大输出限制、停止生成

### 0.2 非目标（V0明确不做）

- RAG/向量检索/引用出处系统
- 文章系统/内容后台
- 注册登录/跨设备同步
- 复杂天体可视化与观测工具

---

## 1. 技术选型（V0）

### 1.1 前端

- 框架：Vue 3 + Vite
- 语言：TypeScript
- UI：Tailwind CSS（快速统一视觉）
- Markdown：`markdown-it`
- LaTeX：KaTeX（优先）
- 流式：EventSource（SSE）或 `fetch` + ReadableStream（可选；SSE优先）
- 状态管理：Pinia（轻量）

### 1.2 后端

- 语言/框架：Java 21（建议 LTS）+ Spring Boot 3.x
- API：REST + SSE
- HTTP Client：Spring `WebClient`（便于对接 OpenAI-compatible streaming）
- JSON：Jackson

### 1.3 数据层

- V0：MySQL（开发/早期上线），通过 JPA/Hibernate 或 MyBatis（建议 MyBatis 更可控）
- 未来：PostgreSQL（迁移预留字段与索引）

### 1.4 AI Provider

- 采用“OpenAI-compatible”抽象：支持 OpenAI GPT / DeepSeek 等
- 关键要求：支持流式增量（chunk/delta）与 token 用量（可选）
- **AI 框架**：Spring AI（简化 LLM 调用、Prompt 管理与流式处理）

---

## 2. 总体架构

```
Browser (Vue)
  |  REST (history/settings)
  |  SSE  (stream answer)
  v
Backend (Spring Boot)
  |  Spring AI (ChatClient, StreamingChatClient)
  |  Provider Adapter (OpenAI-compatible)
  |  Prompt Builder (difficulty/language)
  |  Policy (rate-limit/context-trim/max-output)
  v
LLM API

SQLite
  ^
  |  conversations/messages/usage/term_cache
```

## 2. 总体架构

```
Browser (Vue)
  |  REST (history/settings)
  |  SSE  (stream answer)
  v
Backend (Spring Boot)
  |  Provider Adapter (OpenAI-compatible)
  |  Prompt Builder (difficulty/language)
  |  Policy (rate-limit/context-trim/max-output)
  v
LLM API

SQLite
  ^
  |  conversations/messages/usage/term_cache
```

### 2.1 核心设计原则

- 可替换：LLM Provider、模型、路由策略均配置化
- 可验收：输出“契约化”（模板 + 标记协议），前端可稳定渲染 Concept Cards
- 可控：SSE 流、取消、失败兜底、成本上限

---

## 3. 关键交互与数据流

### 3.1 提问（流式回答）

1) 前端创建或选择会话（conversation）
2) 前端 POST 发送用户消息（message）
3) 前端打开 SSE 连接开始接收增量
4) 后端：
   - 组装上下文（最近 N 轮）
   - 生成 system prompt + user prompt
   - 调用 LLM streaming
   - 将增量转发为 SSE `delta` 事件
5) 完成后：发送 `done` 事件，落库 assistant 消息与用量

### 3.2 Concept Cards 点读解释

V0 采用“模型输出标记协议”实现（最稳、最低工程量）：

- 模型回答中将需要可点击的术语/符号写成可解析的短标记
- 前端渲染时替换为可点击组件
- 点击后：
  - 优先从缓存表查询（term_cache）
  - 未命中则后端调用低成本模型生成卡片并缓存

---

## 4. 输出契约（非常关键）

### 4.1 回答 Markdown 约束

- 输出必须是 Markdown（可包含 LaTeX）
- 禁止捏造引用/论文/书籍
- 对不确定内容必须明确假设与不确定性

### 4.2 Concept Markers 协议（V0）

模型在回答中对关键术语/符号使用以下形式：

- 术语：`[[term:Chandrasekhar limit]]`
- 符号：`[[sym:M_{\rm Ch}]]`
- 可选提供稳定 key：`[[term:Chandrasekhar limit|key=chandra_limit]]`

前端解析规则：

- 识别 `[[...]]`，提取类型（term/sym）、显示文本、可选 key
- 渲染为可点击“chip/link”

### 4.3 Concept Card 返回结构（后端→前端）

统一 JSON：

```json
{
  "key": "chandra_limit",
  "title": "Chandrasekhar limit",
  "short": "Roughly the maximum mass of a stable white dwarf supported by electron degeneracy pressure.",
  "details": [
    {"label": "Meaning", "value": "A stability threshold for white dwarfs; above this, collapse/thermonuclear runaway becomes possible depending on conditions."},
    {"label": "Typical scale", "value": "~1.4 M☉ (order-of-magnitude; depends on composition/assumptions)."},
    {"label": "When to use", "value": "White dwarf structure / Type Ia supernova context."}
  ],
  "seeAlso": [
    "Electron degeneracy pressure",
    "White dwarf"
  ]
}
```

---

## 5. API 设计（V0）

统一前缀：`/api/v0`

### 5.0 通用约定

#### 5.0.1 鉴权（V0）

- V0 无账号体系。
- 以匿名 `clientId` 作为“用户隔离”边界。

#### 5.0.2 必需请求头

- `X-Client-Id: <uuid>`：必需（读写都要带），用于隔离会话/消息。
- `Content-Type: application/json`：仅对 JSON 请求体必需。

> 说明：前端首次访问生成 `clientId`（UUID）写入 localStorage；之后所有请求都携带 `X-Client-Id`。

#### 5.0.3 时间与ID

- `id`：建议使用 ULID 或 UUID 字符串。
- 时间：ISO-8601 字符串（UTC），如 `2026-02-07T00:00:00Z`。

#### 5.0.4 枚举

- `difficulty`：`basic | intermediate | advanced`
- `language`：`en | zh`
- `message.status`：`queued | streaming | done | error | cancelled`

#### 5.0.5 错误返回（JSON）

除 SSE 流接口外，错误统一返回：

```json
{
  "error": {
    "code": "invalid_argument",
    "message": "content is required",
    "requestId": "req_...",
    "details": {
      "field": "content"
    }
  }
}
```

建议 `code` 取值（V0最小集）：

- `invalid_argument`（400）
- `unauthorized`（401，仅保留占位；V0通常不出现）
- `forbidden`（403，clientId 不匹配访问他人资源）
- `not_found`（404）
- `rate_limited`（429）
- `provider_error`（502，上游 LLM 异常）
- `timeout`（504）
- `internal_error`（500）

---

### 5.1 会话（Conversations）

#### POST /conversations

创建会话。

Request:

```json
{ "title": "Optional title" }
```

Response (201):

```json
{
  "id": "c_123",
  "title": "Optional title",
  "createdAt": "2026-02-07T00:00:00Z",
  "updatedAt": "2026-02-07T00:00:00Z"
}
```

#### GET /conversations

列出会话（仅返回当前 `X-Client-Id` 的会话）。

Query（V0最小）：

- `limit`：默认 20，最大 50
- `cursor`：可选（游标分页；具体编码实现可后置）

Response (200):

```json
{
  "items": [
    {
      "id": "c_123",
      "title": "...",
      "createdAt": "2026-02-07T00:00:00Z",
      "updatedAt": "2026-02-07T00:00:00Z",
      "lastMessagePreview": "Explain Type Ia..."
    }
  ],
  "nextCursor": null
}
```

#### GET /conversations/{conversationId}

获取会话详情 + 最近消息。

Query（V0最小）：

- `limit`：默认 50，最大 200
- `before`：可选（按消息创建时间或 messageId 做向前翻页）

Response (200):

```json
{
  "conversation": {
    "id": "c_123",
    "title": "...",
    "createdAt": "2026-02-07T00:00:00Z",
    "updatedAt": "2026-02-07T00:00:00Z"
  },
  "messages": [
    {
      "id": "m_001",
      "role": "user",
      "content": "...",
      "difficulty": "advanced",
      "language": "en",
      "status": "done",
      "createdAt": "2026-02-07T00:00:00Z"
    },
    {
      "id": "m_002",
      "role": "assistant",
      "content": "...",
      "difficulty": "advanced",
      "language": "en",
      "status": "done",
      "promptTokens": 123,
      "completionTokens": 456,
      "estimatedCostUsd": 0.001,
      "createdAt": "2026-02-07T00:00:05Z"
    }
  ],
  "nextBefore": null
}
```

---

### 5.2 消息（Messages）

#### POST /conversations/{conversationId}/messages

提交用户消息，并创建一次“待生成”的 assistant 回复任务。

说明：

- 该接口只负责“写入用户消息 + 创建生成任务”，不直接返回答案正文。
- 客户端随后调用 SSE 接口获取流式输出。

Request:

```json
{
  "content": "Explain how Type Ia supernovae are used as standard candles",
  "difficulty": "advanced",
  "language": "en",
  "clientMessageId": "uuid-optional"
}
```

字段说明：

- `content`：必填，建议限制长度（例如 1~4000 字符）
- `clientMessageId`：可选，用于前端重试时去重（同一会话下相同 `clientMessageId` 应返回同一 `messageId`）

Response (202):

```json
{
  "messageId": "m_user_456",
  "streamUrl": "/api/v0/conversations/c_123/messages/m_user_456/stream",
  "status": "queued"
}
```

---

### 5.3 流式回答（SSE）

#### GET /conversations/{conversationId}/messages/{messageId}/stream

用于流式获取本次 assistant 回复。

- Response Content-Type：`text/event-stream`
- 连接建立后服务端开始推送事件。

SSE events（V0最小）：

- `meta`：本次生成元信息
- `delta`：增量文本片段（直接追加到 UI）
- `done`：正常结束（含用量/成本估算）
- `error`：异常结束

事件数据结构：

`meta`：

```json
{
  "requestId": "req_...",
  "model": "deepseek-v3.2",
  "difficulty": "advanced",
  "language": "en"
}
```

`delta`：

```json
{ "text": "Type Ia supernovae are used as standard candles because..." }
```

`done`：

```json
{
  "status": "done",
  "usage": {
    "promptTokens": 1234,
    "completionTokens": 2100,
    "estimatedCostUsd": 0.02
  }
}
```

`error`：

```json
{
  "status": "error",
  "error": {
    "code": "provider_error",
    "message": "LLM provider returned 503"
  }
}
```

取消/停止生成（V0）：前端关闭 EventSource 即视为取消；后端应尽快中断上游 streaming，并将对应 assistant 消息标记为 `cancelled`（若已落库）。

---

### 5.4 Concept Cards

#### GET /concepts/lookup

用于 Concept Cards 点读解释。

Query（V0最小）：

- `type`：`term | sym`（必填）
- `lang`：`en | zh`（必填）
- `key`：可选（推荐；若回答标了 key，就优先用 key）
- `text`：可选（展示文本；缓存未命中且允许生成时作为生成输入）

行为：

- 优先按 `(key,type,lang)` 查缓存
- 未命中：若开启生成，则调用低成本模型生成 Concept Card 并缓存；否则返回 404

Response (200)：见 4.3

---

## 6. 数据模型（V0）

### 6.1 表结构（SQLite 版草案）

> 说明：为未来迁移 PostgreSQL，字段尽量采用通用类型；`id` 使用字符串（ULID/UUID）。

```sql
CREATE TABLE conversations (
  id TEXT PRIMARY KEY,
  title TEXT,
  client_id TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE INDEX idx_conversations_client_updated ON conversations(client_id, updated_at);

CREATE TABLE messages (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL,
  role TEXT NOT NULL,              -- user | assistant | system
  content TEXT NOT NULL,
  difficulty TEXT,                 -- basic | intermediate | advanced
  language TEXT,                   -- en | zh
  status TEXT NOT NULL,            -- queued | streaming | done | error | cancelled
  error_code TEXT,
  error_message TEXT,
  prompt_tokens INTEGER,
  completion_tokens INTEGER,
  estimated_cost_usd REAL,
  client_message_id TEXT,
  created_at TEXT NOT NULL,
  FOREIGN KEY(conversation_id) REFERENCES conversations(id)
);

CREATE INDEX idx_messages_conv_created ON messages(conversation_id, created_at);
CREATE INDEX idx_messages_client_msgid ON messages(client_message_id);

CREATE TABLE term_cache (
  key TEXT PRIMARY KEY,
  type TEXT NOT NULL,              -- term | sym
  language TEXT NOT NULL,          -- en | zh
  title TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE INDEX idx_term_cache_type_lang ON term_cache(type, language);

CREATE TABLE request_usage (
  id TEXT PRIMARY KEY,
  message_id TEXT NOT NULL,
  model TEXT NOT NULL,
  latency_ms INTEGER NOT NULL,
  prompt_tokens INTEGER,
  completion_tokens INTEGER,
  estimated_cost_usd REAL,
  created_at TEXT NOT NULL,
  FOREIGN KEY(message_id) REFERENCES messages(id)
);

CREATE INDEX idx_usage_message ON request_usage(message_id);
```

### 6.2 匿名用户识别（V0）

- 前端首次访问生成 `clientId`（UUID），写入 localStorage
- 后端所有接口（读写）要求携带 `X-Client-Id`
- V0 不做账号体系

---

## 7. 后端模块划分（Spring Boot）

- `api`
  - ConversationController
  - MessageController
  - StreamController (SSE)
  - ConceptController
- `domain`
  - Conversation, Message, ConceptCard
- `service`
  - ChatService（上下文裁剪、prompt构造、调用LLM）
  - StreamingService（上游流 → SSE 转发、取消、落库）
  - ConceptService（缓存优先、生成兜底）
  - UsageService（token与耗时统计）
- `provider`
  - LlmClient（接口）
  - OpenAiCompatibleClient（实现）
- `policy`
  - RateLimitPolicy（按 clientId + IP）
  - ContextTrimPolicy（最近 N 轮 + 最大字符/token）
  - OutputLimitPolicy（最大 completion tokens）

---

## 8. 前端模块设计（Vue）

### 8.1 页面

- `/`：Landing（可选，V0可直接跳转 `/chat`）
- `/chat`：问答页（核心）

### 8.2 组件建议

- `ChatPage`
  - `ChatHeader`（产品名、语言切换、难度选择、模型信息可选）
  - `ChatHistory`（会话列表/新建）
  - `MessageList`
  - `MessageComposer`（多行输入、回车发送、停止生成）
- `MarkdownRenderer`
  - 统一渲染 Markdown + KaTeX
  - 在渲染前做 marker 解析（`[[term:...]]`）并替换为可点击 token
- `ConceptPopover` / `ConceptDrawer`
  - 展示概念卡片 JSON

### 8.3 流式策略（SSE）

- 发送消息后立即打开 EventSource
- 收到 `delta` 事件就 append 到当前 assistant message buffer
- 收到 `done` 事件：标记完成 + 展示 token 用量（可选）
- 断线重连：V0 可不做自动重连；失败时提示“重试”

---

## 9. Prompt 与路由（V0）

### 9.1 System Prompt（要点）

- 角色：University-level Astronomy Tutor
- 约束：不确定性表达、禁止伪造来源、输出结构固定
- Marker 协议：对关键术语/符号必须用 `[[...]]` 标记

### 9.2 路由策略（建议）

- Advanced：高质量模型
- Basic/Intermediate：低成本模型
- Concept Card 生成：最低成本模型（并缓存）

---

## 10. 可靠性、成本与安全

### 10.1 成本控制

- 最大上下文：最近 N 轮（默认 8）或最大 token/字符（两者取小）
- 最大输出：按难度设定 `maxCompletionTokens`
- 限流：按 `clientId` + IP（例如 20 req / 10 min，可配置）

### 10.2 错误处理

- LLM 超时：返回 `error` SSE event + 建议重试
- Provider 429/5xx：指数退避（V0可仅一次重试）
- 解析/渲染失败：前端降级为原始文本展示

### 10.3 安全

- LLM Key：后端环境变量/KeyVault（V0可先 env）
- CORS：仅允许前端域名
- 输入防护：限制单次输入长度、过滤明显注入型提示（以政策为主，非强对抗）

---

## 11. 可观测性（V0最小集）

- 结构化日志：requestId、clientId、conversationId、messageId、model、latency、status
- 指标：
  - 请求量/成功率/失败率
  - 首 token 延迟、总延迟
  - token 用量分布、估算成本
- Trace（可选）：OpenTelemetry + OTLP

---

## 12. 测试策略（V0）

- 单元测试：
  - ContextTrimPolicy
  - Marker 解析（前后端各一份）
- 集成测试：
  - `/messages` + `/stream` 的端到端（可用 mock provider）
- 回归题库（建议）：10-20 个“种子问题”验证输出结构与拒答策略

---

## 13. 部署建议（V0）

- 前端：静态站点（Azure Static Web Apps / 任意 CDN）
- 后端：
  - Azure App Service（Java）或 Container Apps
- 数据：
  - V0 SQLite 仅适合单实例；若需水平扩展应尽快迁移 PostgreSQL

---

## 14. 里程碑（建议）

- M1：前端问答页 + SSE 流 + 基础后端接口（无 Concept Cards）
- M2：Marker 协议 + Concept Cards 缓存/生成
- M3：观测/限流/成本上限 + 种子题库回归
- M4：灰度上线与监控

---

## 15. Spring AI 集成示例

### 15.1 依赖配置（pom.xml）

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0-M4</version> <!-- 检查最新版本 -->
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-core</artifactId>
    <version>1.0.0-M4</version>
</dependency>
```

### 15.2 配置文件（application.yml）

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL}  # 可配置为 DeepSeek 等兼容接口
      chat:
        options:
          model: gpt-4  # 默认模型，可按难度路由
          temperature: 0.7
          max-tokens: 2000
```

### 15.3 ChatService 示例（非流式）

```java
@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String generateResponse(String systemPrompt, String userMessage, String difficulty) {
        Prompt prompt = new Prompt(List.of(
            new SystemMessage(systemPrompt),
            new UserMessage(userMessage)
        ));

        ChatResponse response = chatClient.call(prompt);
        return response.getResult().getOutput().getContent();
    }
}
```

### 15.4 StreamingService 示例（流式 SSE）

```java
@Service
public class StreamingService {

    private final StreamingChatClient streamingChatClient;

    public StreamingService(StreamingChatClient streamingChatClient) {
        this.streamingChatClient = streamingChatClient;
    }

    public Flux<String> streamResponse(String systemPrompt, String userMessage) {
        Prompt prompt = new Prompt(List.of(
            new SystemMessage(systemPrompt),
            new UserMessage(userMessage)
        ));

        return streamingChatClient.stream(prompt)
            .map(chunk -> chunk.getResult().getOutput().getContent())  // 提取增量文本
            .doOnNext(delta -> {
                // 可在这里记录 token 用量或处理 marker
            });
    }
}
```

### 15.5 StreamController 示例（SSE 端点）

```java
@RestController
public class StreamController {

    private final StreamingService streamingService;

    @GetMapping(value = "/api/v0/conversations/{conversationId}/messages/{messageId}/stream", 
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamAnswer(
            @PathVariable String conversationId,
            @PathVariable String messageId) {

        // 假设从 DB 获取 systemPrompt 和 userMessage
        String systemPrompt = "You are a university-level astronomy tutor...";
        String userMessage = "Explain Type Ia supernovae.";

        return streamingService.streamResponse(systemPrompt, userMessage)
            .map(delta -> ServerSentEvent.builder(delta)
                .event("delta")
                .build())
            .concatWith(Flux.just(
                ServerSentEvent.builder("")
                    .event("done")
                    .data("{\"usage\":{\"promptTokens\":1234,\"completionTokens\":2100}}")
                    .build()
            ));
    }
}
```

### 15.6 路由与配置示例

```java
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, @Value("${ai.model.basic}") String basicModel) {
        return builder
            .defaultOptions(ChatOptions.builder()
                .model(basicModel)
                .temperature(0.7)
                .build())
            .build();
    }

    // 可按难度创建不同 ChatClient
    @Bean
    public ChatClient advancedChatClient(ChatClient.Builder builder, @Value("${ai.model.advanced}") String advancedModel) {
        return builder
            .defaultOptions(ChatOptions.builder()
                .model(advancedModel)
                .temperature(0.3)  // 更确定性
                .build())
            .build();
    }
}
```

> 说明：Spring AI 简化了 LLM 集成，支持 Prompt 模板、流式、工具调用等。V0 可先用基础 ChatClient，未来扩展到 Function Calling 或 RAG。

---

## 16. RAG 设计（V1+）

### 16.1 技术实现概述

RAG（Retrieval-Augmented Generation）通过检索外部知识库增强 AI 回答，避免幻觉，提升准确性与可信度。

- **核心流程**：
  1. 用户问题 → 嵌入模型生成向量。
  2. 向量搜索知识库 → 返回相关文档片段。
  3. 文档片段 + 原问题 → 输入 LLM 生成回答。
  4. 输出中引用来源（可选，提升可信度）。

- **技术栈**：
  - 向量数据库：Qdrant 或 Milvus（开源，支持相似度搜索）。
  - 嵌入模型：OpenAI `text-embedding-ada-002` 或开源如 Sentence Transformers。
  - 检索策略：Top-K 相似片段（K=3-5），可选重排序（BM25 + 向量）。
  - 集成框架：Spring AI 支持 RAG（VectorStore + ChatClient）。

### 16.2 架构扩展

```
用户问题
  ↓
嵌入模型 → 向量
  ↓
向量 DB 检索 → 相关文档
  ↓
LLM (with context) → 增强回答
  ↓
输出 + 引用
```

### 16.3 实现步骤

1. **知识库准备**：
   - 收集合法公开资料（公版教材/自写）。
   - 分块：按段落/公式切分，添加元数据（来源、章节）。
   - 嵌入：批量生成向量，存入向量 DB。

2. **检索服务**：
   - 输入：用户问题。
   - 输出：Top-K 文档片段 + 相似度分数。

3. **数据喂给AI（上下文构建与输入）**：
   - **上下文组装**：将检索到的文档片段按相似度排序，拼接成字符串（格式：`[来源: 章节X] 内容...`）。限制总长度（e.g., 2000 tokens），超长截断或摘要。
   - **Prompt 构造**：系统Prompt + 上下文 + 用户问题。示例：
     ```
     System: You are a university-level astronomy tutor. Use the provided knowledge to answer accurately.
     Context: [文档片段1] [文档片段2] ...
     User: Explain Type Ia supernovae.
     ```
   - **输入LLM**：通过 Spring AI 的 ChatClient 调用，上下文作为 UserMessage 或 SystemMessage 的一部分。
   - **优化策略**：若上下文过长，用 LLM 摘要压缩；优先高相似度片段；支持多轮时更新上下文。

4. **生成增强**：
   - LLM 调用：使用 Spring AI 的 RetrievalAugmentedGeneration（自动检索+生成）。

5. **引用展示**（可选）：
   - 输出中标记来源：`[来源: 章节X]`。
   - 前端可点击跳转（V1.5+）。

### 16.4 示例代码（Spring AI + Qdrant）

#### 依赖（pom.xml）

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vectorstore-qdrant</artifactId>
    <version>1.0.0-M4</version>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0-M4</version>
</dependency>
```

#### 配置（application.yml）

```yaml
spring:
  ai:
    vectorstore:
      qdrant:
        host: localhost
        port: 6334
        collection-name: astro_knowledge
    openai:
      embedding:
        api-key: ${OPENAI_API_KEY}
        options:
          model: text-embedding-ada-002
```

#### RAG Service 示例

```java
@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    public String generateWithRag(String userQuestion) {
        // 检索相关文档
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.query(userQuestion).withTopK(3)
        );

        // 构建上下文
        String context = docs.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n\n"));

        // RAG Prompt
        String promptText = String.format(
            "Based on the following knowledge:\n%s\n\nAnswer the question: %s",
            context, userQuestion
        );

        Prompt prompt = new Prompt(new UserMessage(promptText));
        ChatResponse response = chatClient.call(prompt);
        return response.getResult().getOutput().getContent();
    }
}
```

### 16.5 注意事项

- **合法性**：仅用公版/授权资料，避免版权风险。
- **性能**：向量搜索 <100ms；缓存热点检索结果。
- **可信度**：明确不确定性；引用来源提升信任。
- **扩展**：支持多语言嵌入；未来加重排序模型。

V0 不实现 RAG；V1+ 可按此设计逐步上线。

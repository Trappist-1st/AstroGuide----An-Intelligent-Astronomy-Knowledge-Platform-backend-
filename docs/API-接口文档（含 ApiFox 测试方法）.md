# AstroGuide API 文档（V0 /api/v0）

> 目标：把当前后端已实现的接口，整理成“可直接在 ApiFox 跑通”的测试文档。后续新增接口时，按本文档结构追加即可。

## 0. 基本约定

### 0.1 Base URL
- http://localhost:8093/swagger-ui/index.html 这是接口文档的地址
- 本地（示例）：`http://localhost:8080`
- 统一前缀：`/api/v0`

建议在 ApiFox 里建环境变量：

- `{{baseUrl}}` = `http://localhost:8080`
- `{{clientId}}` = `demo_client_001`

### 0.2 统一请求头

大多数接口都要求：

- `X-Client-Id: {{clientId}}`

### 0.3 错误响应格式（HTTP JSON）

当前代码里存在两种 error 形态：

1) Controller 内手工返回（更常见，带 requestId）：

```json
{
  "error": {
    "code": "invalid_argument",
    "message": "X-Client-Id is required",
    "requestId": "req_xxxxxxxxxxxxxxxx",
    "details": {
      "header": "X-Client-Id"
    }
  }
}
```

2) `@Valid` 校验失败（GlobalValidationExceptionHandler，不带 requestId）：

```json
{
  "error": {
    "code": "invalid_argument",
    "message": "content is required",
    "details": {
      "field": "content"
    }
  }
}
```

### 0.4 流式输出格式（SSE）

流式接口返回 `text/event-stream`，事件序列大致为：

- `event: meta`：本次请求元信息（requestId/model/difficulty/language）
- `event: delta`：增量文本片段（多次）
- `event: done`：结束包（usage 估算 + 可选 citations）
- `event: error`：发生错误时的结束包

示例（仅示意）：

```text
event: meta
data: {"requestId":"req_...","model":"..."}

event: delta
data: {"text":"..."}

event: done
data: {"status":"done","usage":{"promptTokens":123,"completionTokens":456,"estimatedCostUsd":0.0001}}
```

ApiFox 对 SSE 的支持因版本而异：

- 如果 ApiFox 能展示流式响应：直接在响应面板观察 `meta/delta/done`。
- 如果 ApiFox 不能稳定展示 SSE：用 `curl -N` 或浏览器 `EventSource` 验证。

`curl` 示例：

```bash
curl -N \
  -H "X-Client-Id: demo_client_001" \
  "http://localhost:8080/api/v0/conversations/c_xxx/messages/msg_xxx/stream"
```

---

## 1. 会话（Conversations）

### 1.1 创建会话

- Method: `POST`
- Path: `/api/v0/conversations`
- Header: `X-Client-Id` 必填
- Body（可选）：

```json
{
  "title": "木星和土星有什么区别？"
}
```

- Success: `201 Created`

响应（示例）：

```json
{
  "id": "c_0123456789abcdef",
  "title": "木星和土星有什么区别？",
  "createdAt": "2026-02-16T12:34:56.789Z",
  "updatedAt": "2026-02-16T12:34:56.789Z"
}
```

ApiFox 测试步骤：

1) 新建请求：`POST {{baseUrl}}/api/v0/conversations`
2) Headers 添加：`X-Client-Id: {{clientId}}`
3) Body 选择 JSON，填入 `title`（或空 body 也可）
4) 发送后保存返回的 `id`（后续都要用）

常见错误用例：

- 不传 `X-Client-Id` → `400 invalid_argument`

---

### 1.2 会话列表（游标分页）

- Method: `GET`
- Path: `/api/v0/conversations`
- Header: `X-Client-Id` 必填
- Query:
  - `limit`（默认 20，最大 50）
  - `cursor`（可选；上一页返回的 `nextCursor`）

Success: `200 OK`

```json
{
  "items": [
    {
      "id": "c_...",
      "title": "...",
      "createdAt": "...",
      "updatedAt": "...",
      "lastMessagePreview": "..."
    }
  ],
  "nextCursor": "c_..."
}
```

ApiFox 测试步骤：

1) `GET {{baseUrl}}/api/v0/conversations?limit=20`
2) 拿到 `nextCursor` 后再请求：`.../conversations?limit=20&cursor={{nextCursor}}`

常见错误用例：

- cursor 不存在/不属于该 client → `400 invalid_argument (Invalid cursor)`

---

### 1.3 会话详情 + 消息列表（向前翻页）

- Method: `GET`
- Path: `/api/v0/conversations/{conversationId}`
- Header: `X-Client-Id` 必填
- Query:
  - `limit`（默认 50，最大 200）
  - `before`（可选；消息 id，用于向更早翻页）

Success: `200 OK`

```json
{
  "conversation": {
    "id": "c_...",
    "title": "...",
    "createdAt": "...",
    "updatedAt": "..."
  },
  "messages": [
    {
      "id": "msg_...",
      "role": "user",
      "content": "...",
      "difficulty": "intermediate",
      "language": "zh",
      "status": "done",
      "promptTokens": 12,
      "completionTokens": 34,
      "estimatedCostUsd": 0.00001,
      "createdAt": "..."
    }
  ],
  "nextBefore": "msg_..."
}
```

ApiFox 测试步骤：

1) `GET {{baseUrl}}/api/v0/conversations/{{conversationId}}?limit=50`
2) 若返回 `nextBefore`，再请求：`...?limit=50&before={{nextBefore}}`

常见错误用例：

- 会话不存在 → `404 not_found`
- clientId 不匹配 → `403 forbidden`

---

## 2. 消息（Messages）

### 2.1 提交用户消息（生成 queued 的 assistant 占位）

- Method: `POST`
- Path: `/api/v0/conversations/{conversationId}/messages`
- Header: `X-Client-Id` 必填
- Body（JSON，`content` 必填 1~4000）：

**示例 1（中文）：**

```json
{
  "content": "请解释什么是洛希极限，并给一个直观例子",
  "difficulty": "intermediate",
  "language": "zh",
  "clientMessageId": "m_0001"
}
```

**示例 2（英文，适合测试 RAG + Wikipedia 工具）：**

```json
{
  "content": "Tell me about the theory of inflation and cosmic strings",
  "difficulty": "advanced",
  "language": "en",
  "clientMessageId": "test_inflation_cosmic_001"
}
```

- `content`：必填，1~4000 字符。
- `difficulty`：可选，`basic` | `intermediate` | `advanced`，不传时默认 intermediate。
- `language`：可选，`en` | `zh`，不传时默认 en。
- `clientMessageId`：可选，用于幂等；同一会话下相同 clientMessageId 且相同 content 时返回同一 messageId。测试时可用唯一字符串（如 `test_inflation_cosmic_001`）或省略。

Success: `202 Accepted`

```json
{
  "messageId": "msg_0123456789abcdef",
  "streamUrl": "/api/v0/conversations/c_xxx/messages/msg_xxx/stream",
  "status": "queued"
}
```

说明：

- 服务端会写入两条记录：
  - `role=user` 的用户消息（`status=done`）
  - `role=assistant` 的占位消息（id 为 `{messageId}_a`，`status=queued`）
- 幂等：同一会话下，若重复提交相同 `clientMessageId`，会返回相同的 `messageId`。

ApiFox 测试步骤（推荐做成一个集合场景）：

1) `POST {{baseUrl}}/api/v0/conversations/{{conversationId}}/messages`
2) Headers：`X-Client-Id: {{clientId}}`
3) Body：填 `content`，可选 difficulty/language/clientMessageId
4) 保存响应里的 `messageId` 与 `streamUrl`

常见错误用例：

- `content` 为空 → `400 invalid_argument`（来自 @Valid）
- 会话不存在 → `404 not_found`
- clientId 不匹配 → `403 forbidden`

#### 如何测试 Wikipedia / 工具调用

- **不需要在用户问题里显式写“查维基”**：智能体注册了 `search_wikipedia` 工具（描述为“Search Wikipedia for public astronomy background…”），模型会根据工具描述在合适时自动决定是否调用。
- **系统提示中已包含**：对事实性、百科类问题可酌情使用 `search_wikipedia`，因此用户只需问“Tell me about cosmic strings”或“请讲一讲暴涨理论和宇宙弦”等，即有可能触发工具调用。
- **提高触发概率的写法（可选）**：若希望更容易观察到工具调用，可在 `content` 中写“Search Wikipedia for [topic] and summarize”或“请查一下维基上关于……并总结”。
- **推荐测试 Body**：用上面的**示例 2** 作为请求体，提交后打开返回的 `streamUrl` 用 SSE 接收流；在服务端日志或后续的 `done` 事件中若有 citations，且来源为 Wikipedia，则说明调用了 Wikipedia 工具。
- **前置条件**：`app.ai.tools.enabled=true`（默认 true）；Wikipedia 接口需可用（配置正确且网络可达）。

---

## 3. AI 流式回答（SSE）

### 3.1 获取 assistant 的流式回复

- Method: `GET`
- Path: `/api/v0/conversations/{conversationId}/messages/{messageId}/stream`
- Header: `X-Client-Id` 必填
- Produces: `text/event-stream`

说明：

- `messageId` 必须是该会话下的 `role=user` 消息 id（提交消息接口返回的 messageId）。
- 服务端会将 `{messageId}_a` 的 assistant 占位消息从 `queued` 改为 `streaming`，流结束后落库为 `done/error/cancelled`。
- RAG / Wikipedia 增强是否启用由配置决定（不作为请求参数暴露）。

ApiFox 测试步骤：

1) 先调用“提交用户消息”拿到 `messageId` 或 `streamUrl`
2) 发起 `GET {{baseUrl}}{{streamUrl}}`
3) Headers：`X-Client-Id: {{clientId}}`
4) 观察事件流：`meta` → 多个 `delta` → `done`

取消测试（验证 cancelled）：

- 在前端/客户端主动断开 SSE（例如停止请求/关闭连接）
- 之后用“会话详情”接口查看 `{messageId}_a` 是否被标记为 `cancelled`

常见错误用例：

- 不传 `X-Client-Id` → SSE `event:error`（JSON 中 `status=error`）
- messageId 不存在/不属于会话 → SSE `event:error`
- messageId 对应的消息不是 user → SSE `event:error`

---

## 4. Concept Cards（点读解释）

### 4.1 查询概念卡片

- Method: `GET`
- Path: `/api/v0/concepts/lookup`
- Header: `X-Client-Id` 必填
- Query:
  - `type`：必填，`term` 或 `sym`
  - `lang`：必填，`en` 或 `zh`
  - `key`：可选（推荐优先用 key）
  - `text`：可选（key 为空时可传 text）
  - 约束：`key` 与 `text` 至少一个非空

Success: `200 OK`

```json
{
  "key": "chandrasekhar_limit",
  "title": "钱德拉塞卡极限",
  "short": "...",
  "details": [
    {"label": "定义", "value": "..."}
  ],
  "seeAlso": ["白矮星", "电子简并压"]
}
```

ApiFox 测试步骤：

1) `GET {{baseUrl}}/api/v0/concepts/lookup?type=term&lang=zh&text=洛希极限`
2) Headers：`X-Client-Id: {{clientId}}`

常见错误用例：

- `type` 非 term/sym → `400 invalid_argument`
- `lang` 非 en/zh → `400 invalid_argument`
- `key/text` 都不传 → `400 invalid_argument`
- 未命中且生成关闭/失败 → `404 not_found`

---

## 5. 推荐的 ApiFox 测试集合结构（建议）

建议建一个 ApiFox 集合：`AstroGuide V0`，包含以下顺序：

1) 创建会话（保存 conversationId）
2) 提交消息（保存 messageId、streamUrl）
3) 流式回答（使用 streamUrl）
4) 会话详情（确认消息落库、assistant 状态与 usage）
5) Concept lookup（独立用例）

后续扩展接口时：

- 每新增一个 endpoint，按本文档“路径/请求/响应/ApiFox 步骤/错误用例”四段补齐。

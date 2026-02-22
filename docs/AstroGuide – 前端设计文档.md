# 🌌 AstroGuide – 前端设计文档（修订版）

> 日期：2026-02-20  
> 定位：面向天文学习者的 AI 问答与知识探索前端（Vue 3）  
> 目标：与现有后端能力精准对齐，先完成可上线 MVP，再逐步增强视觉与交互。

---

## 1. 产品目标与边界

### 1.1 核心目标

- **易用**：输入问题即可得到流式回答（低学习成本）。
- **专业**：结构化内容 + 数学公式 + 术语卡片。
- **可信**：展示引用来源、状态、异常原因。
- **可扩展**：后续可平滑增加登录、收藏、分享、专题页。

### 1.2 MVP 范围（先做）

- Landing 页 + Chat 主页面（双页）。
- 会话列表、消息列表、SSE 流式回答、停止生成。
- 难度/语言切换、Concept Card 点击解释。
- 错误处理（含网络故障、SSE 中断、后端降级提示）。

### 1.3 暂不纳入（后续）

- 社区、用户体系、积分系统、复杂运营位。

---

## 2. 与后端能力对齐（关键）

> 前端设计必须以后端接口契约为准，避免“视觉先行、联调返工”。

### 2.1 会话与消息

- 会话创建、会话列表、消息发送、消息查询。
- 对话流式接口：`/api/v0/conversations/{conversationId}/messages/{messageId}/stream`
- Header 必须带：`X-Client-Id`

### 2.2 SSE 事件协议（前端状态机）

- `meta`：初始化元信息（requestId/model/language/difficulty）
- `delta`：增量文本块
- `done`：完成事件（可含 usage/citations）
- `error`：异常事件（可恢复/不可恢复）

前端必须实现状态：`idle -> streaming -> done | error | cancelled`

### 2.3 异常与降级策略

- 向量库不可用时，前端提示“知识检索暂不可用，已切换通用回答模式”。
- SSE 断流时，消息保留已生成部分并提供“继续提问/重试”。
- 429/限流场景统一 toast + 冷却倒计时。

---

## 3. 信息架构与页面设计

### 3.1 页面结构

1. **LandingPage**
  - 品牌区：宇宙叙事文案 + 视觉主背景
  - 快速提问框：可直接输入并创建会话
  - 最近热门问题（可选）

2. **ChatPage（核心）**
  - 顶栏：产品名、难度、语言、主题开关
  - 左侧栏：会话历史（搜索/重命名/删除）
  - 主内容区：消息流、引用、Concept 术语交互
  - 底部输入区：多行输入、发送、停止、重试

### 3.2 关键组件

- `ChatHeader`
- `ConversationSidebar`
- `MessageList`
- `MessageItemUser` / `MessageItemAssistant`
- `SseStatusBar`（显示 streaming/error/retrying）
- `MessageComposer`
- `MarkdownRenderer`（Markdown + KaTeX + 术语标记）
- `ConceptPopover`
- `CitationPanel`
- `GlobalToast`

---

## 4. 视觉规范（参考图片风格升级）

> 参考图是“深色 + 玻璃拟态 + 霓虹高亮”的仪表盘风格。可转译为 AstroGuide 的“宇宙学术玻璃风”。

### 4.1 风格关键词

- `Dark Cosmic`
- `Glassmorphism`
- `Neon Accent`
- `Data Dashboard Hierarchy`

### 4.2 色彩建议（设计 Token）

- 背景主色：`#0B1020` / `#0F172A`
- 卡片玻璃层：`rgba(255,255,255,0.08)` + `backdrop-blur`
- 主强调（蓝）：`#4DA3FF`
- 次强调（紫）：`#8B7CFF`
- 成功/警告/错误：`#22C55E` / `#F59E0B` / `#EF4444`
- 文字：主 `#E5E7EB`，次 `#94A3B8`

### 4.3 组件外观规则

- 卡片统一圆角 `12-16px`，弱描边 `1px rgba(255,255,255,0.12)`
- 悬浮态提升：阴影增强 + 边框高亮
- 图表/状态条只做“轻量信息可视化”，不抢主问答焦点
- 动效时长 `120-220ms`，避免花哨过度

---

## 5. 交互规范

### 5.1 输入与生成

- `Enter` 发送，`Shift+Enter` 换行
- 生成中按钮切换为“停止”
- 停止后状态标记 `cancelled`，保留已生成文本

### 5.2 消息呈现

- assistant 消息分层：结论、展开、引用、延伸阅读
- `[[term:xxx]]` 渲染为可点击术语标签
- 公式统一 KaTeX 样式，支持行内与块级

### 5.3 反馈与恢复

- 每条回答提供 👍/👎
- 错误后提供“重试本轮”与“发起新问题”双路径

---

## 6. 前端工程方案（Vue 3，不写代码版）

### 6.1 技术栈

- Vue 3 + Vite + TypeScript
- Pinia（全局状态）
- Vue Router（页面路由）
- Tailwind CSS（Token 化样式）
- markdown-it + KaTeX（内容渲染）

### 6.2 目录建议

- `src/pages`：Landing / Chat
- `src/components/chat`：聊天域组件
- `src/components/common`：通用组件
- `src/stores`：会话、消息、SSE 状态
- `src/services`：API 与 SSE 封装
- `src/types`：接口与事件类型
- `src/theme`：色板、阴影、圆角、动效 Token

### 6.3 状态分层

- **会话域状态**：会话列表、当前会话、分页
- **消息域状态**：消息数组、流式临时缓冲、消息状态
- **系统域状态**：主题、语言、全局通知、网络健康度

### 6.4 Pinia 说明（给非前端同学）

#### 6.4.1 什么是 Pinia

Pinia 是 Vue 3 官方推荐的状态管理方案，可理解为“前端全局内存数据库”。

当页面变复杂后，数据会在很多组件间共享（如会话列表、当前消息流、SSE 状态）。
如果只靠组件逐层传参，维护会迅速变难。Pinia 用统一的 Store 解决这个问题。

#### 6.4.2 为什么它重要

- **集中管理状态**：避免数据散落在多个组件中。
- **更好维护**：状态变化路径清晰，便于排错与联调。
- **类型友好**：与 TypeScript 配合好，接口字段更不易写错。
- **性能可控**：只更新依赖该状态的组件。
- **生态成熟**：与 Vue Devtools、Router、组合式 API 协作自然。

#### 6.4.3 核心概念

- `state`：可变数据（如 `messages`、`currentConversationId`）。
- `getters`：派生数据（如“当前会话未完成消息数”）。
- `actions`：业务动作（如“发送消息并开启 SSE”）。

建议原则：
- API 调用放在 `actions`。
- 组件尽量只负责展示与交互，不直接拼装复杂业务流程。

#### 6.4.4 在 AstroGuide 的落地方式

- `conversationStore`
  - 管理：会话列表、分页游标、当前会话 ID
  - 动作：创建会话、分页加载、切换会话

- `messageStore`
  - 管理：消息列表、流式缓冲、消息状态（`queued/streaming/done/error/cancelled`）
  - 动作：提交消息、消费 `meta/delta/done/error` 事件、停止生成

- `appStore`
  - 管理：主题、语言、全局 toast、网络健康状态
  - 动作：切换主题语言、统一错误提示

#### 6.4.5 与后端接口契约的关系

Pinia Store 是“接口契约的前端承接层”：

- 后端返回 `messageId/streamUrl/status` → 由 `messageStore` 统一落地。
- SSE 四类事件 `meta/delta/done/error` → 由 `messageStore` 统一消费。
- 统一错误结构 `error.code/error.message` → 由 `appStore` 统一展示。

这样可以保证：后端变化时，只需优先调整 Store 与 service 层，页面组件改动最小。

---

## 7. 可用性与非功能要求

### 7.1 响应式

- 桌面优先（≥1280）
- 平板保留左栏折叠
- 移动端采用抽屉式会话列表

### 7.2 性能

- 首屏可交互 < 2.5s（目标）
- 消息列表虚拟化（长会话）
- 按需加载 KaTeX/重组件

### 7.3 可访问性

- 颜色对比度符合 WCAG AA
- 键盘可达（输入、发送、切换会话）
- 为状态变化提供 ARIA 提示

---

## 8. 迭代路线图

### Milestone 1：联调可用版（1-2 周）

- 跑通会话、消息、SSE、停止生成、错误提示

### Milestone 2：视觉升级版（1 周）

- 完成玻璃拟态主题、动效、深色体系

### Milestone 3：专业增强版（1-2 周）

- 引用面板、ConceptCard 体验、反馈闭环

---

## 9. 验收指标

- 问答链路成功率 ≥ 99%
- SSE 中断后的恢复成功率 ≥ 95%
- 首轮回答平均等待时长（主观）明显优于旧版
- 用户对“专业感/可信度”评分 ≥ 4.2/5

---

## 10. 后端接口整理（前端必读）

> 本节用于前端实现时的“接口单一事实源”。若后端接口变更，优先更新本节。

### 10.1 全局约定

- Base URL（本地）：`http://localhost:8093`
- API 前缀：`/api/v0`
- 统一请求头：`X-Client-Id: <clientId>`（除静态资源外均建议携带）
- Content-Type：
  - JSON 接口：`application/json`
  - 流式接口：`text/event-stream`

### 10.2 错误响应约定

HTTP JSON 错误统一为：

```json
{
  "error": {
    "code": "invalid_argument|not_found|forbidden|rate_limited|provider_error",
    "message": "...",
    "requestId": "req_xxx",
    "details": {}
  }
}
```

说明：部分 `@Valid` 场景可能不含 `requestId`，前端需容错读取。

### 10.3 接口清单总览

| 业务域 | 方法 | 路径 | 说明 | 前端使用场景 |
|---|---|---|---|---|
| 会话 | POST | `/conversations` | 创建会话 | Landing 首问创建 |
| 会话 | GET | `/conversations` | 会话列表（游标分页） | 左侧历史会话 |
| 会话 | GET | `/conversations/{conversationId}` | 会话详情 + 消息列表 | 进入会话、向前翻页 |
| 消息 | POST | `/conversations/{conversationId}/messages` | 提交用户消息，返回 `messageId + streamUrl` | 点击发送 |
| AI 流 | GET | `/conversations/{conversationId}/messages/{messageId}/stream` | SSE：`meta/delta/done/error` | 流式渲染 |
| Concept | GET | `/concepts/lookup` | 术语点读解释 | 点击 `[[term:...]]` |
| Ingest | POST | `/ingest/file` | 上传文件入库（管理端） | 知识库管理页 |
| Ingest | POST | `/ingest/text` | 文本入库（管理端） | 知识库管理页 |

---

### 10.4 详细契约（按前端调用顺序）

#### A. 创建会话

- `POST /api/v0/conversations`
- Header：`X-Client-Id`
- Body（可选）：

```json
{ "title": "可选标题" }
```

- 成功 `201`：返回 `id/title/createdAt/updatedAt`

前端适配：
- 若用户从 Landing 直接提问，可先创建会话，再立即调用“提交消息”。

#### B. 会话列表（游标分页）

- `GET /api/v0/conversations?limit=20&cursor=...`
- Header：`X-Client-Id`
- `limit` 默认 20，最大 50
- 成功 `200`：`items[] + nextCursor`

前端适配：
- 侧边栏滚动加载采用游标分页，不要页码分页。
- 当 `nextCursor = null` 时停止加载更多。

#### C. 会话详情 + 消息

- `GET /api/v0/conversations/{conversationId}?limit=50&before=...`
- Header：`X-Client-Id`
- `limit` 默认 50，最大 200
- 成功 `200`：
  - `conversation`
  - `messages[]`（含 `role/content/status/promptTokens/completionTokens/createdAt`）
  - `nextBefore`

前端适配：
- 首次进入会话加载最新一页。
- 上滑加载更早消息时传 `before=nextBefore`。

#### D. 提交消息

- `POST /api/v0/conversations/{conversationId}/messages`
- Header：`X-Client-Id`
- Body：

```json
{
  "content": "必填，1~4000",
  "difficulty": "basic|intermediate|advanced",
  "language": "en|zh",
  "clientMessageId": "可选幂等键"
}
```

- 成功 `202`：

```json
{
  "messageId": "msg_xxx",
  "streamUrl": "/api/v0/conversations/{conversationId}/messages/{messageId}/stream",
  "status": "queued"
}
```

前端适配：
- 点击发送后，先把 user 消息插入列表。
- 立即依据 `streamUrl` 建立 SSE。
- `clientMessageId` 建议前端生成 UUID，用于重试幂等。

#### E. SSE 流式回答

- `GET /api/v0/conversations/{conversationId}/messages/{messageId}/stream`
- Header：`X-Client-Id`
- 响应：`text/event-stream`

事件：
- `meta`：`requestId/model/difficulty/language`
- `delta`：`text`
- `done`：`status=done` + 可选 `usage/citations`
- `error`：`status=error` + 错误信息

前端适配：
- 状态机：`idle -> streaming -> done | error | cancelled`
- 每个 `delta.text` 追加到当前 assistant 气泡。
- 收到 `done` 后落稳态；收到 `error` 时展示“重试本轮”。
- 用户点击“停止生成”时主动关闭 SSE，并将本地状态标记 `cancelled`。

#### F. Concept Card 查询

- `GET /api/v0/concepts/lookup?type=term&lang=zh&key=...`
- Header：`X-Client-Id`
- 参数：
  - `type`：`term|sym`（必填）
  - `lang`：`en|zh`（必填）
  - `key` 或 `text`：至少一个非空
- 成功 `200`：`key/title/short/details/seeAlso`

前端适配：
- `MarkdownRenderer` 识别术语后，hover/click 触发查询。
- 卡片请求失败时不阻塞主回答，仅提示“术语解释暂不可用”。

#### G. Ingest（管理端接口）

- `POST /api/v0/ingest/file`（multipart）
- `POST /api/v0/ingest/text`（json）
- 返回：`accepted/source/chunksAdded/message`

前端适配：
- 该接口建议放入 Admin/知识库页面，普通聊天页不暴露。

---

### 10.5 前端类型与服务层约束（建议）

- 建立统一 `ApiResult<T>` 与 `ApiError` 解析器，兼容带/不带 `requestId` 两种错误。
- SSE 单独封装 `ChatStreamClient`，只向上抛出四类事件：`meta|delta|done|error`。
- 在 `stores/messages` 内只保存与 UI 渲染相关字段，完整原始包可放 `rawPayload` 便于排查。

---

### 10.6 联调检查清单

- [ ] 所有请求均携带 `X-Client-Id`
- [ ] 发送消息后 300ms 内进入 `streaming` 视觉状态
- [ ] SSE 中断可恢复，且已生成内容不丢失
- [ ] `429/403/404/400` 均有明确用户提示
- [ ] `citations` 能在回答末尾正确展示
- [ ] `language/difficulty` 变更可传入下一轮消息

---

### 10.7 Store 命名与字段清单（接口字段一一映射）

> 目标：后端字段如何落到 Pinia Store，一眼可查，减少联调歧义。

#### 10.7.1 `conversationStore`（会话域）

| Store 字段 | 类型建议 | 来源接口 | 后端字段映射 | 说明 |
|---|---|---|---|---|
| `items` | `ConversationListItem[]` | `GET /conversations` | `items[]` | 侧边栏会话列表 |
| `items[].id` | `string` | `GET /conversations` | `items[].id` | 会话主键 |
| `items[].title` | `string \| null` | `GET /conversations` | `items[].title` | 会话标题 |
| `items[].createdAt` | `string` | `GET /conversations` | `items[].createdAt` | ISO 时间 |
| `items[].updatedAt` | `string` | `GET /conversations` | `items[].updatedAt` | 排序基准 |
| `items[].lastMessagePreview` | `string \| null` | `GET /conversations` | `items[].lastMessagePreview` | 列表摘要 |
| `nextCursor` | `string \| null` | `GET /conversations` | `nextCursor` | 游标分页 |
| `currentConversationId` | `string \| null` | 路由/用户选择 | `conversation.id` | 当前会话 |
| `currentConversation` | `Conversation \| null` | `GET /conversations/{id}` | `conversation` | 详情头部信息 |
| `loadingList` | `boolean` | 前端本地 | - | 加载态 |
| `loadingDetail` | `boolean` | 前端本地 | - | 加载态 |

#### 10.7.2 `messageStore`（消息与流式域）

| Store 字段 | 类型建议 | 来源接口/事件 | 后端字段映射 | 说明 |
|---|---|---|---|---|
| `messages` | `UiMessage[]` | `GET /conversations/{id}` | `messages[]` | 当前会话消息 |
| `messages[].id` | `string` | 同上 | `messages[].id` | 消息主键 |
| `messages[].role` | `'user'\|'assistant'` | 同上 | `messages[].role` | 角色 |
| `messages[].content` | `string` | 同上 + SSE | `messages[].content` / `delta.text` 累加 | 展示正文 |
| `messages[].difficulty` | `string \| null` | 同上 | `messages[].difficulty` | 难度回显 |
| `messages[].language` | `string \| null` | 同上 | `messages[].language` | 语言回显 |
| `messages[].status` | `MessageStatus` | 同上 + SSE | `messages[].status` / `done.status` / `error.status` | `queued/streaming/done/error/cancelled` |
| `messages[].promptTokens` | `number \| null` | 同上 + SSE done | `messages[].promptTokens` / `done.usage.promptTokens` | Token 统计 |
| `messages[].completionTokens` | `number \| null` | 同上 + SSE done | `messages[].completionTokens` / `done.usage.completionTokens` | Token 统计 |
| `messages[].estimatedCostUsd` | `number \| null` | `GET /conversations/{id}` | `messages[].estimatedCostUsd` | 费用估算 |
| `messages[].createdAt` | `string` | `GET /conversations/{id}` | `messages[].createdAt` | 时间 |
| `pendingUserMessageId` | `string \| null` | `POST /messages` | `messageId` | 当前流式对应用户消息 |
| `pendingStreamUrl` | `string \| null` | `POST /messages` | `streamUrl` | 建立 SSE 用 |
| `stream.requestId` | `string \| null` | SSE `meta` | `meta.requestId` | 问答链路追踪 |
| `stream.model` | `string \| null` | SSE `meta` | `meta.model` | 模型展示 |
| `stream.phase` | `StreamPhase` | SSE 全事件 | 事件驱动 | `idle/streaming/done/error/cancelled` |
| `stream.errorCode` | `string \| null` | SSE `error` | `error.code` | 错误分类 |
| `stream.errorMessage` | `string \| null` | SSE `error` | `error.message` | 错误提示 |
| `stream.citations` | `Citation[]` | SSE `done` | `done.citations[]` | 引用展示 |
| `nextBefore` | `string \| null` | `GET /conversations/{id}` | `nextBefore` | 消息向前翻页 |

#### 10.7.3 `appStore`（系统域）

| Store 字段 | 类型建议 | 来源 | 映射 | 说明 |
|---|---|---|---|---|
| `clientId` | `string` | 前端生成/本地持久化 | 请求头 `X-Client-Id` | 所有 API 必带 |
| `theme` | `'dark'\|'light'` | 前端本地 | - | 默认 dark |
| `language` | `'zh'\|'en'` | 前端本地 + 发送消息 | `POST /messages.language` | 全局语言偏好 |
| `difficulty` | `'basic'\|'intermediate'\|'advanced'` | 前端本地 + 发送消息 | `POST /messages.difficulty` | 全局难度偏好 |
| `networkHealthy` | `boolean` | 心跳/请求结果 | - | 网络状态 |
| `globalToast` | `ToastState` | 错误处理器 | `error.code/error.message` | 全局提示 |

#### 10.7.4 类型命名建议（TypeScript）

- `ApiErrorPayload`：对应后端 `error` 结构。
- `ConversationListItem`：对应 `GET /conversations` 的 `items[]`。
- `ConversationDetailPayload`：对应 `GET /conversations/{id}`。
- `SubmitMessagePayload`：对应 `POST /messages` 响应。
- `SseMetaEvent` / `SseDeltaEvent` / `SseDoneEvent` / `SseErrorEvent`：对应 SSE 四类事件。
- `UiMessage`：前端渲染统一消息模型（由接口字段归一化而来）。

#### 10.7.5 字段归一化规则（落库到 Store 前）

- 时间字段统一保留 ISO 字符串；展示层再格式化。
- `null/undefined` 统一在 service 层归一，避免组件判空分叉。
- SSE `delta.text` 只做追加，不覆盖历史内容。
- `done` 到达后再一次性写入 `usage/citations`，减少抖动渲染。
- `error` 到达后保持已生成内容，禁止清空当前 assistant 文本。

#### 10.7.6 最小可用 action 清单

- `conversationStore`
  - `fetchConversationList(reset?: boolean)`
  - `fetchConversationDetail(conversationId: string)`
  - `createConversation(title?: string)`
  - `setCurrentConversation(conversationId: string)`

- `messageStore`
  - `submitUserMessage(params)`
  - `startStream(streamUrl: string)`
  - `appendDelta(text: string)`
  - `finishStream(donePayload)`
  - `failStream(errorPayload)`
  - `cancelStream()`
  - `loadMoreMessages(before: string)`

- `appStore`
  - `initClientId()`
  - `setTheme(theme)`
  - `setLanguage(language)`
  - `setDifficulty(difficulty)`
  - `showToast(payload)`

---

## 11. 文档维护机制

- 后端新增/修改接口时，必须同步更新本文件第 10 节。
- 每次前后端联调前，先核对第 10.3 总览表与 Swagger。
- 若出现契约不一致，以后端 controller 与 DTO 为准，文档随后修订。

---

此版本作为“Vue3 前端落地蓝图 + 视觉升级规范 + 后端接口契约整理”，可直接用于原型、任务拆分、联调和开发排期。
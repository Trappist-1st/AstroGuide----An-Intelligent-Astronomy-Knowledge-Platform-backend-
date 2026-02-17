# 🌌 AstroGuide – V1 技术设计文档（TDD）

> 日期：2026-02-12  
> 范围：V1 在 V0 基础上增加 **RAG 知识库增强问答**（向量检索 + 引用来源）、**Wikipedia 按需实时拉取**（固定管线步骤），并预留天文数据库（SIMBAD/NED）分层扩展。  
> 依据：PRD 3.2 / 4.3、V0 TDD、[V1-RAG-技术实现讨论](./V1-RAG-技术实现讨论.md)。

---

## 0. 目标与非目标

### 0.1 目标（V1 必须达成）

- **RAG 知识库增强**：用户提问时，先对「天文学知识库」做向量检索，将 Top-K 片段作为上下文注入 prompt，再流式生成回答，以提升准确性与可追溯性。
- **知识库内容**：本地电子书（经分块与嵌入）+ 可选预取 Wikipedia 词条，统一存入 Qdrant；资料组织无强制格式，能解析出「文本 + 来源」即可。
- **引用来源**：在 SSE `done` 事件中返回 `citations` 数组（chunkId、source、excerpt），前端在回答下方展示「参考来源」列表；V1 不要求正文内可点击的逐段引用。
- **与 V0 兼容**：沿用 V0 的 REST/SSE 接口与鉴权（X-Client-Id）；RAG 可通过配置开关关闭，关闭时行为与 V0 完全一致。
- **技术栈**：向量库 Qdrant（本地、免费），嵌入模型采用 OpenAI/DeepSeek 等云端 Embedding API；不按难度区分 RAG 上下文长度。
- **Wikipedia 按需实时拉取**：用户提问时，根据当前问题调用 Wikipedia 官方 API（MediaWiki REST）搜索/取 1–2 篇摘要或段落，与 RAG 检索结果一起拼进 prompt，并纳入 `citations`；采用**固定管线步骤**（后端根据 query 自动调 API），不依赖 Function Calling。

### 0.2 非目标（V1 明确不做）

- 专题文章系统、文章后台（V1.5+）
- 完整用户系统、注册登录、收藏/跨设备同步（V2+）
- Function Calling、SIMBAD/NED 天文数据库接入（可在 V1.5/V2 按分层设计扩展）
- 复杂多模态、知识图谱、显式工作流引擎

---

## 1. 技术选型（V1）

### 1.1 继承自 V0

- **前端**：Vue 3 + Vite + TypeScript，Tailwind CSS，Markdown + KaTeX，SSE（EventSource），Pinia。
- **后端**：Java 21 + Spring Boot 3.x，REST + SSE，Spring AI（ChatClient/Streaming），OpenAI-compatible Chat + Embedding。
- **业务数据**：MySQL（conversations / messages / term_cache / request_usage），MyBatis-Plus。

### 1.2 V1 新增

| 组件 | 选型 | 说明 |
|------|------|------|
| **向量数据库** | Qdrant | 开源、免费、本地部署；Spring AI 有现成 VectorStore 集成。 |
| **嵌入模型** | OpenAI / DeepSeek 等云端 Embedding API | 与 Chat 同厂商或兼容接口；维度与 Qdrant collection 一致。 |
| **Spring AI 扩展** | `spring-ai-vector-store-qdrant`、Embedding 能力 | 版本与现有 spring-ai-openai 1.1.x 对齐。 |
| **Wikipedia 按需** | MediaWiki REST API（HTTP 客户端） | 搜索 `/search/page?q=...`、取页面 `/page/{title}/bare` 或摘要；需 User-Agent，不爬取网页。 |

### 1.3 Qdrant 本地部署（零成本）

- **Docker**：`docker run -p 6333:6333 -p 6334:6334 -v <本地路径>:/qdrant/storage qdrant/qdrant`
- **二进制**：从 [Qdrant Releases](https://github.com/qdrant/qdrant/releases) 下载，解压运行。
- 应用配置：`host: localhost`，`port: 6334`（gRPC）或 6333（HTTP）。

---

## 2. 总体架构

### 2.1 V1 数据流（单次问答）

#### 2.1.1 V1 当前：固定管线（已实现）

由配置与代码决定是否执行 RAG、Wikipedia，顺序写死；LLM 不参与「是否调用」的决策。

```
用户问题（+ 难度/语言）
        ↓
  鉴权 / 限流（V0）
        ↓
  ┌─────────────────────────────────────────────────────────────┐
  │ [Advisor] RAG（若 app.rag.enabled）                           │
  │   1. 用户问题 → Embedding → 向量                              │
  │   2. Qdrant 相似度检索 → Top-K 片段（含 source / chunk_id）   │
  │   3. 组装「参考段落」字符串 + citations 列表                  │
  └─────────────────────────────────────────────────────────────┘
        ↓
  ┌─────────────────────────────────────────────────────────────┐
  │ [固定步骤] Wikipedia 按需（若 wikipedia-on-demand.enabled）    │
  │   1. 根据用户问题做 Wikipedia 搜索或标题匹配                  │
  │   2. 取 1–2 篇摘要/段落（MediaWiki REST API）                 │
  │   3. 拼入参考段落，并加入 citations（source 如 "Wikipedia: 词条名"） │
  └─────────────────────────────────────────────────────────────┘
        ↓
  构造 Prompt：System（含引用说明）+ [参考]\n{RAG+Wikipedia 片段…}\n---\n + 多轮历史 + 当前问题
        ↓
  LLM 流式生成（与 V0 一致）
        ↓
  SSE：meta → delta → done（含 usage + citations）
        ↓
  落库 assistant 消息 + request_usage（V0）
```

#### 2.1.2 V1.5+ Agent 形态数据流（演进目标，见第 10 节）

RAG 保持为 **Advisor**（先注入）；Wikipedia / 知识库检索等能力以 **Tool** 形式暴露给模型。
本项目采用 **Spring AI 框架托管的 Tool Calling**（`@Tool` + `ChatClient.tools(...)`）：
- 工具 schema 由框架生成
- 工具的选择、执行、结果回填与继续生成由框架完成
- 后端不再自研 Agent Loop 状态机

```
用户问题（+ 难度/语言）
        ↓
  鉴权 / 限流（V0）
        ↓
  ┌─────────────────────────────────────────────────────────────┐
  │ [Advisor] RAG：用当前问题做一次检索，拼入 [参考]（不变）       │
  └─────────────────────────────────────────────────────────────┘
        ↓
  构造 Prompt：System + [参考]\n{RAG 片段}\n---\n + 多轮历史 + 当前问题；并传入 tools（含 search_wikipedia 等）
        ↓
    Spring AI 在内部自动完成工具调用与回填，并继续生成最终答案
      ↓
    SSE：meta → delta → done（含 usage + citations）
        ↓
  落库 assistant 消息 + request_usage（V0）
```

### 2.2 架构图

#### 2.2.1 V1 当前架构（固定管线）

```
Browser (Vue)  [无变更]
  |  REST (conversations, messages)
  |  SSE  (stream)
  v
Backend (Spring Boot)
  |  AIChatController / MessageController / ConversationController / ConceptCardsController（V0）
  |  + RagService：检索 Qdrant，组装 RAG 上下文与 citations [Advisor 数据源]
  |  + WikipediaService：按需调 MediaWiki REST API，取摘要并拼入上下文与 citations [固定步骤]
  |  ChatStreamService（V0）+ 注入 RAG + Wikipedia 上下文
  |  Policy：rate-limit / context-trim / output-limit（V0）
  v
  + VectorStore (Qdrant)  + Wikipedia API (MediaWiki REST)  + Embedding  + LLM API (V0)
       ^                              ^
       |  ingest 脚本（离线）          |  固定管线中按配置调用
       |  写入 chunk + metadata        |
  MySQL (V0) 不变
```

#### 2.2.2 V1.5+ Agent 形态架构（演进目标，见第 10 节）

区分 **Advisor**（调用 LLM 前注入）与 **Tool**（由 LLM 通过 function call 按需调用）；后端增加 Agent 循环与 Tool 执行层。

```
Browser (Vue)  [无变更]
  |  REST (conversations, messages)
  |  SSE  (stream)
  v
Backend (Spring Boot)
  |  AIChatController / MessageController / ConversationController / ConceptCardsController（V0）
  |  + RagService：仅作 [Advisor] — 用当前问题检索一次，拼入 [参考]，不暴露为 Tool
  |  + Agent 循环：解析 LLM 响应中的 tool_calls → 调用 Tool 执行器 → 结果回填 → 再调 LLM
  |  + Tool 执行器：search_wikipedia(query)、[可选] search_knowledge_base(query)、[未来] query_simbad 等
  |  + WikipediaService：作为 search_wikipedia 的实现，仅在被 Tool 调用时执行
  |  ChatStreamService（V0）/ 支持 tools 的流式调用
  |  Policy：rate-limit / context-trim / output-limit（V0）
  v
  + VectorStore (Qdrant)     + Wikipedia API (MediaWiki)  + Embedding  + LLM API (V0，支持 function calling)
       ^ [Advisor 检索]              ^ [Tool 按需调用]
       |  ingest 脚本（离线）         |
  MySQL (V0) 不变
```

### 2.3 核心设计原则

- **可关闭**：`app.rag.enabled=false` 时，不调 VectorStore/Embedding；`app.rag.wikipedia-on-demand.enabled=false` 时不调 Wikipedia API，行为可回退。
- **可追溯**：每个参考片段（RAG + Wikipedia）带 `source`、`chunk_id` 或等价标识，在 `done.citations` 中返回，前端仅做「回答下方参考来源」展示。
- **分层可扩展**：V1 实现「第一层：静态 RAG（Advisor）」+「第二层：Wikipedia 按需(Tool Calling 工具调用)」；第三层 SIMBAD/NED 见第 9 节预留。演进为 Agent 时，Wikipedia 改为 Tool、RAG 保持 Advisor，见第 10 节与 2.1.2、2.2.2。

---

## 3. 关键交互与数据流

### 3.1 提问（流式回答）— 与 V0 的差异

1. 前端行为与 V0 相同：创建/选择会话 → POST 消息 → 打开 SSE 接收流。
2. 后端在「构造 prompt 之前」新增（当 RAG 或 Wikipedia 按需启用时）：
   - **RAG（若启用）**：调用 `RagService.retrieve(userQuestion)`，得到 Top-K 片段与拼接字符串，加入待注入的参考内容与 `citations`。
   - **Wikipedia 按需（若启用）**：调用 `WikipediaService.fetchForQuery(userQuestion)`，根据问题搜索或匹配词条，取 1–2 篇摘要/段落（MediaWiki REST API），拼入参考内容，并追加到 `citations`（source 如 "Wikipedia: 词条名"）。
   - 将「RAG + Wikipedia」合并后的参考段落拼入 System 或 User 首段；总参考长度受 `app.rag.max-context-tokens`（或单独 Wikipedia 长度上限）约束。
3. 后续步骤与 V0 一致：多轮历史 + 当前问题 → LLM 流式 → delta/done/error。
4. 在发送 `done` 时，在 JSON 中增加 `citations` 字段（数组），前端在回答下方展示。

### 3.2 知识库构建（Ingest，离线）

- **时机**：定期或一次性，非实时。
- **流程**：本地/预取文本 → 分块（按段落/知识点，约 300–800 字符）→ 调用 Embedding API → 写入 Qdrant（content + metadata：source、chunk_id）。
- **数据来源**：本地电子书（txt/md 或经转换的 PDF/EPUB）+ 可选通过 Wikipedia API 预取词条并写入 Qdrant。
- **Wikipedia 按需**：在问答时实时调用 Wikipedia 官方 API 取摘要/段落，由 **WikipediaService** 实现，采用固定管线步骤（非 Function Calling），见第 9.2 节。

---

## 4. API 设计（V1）

### 4.1 与 V0 的兼容性

- **不新增、不修改** REST 或 SSE 的 URL、方法、请求体结构。
- 唯一变更：**SSE `done` 事件**的 JSON 中增加可选字段 `citations`。

### 4.2 done 事件扩展（V1）

当 RAG 开启且本轮使用了检索结果时，`done` 事件示例：

```json
{
  "status": "done",
  "usage": {
    "promptTokens": 1200,
    "completionTokens": 800,
    "estimatedCostUsd": 0.015
  },
  "citations": [
    {
      "chunkId": "wiki_Type_Ia_supernova_0",
      "source": "Wikipedia: Type Ia supernova",
      "excerpt": "A Type Ia supernova is ..."
    },
    {
      "chunkId": "book_astro_ch12_3",
      "source": "《书名》: 第12章",
      "excerpt": "Standard candles rely on..."
    }
  ]
}
```

- 当 RAG 关闭或未命中时，`citations` 可为 `[]` 或省略；前端按「有则展示，无则隐藏」处理。

### 4.3 配置开关

- `app.rag.enabled`：`true` 时启用 RAG 检索与 citations；`false` 时与 V0 行为一致。
- 可选：后续在请求级别通过 query 参数（如 `?rag=true`）覆盖，V1 可仅用配置项。

---

## 5. 数据模型（V1）

### 5.1 业务库（MySQL）

- **不新增表、不修改现有表结构**。conversations、messages、term_cache、request_usage 与 V0 一致。
- 若后续需持久化「历史消息的 citations」，可在 V1.5 增加 `message_citations` 表或消息扩展字段；V1 仅通过 SSE `done` 返回当轮 citations。

### 5.2 向量库（Qdrant）

- **Collection**：单集合（如 `astro_knowledge`），存储所有来源的 chunk。
- **向量维度**：由 Embedding 模型决定（如 text-embedding-3-small 的维度），创建 collection 时指定。
- **Payload（元数据）**：建议包含 `source`（可读来源）、`chunk_id`（唯一 ID）、可选 `doc_id`；便于检索结果映射为 `citations` 条目。

---

## 6. 后端模块划分（V1）

### 6.1 新增/扩展

| 模块 | 职责 | 说明 |
|------|------|------|
| **RagService** | 输入问题，返回 Top-K 片段 + 拼接字符串 + citations 列表 | 内部调用 VectorStore.similaritySearch、Embedding；受 app.rag.top-k、max-context-tokens 约束。 |
| **WikipediaService** | 根据用户问题调用 MediaWiki REST API，返回 1–2 条摘要/段落及 source | 搜索 `/search/page?q=...` 或取页面 `/page/{title}/bare`；需 User-Agent；结果拼入参考区并追加 citations。 |
| **VectorStore 配置** | Qdrant 连接与 collection 名 | Spring AI Qdrant 自动配置，或显式 Bean。 |
| **Embedding 配置** | 调用 OpenAI-compatible Embedding API | 与 Chat 可同厂商、同 base-url，或单独配置。 |
| **AIChatController / 流式管线** | 在构造 prompt 前调用 RagService、WikipediaService；合并 RAG + Wikipedia 上下文注入；在 done 中附带 citations | 保持现有 stream 方法，增加 RAG 与 Wikipedia 分支及 citations 合并与传递。 |

### 6.2 不变

- ConversationController、MessageController、ConceptCardsController、ChatStreamService、UsageService、限流/裁剪/输出上限策略、ConceptCardService、TermCache。

### 6.3 依赖（pom.xml）新增

- `spring-ai-vector-store-qdrant`（版本与 spring-ai-openai 1.1.x 对齐）
- Embedding 能力通常已包含在 `spring-ai-openai` 中，需确认 Spring AI 1.1 文档。

---

## 7. 知识库与 Ingest

### 7.1 数据来源

- **本地电子书**：转为 txt/md 后，按目录/章节组织（推荐但不强制）；每块写入 Qdrant 时带 `source`（如 "《书名》: 第3章"）。
- **Wikipedia**：通过 **官方 API**（MediaWiki REST 或 Action API）预取词条正文或摘要，分块后写入 Qdrant，`source` 如 "Wikipedia: 词条名"。禁止爬取网页。

### 7.2 分块策略

- 按语义块：段落、小节为单位，避免在公式或句中切断。
- 块大小：约 300–800 字符（或约 100–250 tokens）；重叠可选 0 或少量。
- 元数据：每块必含 `source`、`chunk_id`（唯一），便于 citations 展示。

### 7.3 Ingest 实现方式

- V1 采用 **离线脚本**（Python/Java 均可）：读取本地文件或调用 Wikipedia API → 分块 → 调 Embedding API → 写入 Qdrant。
- 不要求 Spring Boot 应用内提供「上传/入库」API；若需，可后续增加管理端或 CLI。

---

## 8. 检索与 Prompt 组装

### 8.1 检索参数

- **Top-K**：建议 3–5（如 4），配置项 `app.rag.top-k`。
- **RAG 上下文总长度**：所有难度共用上限，如 2000 tokens（或等价字符数），配置项 `app.rag.max-context-tokens`。
- **相似度**：使用 Qdrant 默认；V1 不做重排序（BM25 等可后续加）。

### 8.2 Prompt 结构

- **System**：在 V0 基础上增加一句：优先依据以下参考内容回答，并可在合适处标注 [来源: xxx]；参考内容仅供增强，不得编造未出现的信息。
- **User（或首条多轮）**：`[参考]\n\n{RAG 片段 + Wikipedia 片段}\n[来源: ...]\n\n---\n\n{多轮对话历史 + 当前问题}`。RAG 与 Wikipedia 按需结果共用同一「参考」区与上下文长度预算（如 max-context-tokens）。
- 多轮历史与当前问题的组装与 V0 一致（最近 N 轮 + 字符上限）。

### 8.3 与多轮历史的结合

- RAG 仅针对 **当前问题** 检索一次；上下文 = RAG 片段 + V0 已有对话历史 + 当前问题。不对上一轮回答或多轮拼接再做检索。

---

## 9. 分层数据源与扩展

### 9.1 三层划分

| 层级 | 数据源 | 数据类型 | 典型时机 | V1 状态 |
|------|--------|----------|----------|---------|
| **1** | 本地电子书 + 预取 Wikipedia | 长文本，向量检索 | 每次问答（RAG 开启时） | ✅ V1 实现 |
| **2** | Wikipedia 按需实时 API | 短文本（摘要/段落） | 每次问答（按需启用时） | ✅ V1 实现（固定管线步骤） |
| **3** | SIMBAD / NED | 结构化（表/JSON） | 涉及天体/星表时 | 预留 V1.5+；建议 Function Calling 或规则+缓存 |

### 9.2 Wikipedia 按需实时拉取（第二层，V1 实现）

- **接口**：MediaWiki REST API（英文 Wikipedia 示例）  
  - 搜索：`GET https://en.wikipedia.org/w/rest.php/v1/search/page?q={query}&limit=2`  
  - 取页面：`GET https://en.wikipedia.org/w/rest.php/v1/page/{title}/bare` 或使用 Action API `prop=extracts&exintro&explaintext` 取纯文本摘要  
  - 请求头必须带 **User-Agent**（如 `AstroGuide/1.0 (contact@example.com)`），否则可能被限流。
- **实现方式（V1）**：**固定管线步骤**。在构造 prompt 前，根据当前用户问题（或从问题中抽取关键词）调用 Wikipedia 搜索 → 取 1–2 条结果对应页面的摘要或首段 → 拼入「参考」区并追加到 `citations`，source 格式如 `"Wikipedia: 词条名"`。不采用 Function Calling。
- **配置**：`app.rag.wikipedia-on-demand.enabled`（默认 true）、可选 `max-results`、`max-chars-per-result`，与 RAG 共用或单独占用上下文长度预算。

### 9.3 天文数据库（第三层，V1.5+ 预留）

- **SIMBAD**：TAP HTTP POST，ADQL，端点如 `https://simbad.cds.unistra.fr/simbad/sim-tap/sync`；限速约 5–6 次/秒。
- **NED**：REST/TAP，限速 1 次/秒；可做 object_id 短时缓存。
- **实现方式**：Function Calling 或规则抽取天体名后调用；引用格式需包含数据库名与对象 ID。V1 不实现。

---

## 10. Agent 演进：Advisor 与 Tool 划分

本节描述从当前「固定管线」演进为「由 LLM 按需调用工具」的 Agent 形态时的设计原则：什么适合保持为 **Advisor**（由我们在调用 LLM 前注入的上下文），什么适合变成 **Tool**（由模型在推理过程中主动调用的能力）。V1 实现为固定管线；以下为 V1.5+ 或后续迭代的演进方向。

> 进一步落地细节（Tool 清单与 schema、Agent Loop、SSE 扩展与限次策略）见：
> [AstroGuide-V1.5-技术设计文档（Agent）](./AstroGuide-V1.5-技术设计文档（Agent）.md)

### 10.1 概念区分

- **Advisor**：在**调用 LLM 之前**由我们决定并注入的上下文（例如固定步骤：先 RAG、再 Wikipedia，再拼进 prompt）。模型不「选择」，只看到已经拼好的内容。
- **Tool**：由**模型在推理过程中**通过 function call 主动调用的能力；我们只负责执行并把结果塞回对话，模型再决定下一步。

目标是把「该由模型决定的」从固定管线里拆出来变成 Tool；把「希望每次都有的基础」保留成 Advisor。

### 10.2 当前设计：固定管线（非 Agent）

当前是一条**写死的顺序**：

```
用户问题 → [若启用] RAG 检索 → [若启用] Wikipedia 按需 → 拼进 prompt → 一次 LLM 调用 → 流式输出 → done
```

- **谁决定**用不用 RAG / Wikipedia？是**配置和代码**，不是模型。
- **执行几次**？每个问题只**一次**检索、一次 LLM。
- 没有「模型说：我要先查 Wikipedia 再查知识库」这种**由模型触发的工具调用**。

因此从常见定义上，当前是 **RAG + 增强的问答系统**，还不是 Agent。

### 10.3 建议划分：什么保持 Advisor，什么变成 Tool

| 能力 | 建议角色 | 说明 |
|------|----------|------|
| **Wikipedia** | **Tool** | 模型按需调用 `search_wikipedia(query)`，决定查不查、查什么、查几次；不再在固定管线里「配置打开就每次调」。 |
| **RAG 知识库** | **Advisor（主） + 可选 Tool** | **默认仍做 Advisor**：用当前问题做一次检索并注入，保证每次回答都有一块来自自家知识库的上下文。**可选**再暴露 `search_knowledge_base(query)` 供模型在需要时**额外**检索（例如多轮中追问某一点）。若希望完全由模型决定用不用知识库，也可将 RAG 仅作 Tool，不再做固定 Advisor。 |
| **Concept Card / 术语解析** | **保持输出侧** | 继续在回复里解析 `[[term:...]]` 等并做概念卡展示；不必须做成 Tool。若希望模型在写答案前先查术语再写，可再考虑 Tool。 |
| **SIMBAD / NED（未来）** | **Tool** | 接入时做成 Tool（如 `query_simbad(object_name)`），由模型在需要查天体时再调。 |

### 10.4 Wikipedia 改为 Tool 的要点

- **工具定义**：例如 `search_wikipedia`，参数 `query: string`（可选 `max_results`）；描述中说明用于查询英文 Wikipedia 摘要。
- **协议**：使用 OpenAI 兼容的 **function calling / tools**（请求中带 `tools` 与工具 schema）。
- **执行与循环**：由 Spring AI 框架托管（Tool Calling 内部自动执行 Tool、回填结果、继续生成），后端不再自研循环。
- **配置**：通过 `app.ai.tools.enabled` 控制是否向模型注册 tools；关闭则不注册 tools。
- **RAG**：若 RAG 保持 Advisor，则**第一次**发 LLM 前仍按现有逻辑做一次 `RagService.retrieve(当前问题)` 并拼入 `[参考]`；Wikipedia 仅作为 Tool 由模型按需调用，不再走固定 Wikipedia 管线。

### 10.5 小结

- **保持 Advisor**：RAG 的「用当前问题做一次检索并注入」——保证天文问答始终有一块来自知识库的上下文。
- **改为 Tool、把决定权交给 LLM**：Wikipedia（以及若采纳的 `search_knowledge_base`、未来 SIMBAD/NED）——由模型决定何时调用、传什么参数、调用几次，实现真正的 Agent 式按需调用。

---

## 11. 配置与部署

### 11.1 配置项（application.yml）

```yaml
app:
  rag:
    enabled: true
    top-k: 4
    max-context-tokens: 2000
    wikipedia-on-demand:
      enabled: true          # 是否在问答时按需拉取 Wikipedia
      max-results: 2         # 最多取几条词条摘要
      max-chars-per-result: 500   # 单条摘要最大字符数（避免占满上下文）

spring:
  ai:
    vectorstore:
      qdrant:
        host: ${QDRANT_HOST:localhost}
        port: ${QDRANT_PORT:6334}
        collection-name: astro_knowledge
    openai:
      chat:
        options: { ... }   # V0 已有
      embedding:
        api-key: ${OPENAI_API_KEY}
        base-url: ${OPENAI_EMBEDDING_BASE_URL:}  # 可选，与 chat 分离
        options:
          model: text-embedding-3-small  # 或 DeepSeek 等价
```

### 11.2 部署要点

- **Qdrant**：与后端同机或同内网，保证 6334/6333 可达；生产可考虑数据卷持久化。
- **Embedding**：走公网 API 时与 Chat 共用密钥与网络策略即可。
- **启动顺序**：先起 Qdrant，再起 Spring Boot；或应用启动时检测 Qdrant 可用性，RAG 关闭时可不依赖 Qdrant。

---

## 12. 测试与里程碑

### 12.1 单元/集成

- **RagService**：Mock VectorStore 与 Embedding，给定问题断言返回的上下文字符串包含预期 source 与内容；测试 Top-K、max-context-tokens 截断。
- **WikipediaService**：Mock HTTP 客户端或 WireMock，给定 query 返回模拟的搜索/摘要响应，断言拼入的参考内容与 citations 含 "Wikipedia:" 来源。
- **流式管线**：RAG 和/或 Wikipedia 按需开启时，集成测试从提交消息到收到 done，检查 `citations` 同时包含 RAG 与 Wikipedia 来源；两者均关闭时与 V0 行为一致。
- **回归**：V0 种子题库在 RAG+Wikipedia 关闭下仍通过；开启下回答可引用知识库与 Wikipedia 且 citations 非空（在有针对性的问题上）。

### 12.2 里程碑（V1）

| 阶段 | 内容 |
|------|------|
| M1 | Qdrant + Embedding 接入；知识库 ingest 脚本（分块 + 元数据 + 写入 Qdrant）；RagService.retrieve 实现与单测 |
| M2 | WikipediaService 实现（MediaWiki REST：搜索 + 取摘要）；单测或集成 mock；配置项 wikipedia-on-demand 生效 |
| M3 | AIChatController/流式管线中集成 RagService + WikipediaService；RAG + Wikipedia 上下文合并注入；app.rag.enabled 与 wikipedia-on-demand.enabled 开关生效 |
| M4 | done 事件携带 citations（含 RAG 与 Wikipedia）；前端（若在 V1）在回答下方展示参考来源 |
| M5 | 回归测试、部署说明、V1 技术设计文档定稿 |

---

## 13. 与工作流的关系

- 当前实现本质是一条 **线性管线 + 条件分支**（鉴权 → 可选 RAG → 可选 Wikipedia 按需 → 拼 prompt → LLM 流式 → 落库 + citations），即一种**隐式工作流**，用代码实现即可。
- V1 不引入显式工作流引擎（如 LangGraph、Dify）；若后续步骤增多或需产品可配置流程，再考虑将「RAG / Wikipedia / 天文库」抽象为可编排节点，或引入轻量编排层。
- 分层设计（第一层 RAG、第二层 Wikipedia 按需、第三层 SIMBAD/NED）与工作流中的「按需分支、工具节点」对应，便于未来接入 Function Calling 或可视化编排。

---

## 14. 文档与引用

- **PRD**：[🌌 AstroGuide – 天文知识智能平台 初版 MVP 产品需求文档（PRD）](./🌌 AstroGuide – 天文知识智能平台 初版 MVP 产品需求文档（PRD）.md) 第 3.2、4.3 节。
- **V0 TDD**：[AstroGuide – V0 技术设计文档（TDD）](./AstroGuide – V0 技术设计文档（TDD）.md)。
- **V1 讨论与决策**：[V1-RAG-技术实现讨论](./V1-RAG-技术实现讨论.md)（目标范围、技术选型、知识库组织、Wikipedia/天文库分层、与工作流关系）。

---

**变更记录**

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-02-12 | 1.0 | 初稿：RAG 知识库增强、Qdrant+云端 Embedding、citations、分层扩展预留。 |
| 2026-02-12 | 1.1 | 将 Wikipedia 按需实时拉取纳入 V1 范围：固定管线步骤、WikipediaService、MediaWiki REST API、配置与里程碑更新。 |
| 2026-02-12 | 1.2 | 新增第 10 节「Agent 演进：Advisor 与 Tool 划分」：概念区分、当前固定管线说明、Wikipedia 改为 Tool / RAG 保持 Advisor 等建议、Wikipedia 成 Tool 的实现要点；后续章节序号顺延。 |

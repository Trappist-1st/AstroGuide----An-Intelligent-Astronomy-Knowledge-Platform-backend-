# AstroGuide V1：RAG 知识库增强问答 — 技术实现讨论

> 基于 PRD「3.2 AI 增强知识库（RAG）」「4.3 RAG 设计（V1+）」与 TDD「16. RAG 设计（V1+）」整理，用于与现有 V0 后端对接与实现决策。

---

## 一、目标与范围（与 PRD 对齐）

### 1.1 产品目标（PRD 3.2）

- **提升回答准确性**：用「天文学知识库」约束模型，减少幻觉与随意发挥。
- **更系统的讲解**：检索到的片段可作为结构化、可追溯的参考，便于分层讲解。
- **知识来源**：仅用合法公开资料（公版/授权）或自写/自建结构化知识点，避免版权风险。

### 1.2 技术目标（TDD 16 + PRD 4.3）

- 用户问题 → **向量检索知识库** → 检索结果作为上下文 → **与现有流式问答管线结合** → 生成回答。
- **可追溯的引用**：输出中可标记来源片段（如 `[来源: 章节X]`），前端可展示引用列表或跳转（V1 至少能返回引用元数据，前端展示可简化）。
- **切块策略**：面向天文概念（按知识点/公式段落切分），便于检索到「一整块」有意义的内容。

### 1.3 V1 不做（避免范围膨胀）

- 专题文章系统、文章后台（V1.5+）。
- 完整用户系统、收藏/同步（V2+）。
- 复杂的多模态、图谱增强等。

---

## 二、整体架构（与现有 V0 的衔接）

### 2.1 数据流（V1 单次问答）

```
用户问题（+ 难度/语言）
        ↓
  ┌─────────────────────────────────────────────────────────┐
  │ 1. 嵌入模型：问题 → 向量                                    │
  │ 2. 向量库：相似度检索 → Top-K 片段（+ 可选重排序）            │
  │ 3. 上下文组装：检索结果 + 多轮对话历史（V0 已有）             │
  │ 4. Prompt：System + RAG 上下文 + 多轮消息 + 当前问题         │
  │ 5. LLM 流式生成（与 V0 一致：SSE delta / done / error）      │
  │ 6. 可选：解析/返回「引用片段」给前端                         │
  └─────────────────────────────────────────────────────────┘
        ↓
  SSE：meta → delta → done（含 usage，可选含 citations）
```

- **复用 V0**：`/conversations/{id}/messages` + `/stream` 的 API 与 SSE 协议不变；仅在「构造发给 LLM 的 prompt」时增加「RAG 检索上下文」这一步。
- **可选**：在 `done` 事件或单独字段中带上本次回答所依据的文档片段 ID/来源，供前端展示「引用」或「参考来源」。

### 2.2 模块划分（后端）

| 模块 | 职责 | 说明 |
|------|------|------|
| **Embedding** | 文本 → 向量 | 与现有 Chat 解耦，可用 OpenAI Embedding 或本地模型 |
| **VectorStore** | 存储与检索 | 文档向量 + 元数据（来源、章节等） |
| **RagService** | 检索 + 上下文组装 | 输入：用户问题；输出：Top-K 片段 + 拼接后的上下文字符串 |
| **现有 ChatStreamService / AIChatController** | 流式生成 | 在构造 prompt 时调用 RagService，把「RAG 上下文」拼进 system 或 user |
| **引用（Citations）** | 可选 | 检索结果带 ID/来源，在 done 或响应体中返回给前端 |

---

## 三、技术选型与 V1 已拍板决策

### 3.1 向量数据库：Qdrant（本地、免费）

**结论**：V1 使用 **Qdrant**，在本地安装、**完全免费**（开源 Apache 2.0，自托管不产生授权费用）。

| 方案 | 优点 | 缺点 |
|------|------|------|
| **Qdrant**（已选） | 免费、开源；Spring AI 有现成集成；本地一条命令起服务 | 需在本机跑一个 Qdrant 进程/容器 |

**本地安装方式（零成本）**：

- **Docker**（推荐）：`docker run -p 6333:6333 -p 6334:6334 qdrant/qdrant`，数据可挂载到本地目录做持久化。
- **可执行文件**：从 [Qdrant GitHub Releases](https://github.com/qdrant/qdrant/releases) 下载对应系统的二进制，解压后运行即可，无需 Docker。

配置里只需指定 `host: localhost`、`port: 6334`（gRPC）或 6333（HTTP），无需任何云服务或付费。

### 3.2 嵌入模型（Embedding）— V1 已选：云端 API

**结论**：V1 使用 **OpenAI / DeepSeek 等云端 Embedding API**（与现有 Chat 同厂商或同 API 风格即可）。不采用本地 Embedding 模型。

| 方案 | 优点 | 缺点 |
|------|------|------|
| **OpenAI Embedding（如 text-embedding-3-small）** | 质量好、多语言、与现有 API 同账号 | 有成本、需网络 |
| **DeepSeek / 其他兼容 OpenAI Embedding API 的服务** | 可与现有 DeepSeek Chat 同厂商 | 需确认是否提供 embedding 接口及维度 |
| **硅基流动（SiliconFlow）** | 国内、OpenAI 兼容、提供 BGE/Qwen 等 Embedding 模型 | 需单独配置 base-url 与 API Key |

**硅基流动 Embedding 配置**（无需新增依赖，沿用 `spring-ai-openai` 的 OpenAI 兼容客户端）：

- 在 `.env` 或环境变量中设置：
  - `OPENAI_EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1`
  - `OPENAI_EMBEDDING_MODEL=BAAI/bge-large-zh-v1.5`（中文、1024 维）或 `Qwen/Qwen3-Embedding-0.6B`（最长 32768 token，维度可配置）
  - `OPENAI_EMBEDDING_API_KEY=<硅基流动 API Key>`（在 [硅基流动控制台](https://cloud.siliconflow.cn/account/ak) 创建）
- Qdrant 的向量维度由 Embedding 模型决定：BGE-large-zh-v1.5 为 **1024**；`initialize-schema: true` 时由 Spring AI 根据当前 `EmbeddingModel` 自动创建对应维度的 collection。

### 3.3 Spring AI 版本与依赖

- 当前：`spring-ai-client-chat` + `spring-ai-openai` **1.1.2**。
- RAG 需要：**VectorStore**、**EmbeddingModel**、文档的 **Document** 抽象。
- 需新增依赖（示例，版本需与 1.1.2 对齐）：
  - `spring-ai-vector-store-qdrant`（或 Pgvector 等）
  - 若用 OpenAI Embedding：一般已包含在 `spring-ai-openai` 的 embedding 支持中

建议在 POC 阶段先确认 Spring AI 1.1.x 中 Qdrant 与 Embedding 的文档与 API，再定最终依赖名和版本。

---

## 四、知识库构建（Ingest Pipeline）

### 4.1 数据来源（V1 已定：Wikipedia + 本地电子书）

- **Wikipedia**：天文相关词条，可抓取或导出为文本/Markdown，来源标注为 "Wikipedia: 词条名"。
- **本地电子书**：你电脑上的几本电子书。只要最终能导出或转换成**纯文本或 Markdown**，即可用同一套 ingest 流程（分块 → 嵌入 → 写入 Qdrant）；**对资料的组织形式没有硬性要求**，下面 4.2、4.3 给出的是**推荐做法**，便于脚本统一处理和引用展示。

### 4.2 分块（Chunking）策略

PRD 4.3 提到「面向天文概念的切块（按知识点/公式段落）」：

- **按语义块**：段落、小节为单元，避免在公式或句子中间切断。
- **公式**：可整段 LaTeX 作为一块，或与前后 1–2 句一起成块。
- **元数据**：每块建议带 `source`、`chapter/section`、`doc_id`，便于检索结果里带「来源」。
- **块大小**：例如 300–800 字符（或按 token 约 100–250），重叠可设 0 或少量，避免重复检索。

实现上可以是：离线脚本（Python/Java）解析 Markdown/HTML/PDF → 按上述规则分块 → 调用 Embedding API → 写入 VectorStore；V1 可不做「在线实时入库」，只做定期批量 ingest。

### 4.3 资料组织建议（无强制要求，便于 ingest 即可）

对「Wikipedia + 本地电子书」这类资料，**没有强制格式**，只要 ingest 脚本能读入并给每段内容一个**可读来源**即可。下面是可以直接采用的简单约定：

- **目录结构（可选）**  
  - 例如：`knowledge/wikipedia/` 下放按词条导出的文本或 Markdown；`knowledge/books/书名/` 下放按章节拆分的文件。  
  - 不强制：只要脚本能遍历到文件并能从路径或文件名推断「来源」即可。

- **单文件格式**  
  - 纯文本（`.txt`）或 Markdown（`.md`）最简单；若是 PDF/EPUB，需先用现成工具转成文本再交给 ingest（如 Calibre、pdftotext 等）。

- **来源标识**  
  - 每个 chunk 写入向量库时带 `source` 元数据即可，例如：  
    - Wikipedia：`source = "Wikipedia: Type Ia supernova"`  
    - 电子书：`source = "《书名》: 第3章"` 或 `"书名 - Ch.12"`  
  - 这样前端「参考来源」列表就能直接展示，无需再建复杂结构。

**总结**：资料按你方便的方式放在本机即可（甚至全部塞在一个文件夹里、用文件名区分来源也行）；唯一必要的是 ingest 时能解析出「这一段文字 + 它来自哪本书/哪个词条」，并写入 Qdrant 的 content + metadata。

### 4.4 元数据设计（与引用对齐）

每个 chunk 在向量库中建议包含：

- `content`：原始文本（检索后返回给 LLM 和引用）。
- `source`：人类可读来源，如 "Carroll & Ostlie, Ch.12"。
- `doc_id` / `chunk_id`：唯一 ID，用于去重与前端展示。
- 可选：`language`、`topic`（用于过滤，V1 可先不做）。

---

## 五、检索与上下文组装

### 5.1 检索参数

- **Top-K**：建议 K=3–5，平衡上下文长度与相关性（TDD 已提）。
- **相似度**：向量库默认的相似度/距离即可；若有阈值可做过滤，V1 可先不设。
- **重排序（可选）**：BM25 + 向量二阶段，提升精确度；V1 可先只用向量，后续再加。

### 5.2 上下文长度与 Prompt 结构（V1：难度不区分 RAG 长度）

- 现有 V0 已有「多轮对话历史 + 当前问题」的上下文裁剪（如最近 8 轮 + 最大字符数）。
- RAG 上下文需要**额外**占一块 token 预算。**V1 约定**：不按难度区分 RAG 上下文长度，所有难度共用同一上限（如 2000 tokens 或固定字符数）。
- 单次检索总长度上限：约 **1500–2500 tokens**（如 2000），所有难度一致。
- Prompt 结构建议：
    - **System**：角色 + 难度/语言 + 输出规范 + 「请优先依据以下参考内容回答，并可在合适处标注 [来源: xxx]」。
    - **User（或多轮 Message）**：  
      `[参考]\n\n{片段1}\n[来源: ...]\n\n{片段2}\n[来源: ...]\n\n---\n\n{多轮对话历史 + 当前问题}`  
      这样模型既能「看到」参考，又能在回答中引用来源。

### 5.3 与多轮历史的结合方式

- **方案 A**：RAG 只针对「当前问题」检索一次，上下文 = RAG 片段 + 已有对话历史（V0 逻辑） + 当前问题。
- **方案 B**：对「当前问题 + 上一轮回答」一起检索（或只对当前问题）。  
V1 建议 **方案 A**，实现简单、行为清晰；若效果不足再考虑多轮 query 扩展。

---

## 六、与现有流式接口的集成方式

### 6.1 不改 API 契约（推荐）

- 仍使用：`POST /api/v0/conversations/{id}/messages` + `GET .../stream`。
- 在 **AIChatController**（或封装在 ChatStreamService 上游）中：
  1. 拿到当前用户问题 + 难度/语言。
  2. 调用 **RagService.retrieve(question)** → 得到 `List<Document>` 或 `List<ChunkWithSource>`。
  3. 将 RAG 结果拼成「参考段落」字符串，与现有 `buildContextAndUserContent` 的对话历史一起，构造 **system**（或首条 user）中的「参考内容」+ **user** 中的「多轮+当前问题」。
  4. 后续流式生成与 V0 完全一致（delta / done / error）。

这样前端无需改协议，只是回答质量与「是否有引用」会变化。

### 6.2 引用（Citations）如何返回 — V1 仅「回答下方列出参考来源」

- **V1 约定**：只在**回答下方**列出参考来源即可，不需要正文内可点击的 `[来源: 章节X]` 与 chunk 一一对应。
- **实现**：在 **done** 事件的 JSON 里增加可选字段，例如 `citations: [{ "chunkId": "...", "source": "...", "excerpt": "..." }]`，前端在回答下方展示「参考来源」列表。
- 若后续希望历史消息也带引用，可再增加方式 2：把 `citations` 存到消息扩展或单独表，GET 会话详情时返回。

### 6.3 开关：是否启用 RAG

- 建议通过 **配置项**（如 `app.rag.enabled=true/false`）或 **请求参数**（如 `?rag=true`）控制，便于 A/B 或回退。
- 当 RAG 关闭时，行为与 V0 完全一致（不调用 VectorStore，不拼 RAG 上下文）。

---

## 七、配置与部署

### 7.1 配置项（示例）

```yaml
app:
  rag:
    enabled: true
    top-k: 4
    max-context-tokens: 2000   # RAG 片段总 token 上限

spring:
  ai:
    vectorstore:
      qdrant:
        host: ${QDRANT_HOST:localhost}
        port: ${QDRANT_PORT:6334}
        collection-name: astro_knowledge
    openai:
      embedding:
        api-key: ${OPENAI_API_KEY}
        base-url: ${OPENAI_EMBEDDING_BASE_URL:}  # 若与 Chat 不同
        options:
          model: text-embedding-3-small
```

### 7.2 部署（V1：Qdrant 本地、Embedding 云端）

- **Qdrant**：在你这台电脑上本地运行即可（免费、无云费用）。例如 Docker：`docker run -p 6333:6333 -p 6334:6334 -v $(pwd)/qdrant_storage:/qdrant/storage qdrant/qdrant`，或直接运行 Qdrant 二进制；应用配置 `host: localhost`、`port: 6334`。
- **Embedding**：使用 OpenAI/DeepSeek 等云端 API，与现有 Chat 共用网络与密钥即可。

---

## 八、测试与里程碑建议

### 8.1 单元/集成

- **RagService**：给定问题，mock VectorStore 返回固定片段，断言组装后的上下文字符串包含预期来源与内容。
- **端到端**：本地起 Qdrant + 少量测试文档，真实检索 + 流式回答，检查回答中是否提及来源、SSE 是否仍符合 V0 契约。

### 8.2 里程碑（V1 RAG）

| 阶段 | 内容 |
|------|------|
| M1 | 向量库 + Embedding 选型落地；知识库 ingest 脚本（含分块与元数据）；RagService 检索接口 |
| M2 | 与 AIChatController/ChatStreamService 集成；System/User 中注入 RAG 上下文；配置开关 |
| M3 | done 事件中带 citations；前端可展示引用（若前端在 V1 一起做） |
| M4 | 回归测试（含无 RAG 时的 V0 行为）、文档与部署说明 |

---

## 九、V1 已拍板决策汇总

| 项 | 决策 |
|----|------|
| **向量库** | **Qdrant**，本地安装使用，**免费**（开源自托管，无授权费）。 |
| **嵌入模型** | **OpenAI / DeepSeek 等云端 Embedding API**。 |
| **引用粒度** | V1 仅「**回答下方列出参考来源**」，不需正文内可点击的逐段引用。 |
| **知识库内容** | **Wikipedia** + **本地几本电子书**；资料组织**无强制要求**，能解析出「文本 + 来源」即可，推荐见 4.3。 |
| **难度与 RAG** | **不按难度区分** RAG 上下文长度，所有难度共用同一 Top-K 与上下文上限。 |

其余按本文档前述各节实现即可。若需要，可再整理一版「V1 RAG 实现清单」（接口、类职责、配置与本地 Qdrant 启动步骤），便于按清单开发。

---

## 十、Wikipedia 与天文数据库：在线获取与分层设计

本节回答三件事：**Wikipedia 是否要爬取、有没有官方 API**；**“AI 在线爬取”是否等于 Function Calling**；**SIMBAD / NED 等天文数据库如何接入**；以及**不同数据源如何分层**。

---

### 10.1 Wikipedia：无需爬取，有官方 API

Wikipedia **提供官方 API**，不需要自己爬网页，合规且稳定。

- **MediaWiki REST API**（推荐）  
  - 按标题取页面：`GET https://en.wikipedia.org/w/rest.php/v1/page/{title}/bare` 或 `/page/{title}/html`。  
  - 搜索：`GET https://en.wikipedia.org/w/rest.php/v1/search/page?q=Type+Ia+supernova&limit=5`，返回标题 + 摘要片段。  
  - 请求时需带 **User-Agent** 头（例如 `AstroGuide/1.0`），否则可能被限流。  
- **MediaWiki Action API**（传统）  
  - `action=query&prop=extracts&exintro&explaintext` 可获取纯文本摘要（字符数可配）。  

因此：**“从 Wikipedia 拿内容”应通过上述 API 用 HTTP 调用实现，而不是爬取 HTML 页面**。

---

### 10.2 “AI 在线爬取 Wikipedia”的两种实现方式

“AI 能去在线爬取 Wikipedia”可以有两种技术形态：

| 方式 | 含义 | 是否等于 Function Calling |
|------|------|----------------------------|
| **A. 批量预取（Ingest）** | 后台脚本定时/一次性用 Wikipedia API 拉取指定词条，分块后写入 Qdrant，供 RAG 检索。 | **否**。只是普通 HTTP 调用 + 写入向量库，与 LLM 无关。 |
| **B. 按需实时拉取（On-demand）** | 用户提问时，由系统或 LLM 决定“要查哪个词条”，再调 Wikipedia API 取回内容，拼进当轮上下文。 | **可以**用 Function Calling（LLM 决定调 `get_wikipedia(title)`），也可以不用（见下）。 |

- **不用 Function Calling 的做法**：在问答管线里加一个固定步骤，例如“根据用户问题做 Wikipedia 搜索/标题匹配 → 取 1–2 篇摘要 → 与 RAG 检索结果一起拼进 prompt”。由后端逻辑决定是否调 Wikipedia，不交给 LLM 决定。  
- **用 Function Calling 的做法**：给 LLM 定义工具，例如 `get_wikipedia_page(title: string)`、`search_wikipedia(query: string)`。LLM 在生成过程中决定“需要查 Wikipedia 的某词条”并调用工具，后端执行 HTTP 请求后把结果塞回对话，LLM 再基于该结果继续写回答。  

**小结**：  
- Wikipedia 内容 = **官方 API 获取**，不爬取。  
- “AI 在线获取” = 要么 **批量 ingest 进 RAG**，要么 **按需调 API**；按需可以做成 **Function Calling**（LLM 决定何时调、调什么），也可以做成 **固定管线步骤**（后端根据 query 自动查 Wikipedia）。

---

### 10.3 天文数据库：SIMBAD、NED 等

这类数据与 Wikipedia 不同：主要是**结构化星表/天体数据**（坐标、星等、红移、类型等），而不是长文本。

- **SIMBAD**（恒星、太阳系天体、交叉证认、文献等）  
  - **TAP（Table Access Protocol）**：HTTP POST，ADQL 查询。  
  - 示例端点：`https://simbad.cds.unistra.fr/simbad/sim-tap/sync`，参数 `REQUEST=doQuery`, `LANG=ADQL`, `FORMAT=votable`（或 json/csv）。  
  - **限速**：建议约 5–6 次/秒，过高可能被临时封 IP。  
- **NED**（NASA/IPAC 河外数据库）  
  - 提供对象查询、区域查询、TAP 等；常用 **astroquery**（Python），若用 Java/Spring 需自己发 HTTP（NED 有 REST 风格接口）。  
  - **限速**：官方建议 **1 次/秒**，单线程，长时间大批量前最好联系 NED。  

两者都**有官方或标准接口**，不需要爬网页；用 HTTP 调用即可，与“爬取”无关。

---

### 10.4 分层设计：不同数据源如何组织

把「静态 RAG 文本」「Wikipedia 实时文本」「天文数据库结构化数据」区分开，便于扩展和限速控制。

- **第一层：静态 RAG（Qdrant）**  
  - **内容**：本地电子书 +（可选）预先从 Wikipedia API 拉取并 ingest 的词条。  
  - **时机**：用户提问 → 向量检索 → Top-K 片段进 prompt。  
  - **特点**：不占 Wikipedia/天文库的实时配额，延迟低；适合概念解释、教材式内容。  

- **第二层：Wikipedia 按需（实时 API）**  
  - **内容**：当前问题相关的 1–2 个词条摘要或段落。  
  - **时机**：  
    - **方案 1**：固定步骤——每次问答先根据 query 做 Wikipedia 搜索/标题解析，取摘要，与 RAG 结果一起拼进上下文。  
    - **方案 2**：Function Calling——LLM 在需要时调用 `get_wikipedia_page(title)` 或 `search_wikipedia(query)`，后端调 MediaWiki API，把结果返回给 LLM。  
  - **特点**：内容较新、覆盖面大；需注意 API 礼貌使用（User-Agent、请求频率）。  

- **第三层：天文数据库（SIMBAD、NED 等）**  
  - **内容**：具体天体的测光/红移/类型等表格或键值数据。  
  - **时机**：仅当问题明显涉及“某个天体/星表/数值”时再调，避免浪费配额。  
    - **方案 1**：Function Calling——定义工具如 `query_simbad(object_id)`、`query_ned(object_name)`，由 LLM 决定何时调用、传什么对象名。  
    - **方案 2**：简单规则——从 query 里用 NER 或关键词抽“天体名”，若存在则自动调 SIMBAD/NED，把结果表格或摘要拼进上下文。  
  - **特点**：数据权威、可引用；必须遵守限速（SIMBAD 约 6 次/秒，NED 1 次/秒），可做缓存（同一 object_id 短时内复用）。  

**分层汇总表**：

| 层级 | 数据源 | 数据类型 | 典型时机 | 可选实现方式 |
|------|--------|----------|----------|--------------|
| 1 | 本地电子书 + 预取 Wikipedia | 长文本，向量检索 | 每次问答 | RAG（Qdrant + Embedding） |
| 2 | Wikipedia 实时 | 短文本（摘要/段落） | 按需 | 固定步骤 或 **Function Calling** |
| 3 | SIMBAD / NED | 结构化（表/JSON） | 仅涉及天体/数据时 | **Function Calling** 或 规则 + 缓存 |

---

### 10.5 与 V1 范围的衔接

- **V1 RAG 文档当前约定**：知识库 = Wikipedia（可预取） + 本地电子书，先做 **静态 RAG（第一层）** 即可。  
- 若你希望 **V1 就支持“AI 在线用 Wikipedia”**：  
  - 推荐先做 **固定管线步骤**（根据 query 调 Wikipedia 搜索/取摘要并拼进 prompt），实现简单、不依赖 Function Calling。  
  - 若希望“由 AI 决定查哪个词条”，再在 V1 内或 V1.5 加 **Function Calling**，把 `get_wikipedia_page` / `search_wikipedia` 定义为工具。  
- **SIMBAD / NED**：更适合作为 **V1.5 或 V2** 的“天文数据层”，与 Function Calling（或规则 + 缓存）一起上，避免首版复杂度过高；实现时注意限速与引用格式（方便在“参考来源”里列出数据库与对象 ID）。

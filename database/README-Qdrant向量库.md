# Qdrant 向量库说明

## 向量维度与 Embedding 模型必须一致

- 当前默认 Embedding 模型：**Qwen/Qwen3-Embedding-4B** → 输出维度 **2560**
- 若 Qdrant 里已有用 **1536** 维（如 OpenAI text-embedding-ada-002）创建的集合，会报错：
  ```text
  Vector dimension error: expected dim: 1536, got 2560
  ```

## 解决方式（二选一）

### 方式一：删除旧集合，让应用按当前模型重建（推荐）

删除后，下次使用 RAG 时会在 `initialize-schema: true` 下按当前 EmbeddingModel 维度自动建表。

**用 Qdrant HTTP API 删除集合（宿主机或能访问 Qdrant 的机器执行）：**

```bash
# 默认 host/port 见 application.yaml 中的 QDRANT_HOST / QDRANT_PORT
curl -X DELETE "http://<QDRANT_HOST>:6333/collections/astro_knowledge"
```

例如：

```bash
curl -X DELETE "http://36.138.238.148:6333/collections/astro_knowledge"
```

注意：REST API 使用 **6333** 端口，配置里的 6334 为 gRPC。删除后重启应用，再触发一次 RAG 检索即可自动创建 2560 维集合。

### 方式二：改用 1536 维的 Embedding 模型（保留现有数据）

若不想删集合且当前集合为 1536 维，可在 `application.yaml` 中把 embedding 改为 1536 维模型，例如：

```yaml
spring.ai.openai.embedding:
  options:
    # 使用 1536 维模型（需接口支持），与现有 Qdrant 集合一致
    model: text-embedding-ada-002   # OpenAI，1536 维
    # 或硅基流动等兼容接口提供的 1536 维模型
```

保存后重启应用即可。

---

## 资料导入（Ingest）：PDF / EPUB 等如何写入 Qdrant

### Qdrant 官方界面能直接上传 PDF/EPUB 吗？

**不能。** Qdrant Web UI（如 `http://localhost:6333/dashboard`）只提供：

- 集合管理、查看点（points）、REST Console、快照上传/恢复等  
- **不会**解析 PDF/EPUB，也不会做「文本 → 分块 → 向量化」

向量库只存「向量 + 可选 payload」；**把一本书/一份 PDF 变成向量并写入，必须通过代码或脚本完成**。

### 推荐做法：用代码做 Ingest

通用流程：

1. **解析文件**：PDF → 文本（如 Apache PDFBox）、EPUB → 文本（如 epublib）
2. **分块**：按段落或固定长度（如 300–800 字符）切成多段
3. **向量化**：用当前项目配置的 Embedding 模型（如 Qwen3-Embedding-4B）得到向量
4. **写入 Qdrant**：把每块的文本 + 元数据（来源、章节等）写成一条记录

在本项目中，RAG 已使用 **Spring AI 的 `VectorStore`**（背后是 Qdrant），因此最一致的方式是：**在应用内用同一套 Embedding + 同一 collection，通过 `VectorStore.add(List<Document>)` 写入**。

- 每个 `Document`：`content` = 一段文本，`metadata` = 如 `source`（《书名》第 3 章）、`chunk_id` 等  
- `vectorStore.add(documents)` 会自动调用当前配置的 EmbeddingModel 并写入 Qdrant，无需手写向量或调 Qdrant API

**可选实现方式：**

| 方式 | 说明 |
|------|------|
| **Java（与本项目一致）** | 在 AstroGuide 里加一个「Ingest 服务」或启动时 Runner：依赖 PDFBox、epublib 等解析 PDF/EPUB → 分块 → 构造 `List<Document>` → 注入 `VectorStore` 并 `vectorStore.add(documents)`。维度、collection 名与现有 RAG 完全一致。 |
| **Python + Unstructured** | 使用 [Unstructured](https://docs.unstructured.io/open-source/ingestion/destination-connectors/qdrant) 的 `unstructured-ingest[qdrant]`：可解析 PDF 等并写入 Qdrant。需保证 **Embedding 维度与当前集合一致**（本项目为 2560 维），或在 Unstructured 中配置相同维度的 embedding 模型。 |
| **单独脚本调 Qdrant API** | 用任意语言解析 PDF/EPUB、分块后调用 Embedding API，再通过 [Qdrant REST API](https://qdrant.tech/documentation/send-data/) 写入 points。同样要注意 collection 名称与向量维度与现有配置一致。 |

**结论**：资料导入**不能**在 Qdrant 官方界面里「上传 PDF」完成，必须**通过代码**做解析 → 分块 → 向量化 → 写入；在本项目中优先用 **Java + Spring AI `VectorStore.add()`** 与现有 RAG 共用同一套配置与集合。

---

## Ingest 摄入接口（本项目已实现）

应用内已提供 REST 接口，可将 PDF/EPUB/TXT/MD 或纯文本写入当前 Qdrant 集合（与 RAG 共用）。

### 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v0/ingest/file` | 上传文件（multipart：`file` 必填，`sourceName` 可选） |
| POST | `/api/v0/ingest/text` | 提交一段文本（JSON：`content`、`sourceName`） |

### 使用前提

- **RAG 开启**：`app.rag.enabled=true`，且 Qdrant 与 Embedding 可用；否则接口返回 `accepted: false`，提示 "RAG is disabled"。
- 支持格式：**PDF、EPUB、TXT、MD**（按文件名后缀或 Content-Type 识别）。

### 示例

**上传文件（curl）：**

```bash
curl -X POST "http://localhost:8093/api/v0/ingest/file" \
  -F "file=@/path/to/基础天文学.pdf" \
  -F "sourceName=《基础天文学》"
```

**提交文本（JSON）：**

```bash
curl -X POST "http://localhost:8093/api/v0/ingest/text" \
  -H "Content-Type: application/json" \
  -d '{"content":"本节介绍天球坐标系……", "sourceName":"《基础天文学》第2章"}'
```

### 配置（可选）

- `app.ingest.chunk-size`：每块字符数，默认 600。
- `app.ingest.chunk-overlap`：块间重叠字符数，默认 80。

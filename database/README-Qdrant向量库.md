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

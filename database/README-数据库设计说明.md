# AstroGuide 数据库设计说明

## 一、设计合理性评估（对照 PRD）

### PRD 要求（九、数据设计）

- **V0 最小集合**：conversations（会话）、messages（消息）
- **可选**：users（匿名用户也可，用 device/session 先跑通 MVP）

### 当前设计评估

| 维度 | 结论 | 说明 |
|------|------|------|
| **覆盖范围** | ✅ 合理 | 四张表：`conversations`、`messages`、`term_cache`、`request_usage`，完全覆盖 V0 会话/消息需求，并支持 Concept Cards 缓存与可观测性 |
| **会话/消息** | ✅ 合理 | 会话用 `client_id` 区分匿名用户（device/session），无需单独 users 表即可跑通 MVP；消息含 role、difficulty、language、status、token 统计，满足三档难度与双语 |
| **幂等与去重** | ✅ 合理 | `messages.client_message_id` 支持前端重试去重 |
| **Concept Cards** | ✅ 合理 | `term_cache` 用 key（含 type/language）存概念卡片，便于复用、降低成本 |
| **可观测性** | ✅ 合理 | `request_usage` 按 message 记录 model、latency_ms、token，满足 PRD「请求日志、耗时与 token 用量统计」 |
| **扩展性** | ✅ 合理 | 主键为字符串 ID，便于分布式；后续加 users 表时只需在 conversations 上增加 user_id 外键即可 |

### 小结

当前数据库设计**合理**，与 PRD 的 V0 范围一致，且为 Concept Cards 与可观测性预留了表结构。无需为 V0 增加 users 表；用 `client_id` 标识匿名用户即可。

---

## 二、MySQL 建库建表

- **可执行脚本**：`database/schema-mysql.sql`
- **用法**：
  - 命令行：`mysql -u root -p < database/schema-mysql.sql`
  - 或在 MySQL 客户端中打开并执行该文件

脚本会：

1. 创建数据库 `astroguide`（utf8mb4）
2. 按依赖顺序建表：`conversations` → `messages` → `term_cache` → `request_usage`
3. 建立外键：`messages.conversation_id` → `conversations.id`（ON DELETE CASCADE），`request_usage.message_id` → `messages.id`（ON DELETE CASCADE）

---

## 三、技术设计文档（TDD）修改建议

若希望 TDD 中「6.1 表结构」直接对应 MySQL、并可在 MySQL 上建立数据库，建议：

1. **标题**：将  
   `### 6.1 表结构（SQLite 版草案）`  
   改为  
   `### 6.1 表结构（MySQL，可直接在 MySQL 中执行）`

2. **正文**：将原 SQLite 的 `CREATE TABLE` / `CREATE INDEX` 整段替换为：
   - 一句说明：完整可执行脚本见 **`database/schema-mysql.sql`**，可直接在 MySQL 中执行以建库建表；
   - 如需在文档内保留示例，可粘贴 `schema-mysql.sql` 中的建表语句（不含 DROP 与 SET 部分）到 TDD 的代码块中。

3. **技术选型**：在 TDD 的「数据层 / 技术选型」中，将「V0：SQLite」改为「V0：MySQL」（或「MySQL，开发与上线统一」），与当前脚本一致。

完成以上修改后，TDD 中的数据库设计即与在 MySQL 上直接建立的数据库一致。

# AstroGuide

基于 Spring Boot 和 Spring AI 构建的天文知识智能平台 MVP，具备 AI 聊天、概念卡片和 RAG（检索增强生成）功能。

## 功能特性

- **AI 聊天流式输出**：支持 SSE（Server-Sent Events）的实时对话 AI
- **概念卡片**：交互式天文概念解释
- **RAG 集成**：使用 Qdrant 从向量化天文内容中检索知识
- **工具调用**：集成维基百科搜索、知识库查询和概念查找工具
- **会话管理**：持久化对话历史和使用情况跟踪
- **可配置顾问**：内存、RAG 和维基百科顾问以增强响应

## 技术栈

- **后端**：Spring Boot 3.x, Spring AI 1.1+
- **数据库**：MySQL（对话、消息、使用情况）
- **向量存储**：Qdrant（用于 RAG）
- **AI 提供商**：OpenAI 兼容 API（DeepSeek、SiliconFlow 等）
- **外部 API**：维基百科 API
- **构建工具**：Maven
- **Java 版本**：17+

## 快速开始

### 前置条件

- Java 17 或更高版本
- Maven 3.6+
- MySQL 8.0+
- Qdrant（可选，用于 RAG）

### 安装

1. 克隆仓库：
   ```bash
   git clone https://github.com/your-username/astroguide.git
   cd astroguide
   ```

2. 在 `application.yaml` 或通过 `.env` 文件配置环境变量：
   ```yaml
   spring:
     ai:
       openai:
         api-key: your-openai-api-key
         base-url: https://api.deepseek.com  # 或其他兼容端点
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

3. 运行应用：
   ```bash
   mvn spring-boot:run
   ```

应用将在 `http://localhost:8080` 上启动。

## API 端点

- `GET /api/v0/conversations/{conversationId}/messages/{messageId}/stream` - AI 聊天流式输出
- `GET /api/v0/conversations` - 列出对话
- `POST /api/v0/conversations` - 创建对话
- `GET /api/v0/concept-cards` - 获取概念卡片

## 配置

`application.yaml` 中的关键配置选项：

- `app.ai.tools.enabled`：启用/禁用工具调用
- `app.rag.enabled`：启用/禁用 RAG 顾问
- `app.rag.wikipedia-on-demand.enabled`：当工具禁用时启用维基百科顾问

## 用户主动调用工具（确定性触发）

除了“用户只打字，模型自动决定是否调用工具”的自动模式外，AstroGuide 还支持用户用非常轻量的前缀来**主动、确定性地触发指定工具**。

当一条用户消息以指令前缀开头时，后端会：
- 先执行对应工具（Wikipedia / 知识库 / 概念卡）进行预取
- 将检索/查询结果作为参考内容注入 prompt
- 在配置允许的前提下，仍保留自动 tool calling，使模型在同一轮里可以继续调用其它工具
- 仅对“已被显式触发的那个工具”做单次禁用（避免重复调用同一个工具）

支持的指令：

- `@wiki: <query>`
  - 示例：`@wiki: black hole`

- `@kb: <query> [topk=<n>]`
  - 示例：`@kb: 钱德拉塞卡极限 topk=5`

- `@card: <payload>`
  - 简写示例：
    - `@card: 黑洞`
    - `@card: term zh 黑洞`
  - 键值对示例：
    - `@card: type=term lang=zh text="黑洞"`

## 项目结构

```
src/main/java/com/imperium/astroguide/
├── controller/          # REST 控制器
├── service/             # 业务逻辑服务
│   ├── impl/            # 服务实现
│   └── ai/tools/        # Spring AI 工具
├── mapper/              # MyBatis 映射器
├── model/               # DTO 和实体
├── policy/              # 限流和上下文策略
├── config/              # 配置类
└── AstroGuideApplication.java
```

## 贡献

1. Fork 本仓库
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 LICENSE 文件了解详情。

## 文档

- [技术设计文档](docs/AstroGuide-V1-技术设计文档.md)
- [后端架构图](docs/后端整体架构图.md)
- [数据库模式](database/schema-mysql.sql)
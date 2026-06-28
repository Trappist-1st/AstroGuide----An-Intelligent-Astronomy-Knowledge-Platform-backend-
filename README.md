# 🌌 AstroGuide

An intelligent astronomy knowledge platform built with Spring Boot, Spring AI, and LangGraph4j — featuring multi-agent routing, RAG, tool calling, and streaming SSE chat.

## Features

- **LangGraph Agent Runtime**: Phase 3 workflow — `route → retrieve → [plan] → prepare → react → review → finalize`
- **Multi-Agent Routing**: SIMPLE / COMPLEX path with Planner and Answer Reviewer
- **ReAct Subgraph**: LangGraph4j agent ↔ tools loop with MySQL checkpoint persistence
- **Tool Registry + Policy**: Wikipedia, knowledge base, concept cards — with budget, timeout, and audit
- **Context Engineering**: history trim, RAG injection, summary memory, token budget estimation
- **Summary Memory**: async rolling conversation summaries (non-blocking SSE)
- **AI Chat Streaming**: SSE events — `meta` / `delta` / `node_*` / `tool_*` / `route` / `review` / `done`
- **RAG Integration**: Qdrant vector retrieval with citations in `done`
- **Concept Cards**: interactive astronomy term and symbol explanations
- **Session Management**: persistent conversations, usage tracking, agent run audit

## Tech Stack

- **Backend**: Spring Boot 3.4, Spring AI 1.1+, LangGraph4j 1.8
- **Database**: MySQL (conversations, messages, agent_runs, checkpoints, memory summaries)
- **Vector Store**: Qdrant (RAG)
- **AI Providers**: OpenAI-compatible APIs (DeepSeek, SiliconFlow, etc.)
- **Observability**: Micrometer metrics, structured agent run records
- **Build**: Maven · **Java 21**

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.6+
- MySQL 8.0+
- Qdrant (optional, enable with `app.rag.enabled=true`)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/astroguide.git
   cd astroguide
   ```

2. Configure environment variables in `application.yaml` or via a `.env` file (see `.env.example`):
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

3. Initialize the database:
   ```bash
   mysql -u root -p astroguide < database/schema-mysql.sql
   ```

4. Run the application:
   ```bash
   mvn spring-boot:run
   ```

The application starts on `http://localhost:8093` by default.

## API Endpoints

- `GET /api/v0/conversations/{conversationId}/messages/{messageId}/stream` — AI chat streaming (SSE)
- `GET /api/v0/conversations` — list conversations
- `POST /api/v0/conversations` — create conversation
- `GET /api/v0/concept-cards` — concept card lookup

## Configuration

Key options in `application.yaml`:

| Key | Description |
|-----|-------------|
| `app.ai.tools.enabled` | Register tools for the ReAct agent |
| `app.ai.runtime.recursion-limit` | Max ReAct loop depth (default 12) |
| `app.ai.runtime.emit-*-events` | Toggle SSE node / tool / route events |
| `app.ai.router.enabled` | Enable SIMPLE / COMPLEX routing |
| `app.ai.planner.enabled` | Enable execution plan for COMPLEX queries |
| `app.ai.reviewer.enabled` | Enable answer review step |
| `app.ai.memory.summary-debounce-ms` | Summary memory debounce interval |
| `app.rag.enabled` | Enable RAG retrieval in workflow |
| `app.rag.top-k` | Vector retrieval top-K |

## Project Structure

```
src/main/java/com/imperium/astroguide/
├── controller/          # REST controllers
├── ai/
│   ├── orchestrator/    # SSE orchestration (ChatStreamOrchestrator)
│   ├── runtime/         # AgentRuntime, LangGraphAgentRunner, stream events
│   ├── graph/           # Workflow runner, ReAct subgraph, checkpoint saver
│   ├── context/         # Context assembly, trim, token budget
│   ├── memory/          # Session + summary memory, async worker
│   ├── multiagent/      # Router / Planner / Reviewer
│   ├── rag/             # RAG retrieval service
│   ├── tool/            # Tool registry, policy, execution audit
│   └── tools/           # Wikipedia / KB / ConceptCard @Tool implementations
├── service/             # Business services (incl. AgentRunService)
├── mapper/              # MyBatis mappers
├── model/               # DTOs and entities
├── policy/              # Rate limiting policies
└── config/              # Spring configuration (Async, Chat, VectorStore, …)
```

## Architecture Overview

```text
Frontend ──► ChatStreamOrchestrator ──► LangGraphAgentRunner
                                              │
                                              ▼
                                    AstroGuideWorkflowRunner
                                    route → retrieve → [plan]
                                    → prepare → react → review → finalize
                                              │
                                              ▼
                                    LangGraph ReAct (agent ↔ tools)
```

See [Backend Architecture Diagram](docs/后端整体架构图.md) for full diagrams, sequence flows, SSE protocol, and DB schema.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License — see the LICENSE file for details.

## Documentation

- [Backend Architecture Diagram](docs/后端整体架构图.md) — **current implementation reference**
- [V1 Technical Design](docs/AstroGuide-V1-技术设计文档.md) — RAG baseline
- [V1.5 Agent Design](docs/AstroGuide-V1.5-技术设计文档（Agent）.md) — tool calling
- [V2 Agent Engineering](docs/AstroGuide-V2-技术设计文档（Agent工程化）.md) — roadmap
- [Database Schema](database/schema-mysql.sql)

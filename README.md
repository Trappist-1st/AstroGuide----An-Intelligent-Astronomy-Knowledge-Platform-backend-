# AstroGuide

An intelligent astronomy knowledge platform MVP built with Spring Boot and Spring AI, featuring AI-powered chat, concept cards, and RAG (Retrieval-Augmented Generation) capabilities.

## Features

- **AI Chat Streaming**: Real-time conversational AI with SSE (Server-Sent Events) support
- **Concept Cards**: Interactive astronomy concept explanations
- **RAG Integration**: Knowledge retrieval from vectorized astronomy content using Qdrant
- **Tool Calling**: Integrated tools for Wikipedia search, knowledge base queries, and concept lookups
- **Session Management**: Persistent conversation history and usage tracking
- **Configurable Advisors**: Memory, RAG, and Wikipedia advisors for enhanced responses

## Tech Stack

- **Backend**: Spring Boot 3.x, Spring AI 1.1+
- **Database**: MySQL (conversations, messages, usage)
- **Vector Store**: Qdrant (for RAG)
- **AI Providers**: OpenAI-compatible APIs (DeepSeek, SiliconFlow, etc.)
- **External APIs**: Wikipedia API
- **Build Tool**: Maven
- **Java Version**: 17+

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- MySQL 8.0+
- Qdrant (optional, for RAG)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/astroguide.git
   cd astroguide
   ```

2. Configure environment variables in `application.yaml` or via `.env` file:
   ```yaml
   spring:
     ai:
       openai:
         api-key: your-openai-api-key
         base-url: https://api.deepseek.com  # or other compatible endpoint
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

3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

The application will start on `http://localhost:8080`.

## API Endpoints

- `GET /api/v0/conversations/{conversationId}/messages/{messageId}/stream` - AI chat streaming
- `GET /api/v0/conversations` - List conversations
- `POST /api/v0/conversations` - Create conversation
- `GET /api/v0/concept-cards` - Get concept cards

## Configuration

Key configuration options in `application.yaml`:

- `app.ai.tools.enabled`: Enable/disable tool calling
- `app.rag.enabled`: Enable/disable RAG advisor
- `app.rag.wikipedia-on-demand.enabled`: Enable Wikipedia advisor when tools are disabled

## Project Structure

```
src/main/java/com/imperium/astroguide/
├── controller/          # REST controllers
├── service/             # Business logic services
│   ├── impl/            # Service implementations
│   └── ai/tools/        # Spring AI tools
├── mapper/              # MyBatis mappers
├── model/               # DTOs and entities
├── policy/              # Rate limiting and context policies
├── config/              # Configuration classes
└── AstroGuideApplication.java
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Documentation

- [Technical Design Document](docs/AstroGuide-V1-技术设计文档.md)
- [Backend Architecture Diagram](docs/后端整体架构图.md)
- [Database Schema](database/schema-mysql.sql)
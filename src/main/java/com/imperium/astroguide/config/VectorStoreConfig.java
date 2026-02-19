package com.imperium.astroguide.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;

/**
 * 条件性 Qdrant VectorStore 配置。
 * <p>
 * 仅当 {@code app.rag.enabled=true} 时才创建 VectorStore bean，
 * 避免 Qdrant 不可达时导致启动失败。
 * 替代了 {@code spring-ai-starter-vector-store-qdrant} 的自动配置。
 */
@Configuration
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port:6334}")
    private int qdrantPort;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:astro_knowledge}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.qdrant.initialize-schema:true}")
    private boolean initializeSchema;

    @Bean
    public QdrantClient qdrantClient() {
        QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false).build();
        return new QdrantClient(grpcClient);
    }

    @Bean
    public QdrantVectorStore vectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(initializeSchema)
                .build();
    }
}

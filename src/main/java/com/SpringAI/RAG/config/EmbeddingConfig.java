package com.SpringAI.RAG.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class EmbeddingConfig {

    @Bean(name = "customVectorStore")
    public VectorStore vectorStore(EmbeddingModel embeddingClient, JdbcTemplate jdbcTemplate) {
        return new PgVectorStore(jdbcTemplate, embeddingClient);
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }
}

package com.example.chatpdf.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class AppConfig {

    private final AppProperties props;

    // ---------------- LLM (chat) ----------------
    @Bean
    public ChatModel chatLanguageModel() {
        var o = props.getOllama();
        return OllamaChatModel.builder()
                .baseUrl(o.getBaseUrl())
                .modelName(o.getChatModel())
                .temperature(o.getTemperature())
                .timeout(Duration.ofSeconds(o.getTimeoutSeconds()))
                .build();
    }

    // ---------------- Embedding model ----------------
    @Bean
    public EmbeddingModel embeddingModel() {
        var o = props.getOllama();
        return OllamaEmbeddingModel.builder()
                .baseUrl(o.getBaseUrl())
                .modelName(o.getEmbeddingModel())
                .timeout(Duration.ofSeconds(o.getTimeoutSeconds()))
                .build();
    }

    // ---------------- Vector store (pgvector) ----------------
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        var p = props.getPgvector();
        return PgVectorEmbeddingStore.builder()
                .host(p.getHost())
                .port(p.getPort())
                .database(p.getDatabase())
                .user(p.getUser())
                .password(p.getPassword())
                .table(p.getTable())
                .dimension(p.getDimension())
                .createTable(p.isCreateTable())
                .useIndex(p.isUseIndex())
                .indexListSize(p.getIndexListSize())
                .build();
    }
}
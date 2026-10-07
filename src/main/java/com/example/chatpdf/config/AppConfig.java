package com.example.chatpdf.config;

import com.example.chatpdf.service.ChatAssistant;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    @Bean
    public ChatAssistant chatAssistant(ChatModel chatModel) {
        return AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider())
                .build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "chatpdf.rag.rerank", name = "enabled", havingValue = "true")
    public ScoringModel scoringModel() {
        var cfg = props.getRag().getRerank();
        return new OnnxScoringModel(cfg.getModelPath(), cfg.getTokenizerPath());
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        // This provider creates a new in-memory store for every conversation/user.
        // We use a MessageWindow to keep the last 5 messages.
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(5) // Tune this value as needed
                .build();
    }
}
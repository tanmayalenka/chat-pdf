package com.example.chatpdf.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "chatpdf")
public class AppProperties {

    private Ollama ollama = new Ollama();
    private Pgvector pgvector = new Pgvector();
    private Rag rag = new Rag();
    private Upload upload = new Upload();

    @Getter @Setter
    public static class Ollama {
        private String baseUrl;
        private String chatModel;
        private String embeddingModel;
        private double temperature;
        private int timeoutSeconds;
    }

    @Getter @Setter
    public static class Pgvector {
        private String host;
        private int port;
        private String database;
        private String user;
        private String password;
        private String table;
        private int dimension;
        private boolean createTable;
        private boolean useIndex;
        private int indexListSize;
    }

    @Getter @Setter
    public static class Rag {
        private int chunkSize;
        private int chunkOverlap;
        private int maxResults;
        private double minScore;
        private Rerank rerank = new Rerank();
    }

    @Getter @Setter
    public static class Rerank {
        private boolean enabled;
        private String modelPath;
        private String tokenizerPath;
        private int finalTopK;
        private double minScore;
    }

    @Getter @Setter
    public static class Upload {
        private String storageDir;
    }
}
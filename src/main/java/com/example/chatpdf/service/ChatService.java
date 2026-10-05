package com.example.chatpdf.service;

import com.example.chatpdf.config.AppProperties;
import com.example.chatpdf.dto.AskRequest;
import com.example.chatpdf.dto.AskResponse;
import com.example.chatpdf.guardrail.output.GroundingContext;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final AppProperties props;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatAssistant chatAssistant;

    public AskResponse ask(AskRequest request) {
        // 1. Embed question
        Embedding queryEmbedding = embeddingModel.embed(request.question()).content();

        // 2. Search
        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder sb =
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(props.getRag().getMaxResults())
                        .minScore(props.getRag().getMinScore());

        if (request.docId() != null && !request.docId().isBlank()) {
            Filter filter = MetadataFilterBuilder
                    .metadataKey("docId")
                    .isEqualTo(request.docId());
            sb.filter(filter);
        }

        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(sb.build()).matches();

        if (matches.isEmpty()) {
            return new AskResponse(
                    "I couldn't find any relevant content in the uploaded documents.",
                    List.of());
        }

        // 3. Build context (also used by GroundingGuardrail)
        List<String> chunkTexts = matches.stream()
                .map(m -> m.embedded().text())
                .toList();

        String context = matches.stream()
                .map(m -> {
                    Integer page = m.embedded().metadata().getInteger("pageNumber");
                    String pageTag = page != null ? "[page " + page + "] " : "";
                    return pageTag + m.embedded().text();
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        // 4. Make context available to the grounding guardrail
        GroundingContext.set(chunkTexts);

        try {
            // 5. Compose the user message: context + question
            String userMessage = """
                    CONTEXT:
                    %s

                    QUESTION: %s
                    """.formatted(context, request.question());

            // 6. Call the AI Service (guardrails run automatically)
            String answer = chatAssistant.answer(userMessage);

            // 7. Build response with citations
            List<AskResponse.Source> sources = matches.stream()
                    .map(this::toSource)
                    .toList();

            return new AskResponse(answer, sources);

        } finally {
            GroundingContext.clear();   // ← always clean up the ThreadLocal
        }
    }

    private AskResponse.Source toSource(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        var meta = segment.metadata();
        String snippet = segment.text();
        if (snippet.length() > 300) snippet = snippet.substring(0, 300) + "...";
        return new AskResponse.Source(
                meta.getString("docId"),
                meta.getString("fileName"),
                meta.getInteger("pageNumber"),
                snippet,
                match.score()
        );
    }
}
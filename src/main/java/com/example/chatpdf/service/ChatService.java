package com.example.chatpdf.service;

import com.example.chatpdf.config.AppProperties;
import com.example.chatpdf.dto.AskRequest;
import com.example.chatpdf.dto.AskResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
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
    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public AskResponse ask(AskRequest request) {
        // ---- 1. Embed the question ----
        Embedding queryEmbedding = embeddingModel.embed(request.question()).content();

        // ---- 2. Build the search request (with optional docId filter) ----
        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder searchBuilder =
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(props.getRag().getMaxResults())
                        .minScore(0.6);   // discard weak matches

        if (request.docId() != null && !request.docId().isBlank()) {
            Filter filter = MetadataFilterBuilder
                    .metadataKey("docId")
                    .isEqualTo(request.docId());
            searchBuilder.filter(filter);
        }

        // ---- 3. Retrieve matching chunks ----
        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(searchBuilder.build()).matches();

        if (matches.isEmpty()) {
            return new AskResponse(
                    "I couldn't find any relevant content in the uploaded documents.",
                    List.of()
            );
        }

        log.debug("Retrieved {} chunks for question: '{}'",
                matches.size(), request.question());

        // ---- 4. Build the grounded prompt ----
        String context = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = buildPrompt(context, request.question());

        // ---- 5. Ask the LLM ----
        String answer = chatModel.chat(prompt);

        // ---- 6. Build the response with citations ----
        List<AskResponse.Source> sources = matches.stream()
                .map(this::toSource)
                .toList();

        return new AskResponse(answer, sources);
    }

    private String buildPrompt(String context, String question) {
        return """
                You are a helpful assistant answering questions about uploaded documents.
                Answer ONLY using the context below. If the answer is not in the context,
                say "I don't know based on the provided documents." Do not invent facts.

                Context:
                %s

                Question: %s

                Answer:
                """.formatted(context, question);
    }

    private AskResponse.Source toSource(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        var meta = segment.metadata();

        String snippet = segment.text();
        if (snippet.length() > 300) {
            snippet = snippet.substring(0, 300) + "...";
        }

        Integer page = meta.getInteger("pageNumber");   // null-safe

        return new AskResponse.Source(
                meta.getString("docId"),
                meta.getString("fileName"),
                page,
                snippet,
                match.score()
        );
    }
}
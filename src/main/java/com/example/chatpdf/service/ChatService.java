package com.example.chatpdf.service;

import com.example.chatpdf.config.AppProperties;
import com.example.chatpdf.dto.AskRequest;
import com.example.chatpdf.dto.AskResponse;
import com.example.chatpdf.guardrail.output.GroundingContext;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
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

    // Optional — null when reranking is disabled
    @Autowired(required = false)
    private ScoringModel scoringModel;

    public AskResponse ask(AskRequest request) {
        // 1. Wide retrieval
        List<EmbeddingMatch<TextSegment>> candidates = retrieveCandidates(request);

        if (candidates.isEmpty()) {
            return new AskResponse(
                    "I couldn't find any relevant content in the uploaded documents.",
                    List.of());
        }

        // 2. Rerank (or just truncate if disabled)
        List<EmbeddingMatch<TextSegment>> topK = selectTopK(candidates, request.question());

        log.info("Selected {} chunks for LLM (from {} candidates)",
                topK.size(), candidates.size());

        // 3. Build context
        String context = topK.stream()
                .map(m -> {
                    Integer page = m.embedded().metadata().getInteger("pageNumber");
                    String tag = page != null ? "[page " + page + "] " : "";
                    return tag + m.embedded().text();
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        // 4. Feed GroundingGuardrail
        GroundingContext.set(topK.stream()
                .map(m -> m.embedded().text())
                .toList());

        try {
            // 5. Compose the message + call the LLM
            String userMessage = """
                    CONTEXT:
                    %s

                    QUESTION: %s
                    """.formatted(context, request.question());

            String answer = chatAssistant.answer(userMessage);

            // 6. Citations from the same top-K set
            List<AskResponse.Source> sources = topK.stream()
                    .map(this::toSource)
                    .toList();

            return new AskResponse(answer, sources);
        } finally {
            GroundingContext.clear();
        }
    }

    // ---------------------------------------------------------------------
    // Retrieval
    // ---------------------------------------------------------------------
    private List<EmbeddingMatch<TextSegment>> retrieveCandidates(AskRequest request) {
        Embedding queryEmbedding = embeddingModel.embed(request.question()).content();

        var sb = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(props.getRag().getMaxResults())
                .minScore(props.getRag().getMinScore());

        if (request.docId() != null && !request.docId().isBlank()) {
            Filter filter = MetadataFilterBuilder
                    .metadataKey("docId")
                    .isEqualTo(request.docId());
            sb.filter(filter);
        }

        return embeddingStore.search(sb.build()).matches();
    }

    // ---------------------------------------------------------------------
    // Reranking
    // ---------------------------------------------------------------------
    private List<EmbeddingMatch<TextSegment>> selectTopK(
            List<EmbeddingMatch<TextSegment>> candidates,
            String query) {

        var rerankCfg = props.getRag().getRerank();
        int topK = rerankCfg.getFinalTopK();

        // Fallback: no reranker, just take the first K (already sorted by cosine score)
        if (scoringModel == null || !rerankCfg.isEnabled()) {
            log.debug("Reranking disabled — returning top {} by cosine score", topK);
            return candidates.stream().limit(topK).toList();
        }

        // Score every candidate against the query
        List<TextSegment> segments = candidates.stream()
                .map(EmbeddingMatch::embedded)
                .toList();

        List<Double> scores = scoringModel
                .scoreAll(segments, query)
                .content();

        if (scores.size() != candidates.size()) {
            log.warn("Reranker returned {} scores for {} candidates — falling back",
                    scores.size(), candidates.size());
            return candidates.stream().limit(topK).toList();
        }

        // Zip + sort desc by rerank score
        List<ScoredMatch> scored = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            scored.add(new ScoredMatch(candidates.get(i), scores.get(i)));
        }
        scored.sort(Comparator.comparingDouble(ScoredMatch::score).reversed());

        // Log top scores so you can tune
        log.info("Reranked {} candidates for '{}' — top scores: {}",
                scored.size(),
                query,
                scored.stream().limit(5)
                        .map(s -> String.format("%.3f", s.score()))
                        .toList());

        // Apply rerank min-score, take top-K
        double minScore = rerankCfg.getMinScore();
        return scored.stream()
                .filter(s -> s.score() >= minScore)
                .limit(topK)
                .map(ScoredMatch::match)
                .toList();
    }

    private record ScoredMatch(EmbeddingMatch<TextSegment> match, double score) { }

    // ---------------------------------------------------------------------
    // Citation mapping
    // ---------------------------------------------------------------------
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
                match.score()     // cosine score from the bi-encoder — kept for reference
        );
    }
}
package com.example.chatpdf.guardrail.output;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class GroundingGuardrail implements OutputGuardrail {

    /** Minimum fraction of answer tokens that must appear in the context. */
    private static final double MIN_OVERLAP = 0.30;

    /** Very short answers (e.g., "Yes." or "Not stated.") bypass the check. */
    private static final int MIN_ANSWER_LEN = 40;

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String answer = responseFromLLM.text();
        if (answer == null || answer.isBlank()) {
            return failure("The model returned an empty answer.");
        }
        if (answer.length() < MIN_ANSWER_LEN) {
            return success();
        }

        List<String> chunks = GroundingContext.get();
        if (chunks.isEmpty()) {
            // No context — can't ground; skip
            return success();
        }

        Set<String> contextTokens = tokenize(String.join(" ", chunks));
        Set<String> answerTokens = tokenize(answer);

        if (answerTokens.isEmpty()) {
            return success();
        }

        long matched = answerTokens.stream().filter(contextTokens::contains).count();
        double overlap = (double) matched / answerTokens.size();

        log.debug("Grounding overlap = {} ({} / {})", overlap, matched, answerTokens.size());

        if (overlap < MIN_OVERLAP) {
            log.warn("Ungrounded answer detected (overlap={}): '{}'", overlap, truncate(answer, 120));
            return reprompt(
                    "Ungrounded answer",
                    "The previous answer was not fully supported by the provided context. " +
                            "Answer ONLY using the facts in the context. If the context does not " +
                            "contain the answer, respond exactly: \"I don't know based on the provided documents.\"");
        }
        return success();
    }

    private static Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase()
                        .replaceAll("[^a-z0-9\\s]", " ")
                        .split("\\s+"))
                .filter(t -> t.length() > 2)           // drop stopword-ish noise
                .filter(t -> !STOPWORDS.contains(t))
                .collect(Collectors.toSet());
    }

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "all", "any",
            "can", "had", "her", "was", "one", "our", "out", "day", "get",
            "has", "him", "his", "how", "its", "may", "new", "now", "old",
            "see", "two", "way", "who", "boy", "did", "use", "that", "this",
            "with", "have", "from", "they", "will", "would", "there", "their",
            "what", "about", "which", "when", "make", "like", "time", "just",
            "know", "take", "into", "your", "some", "them", "than", "then",
            "only", "come", "over", "also", "back", "after", "first", "well"
    );

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
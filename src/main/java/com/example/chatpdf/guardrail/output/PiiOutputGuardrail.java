package com.example.chatpdf.guardrail.output;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@Slf4j
public class PiiOutputGuardrail implements OutputGuardrail {

    private static final List<Pattern> PII = List.of(
            // Credit card (13–19 digits, with optional separators)
            Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b"),
            // SSN (US)
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
            // Email
            Pattern.compile("\\b[\\w.+-]+@[\\w-]+\\.[\\w.-]+\\b"),
            // Phone (US-ish)
            Pattern.compile("\\b\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}\\b")
    );

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String text = responseFromLLM.text();
        if (text == null || text.isBlank()) {
            return success();
        }
        for (Pattern p : PII) {
            if (p.matcher(text).find()) {
                log.warn("PII pattern detected in answer — redacting");
                // REDACT mode: rewrite instead of failing.
                String redacted = p.matcher(text).replaceAll("[REDACTED]");
                return successWith(redacted);
            }
        }
        return success();
    }
}
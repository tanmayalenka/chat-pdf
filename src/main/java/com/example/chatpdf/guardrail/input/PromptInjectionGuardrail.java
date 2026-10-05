package com.example.chatpdf.guardrail.input;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@Slf4j
public class PromptInjectionGuardrail implements InputGuardrail {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // ---- Instruction override ----
            Pattern.compile(
                    "ignore\\s+(all\\s+|any\\s+|the\\s+|previous\\s+|prior\\s+|above\\s+)*(instructions?|rules?|prompts?|context)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "forget\\s+(everything|all\\s+(rules|instructions)|prior\\s+context|the\\s+above)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "disregard\\s+(all\\s+|any\\s+|your\\s+)?(previous\\s+|prior\\s+|above\\s+)?(instructions?|rules?|context)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "override\\s+(your\\s+)?(instructions?|rules?|settings?)",
                    Pattern.CASE_INSENSITIVE),

            // ---- Role hijacking ----
            Pattern.compile("you\\s+are\\s+now\\s+(a|an|the)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act\\s+as\\s+(if\\s+you\\s+are\\s+)?(a|an|the)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pretend\\s+(to\\s+be|you\\s+are)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("from\\s+now\\s+on\\s+you\\s+(are|will|must)", Pattern.CASE_INSENSITIVE),

            // ---- System prompt leakage ----
            Pattern.compile(
                    "(reveal|show|print|repeat|display|output)\\s+(me\\s+)?(your|the)\\s+(system\\s+)?(prompt|instructions?|rules?)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "what\\s+(is|are)\\s+(your|the)\\s+(system\\s+)?(prompt|instructions?)",
                    Pattern.CASE_INSENSITIVE),

            // ---- Jailbreaks ----
            Pattern.compile("\\b(DAN|developer\\s+mode|jailbreak|bypass\\s+safety)\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(grandma|grandmother)\\s+(exploit|trick)",
                    Pattern.CASE_INSENSITIVE),

            // ---- Delimiter / markup injection ----
            Pattern.compile("<\\|im_start\\|>|<\\|im_end\\|>|\\[INST\\]|\\[/INST\\]",
                    Pattern.CASE_INSENSITIVE),

            // ---- ChatPDF-specific ----
            Pattern.compile("(disregard|ignore|forget)\\s+(the\\s+)?(document|pdf|context|chunks?)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("answer\\s+(from|using|with)\\s+(your\\s+)?(own\\s+)?(knowledge|training|memory)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("don'?t\\s+use\\s+(the\\s+)?(document|pdf|context)",
                    Pattern.CASE_INSENSITIVE)
    );

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage.singleText();
        if (text == null || text.isBlank()) {
            return failure("Your message is empty.");
        }
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(text).find()) {
                log.warn("Prompt injection blocked (pattern='{}'): '{}'",
                        p.pattern(), truncate(text, 120));
                return failure(
                        "Your message looks like a prompt-injection attempt and was blocked.");
            }
        }
        return success();
    }

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
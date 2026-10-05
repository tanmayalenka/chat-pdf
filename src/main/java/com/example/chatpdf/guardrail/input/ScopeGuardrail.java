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
public class ScopeGuardrail implements InputGuardrail {

    // If the message matches ANY of these, it's almost certainly out of scope.
    private static final List<Pattern> OFF_TOPIC = List.of(
            Pattern.compile("\\b(write|compose|generate)\\s+(me\\s+)?(a\\s+)?(poem|song|story|essay|joke)\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(what('s| is) the weather|weather (today|tomorrow))\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(who (won|is winning)|sports? score|stock price|bitcoin|ethereum)\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(tell me a joke|sing a song|draw a picture)\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(who are you|what model are you|are you (chatgpt|gpt|claude|gemini))\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(translate|summarize this (article|link)|browse|search the web)\\b",
                    Pattern.CASE_INSENSITIVE)
    );

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage.singleText();
        if (text == null || text.isBlank()) {
            return failure("Your message is empty.");
        }
        for (Pattern p : OFF_TOPIC) {
            if (p.matcher(text).find()) {
                log.info("Out-of-scope query blocked: '{}'", text);
                return failure(
                        "I can only answer questions about the documents you've uploaded. " +
                                "Please ask something about your PDFs.");
            }
        }
        return success();
    }
}
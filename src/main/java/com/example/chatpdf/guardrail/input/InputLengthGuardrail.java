package com.example.chatpdf.guardrail.input;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.springframework.stereotype.Component;

@Component
public class InputLengthGuardrail implements InputGuardrail {

    private static final int MIN_LEN = 3;
    private static final int MAX_LEN = 2000;

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage.singleText();
        if (text == null || text.isBlank()) {
            return failure("Please type a question.");
        }
        String trimmed = text.substring(text.indexOf("QUESTION:") + "QUESTION:".length()).trim();
        if (trimmed.length() < MIN_LEN) {
            return failure("Your question is too short — please be more specific.");
        }
        if (trimmed.length() > MAX_LEN) {
            return failure("Your question is too long (max " + MAX_LEN + " characters).");
        }
        return success();
    }
}
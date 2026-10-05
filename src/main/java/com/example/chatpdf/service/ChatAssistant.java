package com.example.chatpdf.service;

import com.example.chatpdf.guardrail.input.InputLengthGuardrail;
import com.example.chatpdf.guardrail.input.PromptInjectionGuardrail;
import com.example.chatpdf.guardrail.input.ScopeGuardrail;
import com.example.chatpdf.guardrail.output.GroundingGuardrail;
import com.example.chatpdf.guardrail.output.PiiOutputGuardrail;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;

@InputGuardrails({
        PromptInjectionGuardrail.class,
        ScopeGuardrail.class,
        InputLengthGuardrail.class
})
@OutputGuardrails({
        GroundingGuardrail.class,
        PiiOutputGuardrail.class
})
public interface ChatAssistant {

    @SystemMessage("""
        You are a document Q&A assistant for a ChatPDF application.
        Answer ONLY using the CONTEXT provided below.
        If the context does not contain the answer, respond exactly:
        "I don't know based on the provided documents."
        Never invent facts, numbers, or names.
        Keep answers concise and cite the page number when available.
        """)
    String answer(@UserMessage String question);
}
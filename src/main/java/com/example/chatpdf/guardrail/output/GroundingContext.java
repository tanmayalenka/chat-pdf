package com.example.chatpdf.guardrail.output;

import java.util.List;

public final class GroundingContext {
    private static final ThreadLocal<List<String>> CHUNKS = new ThreadLocal<>();

    public static void set(List<String> chunks) {
        CHUNKS.set(chunks);
    }

    public static List<String> get() {
        return CHUNKS.get() == null ? List.of() : CHUNKS.get();
    }

    public static void clear() {
        CHUNKS.remove();
    }

    private GroundingContext() { }
}
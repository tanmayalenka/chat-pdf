package com.example.chatpdf.dto;

import jakarta.validation.constraints.NotBlank;

public record AskRequest(
        @NotBlank String question,
        String docId,          // optional — if null, search all documents
        String sessionId // <-- NEW: client-generated or server-generated ID
) { }
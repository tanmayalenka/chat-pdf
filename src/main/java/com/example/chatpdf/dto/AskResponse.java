package com.example.chatpdf.dto;

import java.util.List;

public record AskResponse(
        String answer,
        List<Source> sources
) {
    public record Source(
            String docId,
            String fileName,
            String snippet,
            double score
    ) { }
}
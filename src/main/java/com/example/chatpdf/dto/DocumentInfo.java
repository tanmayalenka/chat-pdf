package com.example.chatpdf.dto;

public record DocumentInfo(
        String docId,
        String fileName,
        String uploadedAt,
        long chunkCount,
        int pageCount
) { }
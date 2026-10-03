package com.example.chatpdf.dto;

public record UploadResponse(
        String docId,
        String fileName,
        int chunkCount,
        String storedPath,
        String message
) { }
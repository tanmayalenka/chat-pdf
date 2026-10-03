package com.example.chatpdf.controller;

import com.example.chatpdf.dto.DocumentInfo;
import com.example.chatpdf.dto.UploadResponse;
import com.example.chatpdf.entity.DocumentEntity;
import com.example.chatpdf.repository.DocumentRepository;
import com.example.chatpdf.repository.EmbeddingCleanupRepository;
import com.example.chatpdf.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final EmbeddingCleanupRepository embeddingCleanupRepository;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file)
            throws IOException {
        return ResponseEntity.ok(documentService.ingest(file));
    }

    @GetMapping
    public List<DocumentInfo> list() {
        return documentRepository.findAllByOrderByUploadedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/{docId}")
    public ResponseEntity<DocumentInfo> getOne(@PathVariable String docId) {
        return documentRepository.findById(docId)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{docId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String docId) {
        if (!documentRepository.existsById(docId)) {
            return ResponseEntity.notFound().build();
        }
        int deletedChunks = embeddingCleanupRepository.deleteByDocId(docId);
        documentRepository.deleteById(docId);
        return ResponseEntity.ok(Map.of(
                "docId", docId,
                "deletedChunks", deletedChunks
        ));
    }

    private DocumentInfo toDto(DocumentEntity e) {
        return new DocumentInfo(
                e.getDocId(),
                e.getFileName(),
                e.getUploadedAt().toString(),
                e.getChunkCount()
        );
    }
}
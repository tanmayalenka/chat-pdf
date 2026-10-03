package com.example.chatpdf.service;

import com.example.chatpdf.config.AppProperties;
import com.example.chatpdf.dto.UploadResponse;
import com.example.chatpdf.entity.DocumentEntity;
import com.example.chatpdf.repository.DocumentRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final AppProperties props;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final DocumentRepository documentRepository;

    @Transactional
    public UploadResponse ingest(MultipartFile file) throws IOException {
        validate(file);

        String docId = UUID.randomUUID().toString();
        String originalName = file.getOriginalFilename();

        // 1. Persist file to disk
        Path storageDir = Paths.get(props.getUpload().getStorageDir())
                .toAbsolutePath().normalize();
        Files.createDirectories(storageDir);
        String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = storageDir.resolve(docId + "_" + safeName);
        file.transferTo(target);

        // 2. Save JPA record with status INDEXING
        DocumentEntity entity = DocumentEntity.builder()
                .docId(docId)
                .fileName(originalName)
                .storedPath(target.toString())
                .sizeBytes(file.getSize())
                .chunkCount(0)
                .status(DocumentEntity.DocumentStatus.INDEXING)
                .uploadedAt(Instant.now())
                .build();
        documentRepository.save(entity);

        try {
            // 3. Parse PDF
            Document document;
            try (InputStream in = Files.newInputStream(target)) {
                document = new ApachePdfBoxDocumentParser().parse(in);
            }

            // 4. Attach metadata
            Metadata metadata = new Metadata();
            metadata.put("docId", docId);
            metadata.put("fileName", originalName);
            metadata.put("uploadedAt", entity.getUploadedAt().toString());
            document = Document.from(document.text(), metadata);

            // 5. Split
            var rag = props.getRag();
            List<TextSegment> segments = DocumentSplitters
                    .recursive(rag.getChunkSize(), rag.getChunkOverlap())
                    .split(document);

            if (segments.isEmpty()) {
                throw new IllegalStateException("PDF produced no text chunks");
            }

            // 6. Embed + store
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);

            // 7. Update JPA record → INDEXED
            entity.setChunkCount(segments.size());
            entity.setStatus(DocumentEntity.DocumentStatus.INDEXED);
            documentRepository.save(entity);

            log.info("Indexed {} chunks for docId={}", segments.size(), docId);

            return new UploadResponse(
                    docId, originalName, segments.size(),
                    target.toString(), "Indexed successfully");

        } catch (Exception ex) {
            entity.setStatus(DocumentEntity.DocumentStatus.FAILED);
            documentRepository.save(entity);
            throw ex;
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported");
        }
    }
}
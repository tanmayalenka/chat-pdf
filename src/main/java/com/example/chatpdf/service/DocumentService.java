package com.example.chatpdf.service;

import com.example.chatpdf.config.AppProperties;
import com.example.chatpdf.dto.UploadResponse;
import com.example.chatpdf.entity.DocumentEntity;
import com.example.chatpdf.repository.DocumentRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
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
        Instant uploadedAt = Instant.now();

        // 1. Persist file
        Path storageDir = Paths.get(props.getUpload().getStorageDir())
                .toAbsolutePath().normalize();
        Files.createDirectories(storageDir);
        String safeName = originalName != null ? originalName.replaceAll("[^a-zA-Z0-9._-]", "_") : null;
        Path target = storageDir.resolve(docId + "_" + safeName);
        file.transferTo(target);

        // 2. Save JPA record (INDEXING)
        DocumentEntity entity = DocumentEntity.builder()
                .docId(docId)
                .fileName(originalName)
                .storedPath(target.toString())
                .sizeBytes(file.getSize())
                .chunkCount(0)
                .pageCount(0)                       // ← add this column, see 3.2b
                .status(DocumentEntity.DocumentStatus.INDEXING)
                .uploadedAt(uploadedAt)
                .build();
        documentRepository.save(entity);

        try {
            // 3. Split into per-page TextSegments
            List<TextSegment> segments = parseAndSplitPerPage(
                    target, docId, originalName, uploadedAt);

            if (segments.isEmpty()) {
                throw new IllegalStateException(
                        "PDF produced no text chunks — is it a scanned image?");
            }

            // 4. Embed + store in one batch
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);

            // 5. Count distinct pages that actually contributed text
            int pagesWithText = (int) segments.stream()
                    .map(s -> s.metadata().getInteger("pageNumber"))
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();

            // 6. Update JPA record (INDEXED)
            entity.setChunkCount(segments.size());
            entity.setPageCount(pagesWithText);
            entity.setStatus(DocumentEntity.DocumentStatus.INDEXED);
            documentRepository.save(entity);

            log.info("Indexed '{}' — {} pages, {} chunks (docId={})",
                    originalName, pagesWithText, segments.size(), docId);

            return new UploadResponse(
                    docId, originalName, segments.size(),
                    target.toString(), "Indexed successfully");

        } catch (Exception ex) {
            entity.setStatus(DocumentEntity.DocumentStatus.FAILED);
            documentRepository.save(entity);
            throw ex;
        }
    }

    /**
     * Parses a PDF page by page and returns chunks tagged with pageNumber.
     * Chunks never cross page boundaries.
     */
    private List<TextSegment> parseAndSplitPerPage(
            Path pdf,
            String docId,
            String fileName,
            Instant uploadedAt) throws IOException {

        var rag = props.getRag();
        var splitter = DocumentSplitters.recursive(
                rag.getChunkSize(), rag.getChunkOverlap());

        List<TextSegment> allSegments = new ArrayList<>();

        try (PDDocument pdfDoc = Loader.loadPDF(pdf.toFile())) {
            int pageCount = pdfDoc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();

            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdfDoc);

                if (text == null || text.isBlank()) {
                    log.debug("Page {} has no text — skipping (scanned image?)", page);
                    continue;
                }

                // Build metadata for this page
                Metadata meta = new Metadata();
                meta.put("docId",      docId);
                meta.put("fileName",   fileName);
                meta.put("uploadedAt", uploadedAt.toString());
                meta.put("pageNumber", page);   // ← integer metadata

                Document pageDoc = Document.from(text.trim(), meta);

                // Split this page; every produced segment inherits pageNumber
                allSegments.addAll(splitter.split(pageDoc));
            }
        }
        return allSegments;
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
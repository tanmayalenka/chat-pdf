package com.example.chatpdf.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents",
        indexes = {
                @Index(name = "idx_documents_uploaded_at", columnList = "uploaded_at"),
                @Index(name = "idx_documents_file_name",  columnList = "file_name")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEntity {

    @Id
    @Column(name = "doc_id", nullable = false, updatable = false, length = 36)
    private String docId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "stored_path", nullable = false, length = 512)
    private String storedPath;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DocumentStatus status;

    @PrePersist
    void prePersist() {
        if (docId == null) {
            docId = UUID.randomUUID().toString();
        }
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }

    public enum DocumentStatus {
        INDEXING, INDEXED, FAILED
    }
}
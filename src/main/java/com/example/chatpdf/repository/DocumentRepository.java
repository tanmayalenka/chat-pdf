package com.example.chatpdf.repository;

import com.example.chatpdf.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    List<DocumentEntity> findAllByOrderByUploadedAtDesc();

    Optional<DocumentEntity> findByFileName(String fileName);

    boolean existsByFileName(String fileName);
}
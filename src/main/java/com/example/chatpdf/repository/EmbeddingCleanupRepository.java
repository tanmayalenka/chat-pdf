package com.example.chatpdf.repository;

import com.example.chatpdf.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class EmbeddingCleanupRepository {

    private final JdbcTemplate jdbcTemplate;
    private final AppProperties props;

    public int deleteByDocId(String docId) {
        String table = safeTable();
        String sql = "DELETE FROM " + table + " WHERE metadata->>'docId' = ?";
        int rows = jdbcTemplate.update(sql, docId);
        log.info("Deleted {} embedding rows for docId={}", rows, docId);
        return rows;
    }

    private String safeTable() {
        String t = props.getPgvector().getTable();
        if (!t.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalStateException("Invalid table name in config: " + t);
        }
        return t;
    }
}
-- 묶음(collection)에 태그 연결 — 기존 diary_tag/transaction_tag와 동일 패턴. 태그로 검색하면
-- 그 묶음에 연결된 일정·가계부·기록까지 collection 요약 화면에서 함께 확인 가능(2026-07 리뉴얼).
CREATE TABLE collection_tag (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    family_id   BIGINT      NOT NULL,
    collection_id BIGINT    NOT NULL,
    tag_id      BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NULL,
    updated_at  DATETIME(6) NOT NULL,
    updated_by  BIGINT      NULL,
    deleted_at  DATETIME(6) NULL,
    deleted_by  BIGINT      NULL,
    alive_uk    BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND, '1970-01-01', deleted_at))) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_collection_tag (collection_id, tag_id, alive_uk),
    KEY idx_collection_tag_tag (tag_id),
    CONSTRAINT fk_collection_tag_collection FOREIGN KEY (collection_id) REFERENCES collection (id),
    CONSTRAINT fk_collection_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

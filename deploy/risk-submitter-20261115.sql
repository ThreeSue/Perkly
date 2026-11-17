CREATE TABLE IF NOT EXISTS risk_content_author (
    field_key VARCHAR(120) NOT NULL PRIMARY KEY,
    content_version VARCHAR(64) NULL,
    author_type VARCHAR(32) NOT NULL,
    author_id BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

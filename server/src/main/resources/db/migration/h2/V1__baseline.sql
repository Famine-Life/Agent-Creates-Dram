-- V1 基线：单用户版既有表结构。
CREATE TABLE IF NOT EXISTS workspace (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    created_at TIMESTAMP
);
COMMENT ON TABLE workspace IS '工作空间，一张画布对应一条记录';

CREATE TABLE IF NOT EXISTS canvas (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    version      INT    NOT NULL DEFAULT 0,
    snapshot     CLOB,
    updated_at   TIMESTAMP
);
COMMENT ON TABLE canvas IS '画布快照，与 workspace 一一对应';

CREATE TABLE IF NOT EXISTS generation_task (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id     BIGINT      NOT NULL,
    canvas_id        BIGINT      NOT NULL,
    group_id         VARCHAR(64),
    shot_index       INT,
    title            VARCHAR(200),
    prompt           CLOB        NOT NULL,
    provider         VARCHAR(32),
    provider_task_id VARCHAR(128),
    status           VARCHAR(24) NOT NULL,
    progress         INT         DEFAULT 0,
    video_url        VARCHAR(1024),
    cover_url        VARCHAR(1024),
    error_msg        VARCHAR(1024),
    poll_count       INT         DEFAULT 0,
    idem_key         VARCHAR(80),
    next_poll_at     TIMESTAMP,
    submitted_at     TIMESTAMP,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    CONSTRAINT uk_task_idem UNIQUE (idem_key),
    CONSTRAINT uk_task_provider UNIQUE (provider, provider_task_id)
);
CREATE INDEX IF NOT EXISTS idx_task_ws   ON generation_task (workspace_id);
CREATE INDEX IF NOT EXISTS idx_task_poll ON generation_task (status, next_poll_at);

CREATE TABLE IF NOT EXISTS prompt_record (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    task_id      BIGINT NOT NULL,
    prompt       CLOB   NOT NULL,
    created_at   TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_prompt_record_ws   ON prompt_record (workspace_id);
CREATE INDEX IF NOT EXISTS idx_prompt_record_task ON prompt_record (task_id);

CREATE TABLE IF NOT EXISTS canvas_image (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT       NOT NULL,
    canvas_id    BIGINT       NOT NULL,
    name         VARCHAR(200),
    url          VARCHAR(1024) NOT NULL,
    object_key   VARCHAR(512),
    x            DOUBLE,
    y            DOUBLE,
    width        DOUBLE DEFAULT 148,
    height       DOUBLE DEFAULT 166,
    created_at   TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_image_ws ON canvas_image (workspace_id);
ALTER TABLE canvas_image ADD COLUMN IF NOT EXISTS prompt CLOB;
ALTER TABLE canvas_image ADD COLUMN IF NOT EXISTS status VARCHAR(24);
ALTER TABLE canvas_image ADD COLUMN IF NOT EXISTS error_msg VARCHAR(1024);
ALTER TABLE canvas_image ADD COLUMN IF NOT EXISTS provider VARCHAR(32);
ALTER TABLE canvas_image ADD COLUMN IF NOT EXISTS source VARCHAR(16);

CREATE TABLE IF NOT EXISTS canvas_node (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT       NOT NULL,
    node_type    VARCHAR(24)  NOT NULL,
    title        VARCHAR(200),
    prompt       CLOB,
    content      CLOB,
    status       VARCHAR(24),
    error_msg    VARCHAR(1024),
    x            DOUBLE,
    y            DOUBLE,
    width        DOUBLE,
    height       DOUBLE,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_node_ws ON canvas_node (workspace_id);

ALTER TABLE generation_task ADD COLUMN IF NOT EXISTS model VARCHAR(64);
ALTER TABLE generation_task ADD COLUMN IF NOT EXISTS ratio VARCHAR(16);
ALTER TABLE generation_task ADD COLUMN IF NOT EXISTS duration INT;
ALTER TABLE generation_task ADD COLUMN IF NOT EXISTS resolution VARCHAR(16);

CREATE TABLE IF NOT EXISTS task_image_ref (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id    BIGINT NOT NULL,
    image_id   BIGINT NOT NULL,
    sort_index INT    NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    CONSTRAINT uk_ref_task_image UNIQUE (task_id, image_id)
);
CREATE INDEX IF NOT EXISTS idx_ref_task ON task_image_ref (task_id);

INSERT INTO workspace (id, name, created_at) VALUES (1, '默认工作空间', CURRENT_TIMESTAMP);
INSERT INTO canvas (id, workspace_id, version, updated_at) VALUES (1, 1, 0, CURRENT_TIMESTAMP);

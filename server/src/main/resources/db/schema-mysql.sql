CREATE TABLE IF NOT EXISTS workspace (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    created_at DATETIME NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS canvas (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    version      INT    NOT NULL DEFAULT 0,
    snapshot     LONGTEXT NULL,
    updated_at   DATETIME NULL,
    KEY idx_canvas_ws (workspace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS generation_task (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id     BIGINT       NOT NULL,
    canvas_id        BIGINT       NOT NULL,
    group_id         VARCHAR(64)  NULL,
    shot_index       INT          NULL,
    title            VARCHAR(200) NULL,
    prompt           LONGTEXT     NOT NULL,
    model            VARCHAR(64)  NULL,
    ratio            VARCHAR(16)  NULL,
    duration         INT          NULL,
    resolution       VARCHAR(16)  NULL,
    provider         VARCHAR(32)  NULL,
    provider_task_id VARCHAR(128) NULL,
    status           VARCHAR(24)  NOT NULL,
    progress         INT          DEFAULT 0,
    video_url        VARCHAR(1024) NULL,
    cover_url        VARCHAR(1024) NULL,
    error_msg        VARCHAR(1024) NULL,
    poll_count       INT          DEFAULT 0,
    idem_key         VARCHAR(80)  NULL,
    next_poll_at     DATETIME     NULL,
    submitted_at     DATETIME     NULL,
    created_at       DATETIME     NULL,
    updated_at       DATETIME     NULL,
    UNIQUE KEY uk_task_idem (idem_key),
    UNIQUE KEY uk_task_provider (provider, provider_task_id),
    KEY idx_task_ws (workspace_id),
    KEY idx_task_poll (status, next_poll_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS prompt_record (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT   NOT NULL,
    task_id      BIGINT   NOT NULL,
    prompt       LONGTEXT NOT NULL,
    created_at   DATETIME NULL,
    KEY idx_prompt_record_ws (workspace_id),
    KEY idx_prompt_record_task (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS canvas_image (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT        NOT NULL,
    canvas_id    BIGINT        NOT NULL,
    name         VARCHAR(200)  NULL,
    url          VARCHAR(1024) NOT NULL,
    object_key   VARCHAR(512)  NULL,
    x            DOUBLE        NULL,
    y            DOUBLE        NULL,
    width        DOUBLE        DEFAULT 148,
    height       DOUBLE        DEFAULT 166,
    prompt       LONGTEXT      NULL,
    status       VARCHAR(24)   NULL,
    error_msg    VARCHAR(1024) NULL,
    provider     VARCHAR(32)   NULL,
    source       VARCHAR(16)   NULL,
    created_at   DATETIME      NULL,
    KEY idx_image_ws (workspace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS canvas_node (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT        NOT NULL,
    node_type    VARCHAR(24)   NOT NULL,
    title        VARCHAR(200)  NULL,
    prompt       LONGTEXT      NULL,
    content      LONGTEXT      NULL,
    status       VARCHAR(24)   NULL,
    error_msg    VARCHAR(1024) NULL,
    x            DOUBLE        NULL,
    y            DOUBLE        NULL,
    width        DOUBLE        NULL,
    height       DOUBLE        NULL,
    created_at   DATETIME      NULL,
    updated_at   DATETIME      NULL,
    KEY idx_node_ws (workspace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS task_image_ref (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id    BIGINT NOT NULL,
    image_id   BIGINT NOT NULL,
    sort_index INT    NOT NULL DEFAULT 0,
    created_at DATETIME NULL,
    UNIQUE KEY uk_ref_task_image (task_id, image_id),
    KEY idx_ref_task (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT IGNORE INTO workspace (id, name, created_at) VALUES (1, '默认工作空间', NOW());
INSERT IGNORE INTO canvas (id, workspace_id, version, updated_at) VALUES (1, 1, 0, NOW());

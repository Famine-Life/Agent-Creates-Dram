-- V1 基线：单用户版既有表结构（MySQL 5.7 兼容）。
CREATE TABLE IF NOT EXISTS workspace (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    name       VARCHAR(128) NOT NULL COMMENT '画布名称',
    created_at DATETIME NULL COMMENT '创建时间'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工作空间，一张画布对应一条记录';

CREATE TABLE IF NOT EXISTS canvas (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    workspace_id BIGINT NOT NULL COMMENT '所属工作空间 ID',
    version      INT    NOT NULL DEFAULT 0 COMMENT '快照版本号',
    snapshot     LONGTEXT NULL COMMENT '画布快照 JSON',
    updated_at   DATETIME NULL COMMENT '快照最后更新时间',
    KEY idx_canvas_ws (workspace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '画布快照';

CREATE TABLE IF NOT EXISTS generation_task (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    workspace_id     BIGINT       NOT NULL COMMENT '所属工作空间 ID',
    canvas_id        BIGINT       NOT NULL COMMENT '所属画布 ID',
    group_id         VARCHAR(64)  NULL COMMENT '镜头分组',
    shot_index       INT          NULL COMMENT '分组内镜头序号',
    title            VARCHAR(200) NULL COMMENT '镜头标题',
    prompt           LONGTEXT     NOT NULL COMMENT '视频生成提示词',
    model            VARCHAR(64)  NULL COMMENT '视频模型标识',
    ratio            VARCHAR(16)  NULL COMMENT '画幅比例',
    duration         INT          NULL COMMENT '时长（秒）',
    resolution       VARCHAR(16)  NULL COMMENT '清晰度',
    provider         VARCHAR(32)  NULL COMMENT '供应商标识',
    provider_task_id VARCHAR(128) NULL COMMENT '供应商任务 ID',
    status           VARCHAR(24)  NOT NULL COMMENT '任务状态',
    progress         INT          DEFAULT 0 COMMENT '进度百分比',
    video_url        VARCHAR(1024) NULL COMMENT '成片地址',
    cover_url        VARCHAR(1024) NULL COMMENT '封面地址',
    error_msg        VARCHAR(1024) NULL COMMENT '失败原因',
    poll_count       INT          DEFAULT 0 COMMENT '轮询次数',
    idem_key         VARCHAR(80)  NULL COMMENT '幂等键',
    next_poll_at     DATETIME     NULL COMMENT '下次轮询时间',
    submitted_at     DATETIME     NULL COMMENT '提交时间',
    created_at       DATETIME     NULL COMMENT '创建时间',
    updated_at       DATETIME     NULL COMMENT '更新时间',
    UNIQUE KEY uk_task_idem (idem_key),
    UNIQUE KEY uk_task_provider (provider, provider_task_id),
    KEY idx_task_ws (workspace_id),
    KEY idx_task_poll (status, next_poll_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '镜头生成任务';

CREATE TABLE IF NOT EXISTS prompt_record (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    workspace_id BIGINT   NOT NULL COMMENT '所属工作空间 ID',
    task_id      BIGINT   NOT NULL COMMENT '关联任务 ID',
    prompt       LONGTEXT NOT NULL COMMENT '提示词内容',
    created_at   DATETIME NULL COMMENT '提交时间',
    KEY idx_prompt_record_ws (workspace_id),
    KEY idx_prompt_record_task (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '提示词提交历史';

CREATE TABLE IF NOT EXISTS canvas_image (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    workspace_id BIGINT        NOT NULL COMMENT '所属工作空间 ID',
    canvas_id    BIGINT        NOT NULL COMMENT '所属画布 ID',
    name         VARCHAR(200)  NULL COMMENT '图片名称',
    url          VARCHAR(1024) NOT NULL COMMENT '图片地址',
    object_key   VARCHAR(512)  NULL COMMENT '对象存储 key',
    x            DOUBLE        NULL COMMENT 'X 坐标',
    y            DOUBLE        NULL COMMENT 'Y 坐标',
    width        DOUBLE        DEFAULT 148 COMMENT '宽度',
    height       DOUBLE        DEFAULT 166 COMMENT '高度',
    prompt       LONGTEXT      NULL COMMENT '生成提示词',
    status       VARCHAR(24)   NULL COMMENT '生成状态',
    error_msg    VARCHAR(1024) NULL COMMENT '失败原因',
    provider     VARCHAR(32)   NULL COMMENT '供应商',
    source       VARCHAR(16)   NULL COMMENT 'UPLOAD / AI',
    created_at   DATETIME      NULL COMMENT '创建时间',
    KEY idx_image_ws (workspace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '画布图片节点';

CREATE TABLE IF NOT EXISTS canvas_node (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    workspace_id BIGINT        NOT NULL COMMENT '所属工作空间 ID',
    node_type    VARCHAR(24)   NOT NULL COMMENT '节点类型',
    title        VARCHAR(200)  NULL COMMENT '标题',
    prompt       LONGTEXT      NULL COMMENT '提示词',
    content      LONGTEXT      NULL COMMENT '内容',
    status       VARCHAR(24)   NULL COMMENT '状态',
    error_msg    VARCHAR(1024) NULL COMMENT '失败原因',
    x            DOUBLE        NULL COMMENT 'X 坐标',
    y            DOUBLE        NULL COMMENT 'Y 坐标',
    width        DOUBLE        NULL COMMENT '宽度',
    height       DOUBLE        NULL COMMENT '高度',
    created_at   DATETIME      NULL COMMENT '创建时间',
    updated_at   DATETIME      NULL COMMENT '更新时间',
    KEY idx_node_ws (workspace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文本/脚本节点';

CREATE TABLE IF NOT EXISTS task_image_ref (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    task_id    BIGINT NOT NULL COMMENT '镜头任务 ID',
    image_id   BIGINT NOT NULL COMMENT '图片节点 ID',
    sort_index INT    NOT NULL DEFAULT 0 COMMENT '引用顺序',
    created_at DATETIME NULL COMMENT '创建时间',
    UNIQUE KEY uk_ref_task_image (task_id, image_id),
    KEY idx_ref_task (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '图片到镜头的引用连线';

INSERT IGNORE INTO workspace (id, name, created_at) VALUES (1, '默认工作空间', NOW());
INSERT IGNORE INTO canvas (id, workspace_id, version, updated_at) VALUES (1, 1, 0, NOW());

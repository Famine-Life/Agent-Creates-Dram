-- MySQL 表结构（生产可用，utf8mb4）
-- 字段注释直接写在列定义里，建表后即可在 information_schema / 客户端看到 COMMENT。

CREATE TABLE IF NOT EXISTS workspace (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    name       VARCHAR(128) NOT NULL COMMENT '画布名称，新建时按序号自动生成“画布 N”',
    created_at DATETIME NULL COMMENT '创建时间'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工作空间，一张画布对应一条记录';

CREATE TABLE IF NOT EXISTS canvas (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    workspace_id BIGINT NOT NULL COMMENT '所属工作空间 ID',
    version      INT    NOT NULL DEFAULT 0 COMMENT '快照版本号，保存时做乐观锁校验（版本不一致返回 409）',
    snapshot     LONGTEXT NULL COMMENT '画布快照 JSON：节点坐标、视口、连线、图片生成参数',
    updated_at   DATETIME NULL COMMENT '快照最后更新时间',
    KEY idx_canvas_ws (workspace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '画布快照，与 workspace 一一对应';

CREATE TABLE IF NOT EXISTS generation_task (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    workspace_id     BIGINT       NOT NULL COMMENT '所属工作空间 ID',
    canvas_id        BIGINT       NOT NULL COMMENT '所属画布 ID',
    group_id         VARCHAR(64)  NULL COMMENT '镜头分组标识（如 A组），用于生成 A-01 形式的镜头编号',
    shot_index       INT          NULL COMMENT '分组内的镜头序号，从 1 开始',
    title            VARCHAR(200) NULL COMMENT '镜头标题',
    prompt           LONGTEXT     NOT NULL COMMENT '视频生成提示词',
    model            VARCHAR(64)  NULL COMMENT '使用的视频生成模型标识',
    ratio            VARCHAR(16)  NULL COMMENT '生成画幅比例，如 16:9',
    duration         INT          NULL COMMENT '生成视频时长（秒）',
    resolution       VARCHAR(16)  NULL COMMENT '生成分辨率档位，如 720P',
    provider         VARCHAR(32)  NULL COMMENT '供应商标识（如 ark / mock）',
    provider_task_id VARCHAR(128) NULL COMMENT '供应商侧返回的任务 ID',
    status           VARCHAR(24)  NOT NULL COMMENT '任务状态：QUEUED 排队 / RUNNING 生成中 / SUCCEEDED 成功 / FAILED 失败 / TIMEOUT 超时 / CANCELED 已取消',
    progress         INT          DEFAULT 0 COMMENT '生成进度百分比（0-100）',
    video_url        VARCHAR(1024) NULL COMMENT '生成结果视频地址',
    cover_url        VARCHAR(1024) NULL COMMENT '视频封面图地址',
    error_msg        VARCHAR(1024) NULL COMMENT '失败原因，成功时为空',
    poll_count       INT          DEFAULT 0 COMMENT '已轮询供应商的次数',
    idem_key         VARCHAR(80)  NULL COMMENT '幂等键，防止同一请求重复创建任务',
    next_poll_at     DATETIME     NULL COMMENT '下次轮询供应商的时间',
    submitted_at     DATETIME     NULL COMMENT '提交到供应商的时间',
    created_at       DATETIME     NULL COMMENT '创建时间',
    updated_at       DATETIME     NULL COMMENT '最后更新时间',
    UNIQUE KEY uk_task_idem (idem_key),
    UNIQUE KEY uk_task_provider (provider, provider_task_id),
    KEY idx_task_ws (workspace_id),
    KEY idx_task_poll (status, next_poll_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '镜头生成任务，一个镜头对应一条记录';

CREATE TABLE IF NOT EXISTS prompt_record (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    workspace_id BIGINT   NOT NULL COMMENT '所属工作空间 ID',
    task_id      BIGINT   NOT NULL COMMENT '关联的镜头任务 ID',
    prompt       LONGTEXT NOT NULL COMMENT '本次提交的提示词内容',
    created_at   DATETIME NULL COMMENT '提交时间',
    KEY idx_prompt_record_ws (workspace_id),
    KEY idx_prompt_record_task (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '镜头提示词提交历史，REST / CLI / MCP 统一记录';

CREATE TABLE IF NOT EXISTS canvas_image (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    workspace_id BIGINT        NOT NULL COMMENT '所属工作空间 ID',
    canvas_id    BIGINT        NOT NULL COMMENT '所属画布 ID',
    name         VARCHAR(200)  NULL COMMENT '图片名称',
    url          VARCHAR(1024) NOT NULL COMMENT '图片访问地址',
    object_key   VARCHAR(512)  NULL COMMENT '对象存储中的 key，本地存储时为空',
    x            DOUBLE        NULL COMMENT '节点在画布上的 X 坐标',
    y            DOUBLE        NULL COMMENT '节点在画布上的 Y 坐标',
    width        DOUBLE        DEFAULT 148 COMMENT '节点卡片宽度（像素）',
    height       DOUBLE        DEFAULT 166 COMMENT '节点卡片高度（像素）',
    prompt       LONGTEXT      NULL COMMENT 'AI 图片的生成提示词，用户上传的图片为空',
    status       VARCHAR(24)   NULL COMMENT 'AI 图片生成状态：QUEUED / RUNNING / SUCCEEDED / FAILED',
    error_msg    VARCHAR(1024) NULL COMMENT 'AI 图片生成失败原因',
    provider     VARCHAR(32)   NULL COMMENT '生成该图片的供应商标识',
    source       VARCHAR(16)   NULL COMMENT '图片来源：UPLOAD 用户上传 / AI 接口生成',
    created_at   DATETIME      NULL COMMENT '创建时间',
    KEY idx_image_ws (workspace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '画布上的图片节点，含用户上传的参考图和 AI 生成的图片';

CREATE TABLE IF NOT EXISTS canvas_node (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    workspace_id BIGINT        NOT NULL COMMENT '所属工作空间 ID',
    node_type    VARCHAR(24)   NOT NULL COMMENT '节点类型：text 文本 / script 脚本',
    title        VARCHAR(200)  NULL COMMENT '节点标题',
    prompt       LONGTEXT      NULL COMMENT '生成该节点内容的提示词',
    content      LONGTEXT      NULL COMMENT '节点展示的文本内容',
    status       VARCHAR(24)   NULL COMMENT '内容生成状态：QUEUED / RUNNING / SUCCEEDED / FAILED',
    error_msg    VARCHAR(1024) NULL COMMENT '生成失败原因',
    x            DOUBLE        NULL COMMENT '节点在画布上的 X 坐标',
    y            DOUBLE        NULL COMMENT '节点在画布上的 Y 坐标',
    width        DOUBLE        NULL COMMENT '节点宽度（像素）',
    height       DOUBLE        NULL COMMENT '节点高度（像素）',
    created_at   DATETIME      NULL COMMENT '创建时间',
    updated_at   DATETIME      NULL COMMENT '最后更新时间',
    KEY idx_node_ws (workspace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '画布上的文本 / 脚本节点';

CREATE TABLE IF NOT EXISTS task_image_ref (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，自增',
    task_id    BIGINT NOT NULL COMMENT '镜头任务 ID',
    image_id   BIGINT NOT NULL COMMENT '被引用的图片节点 ID',
    sort_index INT    NOT NULL DEFAULT 0 COMMENT '图片在镜头参考图中的顺序',
    created_at DATETIME NULL COMMENT '创建时间',
    UNIQUE KEY uk_ref_task_image (task_id, image_id),
    KEY idx_ref_task (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '图片到镜头的引用连线，一条记录对应画布上一条连线';

INSERT IGNORE INTO workspace (id, name, created_at) VALUES (1, '默认工作空间', NOW());
INSERT IGNORE INTO canvas (id, workspace_id, version, updated_at) VALUES (1, 1, 0, NOW());

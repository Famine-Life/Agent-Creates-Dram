-- H2 本地库表结构（MODE=MySQL）
-- 字段注释统一用 COMMENT ON COLUMN 声明：脚本每次启动都会重放，
-- 因此已存在的老库（建表时没有注释）也能补齐注释。

CREATE TABLE IF NOT EXISTS workspace (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    created_at TIMESTAMP
);

COMMENT ON TABLE workspace IS '工作空间，一张画布对应一条记录';
COMMENT ON COLUMN workspace.id IS '主键，自增';
COMMENT ON COLUMN workspace.name IS '画布名称，新建时按序号自动生成“画布 N”';
COMMENT ON COLUMN workspace.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS canvas (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    version      INT    NOT NULL DEFAULT 0,
    snapshot     CLOB,
    updated_at   TIMESTAMP
);

COMMENT ON TABLE canvas IS '画布快照，与 workspace 一一对应';
COMMENT ON COLUMN canvas.id IS '主键，自增';
COMMENT ON COLUMN canvas.workspace_id IS '所属工作空间 ID';
COMMENT ON COLUMN canvas.version IS '快照版本号，保存时做乐观锁校验（版本不一致返回 409）';
COMMENT ON COLUMN canvas.snapshot IS '画布快照 JSON：节点坐标、视口、连线、图片生成参数';
COMMENT ON COLUMN canvas.updated_at IS '快照最后更新时间';

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

COMMENT ON TABLE generation_task IS '镜头生成任务，一个镜头对应一条记录';
COMMENT ON COLUMN generation_task.id IS '主键，自增';
COMMENT ON COLUMN generation_task.workspace_id IS '所属工作空间 ID';
COMMENT ON COLUMN generation_task.canvas_id IS '所属画布 ID';
COMMENT ON COLUMN generation_task.group_id IS '镜头分组标识（如 A组），用于生成 A-01 形式的镜头编号';
COMMENT ON COLUMN generation_task.shot_index IS '分组内的镜头序号，从 1 开始';
COMMENT ON COLUMN generation_task.title IS '镜头标题';
COMMENT ON COLUMN generation_task.prompt IS '视频生成提示词';
COMMENT ON COLUMN generation_task.provider IS '供应商标识（如 ark / mock）';
COMMENT ON COLUMN generation_task.provider_task_id IS '供应商侧返回的任务 ID';
COMMENT ON COLUMN generation_task.status IS '任务状态：QUEUED 排队 / RUNNING 生成中 / SUCCEEDED 成功 / FAILED 失败 / TIMEOUT 超时 / CANCELED 已取消';
COMMENT ON COLUMN generation_task.progress IS '生成进度百分比（0-100）';
COMMENT ON COLUMN generation_task.video_url IS '生成结果视频地址';
COMMENT ON COLUMN generation_task.cover_url IS '视频封面图地址';
COMMENT ON COLUMN generation_task.error_msg IS '失败原因，成功时为空';
COMMENT ON COLUMN generation_task.poll_count IS '已轮询供应商的次数';
COMMENT ON COLUMN generation_task.idem_key IS '幂等键，防止同一请求重复创建任务';
COMMENT ON COLUMN generation_task.next_poll_at IS '下次轮询供应商的时间';
COMMENT ON COLUMN generation_task.submitted_at IS '提交到供应商的时间';
COMMENT ON COLUMN generation_task.created_at IS '创建时间';
COMMENT ON COLUMN generation_task.updated_at IS '最后更新时间';

CREATE INDEX IF NOT EXISTS idx_task_ws   ON generation_task (workspace_id);
CREATE INDEX IF NOT EXISTS idx_task_poll ON generation_task (status, next_poll_at);

-- 镜头提示词提交历史（REST / CLI / MCP 统一在业务层记录）
CREATE TABLE IF NOT EXISTS prompt_record (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    task_id      BIGINT NOT NULL,
    prompt       CLOB   NOT NULL,
    created_at   TIMESTAMP
);

COMMENT ON TABLE prompt_record IS '镜头提示词提交历史，REST / CLI / MCP 统一记录';
COMMENT ON COLUMN prompt_record.id IS '主键，自增';
COMMENT ON COLUMN prompt_record.workspace_id IS '所属工作空间 ID';
COMMENT ON COLUMN prompt_record.task_id IS '关联的镜头任务 ID';
COMMENT ON COLUMN prompt_record.prompt IS '本次提交的提示词内容';
COMMENT ON COLUMN prompt_record.created_at IS '提交时间';

CREATE INDEX IF NOT EXISTS idx_prompt_record_ws   ON prompt_record (workspace_id);
CREATE INDEX IF NOT EXISTS idx_prompt_record_task ON prompt_record (task_id);

-- 画布上的图片节点（参考图）
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

COMMENT ON TABLE canvas_image IS '画布上的图片节点，含用户上传的参考图和 AI 生成的图片';
COMMENT ON COLUMN canvas_image.id IS '主键，自增';
COMMENT ON COLUMN canvas_image.workspace_id IS '所属工作空间 ID';
COMMENT ON COLUMN canvas_image.canvas_id IS '所属画布 ID';
COMMENT ON COLUMN canvas_image.name IS '图片名称';
COMMENT ON COLUMN canvas_image.url IS '图片访问地址';
COMMENT ON COLUMN canvas_image.object_key IS '对象存储中的 key，本地存储时为空';
COMMENT ON COLUMN canvas_image.x IS '节点在画布上的 X 坐标';
COMMENT ON COLUMN canvas_image.y IS '节点在画布上的 Y 坐标';
COMMENT ON COLUMN canvas_image.width IS '节点卡片宽度（像素）';
COMMENT ON COLUMN canvas_image.height IS '节点卡片高度（像素）';
COMMENT ON COLUMN canvas_image.created_at IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_image_ws ON canvas_image (workspace_id);

-- AI 图片节点：prompt / status 等字段（老库通过 ADD COLUMN IF NOT EXISTS 升级）
ALTER TABLE canvas_image ADD COLUMN IF NOT EXISTS prompt CLOB;
ALTER TABLE canvas_image ADD COLUMN IF NOT EXISTS status VARCHAR(24);
ALTER TABLE canvas_image ADD COLUMN IF NOT EXISTS error_msg VARCHAR(1024);
ALTER TABLE canvas_image ADD COLUMN IF NOT EXISTS provider VARCHAR(32);
-- 图片来源：UPLOAD（用户上传）/ AI（接口生成）
ALTER TABLE canvas_image ADD COLUMN IF NOT EXISTS source VARCHAR(16);
UPDATE canvas_image SET source = CASE WHEN prompt IS NULL THEN 'UPLOAD' ELSE 'AI' END WHERE source IS NULL;

COMMENT ON COLUMN canvas_image.prompt IS 'AI 图片的生成提示词，用户上传的图片为空';
COMMENT ON COLUMN canvas_image.status IS 'AI 图片生成状态：QUEUED / RUNNING / SUCCEEDED / FAILED';
COMMENT ON COLUMN canvas_image.error_msg IS 'AI 图片生成失败原因';
COMMENT ON COLUMN canvas_image.provider IS '生成该图片的供应商标识';
COMMENT ON COLUMN canvas_image.source IS '图片来源：UPLOAD 用户上传 / AI 接口生成';

-- 画布上的文本 / 脚本节点
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

COMMENT ON TABLE canvas_node IS '画布上的文本 / 脚本节点';
COMMENT ON COLUMN canvas_node.id IS '主键，自增';
COMMENT ON COLUMN canvas_node.workspace_id IS '所属工作空间 ID';
COMMENT ON COLUMN canvas_node.node_type IS '节点类型：text 文本 / script 脚本';
COMMENT ON COLUMN canvas_node.title IS '节点标题';
COMMENT ON COLUMN canvas_node.prompt IS '生成该节点内容的提示词';
COMMENT ON COLUMN canvas_node.content IS '节点展示的文本内容';
COMMENT ON COLUMN canvas_node.status IS '内容生成状态：QUEUED / RUNNING / SUCCEEDED / FAILED';
COMMENT ON COLUMN canvas_node.error_msg IS '生成失败原因';
COMMENT ON COLUMN canvas_node.x IS '节点在画布上的 X 坐标';
COMMENT ON COLUMN canvas_node.y IS '节点在画布上的 Y 坐标';
COMMENT ON COLUMN canvas_node.width IS '节点宽度（像素）';
COMMENT ON COLUMN canvas_node.height IS '节点高度（像素）';
COMMENT ON COLUMN canvas_node.created_at IS '创建时间';
COMMENT ON COLUMN canvas_node.updated_at IS '最后更新时间';

CREATE INDEX IF NOT EXISTS idx_node_ws ON canvas_node (workspace_id);

-- 镜头节点生成参数：模型 / 比例 / 时长（老库升级）
ALTER TABLE generation_task ADD COLUMN IF NOT EXISTS model VARCHAR(64);
ALTER TABLE generation_task ADD COLUMN IF NOT EXISTS ratio VARCHAR(16);
ALTER TABLE generation_task ADD COLUMN IF NOT EXISTS duration INT;
ALTER TABLE generation_task ADD COLUMN IF NOT EXISTS resolution VARCHAR(16);

COMMENT ON COLUMN generation_task.model IS '使用的视频生成模型标识';
COMMENT ON COLUMN generation_task.ratio IS '生成画幅比例，如 16:9';
COMMENT ON COLUMN generation_task.duration IS '生成视频时长（秒）';
COMMENT ON COLUMN generation_task.resolution IS '生成分辨率档位，如 720P';

-- 图片 -> 镜头 的引用连线（一条记录即画布上一条连线）
CREATE TABLE IF NOT EXISTS task_image_ref (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id    BIGINT NOT NULL,
    image_id   BIGINT NOT NULL,
    sort_index INT    NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    CONSTRAINT uk_ref_task_image UNIQUE (task_id, image_id)
);

COMMENT ON TABLE task_image_ref IS '图片到镜头的引用连线，一条记录对应画布上一条连线';
COMMENT ON COLUMN task_image_ref.id IS '主键，自增';
COMMENT ON COLUMN task_image_ref.task_id IS '镜头任务 ID';
COMMENT ON COLUMN task_image_ref.image_id IS '被引用的图片节点 ID';
COMMENT ON COLUMN task_image_ref.sort_index IS '图片在镜头参考图中的顺序';
COMMENT ON COLUMN task_image_ref.created_at IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_ref_task ON task_image_ref (task_id);

-- 默认工作空间 + 画布
INSERT INTO workspace (id, name, created_at)
SELECT 1, '默认工作空间', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM workspace WHERE id = 1);

-- 老库修复：早期脚本按平台编码读取导致默认工作空间名称乱码
UPDATE workspace SET name = '默认工作空间' WHERE id = 1 AND name <> '默认工作空间';

INSERT INTO canvas (id, workspace_id, version, updated_at)
SELECT 1, 1, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM canvas WHERE id = 1);

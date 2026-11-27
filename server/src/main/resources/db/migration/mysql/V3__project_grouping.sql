-- V3 项目分组（MySQL 5.7 兼容）：一个项目下可以有多张画布。
CREATE TABLE IF NOT EXISTS project (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL COMMENT '所属用户 ID',
    name       VARCHAR(128) NOT NULL COMMENT '项目名称',
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
) COMMENT='用户项目，一个项目下可包含多张画布';

ALTER TABLE workspace ADD COLUMN project_id BIGINT NULL COMMENT '所属项目 ID';
ALTER TABLE workspace ADD KEY idx_workspace_project (project_id);

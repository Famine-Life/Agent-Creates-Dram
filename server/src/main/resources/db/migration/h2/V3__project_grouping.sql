-- V3 项目分组：一个项目下可以有多张画布。
CREATE TABLE IF NOT EXISTS project (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    name       VARCHAR(128) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
COMMENT ON TABLE project IS '用户项目，一个项目下可包含多张画布';
CREATE INDEX IF NOT EXISTS idx_project_user ON project (user_id);

ALTER TABLE workspace ADD COLUMN project_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_workspace_project ON workspace (project_id);

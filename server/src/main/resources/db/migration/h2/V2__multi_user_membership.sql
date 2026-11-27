-- V2 多用户会员制：业务表增加 user_id、任务计费字段，新增用户/会员/钱包/支付/审计/事件表。
-- legacy 数据的 user_id 由启动引导程序回填为当前管理员。

ALTER TABLE workspace ADD COLUMN user_id BIGINT;
ALTER TABLE canvas ADD COLUMN user_id BIGINT;
ALTER TABLE generation_task ADD COLUMN user_id BIGINT;
ALTER TABLE prompt_record ADD COLUMN user_id BIGINT;
ALTER TABLE canvas_image ADD COLUMN user_id BIGINT;
ALTER TABLE canvas_node ADD COLUMN user_id BIGINT;
ALTER TABLE task_image_ref ADD COLUMN user_id BIGINT;

ALTER TABLE generation_task ADD COLUMN billing_op_id VARCHAR(64);
ALTER TABLE generation_task ADD COLUMN credit_cost INT;

ALTER TABLE generation_task DROP CONSTRAINT uk_task_idem;
CREATE UNIQUE INDEX uk_task_user_idem ON generation_task (user_id, idem_key);
CREATE INDEX idx_task_user ON generation_task (user_id);
CREATE INDEX idx_workspace_user ON workspace (user_id);
CREATE INDEX idx_image_user ON canvas_image (user_id);
CREATE INDEX idx_node_user ON canvas_node (user_id);

CREATE TABLE IF NOT EXISTS app_user (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    username          VARCHAR(64)  NOT NULL,
    username_lower    VARCHAR(64)  NOT NULL,
    email             VARCHAR(190) NOT NULL,
    email_lower       VARCHAR(190) NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,
    role              VARCHAR(16)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    member_code       VARCHAR(24)  NOT NULL DEFAULT 'FREE',
    member_expires_at TIMESTAMP,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    last_login_at     TIMESTAMP,
    CONSTRAINT uk_user_username UNIQUE (username_lower),
    CONSTRAINT uk_user_email UNIQUE (email_lower)
);
COMMENT ON TABLE app_user IS '平台用户，含角色、邮箱验证、会员等级';
COMMENT ON COLUMN app_user.role IS 'USER / ADMIN';
COMMENT ON COLUMN app_user.status IS 'ACTIVE / DISABLED';
COMMENT ON COLUMN app_user.member_code IS '当前会员档位：FREE / PRO / STUDIO';

CREATE TABLE IF NOT EXISTS auth_token (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    purpose    VARCHAR(32) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP,
    CONSTRAINT uk_auth_token UNIQUE (token_hash)
);
COMMENT ON TABLE auth_token IS '邮箱验证与找回密码 Token，只保存哈希';
COMMENT ON COLUMN auth_token.purpose IS 'VERIFY_EMAIL / RESET_PASSWORD';
CREATE INDEX idx_auth_token_user ON auth_token (user_id);

CREATE TABLE IF NOT EXISTS mcp_token (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    name         VARCHAR(64)  NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    token_prefix VARCHAR(16)  NOT NULL,
    expires_at   TIMESTAMP,
    revoked_at   TIMESTAMP,
    last_used_at TIMESTAMP,
    created_at   TIMESTAMP,
    CONSTRAINT uk_mcp_token UNIQUE (token_hash)
);
COMMENT ON TABLE mcp_token IS '用户级 MCP Token，明文仅创建时返回一次';
CREATE INDEX idx_mcp_token_user ON mcp_token (user_id);

CREATE TABLE IF NOT EXISTS member_plan (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(24)  NOT NULL,
    name            VARCHAR(64)  NOT NULL,
    description     VARCHAR(255),
    price_cents     INT          NOT NULL DEFAULT 0,
    duration_days   INT          NOT NULL DEFAULT 30,
    daily_credits   INT          NOT NULL DEFAULT 0,
    max_concurrent  INT          NOT NULL DEFAULT 1,
    max_resolution  VARCHAR(16)  NOT NULL DEFAULT '720P',
    dispatch_weight INT          NOT NULL DEFAULT 1,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INT          NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP,
    CONSTRAINT uk_member_plan_code UNIQUE (code)
);
COMMENT ON TABLE member_plan IS '会员档位与权益，首次启动播种，后台可修改';

CREATE TABLE IF NOT EXISTS credit_package (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(32) NOT NULL,
    name        VARCHAR(64) NOT NULL,
    credits     INT         NOT NULL,
    price_cents INT         NOT NULL,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order  INT         NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP,
    CONSTRAINT uk_credit_package_code UNIQUE (code)
);
COMMENT ON TABLE credit_package IS '积分充值商品';

CREATE TABLE IF NOT EXISTS wallet (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT  NOT NULL,
    available_credits INT     NOT NULL DEFAULT 0,
    frozen_credits    INT     NOT NULL DEFAULT 0,
    version           INT     NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP,
    CONSTRAINT uk_wallet_user UNIQUE (user_id)
);
COMMENT ON TABLE wallet IS '积分钱包；可用与冻结分列，更新使用原子条件';

CREATE TABLE IF NOT EXISTS credit_ledger (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    type            VARCHAR(24) NOT NULL,
    biz_id          VARCHAR(64) NOT NULL,
    amount          INT         NOT NULL,
    available_after INT,
    frozen_after    INT,
    status          VARCHAR(16) NOT NULL,
    note            VARCHAR(255),
    created_at      TIMESTAMP,
    CONSTRAINT uk_ledger_biz UNIQUE (user_id, type, biz_id)
);
COMMENT ON TABLE credit_ledger IS '不可变积分流水，唯一键保证一次业务只记一次';
COMMENT ON COLUMN credit_ledger.type IS 'RESERVE / CAPTURE / RELEASE / RECHARGE / MEMBER_GRANT / ADJUST / INSUFFICIENT';
CREATE INDEX idx_ledger_user ON credit_ledger (user_id, id);

CREATE TABLE IF NOT EXISTS usage_counter (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    period_type VARCHAR(8)  NOT NULL,
    period_key  VARCHAR(16) NOT NULL,
    calls       INT         NOT NULL DEFAULT 0,
    credits     INT         NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP,
    CONSTRAINT uk_usage_period UNIQUE (user_id, period_type, period_key)
);
COMMENT ON TABLE usage_counter IS '按日/小时的调用与积分用量';

CREATE TABLE IF NOT EXISTS payment_order (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no             VARCHAR(40)  NOT NULL,
    user_id              BIGINT       NOT NULL,
    product_type         VARCHAR(16)  NOT NULL,
    product_code         VARCHAR(32)  NOT NULL,
    product_snapshot     CLOB,
    amount_cents         INT          NOT NULL,
    credits              INT,
    plan_code            VARCHAR(24),
    status               VARCHAR(16)  NOT NULL,
    channel              VARCHAR(16)  NOT NULL,
    third_party_trade_no VARCHAR(64),
    expires_at           TIMESTAMP,
    paid_at              TIMESTAMP,
    created_at           TIMESTAMP,
    updated_at           TIMESTAMP,
    version              INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_order_no UNIQUE (order_no)
);
COMMENT ON TABLE payment_order IS '积分/会员订单；状态 PENDING / PAYING / PAID / CLOSED';
CREATE INDEX idx_order_user ON payment_order (user_id, id);

CREATE TABLE IF NOT EXISTS payment_notify_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(40),
    channel         VARCHAR(16),
    signature_valid BOOLEAN,
    result          VARCHAR(32),
    payload_hash    VARCHAR(64),
    created_at      TIMESTAMP
);
COMMENT ON TABLE payment_notify_log IS '支付回调验签与处理结果，不记录密钥';

CREATE TABLE IF NOT EXISTS audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT,
    action      VARCHAR(48) NOT NULL,
    target_type VARCHAR(32),
    target_id   VARCHAR(64),
    ip          VARCHAR(64),
    detail      VARCHAR(512),
    created_at  TIMESTAMP
);
COMMENT ON TABLE audit_log IS '用户与管理操作审计，不记录敏感凭据';
CREATE INDEX idx_audit_user ON audit_log (user_id, id);

CREATE TABLE IF NOT EXISTS event_outbox (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL,
    aggregate_type  VARCHAR(32)  NOT NULL,
    aggregate_id    BIGINT       NOT NULL,
    user_id         BIGINT,
    event_type      VARCHAR(48)  NOT NULL,
    payload         CLOB,
    status          VARCHAR(16)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    last_error      VARCHAR(512),
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    CONSTRAINT uk_outbox_event UNIQUE (event_id)
);
COMMENT ON TABLE event_outbox IS '事务 Outbox：任务与计费在同一事务登记事件';
CREATE INDEX idx_outbox_status ON event_outbox (status, next_attempt_at);

CREATE TABLE IF NOT EXISTS event_inbox (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id     VARCHAR(64) NOT NULL,
    consumer     VARCHAR(48) NOT NULL,
    processed_at TIMESTAMP,
    CONSTRAINT uk_inbox_event UNIQUE (event_id, consumer)
);
COMMENT ON TABLE event_inbox IS '消费幂等表';

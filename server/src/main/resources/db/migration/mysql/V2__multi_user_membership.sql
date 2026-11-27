-- V2 多用户会员制（MySQL 5.7 兼容）。
ALTER TABLE workspace ADD COLUMN user_id BIGINT NULL COMMENT '所属用户 ID';
ALTER TABLE canvas ADD COLUMN user_id BIGINT NULL COMMENT '所属用户 ID';
ALTER TABLE generation_task ADD COLUMN user_id BIGINT NULL COMMENT '所属用户 ID';
ALTER TABLE prompt_record ADD COLUMN user_id BIGINT NULL COMMENT '所属用户 ID';
ALTER TABLE canvas_image ADD COLUMN user_id BIGINT NULL COMMENT '所属用户 ID';
ALTER TABLE canvas_node ADD COLUMN user_id BIGINT NULL COMMENT '所属用户 ID';
ALTER TABLE task_image_ref ADD COLUMN user_id BIGINT NULL COMMENT '所属用户 ID';

ALTER TABLE generation_task ADD COLUMN billing_op_id VARCHAR(64) NULL COMMENT '计费操作 ID';
ALTER TABLE generation_task ADD COLUMN credit_cost INT NULL COMMENT '本次生成消耗积分';

ALTER TABLE generation_task DROP INDEX uk_task_idem,
    ADD UNIQUE KEY uk_task_user_idem (user_id, idem_key),
    ADD KEY idx_task_user (user_id);
ALTER TABLE workspace ADD KEY idx_workspace_user (user_id);
ALTER TABLE canvas_image ADD KEY idx_image_user (user_id);
ALTER TABLE canvas_node ADD KEY idx_node_user (user_id);

CREATE TABLE IF NOT EXISTS app_user (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    username          VARCHAR(64)  NOT NULL,
    username_lower    VARCHAR(64)  NOT NULL,
    email             VARCHAR(190) NOT NULL,
    email_lower       VARCHAR(190) NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,
    role              VARCHAR(16)  NOT NULL COMMENT 'USER / ADMIN',
    status            VARCHAR(16)  NOT NULL COMMENT 'ACTIVE / DISABLED',
    email_verified    TINYINT(1)   NOT NULL DEFAULT 0,
    member_code       VARCHAR(24)  NOT NULL DEFAULT 'FREE',
    member_expires_at DATETIME     NULL,
    created_at        DATETIME     NULL,
    updated_at        DATETIME     NULL,
    last_login_at     DATETIME     NULL,
    UNIQUE KEY uk_user_username (username_lower),
    UNIQUE KEY uk_user_email (email_lower)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '平台用户';

CREATE TABLE IF NOT EXISTS auth_token (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    purpose    VARCHAR(32) NOT NULL COMMENT 'VERIFY_EMAIL / RESET_PASSWORD',
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME    NOT NULL,
    used_at    DATETIME    NULL,
    created_at DATETIME    NULL,
    UNIQUE KEY uk_auth_token (token_hash),
    KEY idx_auth_token_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '邮箱验证/找回密码 Token';

CREATE TABLE IF NOT EXISTS mcp_token (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    name         VARCHAR(64)  NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    token_prefix VARCHAR(16)  NOT NULL,
    expires_at   DATETIME     NULL,
    revoked_at   DATETIME     NULL,
    last_used_at DATETIME     NULL,
    created_at   DATETIME     NULL,
    UNIQUE KEY uk_mcp_token (token_hash),
    KEY idx_mcp_token_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户级 MCP Token';

CREATE TABLE IF NOT EXISTS member_plan (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(24)  NOT NULL,
    name            VARCHAR(64)  NOT NULL,
    description     VARCHAR(255) NULL,
    price_cents     INT          NOT NULL DEFAULT 0,
    duration_days   INT          NOT NULL DEFAULT 30,
    daily_credits   INT          NOT NULL DEFAULT 0,
    max_concurrent  INT          NOT NULL DEFAULT 1,
    max_resolution  VARCHAR(16)  NOT NULL DEFAULT '720P',
    dispatch_weight INT          NOT NULL DEFAULT 1,
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    sort_order      INT          NOT NULL DEFAULT 0,
    updated_at      DATETIME     NULL,
    UNIQUE KEY uk_member_plan_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员档位与权益';

CREATE TABLE IF NOT EXISTS credit_package (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(32) NOT NULL,
    name        VARCHAR(64) NOT NULL,
    credits     INT         NOT NULL,
    price_cents INT         NOT NULL,
    enabled     TINYINT(1)  NOT NULL DEFAULT 1,
    sort_order  INT         NOT NULL DEFAULT 0,
    updated_at  DATETIME    NULL,
    UNIQUE KEY uk_credit_package_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分充值商品';

CREATE TABLE IF NOT EXISTS wallet (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT    NOT NULL,
    available_credits INT       NOT NULL DEFAULT 0,
    frozen_credits    INT       NOT NULL DEFAULT 0,
    version           INT       NOT NULL DEFAULT 0,
    updated_at        DATETIME  NULL,
    UNIQUE KEY uk_wallet_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分钱包';

CREATE TABLE IF NOT EXISTS credit_ledger (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    type            VARCHAR(24) NOT NULL COMMENT 'RESERVE/CAPTURE/RELEASE/RECHARGE/MEMBER_GRANT/ADJUST/INSUFFICIENT',
    biz_id          VARCHAR(64) NOT NULL,
    amount          INT         NOT NULL,
    available_after INT         NULL,
    frozen_after    INT         NULL,
    status          VARCHAR(16) NOT NULL,
    note            VARCHAR(255) NULL,
    created_at      DATETIME    NULL,
    UNIQUE KEY uk_ledger_biz (user_id, type, biz_id),
    KEY idx_ledger_user (user_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '不可变积分流水';

CREATE TABLE IF NOT EXISTS usage_counter (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    period_type VARCHAR(8)  NOT NULL,
    period_key  VARCHAR(16) NOT NULL,
    calls       INT         NOT NULL DEFAULT 0,
    credits     INT         NOT NULL DEFAULT 0,
    updated_at  DATETIME    NULL,
    UNIQUE KEY uk_usage_period (user_id, period_type, period_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '日/小时用量';

CREATE TABLE IF NOT EXISTS payment_order (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no             VARCHAR(40)  NOT NULL,
    user_id              BIGINT       NOT NULL,
    product_type         VARCHAR(16)  NOT NULL COMMENT 'CREDITS / MEMBERSHIP',
    product_code         VARCHAR(32)  NOT NULL,
    product_snapshot     LONGTEXT     NULL,
    amount_cents         INT          NOT NULL,
    credits              INT          NULL,
    plan_code            VARCHAR(24)  NULL,
    status               VARCHAR(16)  NOT NULL COMMENT 'PENDING/PAYING/PAID/CLOSED',
    channel              VARCHAR(16)  NOT NULL,
    third_party_trade_no VARCHAR(64)  NULL,
    expires_at           DATETIME     NULL,
    paid_at              DATETIME     NULL,
    created_at           DATETIME     NULL,
    updated_at           DATETIME     NULL,
    version              INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_user (user_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分/会员订单';

CREATE TABLE IF NOT EXISTS payment_notify_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(40) NULL,
    channel         VARCHAR(16) NULL,
    signature_valid TINYINT(1)  NULL,
    result          VARCHAR(32) NULL,
    payload_hash    VARCHAR(64) NULL,
    created_at      DATETIME    NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '支付回调日志';

CREATE TABLE IF NOT EXISTS audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NULL,
    action      VARCHAR(48)  NOT NULL,
    target_type VARCHAR(32)  NULL,
    target_id   VARCHAR(64)  NULL,
    ip          VARCHAR(64)  NULL,
    detail      VARCHAR(512) NULL,
    created_at  DATETIME     NULL,
    KEY idx_audit_user (user_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审计日志';

CREATE TABLE IF NOT EXISTS event_outbox (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL,
    aggregate_type  VARCHAR(32)  NOT NULL,
    aggregate_id    BIGINT       NOT NULL,
    user_id         BIGINT       NULL,
    event_type      VARCHAR(48)  NOT NULL,
    payload         LONGTEXT     NULL,
    status          VARCHAR(16)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at DATETIME     NULL,
    last_error      VARCHAR(512) NULL,
    created_at      DATETIME     NULL,
    updated_at      DATETIME     NULL,
    UNIQUE KEY uk_outbox_event (event_id),
    KEY idx_outbox_status (status, next_attempt_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '事务 Outbox';

CREATE TABLE IF NOT EXISTS event_inbox (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id     VARCHAR(64) NOT NULL,
    consumer     VARCHAR(48) NOT NULL,
    processed_at DATETIME    NULL,
    UNIQUE KEY uk_inbox_event (event_id, consumer)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '消费幂等表';

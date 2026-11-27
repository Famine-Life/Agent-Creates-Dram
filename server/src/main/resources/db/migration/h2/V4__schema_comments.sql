-- V4 迁移仅补充表与字段的中文注释，不改变任何类型、可空性、默认值、索引或约束。

-- ===== 既有业务表：补充全部字段注释 =====

COMMENT ON TABLE workspace IS '工作空间，一张画布对应一条记录';
COMMENT ON COLUMN workspace.id IS '主键，自增';
COMMENT ON COLUMN workspace.name IS '工作空间名称，同时也是画布名称';
COMMENT ON COLUMN workspace.created_at IS '创建时间';
COMMENT ON COLUMN workspace.user_id IS '所属用户 ID，NULL 表示历史数据未归属';
COMMENT ON COLUMN workspace.project_id IS '所属项目 ID，NULL 表示未归入项目';

COMMENT ON TABLE canvas IS '画布快照，与 workspace 一一对应';
COMMENT ON COLUMN canvas.id IS '主键，自增';
COMMENT ON COLUMN canvas.workspace_id IS '所属工作空间 ID';
COMMENT ON COLUMN canvas.version IS '快照版本号，每次保存递增';
COMMENT ON COLUMN canvas.snapshot IS '画布快照 JSON';
COMMENT ON COLUMN canvas.updated_at IS '快照最后更新时间';
COMMENT ON COLUMN canvas.user_id IS '所属用户 ID，NULL 表示历史数据未归属';

COMMENT ON TABLE generation_task IS '镜头生成任务';
COMMENT ON COLUMN generation_task.id IS '主键，自增';
COMMENT ON COLUMN generation_task.workspace_id IS '所属工作空间 ID';
COMMENT ON COLUMN generation_task.canvas_id IS '所属画布 ID';
COMMENT ON COLUMN generation_task.group_id IS '镜头分组标识';
COMMENT ON COLUMN generation_task.shot_index IS '分组内镜头序号，从 1 开始';
COMMENT ON COLUMN generation_task.title IS '镜头标题';
COMMENT ON COLUMN generation_task.prompt IS '视频生成提示词';
COMMENT ON COLUMN generation_task.model IS '视频模型标识，平台/模型 ID';
COMMENT ON COLUMN generation_task.ratio IS '画幅比例，如 16:9';
COMMENT ON COLUMN generation_task.duration IS '时长，单位秒';
COMMENT ON COLUMN generation_task.resolution IS '清晰度，如 720P / 1080P';
COMMENT ON COLUMN generation_task.provider IS '供应商标识';
COMMENT ON COLUMN generation_task.provider_task_id IS '供应商侧任务 ID';
COMMENT ON COLUMN generation_task.status IS '任务状态：IDLE / RECEIVED / QUEUED / SUBMITTING / SUBMITTED / PROVIDER_QUEUED / RUNNING / SUBMIT_UNKNOWN / SUCCEEDED / FAILED / TIMEOUT / CANCELED / INSUFFICIENT_BALANCE / QUOTA_EXCEEDED';
COMMENT ON COLUMN generation_task.progress IS '生成进度百分比 0-100';
COMMENT ON COLUMN generation_task.video_url IS '成片地址';
COMMENT ON COLUMN generation_task.cover_url IS '封面地址';
COMMENT ON COLUMN generation_task.error_msg IS '失败原因';
COMMENT ON COLUMN generation_task.poll_count IS '已轮询次数';
COMMENT ON COLUMN generation_task.idem_key IS '幂等键，用于防止重复提交';
COMMENT ON COLUMN generation_task.next_poll_at IS '下次轮询时间';
COMMENT ON COLUMN generation_task.submitted_at IS '提交给供应商的时间';
COMMENT ON COLUMN generation_task.created_at IS '创建时间';
COMMENT ON COLUMN generation_task.updated_at IS '更新时间';
COMMENT ON COLUMN generation_task.user_id IS '所属用户 ID，NULL 表示历史数据未归属';
COMMENT ON COLUMN generation_task.billing_op_id IS '计费操作 ID，用于关联积分流水';
COMMENT ON COLUMN generation_task.credit_cost IS '本次生成消耗的积分数量';

COMMENT ON TABLE prompt_record IS '提示词提交历史';
COMMENT ON COLUMN prompt_record.id IS '主键，自增';
COMMENT ON COLUMN prompt_record.workspace_id IS '所属工作空间 ID';
COMMENT ON COLUMN prompt_record.task_id IS '关联的生成任务 ID';
COMMENT ON COLUMN prompt_record.prompt IS '提交的提示词内容';
COMMENT ON COLUMN prompt_record.created_at IS '提交时间';
COMMENT ON COLUMN prompt_record.user_id IS '所属用户 ID，NULL 表示历史数据未归属';

COMMENT ON TABLE canvas_image IS '画布图片节点';
COMMENT ON COLUMN canvas_image.id IS '主键，自增';
COMMENT ON COLUMN canvas_image.workspace_id IS '所属工作空间 ID';
COMMENT ON COLUMN canvas_image.canvas_id IS '所属画布 ID';
COMMENT ON COLUMN canvas_image.name IS '图片节点名称';
COMMENT ON COLUMN canvas_image.url IS '图片地址';
COMMENT ON COLUMN canvas_image.object_key IS '对象存储 key';
COMMENT ON COLUMN canvas_image.x IS '画布 X 坐标';
COMMENT ON COLUMN canvas_image.y IS '画布 Y 坐标';
COMMENT ON COLUMN canvas_image.width IS '节点宽度，默认 148';
COMMENT ON COLUMN canvas_image.height IS '节点高度，默认 166';
COMMENT ON COLUMN canvas_image.created_at IS '创建时间';
COMMENT ON COLUMN canvas_image.prompt IS '生成用提示词';
COMMENT ON COLUMN canvas_image.status IS '生成状态，取值同生成任务状态';
COMMENT ON COLUMN canvas_image.error_msg IS '失败原因';
COMMENT ON COLUMN canvas_image.provider IS '图片供应商标识';
COMMENT ON COLUMN canvas_image.source IS '来源：UPLOAD 上传 / AI 生成';
COMMENT ON COLUMN canvas_image.user_id IS '所属用户 ID，NULL 表示历史数据未归属';

COMMENT ON TABLE canvas_node IS '文本/脚本节点';
COMMENT ON COLUMN canvas_node.id IS '主键，自增';
COMMENT ON COLUMN canvas_node.workspace_id IS '所属工作空间 ID';
COMMENT ON COLUMN canvas_node.node_type IS '节点类型：TEXT 文本 / SCRIPT 脚本等';
COMMENT ON COLUMN canvas_node.title IS '节点标题';
COMMENT ON COLUMN canvas_node.prompt IS '节点提示词';
COMMENT ON COLUMN canvas_node.content IS '节点文本内容';
COMMENT ON COLUMN canvas_node.status IS '节点生成状态';
COMMENT ON COLUMN canvas_node.error_msg IS '失败原因';
COMMENT ON COLUMN canvas_node.x IS '画布 X 坐标';
COMMENT ON COLUMN canvas_node.y IS '画布 Y 坐标';
COMMENT ON COLUMN canvas_node.width IS '节点宽度';
COMMENT ON COLUMN canvas_node.height IS '节点高度';
COMMENT ON COLUMN canvas_node.created_at IS '创建时间';
COMMENT ON COLUMN canvas_node.updated_at IS '更新时间';
COMMENT ON COLUMN canvas_node.user_id IS '所属用户 ID，NULL 表示历史数据未归属';

COMMENT ON TABLE task_image_ref IS '图片到镜头的引用连线';
COMMENT ON COLUMN task_image_ref.id IS '主键，自增';
COMMENT ON COLUMN task_image_ref.task_id IS '镜头生成任务 ID';
COMMENT ON COLUMN task_image_ref.image_id IS '图片节点 ID';
COMMENT ON COLUMN task_image_ref.sort_index IS '引用顺序，从 0 开始';
COMMENT ON COLUMN task_image_ref.created_at IS '创建时间';
COMMENT ON COLUMN task_image_ref.user_id IS '所属用户 ID，NULL 表示历史数据未归属';

-- ===== 多用户会员制新增表 =====

COMMENT ON TABLE app_user IS '平台用户，含角色、邮箱验证、会员等级';
COMMENT ON COLUMN app_user.id IS '主键，自增';
COMMENT ON COLUMN app_user.username IS '用户名，保留用户输入的原始大小写';
COMMENT ON COLUMN app_user.username_lower IS '用户名小写形式，用于唯一约束与登录匹配';
COMMENT ON COLUMN app_user.email IS '邮箱，保留用户输入的原始大小写';
COMMENT ON COLUMN app_user.email_lower IS '邮箱小写形式，用于唯一约束与登录匹配';
COMMENT ON COLUMN app_user.password_hash IS '密码哈希，不保存明文';
COMMENT ON COLUMN app_user.role IS '角色：USER 普通用户 / ADMIN 管理员';
COMMENT ON COLUMN app_user.status IS '状态：ACTIVE 正常 / DISABLED 禁用';
COMMENT ON COLUMN app_user.email_verified IS '邮箱是否已验证：FALSE 未验证 / TRUE 已验证';
COMMENT ON COLUMN app_user.member_code IS '当前会员档位：FREE / PRO / STUDIO';
COMMENT ON COLUMN app_user.member_expires_at IS '会员到期时间，NULL 表示无期限或非会员';
COMMENT ON COLUMN app_user.created_at IS '创建时间';
COMMENT ON COLUMN app_user.updated_at IS '更新时间';
COMMENT ON COLUMN app_user.last_login_at IS '最后登录时间';

COMMENT ON TABLE auth_token IS '邮箱验证与找回密码 Token，只保存哈希';
COMMENT ON COLUMN auth_token.id IS '主键，自增';
COMMENT ON COLUMN auth_token.user_id IS '所属用户 ID';
COMMENT ON COLUMN auth_token.purpose IS '用途：VERIFY_EMAIL 邮箱验证 / RESET_PASSWORD 找回密码';
COMMENT ON COLUMN auth_token.token_hash IS 'Token 的哈希值，明文不落库';
COMMENT ON COLUMN auth_token.expires_at IS '过期时间';
COMMENT ON COLUMN auth_token.used_at IS '使用时间，NULL 表示尚未使用';
COMMENT ON COLUMN auth_token.created_at IS '创建时间';

COMMENT ON TABLE mcp_token IS '用户级 MCP Token，明文仅创建时返回一次';
COMMENT ON COLUMN mcp_token.id IS '主键，自增';
COMMENT ON COLUMN mcp_token.user_id IS '所属用户 ID';
COMMENT ON COLUMN mcp_token.name IS 'Token 名称，便于用户区分';
COMMENT ON COLUMN mcp_token.token_hash IS 'Token 的哈希值，明文不落库';
COMMENT ON COLUMN mcp_token.token_prefix IS 'Token 前缀，用于列表展示';
COMMENT ON COLUMN mcp_token.expires_at IS '过期时间，NULL 表示不过期';
COMMENT ON COLUMN mcp_token.revoked_at IS '吊销时间，NULL 表示未吊销';
COMMENT ON COLUMN mcp_token.last_used_at IS '最后使用时间';
COMMENT ON COLUMN mcp_token.created_at IS '创建时间';

COMMENT ON TABLE member_plan IS '会员档位与权益，首次启动播种，后台可修改';
COMMENT ON COLUMN member_plan.id IS '主键，自增';
COMMENT ON COLUMN member_plan.code IS '档位编码：FREE / PRO / STUDIO';
COMMENT ON COLUMN member_plan.name IS '档位名称';
COMMENT ON COLUMN member_plan.description IS '档位说明';
COMMENT ON COLUMN member_plan.price_cents IS '订阅价格，单位分';
COMMENT ON COLUMN member_plan.duration_days IS '会员有效天数';
COMMENT ON COLUMN member_plan.daily_credits IS '每日赠送积分';
COMMENT ON COLUMN member_plan.max_concurrent IS '最大并发生成数';
COMMENT ON COLUMN member_plan.max_resolution IS '允许的最高清晰度，如 720P / 1080P';
COMMENT ON COLUMN member_plan.dispatch_weight IS '调度权重，值越大越优先';
COMMENT ON COLUMN member_plan.enabled IS '是否启用：FALSE 停用 / TRUE 启用';
COMMENT ON COLUMN member_plan.sort_order IS '排序值，越小越靠前';
COMMENT ON COLUMN member_plan.updated_at IS '更新时间';

COMMENT ON TABLE credit_package IS '积分充值商品';
COMMENT ON COLUMN credit_package.id IS '主键，自增';
COMMENT ON COLUMN credit_package.code IS '商品编码';
COMMENT ON COLUMN credit_package.name IS '商品名称';
COMMENT ON COLUMN credit_package.credits IS '包含的积分数量';
COMMENT ON COLUMN credit_package.price_cents IS '售价，单位分';
COMMENT ON COLUMN credit_package.enabled IS '是否启用：FALSE 停用 / TRUE 启用';
COMMENT ON COLUMN credit_package.sort_order IS '排序值，越小越靠前';
COMMENT ON COLUMN credit_package.updated_at IS '更新时间';

COMMENT ON TABLE wallet IS '积分钱包；可用与冻结分列，更新使用原子条件';
COMMENT ON COLUMN wallet.id IS '主键，自增';
COMMENT ON COLUMN wallet.user_id IS '所属用户 ID，一个用户一条钱包记录';
COMMENT ON COLUMN wallet.available_credits IS '可用积分余额';
COMMENT ON COLUMN wallet.frozen_credits IS '冻结积分，生成中预扣';
COMMENT ON COLUMN wallet.version IS '乐观锁版本号';
COMMENT ON COLUMN wallet.updated_at IS '更新时间';

COMMENT ON TABLE credit_ledger IS '不可变积分流水，唯一键保证一次业务只记一次';
COMMENT ON COLUMN credit_ledger.id IS '主键，自增';
COMMENT ON COLUMN credit_ledger.user_id IS '所属用户 ID';
COMMENT ON COLUMN credit_ledger.type IS '流水类型：RESERVE 预扣 / CAPTURE 扣减 / RELEASE 释放 / RECHARGE 充值 / MEMBER_GRANT 会员赠送 / ADJUST 调整 / INSUFFICIENT 余额不足';
COMMENT ON COLUMN credit_ledger.biz_id IS '业务单据 ID，与 user_id、type 组合唯一';
COMMENT ON COLUMN credit_ledger.amount IS '变动积分数量，正数增加、负数减少';
COMMENT ON COLUMN credit_ledger.available_after IS '变动后的可用积分';
COMMENT ON COLUMN credit_ledger.frozen_after IS '变动后的冻结积分';
COMMENT ON COLUMN credit_ledger.status IS '流水状态：FROZEN 冻结中 / SETTLED 已结算 / FAILED 失败';
COMMENT ON COLUMN credit_ledger.note IS '备注';
COMMENT ON COLUMN credit_ledger.created_at IS '创建时间';

COMMENT ON TABLE usage_counter IS '按日/小时的调用与积分用量';
COMMENT ON COLUMN usage_counter.id IS '主键，自增';
COMMENT ON COLUMN usage_counter.user_id IS '所属用户 ID';
COMMENT ON COLUMN usage_counter.period_type IS '统计周期类型：DAY 按日 / HOUR 按小时';
COMMENT ON COLUMN usage_counter.period_key IS '周期标识，如 2026-01-01 或 2026-01-01T10';
COMMENT ON COLUMN usage_counter.calls IS '周期内调用次数';
COMMENT ON COLUMN usage_counter.credits IS '周期内消耗积分';
COMMENT ON COLUMN usage_counter.updated_at IS '更新时间';

COMMENT ON TABLE payment_order IS '积分/会员订单；状态 PENDING / PAYING / PAID / CLOSED';
COMMENT ON COLUMN payment_order.id IS '主键，自增';
COMMENT ON COLUMN payment_order.order_no IS '订单号，业务唯一';
COMMENT ON COLUMN payment_order.user_id IS '下单用户 ID';
COMMENT ON COLUMN payment_order.product_type IS '商品类型：CREDITS 积分包 / MEMBERSHIP 会员';
COMMENT ON COLUMN payment_order.product_code IS '商品编码，对应积分包或会员档位';
COMMENT ON COLUMN payment_order.product_snapshot IS '下单时的商品快照 JSON';
COMMENT ON COLUMN payment_order.amount_cents IS '订单金额，单位分';
COMMENT ON COLUMN payment_order.credits IS '购买获得的积分数量，会员订单可为空';
COMMENT ON COLUMN payment_order.plan_code IS '会员档位编码，积分订单为空';
COMMENT ON COLUMN payment_order.status IS '订单状态：PENDING 待支付 / PAYING 支付中 / PAID 已支付 / CLOSED 已关闭';
COMMENT ON COLUMN payment_order.channel IS '支付渠道标识，如 wechat / alipay';
COMMENT ON COLUMN payment_order.third_party_trade_no IS '第三方支付交易号';
COMMENT ON COLUMN payment_order.expires_at IS '订单过期时间';
COMMENT ON COLUMN payment_order.paid_at IS '支付完成时间';
COMMENT ON COLUMN payment_order.created_at IS '创建时间';
COMMENT ON COLUMN payment_order.updated_at IS '更新时间';
COMMENT ON COLUMN payment_order.version IS '乐观锁版本号';

COMMENT ON TABLE payment_notify_log IS '支付回调验签与处理结果，不记录密钥';
COMMENT ON COLUMN payment_notify_log.id IS '主键，自增';
COMMENT ON COLUMN payment_notify_log.order_no IS '订单号，无法解析时可为空';
COMMENT ON COLUMN payment_notify_log.channel IS '支付渠道标识，如 wechat / alipay';
COMMENT ON COLUMN payment_notify_log.signature_valid IS '回调验签是否通过：FALSE 失败 / TRUE 通过';
COMMENT ON COLUMN payment_notify_log.result IS '处理结果，如 SUCCESS / DUPLICATE / AMOUNT_MISMATCH / INVALID 等';
COMMENT ON COLUMN payment_notify_log.payload_hash IS '回调报文哈希，用于去重';
COMMENT ON COLUMN payment_notify_log.created_at IS '创建时间';

COMMENT ON TABLE audit_log IS '用户与管理操作审计，不记录敏感凭据';
COMMENT ON COLUMN audit_log.id IS '主键，自增';
COMMENT ON COLUMN audit_log.user_id IS '操作人用户 ID，系统操作可为空';
COMMENT ON COLUMN audit_log.action IS '操作类型，如 LOGIN / ORDER_PAID / ADMIN_OUTBOX_RETRY';
COMMENT ON COLUMN audit_log.target_type IS '目标对象类型，如 payment_order / event_outbox';
COMMENT ON COLUMN audit_log.target_id IS '目标对象 ID';
COMMENT ON COLUMN audit_log.ip IS '客户端 IP';
COMMENT ON COLUMN audit_log.detail IS '操作详情，不含敏感凭据';
COMMENT ON COLUMN audit_log.created_at IS '创建时间';

COMMENT ON TABLE event_outbox IS '事务 Outbox：任务与计费在同一事务登记事件';
COMMENT ON COLUMN event_outbox.id IS '主键，自增';
COMMENT ON COLUMN event_outbox.event_id IS '事件 ID，全局唯一';
COMMENT ON COLUMN event_outbox.aggregate_type IS '聚合类型，如 TASK / ORDER';
COMMENT ON COLUMN event_outbox.aggregate_id IS '聚合根 ID';
COMMENT ON COLUMN event_outbox.user_id IS '事件所属用户 ID';
COMMENT ON COLUMN event_outbox.event_type IS '事件类型';
COMMENT ON COLUMN event_outbox.payload IS '事件载荷 JSON';
COMMENT ON COLUMN event_outbox.status IS '投递状态：PENDING 待投递 / PROCESSING 投递中 / PROCESSED 已投递 / DEAD 死信';
COMMENT ON COLUMN event_outbox.attempts IS '已尝试投递次数';
COMMENT ON COLUMN event_outbox.next_attempt_at IS '下次重试时间';
COMMENT ON COLUMN event_outbox.last_error IS '最后一次投递错误信息';
COMMENT ON COLUMN event_outbox.created_at IS '创建时间';
COMMENT ON COLUMN event_outbox.updated_at IS '更新时间';

COMMENT ON TABLE event_inbox IS '消费幂等表';
COMMENT ON COLUMN event_inbox.id IS '主键，自增';
COMMENT ON COLUMN event_inbox.event_id IS '已消费的事件 ID';
COMMENT ON COLUMN event_inbox.consumer IS '消费者标识';
COMMENT ON COLUMN event_inbox.processed_at IS '处理完成时间';

COMMENT ON TABLE project IS '用户项目，一个项目下可包含多张画布';
COMMENT ON COLUMN project.id IS '主键，自增';
COMMENT ON COLUMN project.user_id IS '所属用户 ID';
COMMENT ON COLUMN project.name IS '项目名称';
COMMENT ON COLUMN project.created_at IS '创建时间';
COMMENT ON COLUMN project.updated_at IS '更新时间';

-- 书源表（全库唯一一张业务表）
CREATE TABLE IF NOT EXISTS book_source (
    id          INTEGER PRIMARY KEY,   -- 雪花算法生成的 ID
    name        TEXT NOT NULL,        -- 书源名称
    base_url    TEXT NOT NULL,        -- 站点地址（mock://local 表示内置测试源）
    enabled     INTEGER DEFAULT 1,    -- 是否启用 1/0
    is_default  INTEGER DEFAULT 0,    -- 是否默认源 1/0
    remark      TEXT,                 -- 备注
    rules       TEXT,                 -- 解析规则 JSON（格式见 docs/02 §5）
    create_time TEXT,
    update_time TEXT
);

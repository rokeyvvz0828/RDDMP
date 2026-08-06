-- 用户联系方式改为手机号，保留原邮箱列中的历史值。
ALTER TABLE sys_user
    CHANGE COLUMN email mobile_phone VARCHAR(32) NULL DEFAULT NULL COMMENT '手机号';

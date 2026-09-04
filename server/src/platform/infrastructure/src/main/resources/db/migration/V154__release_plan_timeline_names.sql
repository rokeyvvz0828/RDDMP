-- REQ-20260904-061：将方案内正向/回退时序名称持久化，保留既有方案指令数据。
ALTER TABLE rel_release_plan
    ADD COLUMN normal_timeline_name VARCHAR(128) NOT NULL DEFAULT '正向投产时序' AFTER status,
    ADD COLUMN rollback_timeline_name VARCHAR(128) NOT NULL DEFAULT '回退时序' AFTER normal_timeline_name;

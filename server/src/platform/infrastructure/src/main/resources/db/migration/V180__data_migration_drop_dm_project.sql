-- 数据迁移模块最终态收敛：dm_project 已完全废弃（历史遗留表，项目主数据统一使用平台 pm_project）。
-- 追加式迁移，DROP TABLE IF EXISTS 天然幂等；不影响平台表、菜单与既有服务契约。
DROP TABLE IF EXISTS dm_project;

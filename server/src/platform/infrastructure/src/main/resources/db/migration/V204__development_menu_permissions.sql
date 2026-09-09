-- 仅超级管理员初始获得开发模块权限，系统负责人仍需显式角色授权。
INSERT INTO sys_menu (id,tenant_id,parent_id,menu_type,menu_name,route_name,route_path,component_path,permission_code,icon,sort_no)
VALUES (910710,1,0,'directory','开发管理','DevelopmentRoot','/development','LAYOUT','development:task:read','tickets',75),
       (910711,1,910710,'menu','开发任务','DevelopmentTasks','/development/tasks','development/tasks','development:task:read','list',10);

INSERT INTO sys_menu_permission (id,tenant_id,menu_id,action_code,permission_code,permission_name)
VALUES (9107111,1,910711,'read','development:task:read','查看开发任务'),
       (9107112,1,910711,'create','development:task:create','承接及新建任务'),
       (9107113,1,910711,'update','development:task:update','维护任务和阶段'),
       (9107114,1,910711,'work-item-update','development:work-item:update','维护及执行工作项'),
       (9107115,1,910711,'work-item-accept','development:work-item:accept','验收工作项'),
       (9107116,1,910711,'complete','development:task:complete','确认任务完成'),
       (9107117,1,910711,'admin','development:admin','授权范围内开发管理');

INSERT IGNORE INTO sys_role_menu (role_id,menu_id,tenant_id)
SELECT r.id,m.id,r.tenant_id FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
WHERE r.tenant_id=1 AND r.role_code='SUPER_ADMIN' AND r.deleted=0 AND r.status=1 AND m.id IN (910710,910711);
INSERT IGNORE INTO sys_role_permission (role_id,permission_id,tenant_id)
SELECT r.id,p.id,r.tenant_id FROM sys_role r JOIN sys_menu_permission p ON p.tenant_id=r.tenant_id
WHERE r.tenant_id=1 AND r.role_code='SUPER_ADMIN' AND r.deleted=0 AND r.status=1 AND p.menu_id=910711;

INSERT INTO sys_dict_type (id,tenant_id,dict_code,dict_name,status,deleted)
VALUES (910712,1,'DEVELOPMENT_SYSTEM_MAPPING','开发需求系统映射',1,0),
       (910713,1,'DEVELOPMENT_NUMBERING','开发任务编号',1,0),
       (910714,1,'DEVELOPMENT_CALENDAR','开发工作日历',1,0);
INSERT INTO sys_config (id,tenant_id,category_id,config_key,config_value,config_type,remark,status,deleted)
VALUES (9107131,1,910713,'LINKED','RW_{requirementNo}_{seq}','string','关联任务编号模板；仅作用于新任务',1,0),
       (9107132,1,910713,'STANDALONE','RW_ZZ_{date}_{seq}','string','自主任务编号模板；仅作用于新任务',1,0),
       (9107141,1,910714,'DEFAULT','{"weekdays":[1,2,3,4,5],"workingDates":[],"restDates":[]}','string','基础工作周；未包含官方节假日，覆盖日期由管理员维护',1,0);

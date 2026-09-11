-- REQ068: local-only, insert-only synthetic drill fixtures. Never use mysql --force.
SET NAMES utf8mb4;
SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;
CREATE TEMPORARY TABLE fixture_guard (ok INT NOT NULL CHECK (ok=1));
INSERT INTO fixture_guard SELECT DATABASE()='ccb_platform_licon';
START TRANSACTION;
INSERT INTO fixture_guard SELECT COUNT(*)=2 FROM pm_project WHERE tenant_id=1 AND deleted=0 AND ((id=940001 AND project_name='投产演练示范项目 A') OR (id=940002 AND project_name='投产演练示范项目 B'));
SELECT id FROM pm_project WHERE tenant_id=1 AND id IN (940001,940002) FOR UPDATE;
INSERT INTO fixture_guard SELECT COUNT(*)=2 FROM rel_release_plan p JOIN rel_release_drill_environment e ON e.tenant_id=p.tenant_id AND e.project_id=p.project_id WHERE p.tenant_id=1 AND p.deleted=0 AND e.deleted=0 AND ((p.project_id=940001 AND p.id=100942001 AND e.id=200942001) OR (p.project_id=940002 AND p.id=100943001 AND e.id=200943001));
SET @base_A=(SELECT COALESCE(MAX(round_no),0) FROM rel_release_drill_round WHERE tenant_id=1 AND project_id=940001 AND deleted=0 AND id NOT IN (9400680100,9400680200,9400680300));
SET @base_B=(SELECT COALESCE(MAX(round_no),0) FROM rel_release_drill_round WHERE tenant_id=1 AND project_id=940002 AND deleted=0 AND id NOT IN (9400681100,9400681200,9400681300));
CREATE TEMPORARY TABLE expected_round LIKE rel_release_drill_round;
CREATE TEMPORARY TABLE expected_step LIKE rel_release_drill_step;
INSERT INTO expected_round (id,tenant_id,project_id,drill_plan_id,release_plan_id,environment_id,round_no,round_name,planned_at,status,result_content,row_version,created_by,updated_by) VALUES
(9400680100,1,940001,NULL,100942001,200942001,COALESCE((SELECT round_no FROM rel_release_drill_round WHERE id=9400680100),@base_A+1),'[REQ068] A - 全流程演练（已完成）','2026-09-05 09:00:00','COMPLETED','[REQ068] 虚构演练已完成，前五项模拟验证通过，未触发实际回退。',0,0,0),
(9400680200,1,940001,NULL,100942001,200942001,COALESCE((SELECT round_no FROM rel_release_drill_round WHERE id=9400680200),@base_A+2),'[REQ068] A - 联调演练（进行中）','2026-09-07 09:00:00','RUNNING','[REQ068] 已完成环境检查及基线备份，正在核对模拟数据同步结果。',0,0,0),
(9400680300,1,940001,NULL,100942001,200942001,COALESCE((SELECT round_no FROM rel_release_drill_round WHERE id=9400680300),@base_A+3),'[REQ068] A - 投产预演（待演练）','2026-09-10 09:00:00','PLANNED',NULL,0,0,0),
(9400681100,1,940002,NULL,100943001,200943001,COALESCE((SELECT round_no FROM rel_release_drill_round WHERE id=9400681100),@base_B+1),'[REQ068] B - 全流程演练（已完成）','2026-09-06 09:00:00','COMPLETED','[REQ068] 虚构演练已完成，前五项模拟验证通过，未触发实际回退。',0,0,0),
(9400681200,1,940002,NULL,100943001,200943001,COALESCE((SELECT round_no FROM rel_release_drill_round WHERE id=9400681200),@base_B+2),'[REQ068] B - 联调演练（进行中）','2026-09-08 09:00:00','RUNNING','[REQ068] 已完成环境检查及基线备份，正在核对模拟数据同步结果。',0,0,0),
(9400681300,1,940002,NULL,100943001,200943001,COALESCE((SELECT round_no FROM rel_release_drill_round WHERE id=9400681300),@base_B+3),'[REQ068] B - 投产预演（待演练）','2026-09-11 09:00:00','PLANNED',NULL,0,0,0);
INSERT INTO expected_step (id,tenant_id,project_id,drill_round_id,seq_no,step_name,owner_id,owner_name,planned_start,planned_end,status,result_content,description,row_version,created_by,updated_by) VALUES
(9400680101,1,940001,9400680100,1,'环境与权限检查',NULL,NULL,'2026-09-05 09:00:00','2026-09-05 09:30:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 核对虚构演练环境的硬件、网络、中间件、组件及数据库检查项。',0,0,0),
(9400680102,1,940001,9400680100,2,'应用及数据基线备份',NULL,NULL,'2026-09-05 09:35:00','2026-09-05 10:05:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 记录模拟备份批次并检查备份完整性；不执行真实备份命令。',0,0,0),
(9400680103,1,940001,9400680100,3,'数据同步与一致性核对',NULL,NULL,'2026-09-05 10:10:00','2026-09-05 10:40:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 使用虚构批次数量核对记录总数和校验结果；不访问生产数据。',0,0,0),
(9400680104,1,940001,9400680100,4,'应用切换与服务启停',NULL,NULL,'2026-09-05 10:45:00','2026-09-05 11:15:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 记录模拟切换顺序、服务状态与操作结果；不执行实际服务命令。',0,0,0),
(9400680105,1,940001,9400680100,5,'关键业务及监控验证',NULL,NULL,'2026-09-05 11:20:00','2026-09-05 11:50:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 核对模拟关键业务交易、告警及监控指标并记录结果。',0,0,0),
(9400680106,1,940001,9400680100,6,'回退检查与演练收尾',NULL,NULL,'2026-09-05 11:55:00','2026-09-05 12:25:00','SKIPPED','[REQ068] 未触发回退条件，本轮跳过实际回退。','[REQ068] 检查回退条件和模拟恢复记录，归档本轮演练结论。',0,0,0),
(9400680201,1,940001,9400680200,1,'环境与权限检查',NULL,NULL,'2026-09-07 09:00:00','2026-09-07 09:30:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 核对虚构演练环境的硬件、网络、中间件、组件及数据库检查项。',0,0,0),
(9400680202,1,940001,9400680200,2,'应用及数据基线备份',NULL,NULL,'2026-09-07 09:35:00','2026-09-07 10:05:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 记录模拟备份批次并检查备份完整性；不执行真实备份命令。',0,0,0),
(9400680203,1,940001,9400680200,3,'数据同步与一致性核对',NULL,NULL,'2026-09-07 10:10:00','2026-09-07 10:40:00','RUNNING','[REQ068] 模拟执行中，等待校验结果。','[REQ068] 使用虚构批次数量核对记录总数和校验结果；不访问生产数据。',0,0,0),
(9400680204,1,940001,9400680200,4,'应用切换与服务启停',NULL,NULL,'2026-09-07 10:45:00','2026-09-07 11:15:00','PENDING',NULL,'[REQ068] 记录模拟切换顺序、服务状态与操作结果；不执行实际服务命令。',0,0,0),
(9400680205,1,940001,9400680200,5,'关键业务及监控验证',NULL,NULL,'2026-09-07 11:20:00','2026-09-07 11:50:00','PENDING',NULL,'[REQ068] 核对模拟关键业务交易、告警及监控指标并记录结果。',0,0,0),
(9400680206,1,940001,9400680200,6,'回退检查与演练收尾',NULL,NULL,'2026-09-07 11:55:00','2026-09-07 12:25:00','PENDING',NULL,'[REQ068] 检查回退条件和模拟恢复记录，归档本轮演练结论。',0,0,0),
(9400680301,1,940001,9400680300,1,'环境与权限检查',NULL,NULL,'2026-09-10 09:00:00','2026-09-10 09:30:00','PENDING',NULL,'[REQ068] 核对虚构演练环境的硬件、网络、中间件、组件及数据库检查项。',0,0,0),
(9400680302,1,940001,9400680300,2,'应用及数据基线备份',NULL,NULL,'2026-09-10 09:35:00','2026-09-10 10:05:00','PENDING',NULL,'[REQ068] 记录模拟备份批次并检查备份完整性；不执行真实备份命令。',0,0,0),
(9400680303,1,940001,9400680300,3,'数据同步与一致性核对',NULL,NULL,'2026-09-10 10:10:00','2026-09-10 10:40:00','PENDING',NULL,'[REQ068] 使用虚构批次数量核对记录总数和校验结果；不访问生产数据。',0,0,0),
(9400680304,1,940001,9400680300,4,'应用切换与服务启停',NULL,NULL,'2026-09-10 10:45:00','2026-09-10 11:15:00','PENDING',NULL,'[REQ068] 记录模拟切换顺序、服务状态与操作结果；不执行实际服务命令。',0,0,0),
(9400680305,1,940001,9400680300,5,'关键业务及监控验证',NULL,NULL,'2026-09-10 11:20:00','2026-09-10 11:50:00','PENDING',NULL,'[REQ068] 核对模拟关键业务交易、告警及监控指标并记录结果。',0,0,0),
(9400680306,1,940001,9400680300,6,'回退检查与演练收尾',NULL,NULL,'2026-09-10 11:55:00','2026-09-10 12:25:00','PENDING',NULL,'[REQ068] 检查回退条件和模拟恢复记录，归档本轮演练结论。',0,0,0),
(9400681101,1,940002,9400681100,1,'环境与权限检查',NULL,NULL,'2026-09-06 09:00:00','2026-09-06 09:30:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 核对虚构演练环境的硬件、网络、中间件、组件及数据库检查项。',0,0,0),
(9400681102,1,940002,9400681100,2,'应用及数据基线备份',NULL,NULL,'2026-09-06 09:35:00','2026-09-06 10:05:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 记录模拟备份批次并检查备份完整性；不执行真实备份命令。',0,0,0),
(9400681103,1,940002,9400681100,3,'数据同步与一致性核对',NULL,NULL,'2026-09-06 10:10:00','2026-09-06 10:40:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 使用虚构批次数量核对记录总数和校验结果；不访问生产数据。',0,0,0),
(9400681104,1,940002,9400681100,4,'应用切换与服务启停',NULL,NULL,'2026-09-06 10:45:00','2026-09-06 11:15:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 记录模拟切换顺序、服务状态与操作结果；不执行实际服务命令。',0,0,0),
(9400681105,1,940002,9400681100,5,'关键业务及监控验证',NULL,NULL,'2026-09-06 11:20:00','2026-09-06 11:50:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 核对模拟关键业务交易、告警及监控指标并记录结果。',0,0,0),
(9400681106,1,940002,9400681100,6,'回退检查与演练收尾',NULL,NULL,'2026-09-06 11:55:00','2026-09-06 12:25:00','SKIPPED','[REQ068] 未触发回退条件，本轮跳过实际回退。','[REQ068] 检查回退条件和模拟恢复记录，归档本轮演练结论。',0,0,0),
(9400681201,1,940002,9400681200,1,'环境与权限检查',NULL,NULL,'2026-09-08 09:00:00','2026-09-08 09:30:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 核对虚构演练环境的硬件、网络、中间件、组件及数据库检查项。',0,0,0),
(9400681202,1,940002,9400681200,2,'应用及数据基线备份',NULL,NULL,'2026-09-08 09:35:00','2026-09-08 10:05:00','COMPLETED','[REQ068] 模拟检查通过，结果已记录。','[REQ068] 记录模拟备份批次并检查备份完整性；不执行真实备份命令。',0,0,0),
(9400681203,1,940002,9400681200,3,'数据同步与一致性核对',NULL,NULL,'2026-09-08 10:10:00','2026-09-08 10:40:00','RUNNING','[REQ068] 模拟执行中，等待校验结果。','[REQ068] 使用虚构批次数量核对记录总数和校验结果；不访问生产数据。',0,0,0),
(9400681204,1,940002,9400681200,4,'应用切换与服务启停',NULL,NULL,'2026-09-08 10:45:00','2026-09-08 11:15:00','PENDING',NULL,'[REQ068] 记录模拟切换顺序、服务状态与操作结果；不执行实际服务命令。',0,0,0),
(9400681205,1,940002,9400681200,5,'关键业务及监控验证',NULL,NULL,'2026-09-08 11:20:00','2026-09-08 11:50:00','PENDING',NULL,'[REQ068] 核对模拟关键业务交易、告警及监控指标并记录结果。',0,0,0),
(9400681206,1,940002,9400681200,6,'回退检查与演练收尾',NULL,NULL,'2026-09-08 11:55:00','2026-09-08 12:25:00','PENDING',NULL,'[REQ068] 检查回退条件和模拟恢复记录，归档本轮演练结论。',0,0,0),
(9400681301,1,940002,9400681300,1,'环境与权限检查',NULL,NULL,'2026-09-11 09:00:00','2026-09-11 09:30:00','PENDING',NULL,'[REQ068] 核对虚构演练环境的硬件、网络、中间件、组件及数据库检查项。',0,0,0),
(9400681302,1,940002,9400681300,2,'应用及数据基线备份',NULL,NULL,'2026-09-11 09:35:00','2026-09-11 10:05:00','PENDING',NULL,'[REQ068] 记录模拟备份批次并检查备份完整性；不执行真实备份命令。',0,0,0),
(9400681303,1,940002,9400681300,3,'数据同步与一致性核对',NULL,NULL,'2026-09-11 10:10:00','2026-09-11 10:40:00','PENDING',NULL,'[REQ068] 使用虚构批次数量核对记录总数和校验结果；不访问生产数据。',0,0,0),
(9400681304,1,940002,9400681300,4,'应用切换与服务启停',NULL,NULL,'2026-09-11 10:45:00','2026-09-11 11:15:00','PENDING',NULL,'[REQ068] 记录模拟切换顺序、服务状态与操作结果；不执行实际服务命令。',0,0,0),
(9400681305,1,940002,9400681300,5,'关键业务及监控验证',NULL,NULL,'2026-09-11 11:20:00','2026-09-11 11:50:00','PENDING',NULL,'[REQ068] 核对模拟关键业务交易、告警及监控指标并记录结果。',0,0,0),
(9400681306,1,940002,9400681300,6,'回退检查与演练收尾',NULL,NULL,'2026-09-11 11:55:00','2026-09-11 12:25:00','PENDING',NULL,'[REQ068] 检查回退条件和模拟恢复记录，归档本轮演练结论。',0,0,0);
-- A collision or later edit is an error, never an upsert.
INSERT INTO fixture_guard SELECT COUNT(*)=0 FROM rel_release_drill_round r JOIN expected_round e ON e.id=r.id WHERE NOT (r.id <=> e.id AND r.tenant_id <=> e.tenant_id AND r.project_id <=> e.project_id AND r.drill_plan_id <=> e.drill_plan_id AND r.release_plan_id <=> e.release_plan_id AND r.environment_id <=> e.environment_id AND r.round_no <=> e.round_no AND r.round_name <=> e.round_name AND r.planned_at <=> e.planned_at AND r.status <=> e.status AND r.result_content <=> e.result_content AND r.row_version <=> e.row_version AND r.created_by <=> e.created_by AND r.updated_by <=> e.updated_by AND r.deleted=0);
INSERT INTO fixture_guard SELECT COUNT(*)=0 FROM rel_release_drill_step s JOIN expected_step e ON e.id=s.id WHERE NOT (s.id <=> e.id AND s.tenant_id <=> e.tenant_id AND s.project_id <=> e.project_id AND s.drill_round_id <=> e.drill_round_id AND s.seq_no <=> e.seq_no AND s.step_name <=> e.step_name AND s.owner_id <=> e.owner_id AND s.owner_name <=> e.owner_name AND s.planned_start <=> e.planned_start AND s.planned_end <=> e.planned_end AND s.status <=> e.status AND s.result_content <=> e.result_content AND s.description <=> e.description AND s.row_version <=> e.row_version AND s.created_by <=> e.created_by AND s.updated_by <=> e.updated_by AND s.deleted=0);
INSERT INTO fixture_guard SELECT COUNT(*)=0 FROM rel_release_drill_round r JOIN expected_round e ON e.tenant_id=r.tenant_id AND e.project_id=r.project_id AND e.round_name=r.round_name WHERE r.id<>e.id;
INSERT INTO fixture_guard SELECT COUNT(*)=0 FROM rel_release_drill_round r JOIN expected_round e ON e.tenant_id=r.tenant_id AND e.project_id=r.project_id AND e.round_no=r.round_no WHERE r.id<>e.id AND r.deleted=0;
INSERT INTO rel_release_drill_round (id,tenant_id,project_id,drill_plan_id,release_plan_id,environment_id,round_no,round_name,planned_at,status,result_content,row_version,created_by,updated_by) SELECT e.id,e.tenant_id,e.project_id,e.drill_plan_id,e.release_plan_id,e.environment_id,e.round_no,e.round_name,e.planned_at,e.status,e.result_content,e.row_version,e.created_by,e.updated_by FROM expected_round e WHERE NOT EXISTS(SELECT 1 FROM rel_release_drill_round r WHERE r.id=e.id);
SET @inserted_rounds=ROW_COUNT();
INSERT INTO rel_release_drill_step (id,tenant_id,project_id,drill_round_id,seq_no,step_name,owner_id,owner_name,planned_start,planned_end,status,result_content,description,row_version,created_by,updated_by) SELECT e.id,e.tenant_id,e.project_id,e.drill_round_id,e.seq_no,e.step_name,e.owner_id,e.owner_name,e.planned_start,e.planned_end,e.status,e.result_content,e.description,e.row_version,e.created_by,e.updated_by FROM expected_step e WHERE NOT EXISTS(SELECT 1 FROM rel_release_drill_step s WHERE s.id=e.id);
SET @inserted_steps=ROW_COUNT();
INSERT INTO fixture_guard SELECT COUNT(*)=6 FROM rel_release_drill_round r JOIN expected_round e ON e.id=r.id WHERE r.id <=> e.id AND r.tenant_id <=> e.tenant_id AND r.project_id <=> e.project_id AND r.drill_plan_id <=> e.drill_plan_id AND r.release_plan_id <=> e.release_plan_id AND r.environment_id <=> e.environment_id AND r.round_no <=> e.round_no AND r.round_name <=> e.round_name AND r.planned_at <=> e.planned_at AND r.status <=> e.status AND r.result_content <=> e.result_content AND r.row_version <=> e.row_version AND r.created_by <=> e.created_by AND r.updated_by <=> e.updated_by AND r.deleted=0;
INSERT INTO fixture_guard SELECT COUNT(*)=36 FROM rel_release_drill_step s JOIN expected_step e ON e.id=s.id WHERE s.id <=> e.id AND s.tenant_id <=> e.tenant_id AND s.project_id <=> e.project_id AND s.drill_round_id <=> e.drill_round_id AND s.seq_no <=> e.seq_no AND s.step_name <=> e.step_name AND s.owner_id <=> e.owner_id AND s.owner_name <=> e.owner_name AND s.planned_start <=> e.planned_start AND s.planned_end <=> e.planned_end AND s.status <=> e.status AND s.result_content <=> e.result_content AND s.description <=> e.description AND s.row_version <=> e.row_version AND s.created_by <=> e.created_by AND s.updated_by <=> e.updated_by AND s.deleted=0 AND s.planned_end>s.planned_start;
COMMIT;
SELECT JSON_OBJECT('insertedRounds',@inserted_rounds,'insertedSteps',@inserted_steps,'batch','REQ068') AS result;

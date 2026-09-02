-- 测试管理「平台能力升级项目」快速演示数据。
-- 用法见同目录 ../../../../../docs/requirements/REQ-20260831-057-test-management-configuration/mock-data-import-guide.md。
-- 幂等：使用固定 ID；重复执行会更新本脚本创建的数据。不会删除任何既有数据，也不模拟附件。

SET NAMES utf8mb4;

SET @tenant_id := 1;
SET @project_code := 'RDDMP-PLATFORM';
SET @project_id := (SELECT id FROM pm_project WHERE tenant_id = @tenant_id AND project_code = @project_code AND deleted = 0 LIMIT 1);
SET @system_id := (SELECT id FROM arch_physical_subsystem WHERE tenant_id = @tenant_id AND deleted = 0 ORDER BY id LIMIT 1);
SET @operator_id := COALESCE((SELECT id FROM sys_user WHERE tenant_id = @tenant_id AND deleted = 0 ORDER BY id LIMIT 1), 1);

-- 在写入任何业务表前校验上下文，避免主数据缺失时出现部分导入。
DROP TEMPORARY TABLE IF EXISTS tmp_tm_mock_context;
CREATE TEMPORARY TABLE tmp_tm_mock_context (
    project_id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL
);
INSERT INTO tmp_tm_mock_context (project_id, system_id, operator_id)
VALUES (@project_id, @system_id, @operator_id);

SET @domain := 'application-assembly';
SET @base_id := 990057000000000;

START TRANSACTION;

INSERT INTO tm_test_participating_system (id,tenant_id,test_domain,project_id,physical_subsystem_id,enabled,remark,created_by,updated_by,deleted)
VALUES (@base_id+1,@tenant_id,@domain,@project_id,@system_id,1,'【模拟】平台能力升级项目参测系统',@operator_id,@operator_id,0)
ON DUPLICATE KEY UPDATE enabled=VALUES(enabled),remark=VALUES(remark),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_round (id,tenant_id,test_domain,project_id,round_code,round_name,planned_start_date,planned_end_date,status,sort_no,remark,created_by,updated_by,deleted)
VALUES
(@base_id+10,@tenant_id,@domain,@project_id,'R1','第一轮联调测试','2026-09-01','2026-09-12','IN_PROGRESS',1,'【模拟】接口与主流程联调',@operator_id,@operator_id,0),
(@base_id+11,@tenant_id,@domain,@project_id,'R2','第二轮回归测试','2026-09-15','2026-09-26','DRAFT',2,'【模拟】缺陷回归与验收准备',@operator_id,@operator_id,0)
ON DUPLICATE KEY UPDATE round_name=VALUES(round_name),status=VALUES(status),sort_no=VALUES(sort_no),remark=VALUES(remark),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_cycle (id,tenant_id,round_id,cycle_code,cycle_name,planned_start_date,planned_end_date,status,sort_no,remark,created_by,updated_by,deleted)
VALUES
(@base_id+20,@tenant_id,@base_id+10,'R1-C1','第一周期：核心流程','2026-09-01','2026-09-05','IN_PROGRESS',1,'【模拟】核心功能验证',@operator_id,@operator_id,0),
(@base_id+21,@tenant_id,@base_id+10,'R1-C2','第二周期：异常场景','2026-09-08','2026-09-12','DRAFT',2,'【模拟】异常与边界验证',@operator_id,@operator_id,0),
(@base_id+22,@tenant_id,@base_id+11,'R2-C1','第一周期：缺陷回归','2026-09-15','2026-09-19','DRAFT',1,'【模拟】缺陷回归',@operator_id,@operator_id,0),
(@base_id+23,@tenant_id,@base_id+11,'R2-C2','第二周期：验收准备','2026-09-22','2026-09-26','DRAFT',2,'【模拟】验收前检查',@operator_id,@operator_id,0)
ON DUPLICATE KEY UPDATE cycle_name=VALUES(cycle_name),status=VALUES(status),sort_no=VALUES(sort_no),remark=VALUES(remark),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_dictionary (id,tenant_id,test_domain,project_id,dictionary_code,dictionary_name,source_type,enabled,remark,created_by,updated_by,deleted)
VALUES
(@base_id+30,@tenant_id,@domain,@project_id,'func_type','功能类型','LOCAL',1,'【模拟】范围功能类型',@operator_id,@operator_id,0),
(@base_id+31,@tenant_id,@domain,@project_id,'change_status','变动状态','LOCAL',1,'【模拟】范围变动状态',@operator_id,@operator_id,0),
(@base_id+32,@tenant_id,@domain,@project_id,'importance','业务重要程度','LOCAL',1,'【模拟】范围重要程度',@operator_id,@operator_id,0)
ON DUPLICATE KEY UPDATE dictionary_name=VALUES(dictionary_name),enabled=1,updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_dictionary_option (id,tenant_id,dictionary_id,option_code,option_name,enabled,sort_no,remark,created_by,updated_by,deleted)
VALUES
(@base_id+40,@tenant_id,@base_id+30,'ONLINE','联机交易',1,1,'【模拟】',@operator_id,@operator_id,0),
(@base_id+41,@tenant_id,@base_id+30,'BATCH','批处理',1,2,'【模拟】',@operator_id,@operator_id,0),
(@base_id+42,@tenant_id,@base_id+31,'NEW','新增',1,1,'【模拟】',@operator_id,@operator_id,0),
(@base_id+43,@tenant_id,@base_id+31,'MODIFIED','修改',1,2,'【模拟】',@operator_id,@operator_id,0),
(@base_id+44,@tenant_id,@base_id+32,'HIGH','高',1,1,'【模拟】',@operator_id,@operator_id,0),
(@base_id+45,@tenant_id,@base_id+32,'MEDIUM','中',1,2,'【模拟】',@operator_id,@operator_id,0),
(@base_id+46,@tenant_id,@base_id+32,'LOW','低',1,3,'【模拟】',@operator_id,@operator_id,0)
ON DUPLICATE KEY UPDATE option_name=VALUES(option_name),enabled=1,sort_no=VALUES(sort_no),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_announcement (id,tenant_id,test_domain,project_id,title,content_html,pinned,pinned_at,published_by,published_at,last_edited_by,last_edited_at,deleted)
VALUES
(@base_id+100,@tenant_id,@domain,@project_id,'【模拟】第一轮联调测试启动','<h2>第一轮联调测试启动</h2><p>请各参测系统按周期完成核心流程验证，并及时登记问题。</p>',1,NOW(),@operator_id,NOW(),@operator_id,NOW(),0),
(@base_id+101,@tenant_id,@domain,@project_id,'【模拟】缺陷提交流程说明','<p><strong>失败案例须关联缺陷。</strong>请补全严重程度、优先级和复现步骤。</p>',1,NOW(),@operator_id,NOW(),@operator_id,NOW(),0),
(@base_id+102,@tenant_id,@domain,@project_id,'【模拟】日报提交提醒','<p>请在每日 17:00 前完成执行结果更新。</p>',0,NULL,@operator_id,NOW(),NULL,NULL,0)
ON DUPLICATE KEY UPDATE title=VALUES(title),content_html=VALUES(content_html),pinned=VALUES(pinned),pinned_at=VALUES(pinned_at),last_edited_by=VALUES(last_edited_by),last_edited_at=VALUES(last_edited_at),deleted=0;

INSERT INTO tm_test_scope (id,tenant_id,test_domain,project_id,physical_subsystem_id,directory_id,scope_code,scope_name,leaf_menu,function_type,change_status,importance,accounting_flag,created_by,updated_by,deleted)
VALUES
(@base_id+200,@tenant_id,@domain,@project_id,@system_id,NULL,'MOCK-0001','登录与单点认证','统一登录','ONLINE','NEW','HIGH','是',@operator_id,@operator_id,0),
(@base_id+201,@tenant_id,@domain,@project_id,@system_id,NULL,'MOCK-0002','首页工作台展示','工作台','ONLINE','MODIFIED','HIGH','否',@operator_id,@operator_id,0),
(@base_id+202,@tenant_id,@domain,@project_id,@system_id,NULL,'MOCK-0003','任务查询与筛选','任务中心','ONLINE','MODIFIED','MEDIUM','否',@operator_id,@operator_id,0),
(@base_id+203,@tenant_id,@domain,@project_id,@system_id,NULL,'MOCK-0004','批量数据同步','数据同步','BATCH','NEW','MEDIUM','是',@operator_id,@operator_id,0),
(@base_id+204,@tenant_id,@domain,@project_id,@system_id,NULL,'MOCK-0005','运行日报生成','报表中心','BATCH','NEW','LOW','否',@operator_id,@operator_id,0)
ON DUPLICATE KEY UPDATE scope_name=VALUES(scope_name),leaf_menu=VALUES(leaf_menu),function_type=VALUES(function_type),change_status=VALUES(change_status),importance=VALUES(importance),accounting_flag=VALUES(accounting_flag),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_case (id,tenant_id,test_domain,project_id,physical_subsystem_id,scope_id,directory_id,case_code,case_serial_no,case_name,case_type,test_level,priority,precondition_html,steps_html,expected_result_html,remark,created_by,updated_by,deleted)
VALUES
(@base_id+300,@tenant_id,@domain,@project_id,@system_id,@base_id+200,NULL,'MOCK-0001-001',1,'有效账号登录成功','功能','系统','P1','<p>账号已启用</p>','<ol><li>输入有效账号</li><li>提交登录</li></ol>','<p>进入首页工作台</p>','【模拟】正向场景',@operator_id,@operator_id,0),
(@base_id+301,@tenant_id,@domain,@project_id,@system_id,@base_id+200,NULL,'MOCK-0001-002',2,'错误密码登录失败','功能','系统','P1','<p>账号已启用</p>','<ol><li>输入错误密码</li><li>提交登录</li></ol>','<p>提示认证失败且不进入系统</p>','【模拟】异常场景',@operator_id,@operator_id,0),
(@base_id+302,@tenant_id,@domain,@project_id,@system_id,@base_id+201,NULL,'MOCK-0002-001',1,'工作台展示待办统计','功能','系统','P2','<p>用户已登录</p>','<ol><li>打开工作台</li></ol>','<p>展示待办、已办和通知统计</p>','【模拟】展示场景',@operator_id,@operator_id,0),
(@base_id+303,@tenant_id,@domain,@project_id,@system_id,@base_id+202,NULL,'MOCK-0003-001',1,'按状态筛选任务','功能','系统','P2','<p>存在多状态任务</p>','<ol><li>选择进行中</li><li>点击查询</li></ol>','<p>仅返回进行中任务</p>','【模拟】筛选场景',@operator_id,@operator_id,0),
(@base_id+304,@tenant_id,@domain,@project_id,@system_id,@base_id+203,NULL,'MOCK-0004-001',1,'批量同步完成','批处理','系统','P1','<p>存在待同步数据</p>','<ol><li>触发同步任务</li><li>等待完成</li></ol>','<p>同步成功且生成处理日志</p>','【模拟】批处理场景',@operator_id,@operator_id,0)
ON DUPLICATE KEY UPDATE case_name=VALUES(case_name),steps_html=VALUES(steps_html),expected_result_html=VALUES(expected_result_html),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_execution_directory (id,tenant_id,test_domain,project_id,physical_subsystem_id,round_id,cycle_id,parent_id,directory_name,sort_no,created_by,updated_by,deleted)
VALUES (@base_id+400,@tenant_id,@domain,@project_id,@system_id,@base_id+10,@base_id+20,NULL,'【模拟】第一周期执行集',1,@operator_id,@operator_id,0)
ON DUPLICATE KEY UPDATE directory_name=VALUES(directory_name),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_execution (id,tenant_id,test_domain,project_id,physical_subsystem_id,round_id,cycle_id,directory_id,case_id,execution_status,actual_result_html,remark_html,executor_id,executed_at,created_by,updated_by,deleted)
VALUES
(@base_id+500,@tenant_id,@domain,@project_id,@system_id,@base_id+10,@base_id+20,@base_id+400,@base_id+300,'SUCCESS','<p>登录成功，首页加载正常。</p>',NULL,@operator_id,NOW(),@operator_id,@operator_id,0),
(@base_id+501,@tenant_id,@domain,@project_id,@system_id,@base_id+10,@base_id+20,@base_id+400,@base_id+301,'FAILURE','<p>错误密码提示与预期不一致。</p>','<p>已关联缺陷。</p>',@operator_id,NOW(),@operator_id,@operator_id,0),
(@base_id+502,@tenant_id,@domain,@project_id,@system_id,@base_id+10,@base_id+20,@base_id+400,@base_id+302,'SUCCESS','<p>统计展示正确。</p>',NULL,@operator_id,NOW(),@operator_id,@operator_id,0),
(@base_id+503,@tenant_id,@domain,@project_id,@system_id,@base_id+10,@base_id+20,@base_id+400,@base_id+303,'IN_PROGRESS',NULL,'<p>待补充验证数据。</p>',NULL,NULL,@operator_id,@operator_id,0),
(@base_id+504,@tenant_id,@domain,@project_id,@system_id,@base_id+10,@base_id+20,@base_id+400,@base_id+304,'UNEXECUTED',NULL,NULL,NULL,NULL,@operator_id,@operator_id,0)
ON DUPLICATE KEY UPDATE execution_status=VALUES(execution_status),actual_result_html=VALUES(actual_result_html),remark_html=VALUES(remark_html),executor_id=VALUES(executor_id),executed_at=VALUES(executed_at),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_defect (id,tenant_id,test_domain,project_id,physical_subsystem_id,defect_code,defect_serial_no,summary,description_html,round_id,cycle_id,defect_category,severity,priority,urgency,found_version,test_environment_code,status,handler_id,proposer_id,proposed_at,created_by,updated_by,deleted)
VALUES
(@base_id+600,@tenant_id,@domain,@project_id,@system_id,'MOCK-BUG-0001',1,'错误密码提示文案不符合规范','<p>错误密码时提示文案未遵循统一认证规范。</p>',@base_id+10,@base_id+20,'功能缺陷','MAJOR','HIGH','HIGH','1.0.0','SIT','RAISED',@operator_id,@operator_id,NOW(),@operator_id,@operator_id,0),
(@base_id+601,@tenant_id,@domain,@project_id,@system_id,'MOCK-BUG-0002',2,'任务筛选结果偶发不稳定','<p>特定条件组合下筛选结果需要复核。</p>',@base_id+10,@base_id+20,'功能缺陷','MINOR','MEDIUM','MEDIUM','1.0.0','SIT','ANALYZING',@operator_id,@operator_id,NOW(),@operator_id,@operator_id,0)
ON DUPLICATE KEY UPDATE summary=VALUES(summary),description_html=VALUES(description_html),status=VALUES(status),handler_id=VALUES(handler_id),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_defect_execution (id,tenant_id,defect_id,execution_id,relation_state,snapshot_case_code,snapshot_case_name,snapshot_scope_code,snapshot_scope_name,created_by,updated_by)
VALUES (@base_id+700,@tenant_id,@base_id+600,@base_id+501,'ACTIVE','MOCK-0001-002','错误密码登录失败','MOCK-0001','登录与单点认证',@operator_id,@operator_id)
ON DUPLICATE KEY UPDATE execution_id=VALUES(execution_id),relation_state=VALUES(relation_state),updated_by=VALUES(updated_by);

INSERT INTO tm_test_report (id,tenant_id,test_domain,project_id,scope_type,physical_subsystem_id,special_node_id,report_name,report_type,round_id,cycle_id,source_type,selected_sections,current_version_no,generated_by,generated_at,created_by,updated_by)
VALUES (@base_id+800,@tenant_id,@domain,@project_id,'PROJECT',NULL,NULL,'【模拟】平台能力升级项目第一轮测试报告','ROUND',@base_id+10,NULL,'LIVE',JSON_ARRAY('OVERVIEW','SCOPE','EXECUTION','DEFECT','RISK','CONCLUSION'),1,@operator_id,NOW(),@operator_id,@operator_id)
ON DUPLICATE KEY UPDATE report_name=VALUES(report_name),current_version_no=1,generated_by=VALUES(generated_by),generated_at=VALUES(generated_at),updated_by=VALUES(updated_by);

INSERT INTO tm_test_report_version (id,tenant_id,report_id,version_no,snapshot_json,generated_by,generated_at)
VALUES (@base_id+801,@tenant_id,@base_id+800,1,JSON_OBJECT('title','平台能力升级项目第一轮测试报告','summary','【模拟】执行覆盖与缺陷统计快照'),@operator_id,NOW())
ON DUPLICATE KEY UPDATE snapshot_json=VALUES(snapshot_json),generated_by=VALUES(generated_by),generated_at=VALUES(generated_at);

INSERT INTO tm_test_analytics_snapshot (id,tenant_id,test_domain,project_id,round_id,report_key,snapshot_json,archived_by,archived_at)
VALUES
(@base_id+900,@tenant_id,@domain,@project_id,@base_id+10,'EXECUTION_OVERVIEW',JSON_OBJECT('total',5,'success',2,'failure',1,'inProgress',1,'unexecuted',1),@operator_id,NOW()),
(@base_id+901,@tenant_id,@domain,@project_id,@base_id+10,'DEFECT_OVERVIEW',JSON_OBJECT('total',2,'raised',1,'analyzing',1),@operator_id,NOW())
ON DUPLICATE KEY UPDATE snapshot_json=VALUES(snapshot_json),archived_by=VALUES(archived_by),archived_at=VALUES(archived_at);

COMMIT;
DROP TEMPORARY TABLE tmp_tm_mock_context;

SELECT '测试管理模拟数据导入完成' AS result, @project_id AS project_id, @system_id AS physical_subsystem_id;

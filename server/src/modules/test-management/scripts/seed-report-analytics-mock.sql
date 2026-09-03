-- REQ-20260903-063 本地测试报告与分析统计模拟数据。
-- 仅用于本地开发库，幂等写入 tm_* 测试管理表；绝不写项目、组织、用户或架构主数据。
-- 运行前提：当前租户至少有 1 个项目、2 个有效用户、3 个物理子系统，且三系统跨至少 2 个责任团队组织。

SET NAMES utf8mb4;
SET @tenant_id := 1;
SET @domain := 'application-assembly';
SET @base_id := 990063000000000;
SET @project_id := (SELECT id FROM pm_project WHERE tenant_id=@tenant_id AND deleted=0 ORDER BY id LIMIT 1);
SET @operator_1 := (SELECT id FROM sys_user WHERE tenant_id=@tenant_id AND deleted=0 ORDER BY id LIMIT 1);
SET @operator_2 := (SELECT id FROM sys_user WHERE tenant_id=@tenant_id AND deleted=0 AND id<>@operator_1 ORDER BY id LIMIT 1);
SET @system_1 := (SELECT id FROM arch_physical_subsystem WHERE tenant_id=@tenant_id AND deleted=0 ORDER BY id LIMIT 1);
SET @team_1 := (SELECT responsible_team_org_id FROM arch_physical_subsystem WHERE id=@system_1 AND tenant_id=@tenant_id AND deleted=0);
SET @system_2 := (SELECT id FROM arch_physical_subsystem WHERE tenant_id=@tenant_id AND deleted=0 AND responsible_team_org_id<>@team_1 ORDER BY id LIMIT 1);
SET @system_3 := (SELECT id FROM arch_physical_subsystem WHERE tenant_id=@tenant_id AND deleted=0 AND id NOT IN (@system_1,@system_2) ORDER BY id LIMIT 1);
SET @ready := IF(@project_id IS NOT NULL AND @operator_1 IS NOT NULL AND @operator_2 IS NOT NULL AND @system_1 IS NOT NULL AND @system_2 IS NOT NULL AND @system_3 IS NOT NULL,1,0);

-- CHECK 失败即停止：尚未开始事务，也没有写入任何业务表。
DROP TEMPORARY TABLE IF EXISTS tmp_tm_report_mock_context;
CREATE TEMPORARY TABLE tmp_tm_report_mock_context (
  ready TINYINT NOT NULL CHECK (ready=1), project_id BIGINT NOT NULL, operator_1 BIGINT NOT NULL, operator_2 BIGINT NOT NULL,
  system_1 BIGINT NOT NULL, system_2 BIGINT NOT NULL, system_3 BIGINT NOT NULL
);
INSERT INTO tmp_tm_report_mock_context VALUES (@ready,@project_id,@operator_1,@operator_2,@system_1,@system_2,@system_3);

START TRANSACTION;

INSERT INTO tm_test_participating_system (id,tenant_id,test_domain,project_id,physical_subsystem_id,enabled,remark,created_by,updated_by,deleted)
SELECT @base_id+n,@tenant_id,@domain,ctx.project_id,CASE n WHEN 1 THEN ctx.system_1 WHEN 2 THEN ctx.system_2 ELSE ctx.system_3 END,1,CONCAT('【本地模拟】报告统计样本系统',ELT(n,'一','二','三')),ctx.operator_1,ctx.operator_1,0
FROM tmp_tm_report_mock_context ctx CROSS JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3) item_list
WHERE TRUE ON DUPLICATE KEY UPDATE enabled=1,remark=VALUES(remark),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_round (id,tenant_id,test_domain,project_id,round_code,round_name,planned_start_date,planned_end_date,status,sort_no,remark,created_by,updated_by,deleted)
SELECT @base_id+9+n,@tenant_id,@domain,ctx.project_id,CONCAT('MOCK-R0',n),CONCAT('【本地模拟】第',ELT(n,'一','二','三'),'轮',ELT(n,'主体测试','回归测试','验收测试')),ELT(n,'2026-01-05','2026-02-02','2026-03-02'),ELT(n,'2026-01-16','2026-02-13','2026-03-13'),IF(n=3,'ACTIVE','CLOSED'),n,'统计样本',IF(n=3,ctx.operator_2,ctx.operator_1),IF(n=3,ctx.operator_2,ctx.operator_1),0
FROM tmp_tm_report_mock_context ctx CROSS JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3) item_list
WHERE TRUE ON DUPLICATE KEY UPDATE round_name=VALUES(round_name),status=VALUES(status),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_cycle (id,tenant_id,round_id,cycle_code,cycle_name,planned_start_date,planned_end_date,status,sort_no,remark,created_by,updated_by,deleted)
SELECT @base_id+19+n,@tenant_id,@base_id+9+CEIL(n/2),CONCAT('MOCK-R0',CEIL(n/2),'-C0',IF(MOD(n,2)=1,1,2)),CONCAT('【本地模拟】第',ELT(CEIL(n/2),'一','二','三'),'轮第',IF(MOD(n,2)=1,'一','二'),'周期'),ELT(n,'2026-01-05','2026-01-10','2026-02-02','2026-02-07','2026-03-02','2026-03-07'),ELT(n,'2026-01-09','2026-01-16','2026-02-06','2026-02-13','2026-03-06','2026-03-13'),ELT(n,'CLOSED','CLOSED','CLOSED','CLOSED','ACTIVE','DRAFT'),IF(MOD(n,2)=1,1,2),'统计样本',IF(n>=4,ctx.operator_2,ctx.operator_1),IF(n>=4,ctx.operator_2,ctx.operator_1),0
FROM tmp_tm_report_mock_context ctx CROSS JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) item_list
WHERE TRUE ON DUPLICATE KEY UPDATE cycle_name=VALUES(cycle_name),status=VALUES(status),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_scope (id,tenant_id,test_domain,project_id,physical_subsystem_id,directory_id,scope_code,scope_name,leaf_menu,function_type,change_status,importance,accounting_flag,created_by,updated_by,deleted)
SELECT @base_id+100+n,@tenant_id,@domain,ctx.project_id,CASE MOD(n,3) WHEN 0 THEN ctx.system_1 WHEN 1 THEN ctx.system_2 ELSE ctx.system_3 END,NULL,CONCAT('RPT-MOCK-S-',LPAD(n,2,'0')),CONCAT('【本地模拟】测试范围',n),'模拟菜单','ONLINE','NEW',IF(MOD(n,3)=0,'HIGH','MEDIUM'),'否',ctx.operator_1,ctx.operator_1,0
FROM tmp_tm_report_mock_context ctx CROSS JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) numbers
WHERE TRUE ON DUPLICATE KEY UPDATE scope_name=VALUES(scope_name),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_case (id,tenant_id,test_domain,project_id,physical_subsystem_id,scope_id,directory_id,case_code,case_serial_no,case_name,case_type,test_level,priority,invalidated,invalid_reason,precondition_html,steps_html,expected_result_html,remark,created_by,updated_by,deleted)
SELECT @base_id+1000+n,@tenant_id,@domain,ctx.project_id,CASE MOD(n,3) WHEN 0 THEN ctx.system_1 WHEN 1 THEN ctx.system_2 ELSE ctx.system_3 END,@base_id+100+MOD(n-1,6)+1,NULL,CONCAT('RPT-MOCK-C-',LPAD(n,3,'0')),n,CONCAT('【本地模拟】统计案例',n),'功能','系统',IF(MOD(n,4)=0,'P1','P2'),IF(MOD(n,15)=0,1,0),IF(MOD(n,15)=0,'【本地模拟】无效案例',''),'<p>本地模拟前置条件</p>','<p>执行模拟步骤</p>','<p>得到模拟结果</p>','【本地模拟】用于验证报告统计',ctx.operator_1,ctx.operator_1,0
FROM tmp_tm_report_mock_context ctx CROSS JOIN (
 SELECT ones.n+tens.n*10+1 n FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
 CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) tens
) numbers
WHERE TRUE ON DUPLICATE KEY UPDATE case_name=VALUES(case_name),invalidated=VALUES(invalidated),invalid_reason=VALUES(invalid_reason),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_execution_directory (id,tenant_id,test_domain,project_id,physical_subsystem_id,round_id,cycle_id,parent_id,directory_name,sort_no,created_by,updated_by,deleted)
SELECT @base_id+200+seq,@tenant_id,@domain,ctx.project_id,CASE MOD(seq,3) WHEN 0 THEN ctx.system_1 WHEN 1 THEN ctx.system_2 ELSE ctx.system_3 END,@base_id+10+FLOOR((seq-1)/2),@base_id+20+seq-1,NULL,CONCAT('【本地模拟】执行目录',seq),seq,ctx.operator_1,ctx.operator_1,0
FROM tmp_tm_report_mock_context ctx CROSS JOIN (SELECT 1 seq UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) cycles
WHERE TRUE ON DUPLICATE KEY UPDATE directory_name=VALUES(directory_name),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_execution (id,tenant_id,test_domain,project_id,physical_subsystem_id,round_id,cycle_id,directory_id,case_id,execution_status,actual_result_html,remark_html,executor_id,executed_at,created_by,updated_by,deleted)
SELECT @base_id+2000+n,@tenant_id,@domain,ctx.project_id,CASE MOD(n,3) WHEN 0 THEN ctx.system_1 WHEN 1 THEN ctx.system_2 ELSE ctx.system_3 END,@base_id+10+FLOOR(MOD(n-1,60)/20),@base_id+20+MOD(n-1,6),@base_id+200+MOD(n-1,6)+1,@base_id+1000+n,
 CASE MOD(n,6) WHEN 0 THEN 'SUCCESS' WHEN 1 THEN 'FAILED' WHEN 2 THEN 'BLOCKED' WHEN 3 THEN 'IN_PROGRESS' WHEN 4 THEN 'UNEXECUTED' ELSE 'SUCCESS' END,
 '<p>【本地模拟】执行结果</p>','<p>【本地模拟】备注</p>',IF(MOD(n,2)=0,ctx.operator_1,ctx.operator_2),IF(MOD(n,6) IN (3,4),NULL,NOW()),ctx.operator_1,ctx.operator_1,0
FROM tmp_tm_report_mock_context ctx CROSS JOIN (
 SELECT ones.n+tens.n*10+1 n FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
 CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) tens
) numbers
WHERE TRUE ON DUPLICATE KEY UPDATE execution_status=VALUES(execution_status),executor_id=VALUES(executor_id),executed_at=VALUES(executed_at),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_defect (id,tenant_id,test_domain,project_id,physical_subsystem_id,defect_code,defect_serial_no,summary,description_html,round_id,cycle_id,defect_category,severity,priority,urgency,status,handler_id,proposer_id,proposed_at,created_by,updated_by,deleted)
SELECT @base_id+3000+n,@tenant_id,@domain,ctx.project_id,CASE MOD(n,3) WHEN 0 THEN ctx.system_1 WHEN 1 THEN ctx.system_2 ELSE ctx.system_3 END,CONCAT('RPT-MOCK-D-',LPAD(n,3,'0')),n,CONCAT('【本地模拟】缺陷',n),'<p>本地模拟缺陷说明</p>',@base_id+10+FLOOR((n-1)/6),@base_id+20+MOD(n-1,6),'功能缺陷',ELT(MOD(n-1,4)+1,'FATAL','SERIOUS','NORMAL','MINOR'),'HIGH','HIGH',ELT(MOD(n-1,4)+1,'RAISED','ANALYZING','RESOLVED','CLOSED'),IF(MOD(n,2)=0,ctx.operator_1,ctx.operator_2),ctx.operator_1,NOW(),ctx.operator_1,ctx.operator_1,0
FROM tmp_tm_report_mock_context ctx CROSS JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12) numbers
WHERE TRUE ON DUPLICATE KEY UPDATE summary=VALUES(summary),severity=VALUES(severity),status=VALUES(status),handler_id=VALUES(handler_id),updated_by=VALUES(updated_by),deleted=0;

INSERT INTO tm_test_quality_threshold (id,tenant_id,test_domain,project_id,metric_code,comparison_direction,qualified_threshold,risk_threshold,enabled,created_by,updated_by)
SELECT @base_id+4000+n,@tenant_id,@domain,ctx.project_id,metric_code,direction,qualified,risk,enabled,ctx.operator_1,ctx.operator_1
FROM tmp_tm_report_mock_context ctx CROSS JOIN (
 SELECT 1 n,'execution_rate' metric_code,'AT_LEAST' direction,95 qualified,80 risk,1 enabled UNION ALL
 SELECT 2,'case_success_rate','AT_LEAST',90,75,1 UNION ALL SELECT 3,'executed_case_success_rate','AT_LEAST',92,80,1 UNION ALL
 SELECT 4,'defect_density','AT_MOST',15,30,1 UNION ALL SELECT 5,'defect_repair_rate','AT_LEAST',90,70,0 UNION ALL
 SELECT 6,'severe_defect_count','AT_MOST',0,2,1 UNION ALL SELECT 7,'blocked_case_count','AT_MOST',0,3,0
) metrics
WHERE TRUE ON DUPLICATE KEY UPDATE qualified_threshold=VALUES(qualified_threshold),risk_threshold=VALUES(risk_threshold),enabled=VALUES(enabled),updated_by=VALUES(updated_by);

INSERT INTO tm_test_report (id,tenant_id,test_domain,project_id,scope_type,physical_subsystem_id,report_name,report_type,round_id,cycle_id,source_type,selected_sections,current_version_no,generated_by,generated_at,created_by,updated_by)
SELECT @base_id+5000,@tenant_id,@domain,project_id,'PROJECT',NULL,'【本地模拟】项目全周期测试报告','LIFECYCLE',NULL,NULL,'LIVE',JSON_ARRAY('OVERVIEW','SCOPE_STAT','EXECUTION_STAT','DEFECT_STAT','QUALITY_STAT','SCOPE_DETAIL','EXECUTION_DETAIL','DEFECT_DETAIL'),2,operator_1,NOW(),operator_1,operator_1 FROM tmp_tm_report_mock_context
WHERE TRUE ON DUPLICATE KEY UPDATE report_name=VALUES(report_name),current_version_no=2,generated_by=VALUES(generated_by),generated_at=VALUES(generated_at),updated_by=VALUES(updated_by);

INSERT INTO tm_test_report_version (id,tenant_id,report_id,version_no,report_semantic_version,snapshot_json,quality_snapshot_json,generated_by,generated_at)
SELECT @base_id+5000+n,@tenant_id,@base_id+5000,n,'V2',JSON_OBJECT('title',CONCAT('【本地模拟】项目全周期测试报告',IF(n=2,' V2','')),'execution_in_progress',10,'execution_total',30),JSON_OBJECT('enabled_metrics',5,'overall','风险'),IF(n=1,ctx.operator_1,ctx.operator_2),NOW()
FROM tmp_tm_report_mock_context ctx CROSS JOIN (SELECT 1 n UNION ALL SELECT 2) versions
WHERE TRUE ON DUPLICATE KEY UPDATE snapshot_json=VALUES(snapshot_json),quality_snapshot_json=VALUES(quality_snapshot_json),generated_by=VALUES(generated_by),generated_at=VALUES(generated_at);

INSERT INTO tm_test_analytics_snapshot (id,tenant_id,test_domain,project_id,round_id,report_key,snapshot_json,archived_by,archived_at)
SELECT @base_id+6000+n,@tenant_id,@domain,ctx.project_id,@base_id+9+n,report_key,IF(n=1,JSON_OBJECT('summary',JSON_OBJECT('effective_case_total',56,'invalid_case_total',4,'execution_in_progress',10,'execution_total',30,'execution_rate',53.57)),JSON_OBJECT('summary',JSON_OBJECT('success_rate',35.71,'defect_total',12,'defect_open',6))),IF(n=1,ctx.operator_1,ctx.operator_2),NOW()
FROM tmp_tm_report_mock_context ctx CROSS JOIN (SELECT 1 n,'RPT-001' report_key UNION ALL SELECT 2,'CHT-001') snapshots
WHERE TRUE ON DUPLICATE KEY UPDATE snapshot_json=VALUES(snapshot_json),archived_by=VALUES(archived_by),archived_at=VALUES(archived_at);

COMMIT;
DROP TEMPORARY TABLE tmp_tm_report_mock_context;
SELECT '测试报告与分析统计本地模拟数据已完成幂等写入' AS result;

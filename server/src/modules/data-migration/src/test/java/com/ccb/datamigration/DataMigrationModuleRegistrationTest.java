package com.ccb.datamigration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataMigrationModuleRegistrationTest {
    @Test
    void dashboardUsesDedicatedControllerWithoutSnapshotScheduler() {
        assertTrue(Files.exists(Path.of("src/main/java/com/ccb/datamigration/web/DashboardController.java")));
        assertTrue(!Files.exists(Path.of("src/main/java/com/ccb/datamigration/service/DashboardSnapshotScheduler.java")));
    }

    @Test
    void moduleRegistrationIsBackedByV84() {
        assertTrue(Files.exists(Path.of("../../platform/infrastructure/src/main/resources/db/migration/V84__data_migration_component_enrichment.sql")));
        assertTrue(Files.exists(Path.of("../../platform/infrastructure/src/main/resources/db/migration/V91__attachment_expires_at_datetime.sql")));
    }

    @Test
    void targetTableMigrationAndServiceUseCurrentProjectContract() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V88__data_migration_target_table_structure.sql");
        Path service = Path.of("src/main/java/com/ccb/datamigration/service/TargetTableService.java");
        assertTrue(Files.exists(migration));
        String migrationSql = Files.readString(migration);
        String source = Files.readString(service);
        assertTrue(migrationSql.contains("pm_project"));
        assertTrue(source.contains("pm_project"));
        assertTrue(source.contains("ensureTableUnique(user.tenantId()"));
        assertTrue(source.contains("normalizedTableNameEn"));
        assertTrue(source.contains("if (createTableSafely"));
        assertTrue(source.contains("validateImportFields(fields)"));
    }

    @Test
    void intermediateTablesUseTargetTableModelOnly() throws Exception {
        String tables = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/ContentAssetTables.java"));
        String structured = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/StructuredAssetService.java"));
        String excel = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/ExcelService.java"));
        String migration = Files.readString(Path.of("../../platform/infrastructure/src/main/resources/db/migration/V169__data_migration_intermediate_table_canonicalization.sql"));
        assertTrue(!tables.contains("dm_intermediate_table"));
        assertTrue(!structured.contains("INTERMEDIATE_TABLE"));
        assertTrue(!excel.contains("INTERMEDIATE_TABLE"));
        assertTrue(migration.contains("dm_v169_assert_intermediate_empty"));
        assertTrue(migration.contains("DROP TABLE dm_intermediate_table"));
    }

    @Test
    void issuePageUsesBoundedServerImportAndConsumesPermissionGuards() throws Exception {
        String source = Files.readString(Path.of("../../../../web/src/modules/data-migration/views/content/IssuesPage.vue"));
        assertTrue(source.contains("await importIssues(pid, importFile.value)"));
        assertTrue(!source.contains("for (const item of importData.value)"));
        assertTrue(!source.contains("单次导入不超过500条"));
        assertTrue(source.contains(":selectable=\"canDelete\""));
        assertTrue(source.contains(":disabled=\"busy || !canEdit(row)\""));
        assertTrue(source.contains(":disabled=\"busy || !canDelete(row)\""));
        assertTrue(source.contains("v-if=\"canManage\" label=\"回收站\""));
        assertTrue(source.contains("const oldIds = [...previousTableIds.value]"));
        assertTrue(source.contains("fv.value.relatedTables = oldIds"));
    }

    @Test
    void categoryPermissionsAreEnforcedInServiceLayer() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/TargetTableService.java"));
        assertTrue(source.contains("requireCategoryPermission(user, cat, \"create\")"));
        assertTrue(source.contains("requireCategoryPermission(user, cat, \"update\")"));
        assertTrue(source.contains("requireCategoryPermission(user, cat, \"delete\")"));
        assertTrue(Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/DataMigrationPermissionService.java"))
                .contains("table-fields-"));
    }

    @Test
    void migrationRestoresRuntimeTablesAndImportPreservesAuthorizationErrors() throws Exception {
        String migration = Files.readString(Path.of("../../platform/infrastructure/src/main/resources/db/migration/V84__data_migration_component_enrichment.sql"));
        String controller = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/TargetTableController.java"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS dm_asset"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS dm_operation_log"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS dm_dashboard_snapshot"));
        assertTrue(controller.contains("catch (IOException ex)"));
        assertTrue(!controller.contains("catch (Exception ex)"));
    }

    @Test
    void contentResourceEndpointsRequireWriteOrManageAuthority() throws Exception {
        String assets = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/ContentAssetController.java"));
        String recycle = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/ContentRecycleBinController.java"));
        String structured = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/StructuredAssetController.java"));
        String plan = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/PlanController.java"));
        String releaseDrill = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/ReleaseDrillController.java"));
        String mapping = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/MappingController.java"));
        // PLAN/RELEASE_DRILL/MAPPING_DOC 已从通用文件控制器剥离至专属 Controller（REQ-20260820-031 增量），通用侧仅验 dependencies。
        assertTrue(!assets.contains("/plans") && !assets.contains("/release-drills") && !assets.contains("/mappings") && assets.contains("/dependencies/upload") && assets.contains("data-migration:write"));
        assertTrue(mapping.contains("/api/data-migration/mappings")
                && mapping.contains("data-migration:content:mappings:create")
                && mapping.contains("data-migration:content:mappings:delete")
                && mapping.contains("data-migration:write"));
        String rule = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/RuleController.java"));
        assertTrue(rule.contains("/api/data-migration/rules")
                && rule.contains("data-migration:content:validation-rules:create")
                && rule.contains("data-migration:content:validation-rules:delete")
                && rule.contains("data-migration:write"));
        assertTrue(recycle.contains("/restore") && recycle.contains("data-migration:manage"));
        String parameter = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/ParameterController.java"));
        assertTrue(parameter.contains("/api/data-migration/parameters")
                && parameter.contains("data-migration:content:parameters:create")
                && parameter.contains("data-migration:content:parameters:delete")
                && parameter.contains("data-migration:write"));
        assertTrue(!structured.contains("/parameters/import") && !structured.contains("/parameters/delete"));
        assertTrue(plan.contains("/api/data-migration/plans")
                && plan.contains("data-migration:content:plans:create")
                && plan.contains("data-migration:content:plans:delete")
                && plan.contains("data-migration:write"));
        assertTrue(releaseDrill.contains("/api/data-migration/release-drills")
                && releaseDrill.contains("data-migration:content:release-drills:create")
                && releaseDrill.contains("data-migration:content:release-drills:delete")
                && releaseDrill.contains("data-migration:write"));
    }

    @Test
    void contentFileAssetsOwnTheUploadWithoutMd5Contract() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/ContentFileAssetService.java"));
        String binding = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/ContentAttachmentService.java"));
        String controller = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/ContentAssetController.java"));
        assertTrue(source.contains("DATA_MIGRATION_ASSET"));
        assertTrue(!source.contains("assertMd5Available"));
        assertTrue(!source.contains("checksum_md5"));
        assertTrue(binding.contains("attachmentGateway.bind"));
        assertTrue(controller.contains("@RequestParam Long attachmentId"));
        assertTrue(!controller.contains("@RequestParam String md5"));
        assertTrue(!controller.contains("/content/check-md5"));
    }

    @Test
    void checksumMd5RemovalMigrationIsRegistered() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V174__data_migration_remove_checksum_md5.sql");
        assertTrue(Files.exists(migration));
        String sql = Files.readString(migration);
        assertTrue(sql.contains("checksum_md5"));
        assertTrue(sql.contains("DROP COLUMN"));
        assertTrue(sql.contains("idx_dm_plan_md5"));
        assertTrue(sql.contains("dm_report"));
    }

    @Test
    void issueTrackingHasDedicatedBackendService() {
        assertTrue(Files.exists(Path.of("src/main/java/com/ccb/datamigration/service/IssueService.java")));
        assertTrue(Files.exists(Path.of("src/main/java/com/ccb/datamigration/web/IssueController.java")));
    }

    @Test
    void issueStorageIsIndependentAndGenericAssetTypesRejectIssue() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V156__data_migration_issue_independent_storage.sql");
        String sql = Files.readString(migration);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS dm_issue"));
        assertTrue(sql.contains("DELETE FROM dm_asset_relation"));
        assertTrue(sql.contains("DELETE FROM dm_asset"));
        String issue = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/IssueService.java"));
        assertTrue(issue.contains("FROM dm_issue"));
        assertTrue(!issue.contains("FROM dm_asset a"));
        assertTrue(!Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/StructuredAssetService.java")).contains("\"ISSUE\""));
        assertTrue(!Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/ContentFileAssetService.java")).contains("\"ISSUE\""));
    }

    @Test
    void issueCreateInsertBindsAllColumns() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/IssueService.java"));
        String insert = source.lines().filter(line -> line.contains("INSERT INTO dm_issue")).findFirst().orElseThrow();
        assertTrue(insert.substring(insert.indexOf("VALUES")).chars().filter(ch -> ch == '?').count() == 21);
        assertTrue(insert.contains("system_code, issue_source"));
        assertTrue(!insert.contains("system_id"));
        assertTrue(insert.contains("created_by, updated_by"));
    }

    @Test
    void issueGovernanceUsesAdditiveActiveCodeMigrationAndServerRbac() throws Exception {
        String migration = Files.readString(Path.of("../../platform/infrastructure/src/main/resources/db/migration/V157__data_migration_issue_active_code_uniqueness.sql"));
        String service = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/IssueService.java"));
        String controller = Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/IssueController.java"));
        assertTrue(migration.contains("active_issue_code"));
        assertTrue(migration.contains("uk_dm_issue_active_code"));
        assertTrue(service.contains("body.containsKey(\"relatedMeetingMinutes\")"));
        assertTrue(service.contains("DataIntegrityViolationException"));
        assertTrue(controller.contains("data-migration:content:issues:create"));
        assertTrue(controller.contains("data-migration:content:issues:update"));
        assertTrue(controller.contains("data-migration:content:issues:delete"));
        assertTrue(controller.contains("data-migration:manage"));
    }

    @Test
    void meetingRelationsAndAttachmentLifecycleUseDedicatedStorage() throws Exception {
        String issue = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/IssueService.java"));
        String meeting = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/MeetingService.java"));
        String policy = Files.readString(Path.of("src/main/java/com/ccb/datamigration/integration/DataMigrationMeetingAttachmentAccessPolicy.java"));
        assertTrue(issue.contains("FROM dm_meeting WHERE tenant_id"));
        assertTrue(issue.contains("JOIN dm_meeting m ON m.meeting_id = r.related_id"));
        assertTrue(issue.contains("FROM dm_meeting WHERE meeting_id = ?"));
        assertTrue(Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/ContentAttachmentService.java")).contains("attachmentGateway.deleteBound"));
        assertTrue(Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/ContentAttachmentService.java")).contains("DELETE FROM dm_content_attachment WHERE tenant_id = ? AND business_type = ? AND business_id = ?"));
        assertTrue(meeting.contains("contentAttachments.unbindAndRemoveAll"));
        assertTrue(meeting.contains("related_type = 'MEETING'"));
        assertTrue(policy.contains("DATA_MIGRATION_MEETING") || policy.contains("MeetingService.BUSINESS_TYPE"));
    }

    @Test
    void genericAssetRecycleBinWritesDeletionAuditFields() throws Exception {
        String asset = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/ContentFileAssetService.java"));
        assertTrue(asset.contains("deleted_by = ?"));
        assertTrue(asset.contains("deleted_at = CURRENT_TIMESTAMP"));
        assertTrue(asset.contains("deleted_by = NULL"));
        assertTrue(asset.contains("deleted_at = NULL"));
    }

    @Test
    void v98RemovesCompatibilityColumnsAndClosesV96V97Gaps() throws Exception {
        String migration = Files.readString(Path.of("../../platform/infrastructure/src/main/resources/db/migration/V161__data_migration_remove_compatibility_columns.sql"));
        assertTrue(migration.contains("DROP TABLE IF EXISTS dm_topic_type"));
        assertTrue(migration.contains("DROP COLUMN project_name"));
        assertTrue(migration.contains("DROP COLUMN attachment_id"));
        assertTrue(migration.contains("DROP COLUMN file_name"));
        assertTrue(migration.contains("DROP COLUMN system_name"));
        assertTrue(migration.contains("DROP COLUMN table_code"));
        assertTrue(migration.contains("DROP COLUMN object_key"));
        assertTrue(migration.contains("uk_dm_meeting_att_active"));
        assertTrue(migration.contains("idx_dm_meeting_att_meeting (tenant_id, meeting_id"));
        assertTrue(migration.contains("ADD KEY idx_dm_meeting_project (tenant_id, project_id"));
        assertTrue(!Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/ContentFileAssetService.java")).contains("object_key"));
        assertTrue(!Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/ReportService.java")).contains("object_key"));
        // V177：字段表冗余 table_code 下线后（V161），关联键改名 table_id -> table_code，服务按业务编号关联。
        String targetTableMigration = Files.readString(Path.of("../../platform/infrastructure/src/main/resources/db/migration/V177__data_migration_target_table_code_pk.sql"));
        assertTrue(targetTableMigration.contains("CHANGE COLUMN table_id table_code"));
        assertTrue(Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/TargetTableService.java")).contains("t.table_code = f.table_code"));
        // V178（T42）：全模块下线 active_* 活动生成列，唯一键直接建在业务列。
        String activeRemovalMigration = Files.readString(Path.of("../../platform/infrastructure/src/main/resources/db/migration/V178__data_migration_active_uniqueness_columns_removal.sql"));
        assertTrue(activeRemovalMigration.contains("DROP COLUMN active_table_code"));
        assertTrue(activeRemovalMigration.contains("ADD UNIQUE KEY uk_target_table_en (tenant_id, project_id, system_code, table_name_en)"));
        assertTrue(activeRemovalMigration.contains("ADD UNIQUE KEY uk_dm_plan_dimension (tenant_id, project_id, granularity, plan_type, system_code)"));
        assertTrue(activeRemovalMigration.contains("DROP COLUMN active_issue_code"));
        String targetTableServiceSource = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/TargetTableService.java"));
        assertTrue(!targetTableServiceSource.contains("table_name_en = ? AND deleted = 0 AND table_code <>"));
        assertTrue(!Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/IssueService.java")).contains("issue_code = ? AND deleted = 0"));
    }

    @Test
    void v180TopicDomainAddsColumnsRelationsAndParameterSeeds() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V180__data_migration_topic_domain.sql");
        assertTrue(Files.exists(migration));
        String sql = Files.readString(migration);
        assertTrue(sql.contains("dm_topic ADD COLUMN granularity"));
        assertTrue(sql.contains("ADD COLUMN topic_type_code"));
        assertTrue(sql.contains("ADD COLUMN topic_summary"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS dm_topic_system"));
        assertTrue(sql.contains("uk_dm_topic_system (tenant_id, topic_id, system_code)"));
        assertTrue(sql.contains("idx_dm_topic_granularity"));
        assertTrue(sql.contains("idx_dm_topic_type"));
        assertTrue(sql.contains("'DM_TOPIC_PROJECT_TYPE'"));
        assertTrue(sql.contains("'DM_TOPIC_SYSTEM_TYPE'"));
        assertTrue(sql.contains("INSERT IGNORE INTO sys_config"));
        // 幂等初始化不覆盖管理员既有配置：仅 INSERT IGNORE，不允许覆盖既有名称/排序/状态。
        assertTrue(!sql.contains("UPDATE sys_config"));
        assertTrue(!sql.contains("UPDATE sys_dict_type"));
        assertTrue(!sql.contains("INSERT INTO sys_config "));
    }

    @Test
    void dmProjectIsDroppedFromFinalModelByV179() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V179__data_migration_drop_dm_project.sql");
        assertTrue(Files.exists(migration));
        assertTrue(Files.readString(migration).contains("DROP TABLE IF EXISTS dm_project"));
        assertTrue(!Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/ContentAssetTables.java")).contains("dm_project"));
    }

    @Test
    void ruleDomainUsesDedicatedStorageAndDropGenericRuleEnvelope() throws Exception {
        Path migration = Path.of("../../platform/infrastructure/src/main/resources/db/migration/V194__data_migration_rule_domain.sql");
        assertTrue(Files.exists(migration));
        String sql = Files.readString(migration);
        assertTrue(sql.contains("ALTER TABLE dm_rule DROP COLUMN doc_code"));
        assertTrue(sql.contains("ALTER TABLE dm_rule DROP COLUMN doc_name"));
        assertTrue(sql.contains("ADD COLUMN check_target_type"));
        assertTrue(sql.contains("ADD COLUMN rule_category"));
        assertTrue(sql.contains("ADD COLUMN rule_code"));
        assertTrue(sql.contains("ADD COLUMN table_name_en"));
        assertTrue(sql.contains("'DM_RULE_TARGET_TYPE'"));
        assertTrue(sql.contains("'DM_RULE_CATEGORY'"));
        assertTrue(sql.contains("INSERT IGNORE INTO sys_config"));

        String service = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/RuleService.java"));
        assertTrue(service.contains("FROM dm_rule"));
        assertTrue(service.contains("entity_type, entity_id"));
        assertTrue(service.contains("'RULE'"));
        assertTrue(service.contains("RULE_CREATE"));
        assertTrue(service.contains("RULE_IMPORT"));

        String structured = Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/StructuredAssetService.java"));
        assertTrue(!structured.contains("PARAMETER"));
        assertTrue(!structured.contains("RULE"));
        assertTrue(Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/ParameterController.java")).contains("/api/data-migration/parameters"));
        assertTrue(!Files.readString(Path.of("src/main/java/com/ccb/datamigration/web/StructuredAssetController.java")).contains("/rules/import"));
        assertTrue(Files.readString(Path.of("src/main/java/com/ccb/datamigration/service/RuleRecycleBinSource.java")).contains("supports()"));
    }

}

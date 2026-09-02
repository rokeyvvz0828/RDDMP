package com.ccb.architecture.service;

import com.ccb.architecture.model.DeploymentUnitModels.ImportBatchStatus;
import com.ccb.architecture.service.DeploymentUnitImportService.ImportBatchView;
import com.ccb.architecture.service.DeploymentUnitImportService.ImportItemView;
import com.ccb.architecture.persistence.DeploymentUnitStore;
import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemReferenceQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 部署单元初始化导入 MySQL 集成测试：预览、确认、幂等重导、失败明细、整批回滚。
 */
@Testcontainers
class DeploymentUnitImportMySqlTest {
    private static final String DATABASE = "deployment_unit_import";
    private static final long TENANT_ID = 1L;
    private static final long PHYSICAL_ID = 501L;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

    private static JdbcTemplate jdbc;
    private static DeploymentUnitImportService importService;
    private static DeploymentUnitService unitService;
    private static AtomicInteger identifiers = new AtomicInteger(90_000);

    private final AuthUser actor = new AuthUser(88L, TENANT_ID, "tech", "-", "技术架构师", 1L, true);

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion("124"))
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        DeploymentUnitStore store = new DeploymentUnitStore(jdbc);
        LongSupplier idSupplier = () -> identifiers.incrementAndGet();
        unitService = new DeploymentUnitService(store, new DeploymentUnitReferenceGuard(java.util.List.of()),
                mock(SystemReferenceQuery.class), mock(SystemOperationAudit.class), transactions, idSupplier);
        importService = new DeploymentUnitImportService(store, unitService, mock(SystemReferenceQuery.class),
                mock(SystemOperationAudit.class), transactions, new ObjectMapper());
    }

    @BeforeEach
    void seedArchitecture() {
        jdbc.update("DELETE FROM arch_deployment_unit_relation_history");
        jdbc.update("DELETE FROM arch_deployment_unit_relation");
        jdbc.update("DELETE FROM arch_deployment_unit_version");
        jdbc.update("DELETE FROM arch_deployment_unit_import_item");
        jdbc.update("DELETE FROM arch_deployment_unit");
        jdbc.update("DELETE FROM arch_deployment_unit_import_batch");
        jdbc.update("DELETE FROM arch_deployment_unit_number_seq");
        jdbc.update("DELETE FROM arch_physical_subsystem WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("INSERT INTO arch_physical_subsystem "
                        + "(id, tenant_id, code, short_name, name, logical_subsystem_name, responsible_team_org_id,"
                        + " responsible_team_name_snapshot, status, row_version, created_by, updated_by) "
                        + "VALUES (?, ?, 'W0001A', '渠道接入', '渠道接入系统', '渠道域逻辑子系统', 1, '渠道团队', 'ACTIVE', 0, 1, 1)",
                PHYSICAL_ID, TENANT_ID);
    }

    @AfterAll
    static void clearStatics() {
        jdbc = null;
        importService = null;
        unitService = null;
    }

    @Test
    void uploadPreviewConfirmCreatesUnitsWithNumbers() {
        byte[] file = workbook(
                row("W0001A", "ECIP_AP", "应用", "渠道接入", null),
                row("W0001A", "ECIP_DB", "数据库", null, null),
                row("W0001A", "ECIP_WB", "Web", null, null));

        ImportBatchView preview = importService.upload(actor, multipart(file), "trace");
        assertThat(preview.batch().status()).isEqualTo(ImportBatchStatus.PREVIEW.name());
        assertThat(preview.batch().totalRows()).isEqualTo(3);
        assertThat(preview.batch().validRows()).isEqualTo(3);
        assertThat(preview.items()).hasSize(3);

        ImportBatchView confirmed = importService.confirm(actor, preview.batch().id(), "trace");
        assertThat(confirmed.batch().status()).isEqualTo(ImportBatchStatus.SUCCESS.name());
        assertThat(confirmed.batch().successRows()).isEqualTo(3);

        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_deployment_unit WHERE tenant_id = ?",
                Long.class, TENANT_ID);
        assertThat(count).isEqualTo(3);
        String code = jdbc.queryForObject(
                "SELECT code FROM arch_deployment_unit WHERE tenant_id = ? AND name = ?",
                String.class, TENANT_ID, "ECIP_AP");
        assertThat(code).isEqualTo("DW0001A001");
        String dbCode = jdbc.queryForObject(
                "SELECT code FROM arch_deployment_unit WHERE tenant_id = ? AND name = ?",
                String.class, TENANT_ID, "ECIP_DB");
        assertThat(dbCode).isEqualTo("DW0001A002");
        Integer versions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_deployment_unit_version WHERE tenant_id = ?", Integer.class, TENANT_ID);
        assertThat(versions).isEqualTo(3);
    }

    @Test
    void previewMarksInvalidRowsAndConfirmIsPartial() {
        byte[] file = workbook(
                row("W0001A", "ECIP_AP", "应用", null, null),
                row("W0001A", "BAD_AP", "中间件", null, null),
                row("NO-SUCH", "MISSING_AP", "应用", null, null),
                row("W0001A", "ECIP_AP", "应用", null, null));

        ImportBatchView preview = importService.upload(actor, multipart(file), "trace");
        assertThat(preview.batch().totalRows()).isEqualTo(4);
        assertThat(preview.batch().validRows()).isEqualTo(1);
        assertThat(preview.items().stream().filter(item -> item.rowStatus().equals("INVALID")).count())
                .isEqualTo(3);

        ImportBatchView confirmed = importService.confirm(actor, preview.batch().id(), "trace");
        assertThat(confirmed.batch().status()).isEqualTo(ImportBatchStatus.PARTIAL.name());
        assertThat(confirmed.batch().successRows()).isEqualTo(1);
        assertThat(confirmed.batch().failedRows()).isEqualTo(3);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_deployment_unit WHERE tenant_id = ?",
                Long.class, TENANT_ID);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void reimportIsIdempotentAndSkipsExistingActiveUnits() {
        byte[] file = workbook(
                row("W0001A", "ECIP_AP", "应用", null, null));

        ImportBatchView first = importService.confirm(actor, importService.upload(actor, multipart(file), "t1").batch().id(), "t1");
        assertThat(first.batch().successRows()).isEqualTo(1);

        ImportBatchView preview = importService.upload(actor, multipart(file), "t2");
        ImportItemView item = preview.items().get(0);
        assertThat(item.rowStatus()).isEqualTo("VALID");
        assertThat(item.note()).contains("跳过");

        ImportBatchView second = importService.confirm(actor, preview.batch().id(), "t2");
        assertThat(second.batch().status()).isEqualTo(ImportBatchStatus.SUCCESS.name());
        assertThat(second.batch().successRows()).isEqualTo(1);
        assertThat(second.batch().skippedRows()).isEqualTo(1);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_deployment_unit WHERE tenant_id = ?",
                Long.class, TENANT_ID);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void nameConflictWithVoidedUnitFailsRow() {
        unitService.create(actor, new com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitCommand(
                PHYSICAL_ID, "ECIP_AP", "APPLICATION", List.of(), null, null, null, null), "seed");
        jdbc.update("UPDATE arch_deployment_unit SET status = 'VOIDED' WHERE tenant_id = ? AND name = ?",
                TENANT_ID, "ECIP_AP");

        byte[] file = workbook(row("W0001A", "ECIP_AP", "应用", null, null));
        ImportBatchView preview = importService.upload(actor, multipart(file), "trace");
        assertThat(preview.items().get(0).rowStatus()).isEqualTo("INVALID");
        assertThat(preview.items().get(0).errorMessage()).contains("停用或作废");
    }

    @Test
    void confirmRejectsFinishedBatch() {
        byte[] file = workbook(row("W0001A", "ECIP_AP", "应用", null, null));
        ImportBatchView preview = importService.upload(actor, multipart(file), "trace");
        importService.confirm(actor, preview.batch().id(), "trace");

        assertThatThrownBy(() -> importService.confirm(actor, preview.batch().id(), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(error.getMessage()).contains("不能重复确认"));
    }

    @Test
    void unexpectedRowFailureRollsBackWholeBatchAndMarksFailed() {
        byte[] file = workbook(row("W0001A", "ECIP_AP", "应用", null, null));
        ImportBatchView preview = importService.upload(actor, multipart(file), "trace");

        jdbc.update("UPDATE arch_deployment_unit_import_item SET raw_json = '[\"only-one\"]' "
                + "WHERE tenant_id = ? AND batch_id = ?", TENANT_ID, preview.batch().id());

        assertThatThrownBy(() -> importService.confirm(actor, preview.batch().id(), "trace"))
                .isInstanceOf(BusinessException.class);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_deployment_unit WHERE tenant_id = ?",
                Long.class, TENANT_ID);
        assertThat(count).isZero();
        String status = jdbc.queryForObject(
                "SELECT status FROM arch_deployment_unit_import_batch WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, preview.batch().id());
        assertThat(status).isEqualTo(ImportBatchStatus.FAILED.name());
    }

    @Test
    void errorReportContainsOnlyInvalidAndFailedRows() {
        byte[] file = workbook(
                row("W0001A", "ECIP_AP", "应用", null, null),
                row("W0001A", "BAD_AP", "中间件", null, null));
        ImportBatchView preview = importService.upload(actor, multipart(file), "trace");

        byte[] report = importService.errorReport(actor, preview.batch().id());
        String csv = new String(report, StandardCharsets.UTF_8);
        assertThat(csv).contains("物理子系统编号");
        assertThat(csv).contains("BAD_AP");
        assertThat(csv).contains("部署单元类型仅支持");
        assertThat(csv).doesNotContain("ECIP_AP\n");
    }

    @Test
    void templateDownloadsValidWorkbook() {
        byte[] template = importService.template();
        assertThat(template.length).isGreaterThan(1_000);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(template))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            assertThat(List.of(
                    header.getCell(0).getStringCellValue(),
                    header.getCell(1).getStringCellValue(),
                    header.getCell(2).getStringCellValue(),
                    header.getCell(3).getStringCellValue(),
                    header.getCell(4).getStringCellValue()))
                    .containsExactly("物理子系统编号", "部署单元名称", "部署单元类型", "描述", "备注");
            Row sample = workbook.getSheetAt(0).getRow(1);
            assertThat(List.of(
                    sample.getCell(0).getStringCellValue(),
                    sample.getCell(1).getStringCellValue(),
                    sample.getCell(2).getStringCellValue(),
                    sample.getCell(3).getStringCellValue(),
                    sample.getCell(4).getStringCellValue()))
                    .containsExactly("W0001A", "ECIP_AP", "应用", "渠道接入服务（示例数据）", "演示用，可删除");
            assertThat(sample.getCell(5)).isNull();
        } catch (Exception exception) {
            throw new AssertionError("模板不是有效 xlsx", exception);
        }

        ImportBatchView preview = importService.upload(actor, multipart(template), "trace");
        assertThat(preview.batch().validRows()).isEqualTo(1);
        assertThat(preview.items()).singleElement()
                .satisfies(item -> assertThat(item.rowStatus()).isEqualTo("VALID"));
    }

    // ---------- 工具 ----------

    private String[] row(String physicalCode, String name, String kind, String description, String remark) {
        return new String[]{physicalCode, name, kind, description, remark};
    }

    private byte[] workbook(String[]... rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("导入");
            Row header = sheet.createRow(0);
            String[] headers = {"物理子系统编号", "部署单元名称", "部署单元类型", "描述", "备注"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int c = 0; c < rows[i].length; c++) {
                    if (rows[i][c] != null) {
                        row.createCell(c).setCellValue(rows[i][c]);
                    }
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("测试文件生成失败", exception);
        }
    }

    private MockMultipartFile multipart(byte[] content) {
        return new MockMultipartFile("file", "deployment-units.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    private static String migrationDirectory() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("server/src/platform/infrastructure/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)) {
                return candidate.toString();
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("找不到 Flyway 迁移目录");
    }
}

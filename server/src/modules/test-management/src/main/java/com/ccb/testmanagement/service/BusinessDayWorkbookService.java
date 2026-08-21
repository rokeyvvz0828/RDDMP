/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/BusinessDayWorkbookService.java
 * 说明：营业日日历与跑批需求 XLSX 文件转换服务。
 * 用途：生成模板和导出工作簿，并在限额内解析不可信日历导入文件。
 * 作者：hengguan
 */
package com.ccb.testmanagement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BusinessDayWorkbookService {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 2000;
    private static final List<String> SCHEDULE_HEADERS = List.of(
            "测试环境", "自然日期", "营业日期", "是否跑批", "批处理类型", "跑批时间", "涉及系统", "跑批验证内容");
    private static final Map<String, List<String>> SCHEDULE_HEADER_ALIASES = Map.of(
            "env_code", List.of("测试环境", "环境编码"),
            "natural_date", List.of("自然日期", "自然日"),
            "business_date", List.of("营业日期", "营业日"),
            "has_batch", List.of("是否跑批"),
            "batch_type", List.of("批处理类型", "跑批类型"),
            "batch_time", List.of("跑批时间"),
            "systems", List.of("涉及系统"),
            "validation_content", List.of("跑批验证内容", "验证内容"),
            "maintainer", List.of("维护人"));
    private static final List<String> REQUIREMENT_HEADERS = List.of(
            "环境编码", "需求日期", "营业日", "是否跑批", "跑批类型", "跑批时间", "涉及系统", "验证内容",
            "提出人", "提出人组织", "提出人手机", "采纳状态", "评审人", "评审意见", "创建时间");
    private final DataFormatter formatter = new DataFormatter();

    // 关键逻辑：解析前同时限制扩展名、5MB 文件大小和 2000 行；按 RADAR 表头读取并兼容已发布的 RDDMP 旧表头。
    public List<Map<String, Object>> parseSchedules(MultipartFile file) {
        if (file == null || file.isEmpty()) throw bad("请选择 XLSX 文件");
        if (file.getSize() > MAX_FILE_SIZE) throw bad("导入文件不能超过 5MB");
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".xlsx")) throw bad("仅支持 XLSX 文件");
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.getBytes()))) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) throw bad("导入文件没有工作表数据");
            Map<String, Integer> columns = scheduleColumns(sheet.getRow(0));
            int last = Math.min(sheet.getLastRowNum(), MAX_ROWS);
            if (sheet.getLastRowNum() > MAX_ROWS) throw bad("单次最多导入 2000 行");
            List<Map<String, Object>> records = new ArrayList<>();
            for (int index = 1; index <= last; index++) {
                Row row = sheet.getRow(index);
                if (row == null || empty(row, SCHEDULE_HEADERS.size())) continue;
                Map<String, Object> record = new LinkedHashMap<>();
                String batchType = text(row, columns.get("batch_type"));
                String batchTime = text(row, columns.get("batch_time"));
                List<String> systems = splitSystems(text(row, columns.get("systems")));
                String validation = text(row, columns.get("validation_content"));
                record.put("env_code", text(row, columns.get("env_code")));
                record.put("natural_date", importedDate(row.getCell(columns.get("natural_date")), false));
                record.put("business_date", importedDate(row.getCell(columns.get("business_date")), true));
                // RADAR 会在跑批字段有值时自动推断为跑批，兼容“是否跑批”留空的历史文件。
                record.put("has_batch", yes(text(row, columns.get("has_batch")))
                        || !batchType.isBlank() || !batchTime.isBlank() || !systems.isEmpty() || !validation.isBlank());
                record.put("batch_type", batchType);
                record.put("batch_time", batchTime);
                record.put("systems", systems);
                record.put("validation_content", validation);
                Integer maintainer = columns.get("maintainer");
                if (maintainer != null) record.put("maintainer", text(row, maintainer));
                records.add(record);
            }
            return records;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw bad("XLSX 解析失败，请使用下载的模板并检查文件内容");
        }
    }

    public byte[] scheduleTemplate() {
        Map<String, Object> businessDay = new LinkedHashMap<>();
        businessDay.put("env_code", "PL1");
        businessDay.put("natural_date", "2026-08-12");
        businessDay.put("business_date", "20260812");
        businessDay.put("has_batch", false);
        Map<String, Object> batchDay = new LinkedHashMap<>();
        batchDay.put("env_code", "PL1");
        batchDay.put("natural_date", "2026-08-13");
        batchDay.put("business_date", "20260814");
        batchDay.put("has_batch", true);
        batchDay.put("batch_type", "增量");
        batchDay.put("batch_time", "18:00");
        batchDay.put("systems", List.of("全部系统"));
        batchDay.put("validation_content", "日终验证");
        return scheduleWorkbook(List.of(businessDay, batchDay), "日历安排模板");
    }

    public byte[] schedules(List<Map<String, Object>> rows) {
        return scheduleWorkbook(rows, "日历安排");
    }

    public byte[] requirements(List<Map<String, Object>> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("跑批需求");
            writeHeader(workbook, sheet, REQUIREMENT_HEADERS);
            int index = 1;
            for (Map<String, Object> row : rows) {
                Row excel = sheet.createRow(index++);
                write(excel, 0, row.get("env_code"));
                write(excel, 1, row.get("natural_date"));
                write(excel, 2, row.get("business_date"));
                write(excel, 3, truthy(row.get("has_batch")) ? "是" : "否");
                write(excel, 4, row.get("batch_type"));
                write(excel, 5, timeText(row.get("batch_time")));
                write(excel, 6, joinSystems(row.get("systems")));
                write(excel, 7, row.get("validation_content"));
                write(excel, 8, row.get("proposer_name"));
                write(excel, 9, row.get("proposer_org_name"));
                write(excel, 10, row.get("proposer_mobile_phone"));
                write(excel, 11, adoptionLabel(row.get("adoption")));
                write(excel, 12, row.get("reviewer_name"));
                write(excel, 13, row.get("review_comment"));
                write(excel, 14, row.get("created_at"));
            }
            finish(sheet, REQUIREMENT_HEADERS.size());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "跑批需求导出失败");
        }
    }

    private byte[] scheduleWorkbook(List<Map<String, Object>> rows, String title) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(title);
            writeHeader(workbook, sheet, SCHEDULE_HEADERS);
            int index = 1;
            for (Map<String, Object> row : rows) {
                Row excel = sheet.createRow(index++);
                write(excel, 0, row.get("env_code"));
                write(excel, 1, row.get("natural_date"));
                write(excel, 2, row.get("business_date"));
                write(excel, 3, truthy(row.get("has_batch")) ? "是" : "否");
                write(excel, 4, row.get("batch_type"));
                write(excel, 5, timeText(row.get("batch_time")));
                write(excel, 6, joinSystems(row.get("systems")));
                write(excel, 7, row.get("validation_content"));
            }
            finish(sheet, SCHEDULE_HEADERS.size());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "日历工作簿生成失败");
        }
    }

    private Map<String, Integer> scheduleColumns(Row row) {
        if (row == null) throw bad("导入模板缺少表头");
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Cell cell : row) {
            String title = formatter.formatCellValue(cell).trim();
            SCHEDULE_HEADER_ALIASES.forEach((key, aliases) -> {
                if (aliases.contains(title)) result.put(key, cell.getColumnIndex());
            });
        }
        for (String key : List.of("env_code", "natural_date", "business_date", "has_batch", "batch_type", "batch_time", "systems", "validation_content")) {
            if (!result.containsKey(key)) throw bad("导入模板缺少表头“" + SCHEDULE_HEADER_ALIASES.get(key).get(0) + "”");
        }
        return result;
    }

    // 关键逻辑：Excel 日期对象和序列使用 POI 的 1900 日期系统转换，文本日期按 RADAR 支持的多种格式规范化，避免时区换算偏移一天。
    private String importedDate(Cell cell, boolean compact) {
        if (cell == null) return "";
        LocalDate parsed = null;
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            double numeric = cell.getNumericCellValue();
            if (DateUtil.isCellDateFormatted(cell)
                    || (numeric >= 30000 && numeric <= 60000 && numeric == Math.rint(numeric))) {
                parsed = DateUtil.getLocalDateTime(numeric).toLocalDate();
            }
        }
        if (parsed == null) parsed = parseImportedDate(formatter.formatCellValue(cell).trim());
        if (parsed == null) return formatter.formatCellValue(cell).trim();
        return parsed.format(compact ? DateTimeFormatter.BASIC_ISO_DATE : DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private LocalDate parseImportedDate(String value) {
        String[] parts = null;
        java.util.regex.Matcher compact = java.util.regex.Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})$").matcher(value);
        java.util.regex.Matcher separated = java.util.regex.Pattern.compile("^(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})(?:\\D.*)?$").matcher(value);
        java.util.regex.Matcher chinese = java.util.regex.Pattern.compile("^(\\d{4})年\\s*(\\d{1,2})月\\s*(\\d{1,2})日?.*$").matcher(value);
        java.util.regex.Matcher match = compact.matches() ? compact : separated.matches() ? separated : chinese.matches() ? chinese : null;
        if (match != null) parts = new String[]{match.group(1), match.group(2), match.group(3)};
        if (parts == null) return null;
        try { return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])); }
        catch (DateTimeException exception) { return null; }
    }

    private void writeHeader(Workbook workbook, Sheet sheet, List<String> headers) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.size() - 1));
    }

    private void finish(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 768, 12000));
        }
    }

    // 关键逻辑：用户输入以公式起始字符开头时加单引号，避免导出的工作簿触发公式注入。
    private void write(Row row, int column, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        row.createCell(column).setCellValue(text);
    }

    private String text(Row row, int column) {
        if (column < 0) return "";
        Cell cell = row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private boolean empty(Row row, int columns) {
        for (int i = 0; i < columns; i++) if (!text(row, i).isEmpty()) return false;
        return true;
    }

    private boolean yes(String value) {
        return List.of("是", "true", "1", "yes", "y").contains(value.toLowerCase());
    }

    private List<String> splitSystems(String value) {
        if (value.isBlank()) return List.of();
        return Arrays.stream(value.split("[,，、;；]"))
                .map(String::trim).filter(text -> !text.isEmpty()).distinct().toList();
    }

    private String joinSystems(Object value) {
        if (value instanceof List<?> list) return String.join("、", list.stream().map(String::valueOf).toList());
        return value == null ? "" : String.valueOf(value);
    }

    private String timeText(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        return text.length() >= 5 ? text.substring(0, 5) : text;
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private String adoptionLabel(Object value) {
        return switch (String.valueOf(value)) {
            case "ACCEPTED" -> "采纳";
            case "REJECTED" -> "不采纳";
            default -> "待定";
        };
    }

    private BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
}

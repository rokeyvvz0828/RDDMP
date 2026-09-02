/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/TestConfigurationWorkbookService.java
 * 说明：测试管理的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.service;

// 关键逻辑：文件输入输出在模块内限制格式、大小和字段边界；异常内容不能绕过既有业务校验。

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 仅处理受控的 XLSX 模板，导入业务校验与事务由配置领域服务负责。 */
@Service
public class TestConfigurationWorkbookService {
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 2000;
    private final DataFormatter formatter = new DataFormatter();

    public byte[] systemTemplate() { return workbook("参测系统导入", List.of("物理子系统编号", "是否参测", "备注"), List.of(List.of("PHYSICAL_DEMO", "是", "可参与本轮测试"))); }
    public byte[] roleTemplate() { return workbook("系统角色导入", List.of("物理子系统编号", "用户ID", "角色编码"), List.of(List.of("PHYSICAL_DEMO", "10001", "TEST_EXECUTOR"))); }

    public List<Map<String,Object>> systems(MultipartFile file) { return rows(file, List.of("物理子系统编号", "是否参测"), List.of("physical_code", "enabled"), List.of("备注"), List.of("remark")); }
    public List<Map<String,Object>> roles(MultipartFile file) { return rows(file, List.of("物理子系统编号", "用户ID", "角色编码"), List.of("physical_code", "user_id", "role_code"), List.of(), List.of()); }

    private List<Map<String,Object>> rows(MultipartFile file, List<String> required, List<String> keys, List<String> optional, List<String> optionalKeys) {
        validate(file);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.getBytes()))) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) throw bad("导入文件没有工作表数据");
            Map<String,Integer> columns = columns(sheet.getRow(0), required, keys, optional, optionalKeys);
            if (sheet.getLastRowNum() > MAX_ROWS) throw bad("单次最多导入 2000 行");
            List<Map<String,Object>> result = new ArrayList<>();
            for (int rowNo=1; rowNo<=sheet.getLastRowNum(); rowNo++) {
                Row row = sheet.getRow(rowNo); if (row == null || empty(row, columns)) continue;
                Map<String,Object> values = new LinkedHashMap<>(); values.put("row_number", rowNo + 1);
                columns.forEach((key,index) -> values.put(key, text(row, index)));
                result.add(values);
            }
            if (result.isEmpty()) throw bad("导入文件没有有效数据行");
            return result;
        } catch (BusinessException exception) { throw exception; }
        catch (IOException | RuntimeException exception) { throw bad("XLSX 解析失败，请使用下载的模板并检查文件内容"); }
    }

    private Map<String,Integer> columns(Row header,List<String> required,List<String> keys,List<String> optional,List<String> optionalKeys) {
        if (header == null) throw bad("导入模板缺少表头");
        Map<String,Integer> result = new LinkedHashMap<>();
        for (Cell cell : header) {
            String title = formatter.formatCellValue(cell).trim();
            int index = required.indexOf(title); if (index >= 0) result.put(keys.get(index), cell.getColumnIndex());
            int optionalIndex = optional.indexOf(title); if (optionalIndex >= 0) result.put(optionalKeys.get(optionalIndex), cell.getColumnIndex());
        }
        for (int index=0;index<keys.size();index++) if(!result.containsKey(keys.get(index))) throw bad("导入模板缺少表头“"+required.get(index)+"”");
        return result;
    }

    private byte[] workbook(String name,List<String> headers,List<List<String>> examples) {
        try (Workbook workbook=new XSSFWorkbook(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            Sheet sheet=workbook.createSheet(name); CellStyle style=workbook.createCellStyle(); style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); Font font=workbook.createFont(); font.setBold(true); style.setFont(font);
            Row header=sheet.createRow(0); for(int i=0;i<headers.size();i++){Cell cell=header.createCell(i);cell.setCellValue(headers.get(i));cell.setCellStyle(style);}
            int rowNo=1;for(List<String> example:examples){Row row=sheet.createRow(rowNo++);for(int i=0;i<example.size();i++)row.createCell(i).setCellValue(example.get(i));}
            sheet.createFreezePane(0,1);for(int i=0;i<headers.size();i++){sheet.autoSizeColumn(i);sheet.setColumnWidth(i,Math.min(sheet.getColumnWidth(i)+768,12000));} workbook.write(output);return output.toByteArray();
        } catch(IOException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR,"导入模板生成失败"); }
    }
    private void validate(MultipartFile file){if(file==null||file.isEmpty())throw bad("请选择 XLSX 文件");if(file.getSize()>MAX_SIZE)throw bad("导入文件不能超过 5MB");String name=file.getOriginalFilename();if(name==null||!name.toLowerCase().endsWith(".xlsx"))throw bad("仅支持 XLSX 文件");}
    private String text(Row row,int index){Cell cell=row.getCell(index);return cell==null?"":formatter.formatCellValue(cell).trim();}
    private boolean empty(Row row,Map<String,Integer> columns){for(int index:columns.values())if(!text(row,index).isEmpty())return false;return true;}
    private BusinessException bad(String message){return new BusinessException(ErrorCode.BAD_REQUEST,message);}
}

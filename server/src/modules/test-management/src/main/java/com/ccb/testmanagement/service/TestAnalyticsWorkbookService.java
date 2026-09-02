/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/TestAnalyticsWorkbookService.java
 * 说明：测试管理的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.service;

// 关键逻辑：文件输入输出在模块内限制格式、大小和字段边界；异常内容不能绕过既有业务校验。
import org.apache.poi.ss.usermodel.*;import org.apache.poi.xssf.usermodel.XSSFWorkbook;import org.springframework.stereotype.Service;import java.io.*;import java.util.*;
@Service public class TestAnalyticsWorkbookService { public byte[] export(Map<String,Object> data){try(Workbook b=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){Sheet s=b.createSheet("分析统计");int r=0;Row title=s.createRow(r++);title.createCell(0).setCellValue(String.valueOf(data.getOrDefault("report_key",data.getOrDefault("key","分析统计"))));Object rawSummary=data.get("summary");if(rawSummary instanceof Map<?,?> summary)for(Map.Entry<?,?> e:summary.entrySet())if(!(e.getValue() instanceof Collection<?>)){Row row=s.createRow(r++);row.createCell(0).setCellValue(String.valueOf(e.getKey()));row.createCell(1).setCellValue(String.valueOf(e.getValue()));}r++;Object rawRows=data.containsKey("rows")?data.get("rows"):data.getOrDefault("table",List.of());List<?> rows=rawRows instanceof List<?> list?list:List.of();if(!rows.isEmpty()&&rows.get(0) instanceof Map<?,?> first){Row h=s.createRow(r++);int c=0;for(Object k:first.keySet())h.createCell(c++).setCellValue(String.valueOf(k));for(Object item:rows){if(!(item instanceof Map<?,?> values))continue;Row row=s.createRow(r++);c=0;for(Object v:values.values())row.createCell(c++).setCellValue(String.valueOf(v==null?"":v));}}for(int i=0;i<12;i++)s.autoSizeColumn(i);b.write(out);return out.toByteArray();}catch(IOException e){throw new IllegalStateException("统计导出失败",e);}}}

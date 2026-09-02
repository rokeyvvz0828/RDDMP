/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/service/TestReportDocumentService.java
 * 说明：测试管理的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.service;

// 关键逻辑：文件输入输出在模块内限制格式、大小和字段边界；异常内容不能绕过既有业务校验。

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 报告导出使用真正的 OOXML Word；PDF 采用无依赖的基础文本 PDF，确保离线环境可下载。 */
@Service public class TestReportDocumentService {
 public byte[] docx(Map<String,Object> detail){try(XWPFDocument doc=new XWPFDocument();ByteArrayOutputStream out=new ByteArrayOutputStream()){title(doc,String.valueOf(((Map<?,?>)detail.get("report")).get("report_name")));Map<?,?> facts=(Map<?,?>)detail.get("snapshot");for(String k:List.of("scope_total","case_total","execution_total","execution_success","execution_failed","execution_blocked","execution_rate","success_rate","defect_total","defect_open")){line(doc,k+"："+String.valueOf(facts.get(k)==null?"0":facts.get(k)));}for(Map<?,?> s:(List<Map<?,?>>)detail.getOrDefault("supplements",List.of())){title(doc,String.valueOf(s.get("chapter_code")));line(doc,strip(String.valueOf(s.get("content_html")==null?"":s.get("content_html"))));}doc.write(out);return out.toByteArray();}catch(Exception e){throw new IllegalStateException("Word 导出失败",e);}}
 public byte[] pdf(Map<String,Object> detail){List<String> lines=new ArrayList<>();Map<?,?> report=(Map<?,?>)detail.get("report");lines.add("Test Report: "+report.get("report_name"));Map<?,?> facts=(Map<?,?>)detail.get("snapshot");facts.forEach((k,v)->{if(!(v instanceof Collection<?>))lines.add(k+": "+v);});StringBuilder content=new StringBuilder("BT /F1 11 Tf 50 790 Td ");for(String line:lines){content.append("(").append(escape(line)).append(") Tj 0 -16 Td ");}content.append("ET");String body=content.toString();List<String> objects=List.of("<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R] /Count 1 >>","<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>","<< /Length "+body.getBytes(StandardCharsets.ISO_8859_1).length+" >>\nstream\n"+body+"\nendstream","<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");StringBuilder out=new StringBuilder("%PDF-1.4\n");List<Integer> pos=new ArrayList<>();for(int i=0;i<objects.size();i++){pos.add(out.length());out.append(i+1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");}int start=out.length();out.append("xref\n0 ").append(objects.size()+1).append("\n0000000000 65535 f \n");for(int p:pos)out.append(String.format(Locale.ROOT,"%010d 00000 n \n",p));out.append("trailer << /Size ").append(objects.size()+1).append(" /Root 1 0 R >>\nstartxref\n").append(start).append("\n%%EOF");return out.toString().getBytes(StandardCharsets.ISO_8859_1);}
 private void title(XWPFDocument d,String s){XWPFParagraph p=d.createParagraph();p.createRun().setBold(true);p.createRun().setText(s);}private void line(XWPFDocument d,String s){d.createParagraph().createRun().setText(s);}private String strip(String s){return s.replaceAll("<[^>]*>"," ").replace("&nbsp;"," ").trim();}private String escape(String s){return s.replaceAll("[^\\x20-\\x7E]","?").replace("\\","\\\\").replace("(","\\(").replace(")","\\)");}
}

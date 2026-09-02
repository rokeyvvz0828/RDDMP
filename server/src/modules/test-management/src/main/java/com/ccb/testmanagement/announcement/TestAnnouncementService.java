/*
 * 文件：server/src/modules/test-management/src/main/java/com/ccb/testmanagement/announcement/TestAnnouncementService.java
 * 说明：测试公告板的服务、策略或接口实现。
 * 用途：承载模块边界内的查询、校验、事务、权限或文件处理职责。
 * 作者：hengguan
 */
package com.ccb.testmanagement.announcement;

// 关键逻辑：所有读写以认证用户的租户、测试大类和项目为共同边界；写入由事务与审计保持一致性。

import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.integration.AttachmentItem;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TestAnnouncementService {
    public static final String BUSINESS_TYPE = "TEST_ANNOUNCEMENT";
    private static final Set<String> DOMAINS=Set.of("application-assembly","user-testing","non-functional","security");
    private static final Pattern STYLE_ATTRIBUTE=Pattern.compile("(?is)\\sstyle\\s*=\\s*(['\"])(.*?)\\1");
    private final JdbcTemplate jdbc; private final AttachmentGateway attachments;
    public TestAnnouncementService(JdbcTemplate jdbc, AttachmentGateway attachments){this.jdbc=jdbc;this.attachments=attachments;}
    public List<Map<String,Object>> projects(AuthUser user){return jdbc.queryForList("SELECT id,project_code,project_name FROM pm_project WHERE tenant_id=? AND deleted=0 ORDER BY project_name,id",user.tenantId());}
    public List<Map<String,Object>> current(String domain,long projectId,AuthUser user){
        scope(domain,projectId,user); List<Map<String,Object>> pinned=jdbc.queryForList("SELECT * FROM tm_test_announcement WHERE tenant_id=? AND test_domain=? AND project_id=? AND deleted=0 AND pinned=1 ORDER BY pinned_at DESC,id DESC",user.tenantId(),domain,projectId);
        if(pinned.isEmpty()) pinned=jdbc.queryForList("SELECT * FROM tm_test_announcement WHERE tenant_id=? AND test_domain=? AND project_id=? AND deleted=0 ORDER BY published_at DESC,id DESC LIMIT 1",user.tenantId(),domain,projectId);
        return detailRows(pinned,user);
    }
    public PageResult<Map<String,Object>> list(String domain,long projectId,PageQuery page,String keyword,String publisher,String from,String to,AuthUser user){
        scope(domain,projectId,user); List<Object>a=new ArrayList<>(List.of(user.tenantId(),domain,projectId)); StringBuilder w=new StringBuilder(" WHERE tenant_id=? AND test_domain=? AND project_id=? AND deleted=0");
        if(has(keyword)){w.append(" AND title LIKE ?");a.add("%"+keyword.trim()+"%");} if(has(publisher)){w.append(" AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id=tm_test_announcement.published_by AND u.tenant_id=tm_test_announcement.tenant_id AND u.deleted=0 AND u.display_name LIKE ?)");a.add("%"+publisher.trim()+"%");} if(has(from)){w.append(" AND published_at>=?");a.add(from+" 00:00:00");} if(has(to)){w.append(" AND published_at<=?");a.add(to+" 23:59:59");}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_announcement"+w,Long.class,a.toArray()); List<Object>b=new ArrayList<>(a);b.add((page.page()-1)*page.size());b.add(page.size());
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,title,REGEXP_REPLACE(content_html,'<[^>]*>','') AS summary,pinned,pinned_at,published_by,(SELECT u.display_name FROM sys_user u WHERE u.id=tm_test_announcement.published_by AND u.tenant_id=tm_test_announcement.tenant_id AND u.deleted=0) AS publisher_name,published_at,last_edited_by,last_edited_at FROM tm_test_announcement"+w+" ORDER BY pinned DESC,pinned_at DESC,published_at DESC,id DESC LIMIT ?,?",b.toArray());
        for(Map<String,Object> row:rows) row.put("attachment_count",count(number(row.get("id")),user.tenantId())); return new PageResult<>(rows,total==null?0:total,page.page(),page.size());
    }
    public Map<String,Object> detail(String domain,long projectId,long id,AuthUser user){scope(domain,projectId,user);return detailRow(announcement(id,domain,projectId,user.tenantId()),user);}
    @Transactional public Map<String,Object> save(String domain,long projectId,Long id,Map<String,Object> body,AuthUser user){
        scope(domain,projectId,user); String title=required(body.get("title"),"公告标题",100),html=sanitize(required(body.get("content_html"),"公告正文",100000)); if(strip(html).isBlank())throw bad("公告正文不能为空");
        long saved=id==null?next():id; if(id==null)jdbc.update("INSERT INTO tm_test_announcement(id,tenant_id,test_domain,project_id,title,content_html,pinned,pinned_at,published_by) VALUES(?,?,?,?,?,?,?,CASE WHEN ?=1 THEN CURRENT_TIMESTAMP ELSE NULL END,?)",saved,user.tenantId(),domain,projectId,title,html,bool(body.get("pinned"))?1:0,bool(body.get("pinned"))?1:0,user.id());
        else {announcement(id,domain,projectId,user.tenantId());jdbc.update("UPDATE tm_test_announcement SET title=?,content_html=?,pinned=?,pinned_at=CASE WHEN ?=1 AND pinned=0 THEN CURRENT_TIMESTAMP WHEN ?=0 THEN NULL ELSE pinned_at END,last_edited_by=?,last_edited_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",title,html,bool(body.get("pinned"))?1:0,bool(body.get("pinned"))?1:0,bool(body.get("pinned"))?1:0,user.id(),saved,user.tenantId());}
        syncAttachments(saved,body.get("attachment_ids"),body.get("inline_attachment_ids"),user);audit(domain,projectId,saved,id==null?"CREATE":"UPDATE",user,Map.of("title",title));return detail(domain,projectId,saved,user);
    }
    @Transactional public Map<String,Object> pin(String domain,long projectId,long id,boolean pinned,AuthUser user){scope(domain,projectId,user);announcement(id,domain,projectId,user.tenantId());jdbc.update("UPDATE tm_test_announcement SET pinned=?,pinned_at=CASE WHEN ?=1 THEN CURRENT_TIMESTAMP ELSE NULL END,last_edited_by=?,last_edited_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",pinned?1:0,pinned?1:0,user.id(),id,user.tenantId());audit(domain,projectId,id,pinned?"PIN":"UNPIN",user,Map.of());return detail(domain,projectId,id,user);}
    @Transactional public void delete(String domain,long projectId,long id,AuthUser user){scope(domain,projectId,user);announcement(id,domain,projectId,user.tenantId());for(Map<String,Object> x:jdbc.queryForList("SELECT attachment_id FROM tm_test_announcement_attachment WHERE tenant_id=? AND announcement_id=? AND deleted=0",user.tenantId(),id)){attachments.deleteBound(number(x.get("attachment_id")),BUSINESS_TYPE,String.valueOf(id),user);}jdbc.update("UPDATE tm_test_announcement_attachment SET deleted=1 WHERE tenant_id=? AND announcement_id=?",user.tenantId(),id);jdbc.update("UPDATE tm_test_announcement SET deleted=1,last_edited_by=?,last_edited_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",user.id(),id,user.tenantId());audit(domain,projectId,id,"DELETE",user,Map.of());}
    private void syncAttachments(long id,Object normal,Object inline,AuthUser user){Set<Long>wanted=new LinkedHashSet<>();Set<Long>inlines=ids(inline);wanted.addAll(ids(normal));wanted.addAll(inlines);List<Map<String,Object>> old=jdbc.queryForList("SELECT attachment_id FROM tm_test_announcement_attachment WHERE tenant_id=? AND announcement_id=? AND deleted=0",user.tenantId(),id);for(Map<String,Object>x:old){long aid=number(x.get("attachment_id"));if(!wanted.contains(aid)){attachments.deleteBound(aid,BUSINESS_TYPE,String.valueOf(id),user);jdbc.update("UPDATE tm_test_announcement_attachment SET deleted=1 WHERE tenant_id=? AND announcement_id=? AND attachment_id=?",user.tenantId(),id,aid);}}
        int sort=0;for(long aid:wanted){AttachmentItem item=attachments.get(aid,user);if(item.fileSize()>50L*1024*1024)throw bad("单个附件不能超过50MB");Long c=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_announcement_attachment WHERE tenant_id=? AND announcement_id=? AND attachment_id=? AND deleted=0",Long.class,user.tenantId(),id,aid);if(c==null||c==0){attachments.bind(new AttachmentBindingCommand(aid,BUSINESS_TYPE,String.valueOf(id),null),user);jdbc.update("INSERT INTO tm_test_announcement_attachment(id,tenant_id,announcement_id,attachment_id,attachment_type,sort_no) VALUES(?,?,?,?,?,?)",next(),user.tenantId(),id,aid,inlines.contains(aid)?"INLINE":"FILE",sort);}sort++;}}
    private List<Map<String,Object>> detailRows(List<Map<String,Object>> rows,AuthUser u){for(Map<String,Object>r:rows){r.putAll(detailRow(r,u));}return rows;} private Map<String,Object> detailRow(Map<String,Object>r,AuthUser u){Map<String,Object>x=new LinkedHashMap<>(r);long id=number(r.get("id"));x.put("attachments",jdbc.queryForList("SELECT a.attachment_id AS id,f.file_name AS fileName,f.file_size AS fileSize,f.content_type AS contentType,f.created_at AS createdAt,a.attachment_type AS attachmentType FROM tm_test_announcement_attachment a JOIN att_file f ON f.id=a.attachment_id AND f.tenant_id=a.tenant_id WHERE a.tenant_id=? AND a.announcement_id=? AND a.deleted=0 ORDER BY a.sort_no,a.id",u.tenantId(),id));return x;}
    private Map<String,Object> announcement(long id,String d,long p,long t){List<Map<String,Object>>r=jdbc.queryForList("SELECT * FROM tm_test_announcement WHERE id=? AND tenant_id=? AND test_domain=? AND project_id=? AND deleted=0",id,t,d,p);if(r.isEmpty())throw bad("公告不存在或不属于当前项目");return r.get(0);} private void scope(String d,long p,AuthUser u){if(!DOMAINS.contains(d))throw bad("测试大类无效");Long n=jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE id=? AND tenant_id=? AND deleted=0",Long.class,p,u.tenantId());if(n==null||n==0)throw bad("项目不存在或无权访问");}
    private long count(long id,long tenant){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM tm_test_announcement_attachment WHERE tenant_id=? AND announcement_id=? AND deleted=0",Long.class,tenant,id);return n==null?0:n;} private void audit(String d,long p,long id,String a,AuthUser u,Map<String,Object>x){jdbc.update("INSERT INTO tm_test_announcement_audit(id,tenant_id,test_domain,project_id,announcement_id,action_code,operator_id,detail_json) VALUES(?,?,?,?,?,?,?,?)",next(),u.tenantId(),d,p,id,a,u.id(),"{}");}
    private static String required(Object x,String n,int max){String s=x==null?"":String.valueOf(x).trim();if(s.isBlank()||s.length()>max)throw bad(n+"无效");return s;} private static boolean bool(Object x){return x instanceof Boolean b?b:"1".equals(String.valueOf(x))||"true".equalsIgnoreCase(String.valueOf(x));} private static boolean has(String s){return s!=null&&!s.isBlank();} private static long number(Object x){return ((Number)x).longValue();} private static BusinessException bad(String s){return new BusinessException(ErrorCode.BAD_REQUEST,s);} private static long next(){return System.currentTimeMillis()*1000+ThreadLocalRandom.current().nextInt(1000);} private static Set<Long> ids(Object x){Set<Long>s=new LinkedHashSet<>();if(x instanceof Collection<?> c)for(Object v:c)try{s.add(Long.parseLong(String.valueOf(v)));}catch(NumberFormatException ignored){}return s;} private static String strip(String s){return s.replaceAll("<[^>]*>","").replace("&nbsp;"," ").trim();}
    private static String sanitize(String value){String s=value.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>","").replaceAll("(?i)\\s+on\\w+\\s*=\\s*(['\"]).*?\\1","").replaceAll("(?i)\\s+on\\w+\\s*=\\s*[^\\s>]+","").replaceAll("(?i)(javascript|data):","");s=s.replaceAll("(?is)<(?!/?(p|br|strong|b|em|i|s|del|sup|sub|span|h1|h2|h3|h4|h5|ul|ol|li|blockquote|pre|code|hr|table|thead|tbody|tr|th|td|a|img)\\b)[^>]*>","");return sanitizeStyles(s);}
    private static String sanitizeStyles(String html){Matcher matcher=STYLE_ATTRIBUTE.matcher(html);StringBuffer out=new StringBuffer();while(matcher.find()){String style=sanitizeStyle(matcher.group(2));matcher.appendReplacement(out,Matcher.quoteReplacement(style.isBlank()?"":" style=\""+style+"\""));}matcher.appendTail(out);return out.toString();}
    private static String sanitizeStyle(String raw){List<String> safe=new ArrayList<>();for(String declaration:raw.split(";")){int colon=declaration.indexOf(':');if(colon<1)continue;String property=declaration.substring(0,colon).trim().toLowerCase(Locale.ROOT),value=declaration.substring(colon+1).trim();if(safeStyle(property,value))safe.add(property+":"+value);}return String.join(";",safe);}
    private static boolean safeStyle(String property,String value){return switch(property){case "color","background-color" -> value.matches("(?i)(#[0-9a-f]{3,8}|rgba?\\([0-9.,%\\s]+\\)|hsla?\\([0-9.,%\\s]+\\)|[a-z]{3,20})");case "font-size" -> value.matches("\\d{1,3}(px|pt|em|rem|%)");case "font-family" -> value.matches("[\\p{L}\\p{N}\\s,'\"-]{1,80}");case "line-height" -> value.matches("(?:\\d(?:\\.\\d{1,2})?|\\d{1,3}(px|pt|em|rem|%))");case "text-align" -> value.matches("(?i)(left|right|center|justify)");case "text-indent" -> value.matches("\\d{1,3}(px|em|rem|%)");case "font-weight" -> value.matches("(?i)(normal|bold|[1-9]00)");case "font-style" -> value.matches("(?i)(normal|italic)");case "text-decoration" -> value.matches("(?i)(none|underline|line-through)");default -> false;};}
}

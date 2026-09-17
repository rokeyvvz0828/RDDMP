package com.ccb.architecture.standard.persistence;

import com.ccb.architecture.standard.model.StandardModels.DocumentStatus;
import com.ccb.architecture.standard.model.StandardModels.StandardCommand;
import com.ccb.architecture.standard.model.StandardModels.StandardDocument;
import com.ccb.architecture.standard.model.StandardModels.StandardQuery;
import com.ccb.architecture.standard.model.StandardModels.StandardVersion;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 架构规范文档数据访问：主记录、版本快照与发布事务。 */
@Repository
public class StandardStore {

    private final StandardMapper mapper;

    public StandardStore(StandardMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<StandardDocument> pageDocuments(long tenantId, PageQuery page, StandardQuery query) {
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        StandardQuery normalized = query == null ? StandardQuery.empty() : query;
        Map<String,Object> params=params("tenantId",tenantId,"title",text(normalized.title()),"categoryCode",normalized.categoryCode(),"status",normalized.status(),"size",normalizedPage.size(),"offset",(normalizedPage.page()-1)*normalizedPage.size()); Long total=mapper.countDocuments(params); return new PageResult<>(mapper.documents(params),total==null?0:total,normalizedPage.page(),normalizedPage.size());
    }

    public Optional<StandardDocument> findDocument(long tenantId, long id) {
        return Optional.ofNullable(mapper.document(params("tenantId",tenantId,"id",id)));
    }

    /** 乐观锁读取：行版本不一致返回 empty。 */
    public Optional<StandardDocument> lockDocument(long tenantId, long id, long expectedRowVersion) {
        return Optional.ofNullable(mapper.lockedDocument(params("tenantId",tenantId,"id",id,"rowVersion",expectedRowVersion)));
    }

    public long createDocument(long id, long tenantId, StandardCommand command,
                               long operatorId, String operatorName) {
        mapper.insertDocument(params("id",id,"tenantId",tenantId,"title",command.title(),"categoryCode",command.categoryCode(),"summary",command.summary(),"content",command.content(),"operatorId",operatorId,"operatorName",operatorName));
        return id;
    }

    public void updateDocument(long tenantId, long id, long expectedRowVersion, StandardCommand command,
                               long operatorId) {
        int updated=mapper.updateDocument(params("title",command.title(),"categoryCode",command.categoryCode(),"summary",command.summary(),"content",command.content(),"operatorId",operatorId,"tenantId",tenantId,"id",id,"rowVersion",expectedRowVersion));
        if (updated != 1) {
            throw new IllegalStateException("架构规范文档行版本冲突");
        }
    }

    /** 发布：状态、版本与发布信息在同一事务更新，并追加不可变版本快照。 */
    public StandardVersion publish(long tenantId, long id, long expectedRowVersion,
                                   long operatorId, String operatorName) {
        StandardDocument document = lockDocument(tenantId, id, expectedRowVersion)
                .orElseThrow(() -> new IllegalStateException("架构规范文档不存在或行版本冲突"));
        if (document.status() != DocumentStatus.DRAFT && document.status() != DocumentStatus.OFFLINE) {
            throw new IllegalStateException("只有草稿或已下线文档可以发布");
        }
        int nextVersion = document.currentVersion() + 1;
        long snapshotId = System.currentTimeMillis() * 1_000 + (id % 1_000);
        mapper.publishDocument(params("versionNo",nextVersion,"operatorId",operatorId,"operatorName",operatorName,"tenantId",tenantId,"id",id,"rowVersion",expectedRowVersion)); mapper.insertVersion(params("snapshotId",snapshotId,"tenantId",tenantId,"id",id,"versionNo",nextVersion,"title",document.title(),"categoryCode",document.categoryCode(),"summary",document.summary(),"content",document.content(),"operatorId",operatorId,"operatorName",operatorName));
        return new StandardVersion(snapshotId, tenantId, id, nextVersion, document.title(),
                document.categoryCode(), document.summary(), document.content(),
                LocalDateTime.now(), operatorId, operatorName);
    }

    /** 下线：已发布文档进入 OFFLINE，保留历史快照。 */
    public void offline(long tenantId, long id, long expectedRowVersion, long operatorId) {
        int updated=mapper.offlineDocument(params("operatorId",operatorId,"tenantId",tenantId,"id",id,"rowVersion",expectedRowVersion));
        if (updated != 1) {
            throw new IllegalStateException("架构规范文档不是已发布状态或行版本冲突");
        }
    }

    /** 删除仅允许从未发布的草稿。 */
    public void deleteDraft(long tenantId, long id, long expectedRowVersion, long operatorId) {
        int updated=mapper.deleteDraft(params("operatorId",operatorId,"tenantId",tenantId,"id",id,"rowVersion",expectedRowVersion));
        if (updated != 1) {
            throw new IllegalStateException("只有未发布的草稿可以删除");
        }
    }

    public List<StandardVersion> listVersions(long tenantId, long documentId) {
        return mapper.versions(params("tenantId",tenantId,"documentId",documentId));
    }

    private String text(String value){return value==null||value.isBlank()?null:value.trim();} private Map<String,Object> params(Object... values){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)result.put(String.valueOf(values[i]),values[i+1]);return result;}
}

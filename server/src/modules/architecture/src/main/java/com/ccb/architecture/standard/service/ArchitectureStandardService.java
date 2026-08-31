package com.ccb.architecture.standard.service;

import com.ccb.architecture.standard.model.StandardModels.DocumentStatus;
import com.ccb.architecture.standard.model.StandardModels.StandardCommand;
import com.ccb.architecture.standard.model.StandardModels.StandardDocument;
import com.ccb.architecture.standard.model.StandardModels.StandardQuery;
import com.ccb.architecture.standard.model.StandardModels.StandardVersion;
import com.ccb.architecture.standard.persistence.StandardStore;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.attachment.integration.AttachmentBindingCommand;
import com.ccb.attachment.integration.AttachmentGateway;
import com.ccb.attachment.model.AttachmentItem;
import com.ccb.attachment.model.AttachmentPort;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemReferenceQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 架构规范文档业务服务。
 *
 * <p>类别只接受平台参数 {@code ARCH_STANDARD_CATEGORY} 的现有键；发布/下线/重发
 * 通过行版本乐观锁保证并发安全，每次发布追加不可变版本快照。</p>
 */
@Service
public class ArchitectureStandardService {
    public static final String CATEGORY_CODE = "ARCH_STANDARD_CATEGORY";
    public static final String BUSINESS_TYPE = "architecture-standard";
    public static final String RESOURCE_PATH = "/api/architecture/standards";

    private static final Set<String> ALLOWED_STATUSES = Set.of("DRAFT", "PUBLISHED", "OFFLINE");

    private final StandardStore store;
    private final SystemReferenceQuery referenceQuery;
    private final AttachmentPort attachmentPort;
    private final AttachmentGateway attachmentGateway;

    public ArchitectureStandardService(StandardStore store, SystemReferenceQuery referenceQuery,
                                       AttachmentPort attachmentPort, AttachmentGateway attachmentGateway) {
        this.store = store;
        this.referenceQuery = referenceQuery;
        this.attachmentPort = attachmentPort;
        this.attachmentGateway = attachmentGateway;
    }

    public List<com.ccb.system.capability.SystemParameterReference> categories(AuthUser actor) {
        requireActor(actor);
        return referenceQuery.activeParameters(actor, CATEGORY_CODE);
    }

    public PageResult<StandardDocument> list(AuthUser actor, PageQuery page, StandardQuery query) {
        requireActor(actor);
        return store.pageDocuments(actor.tenantId(), page, normalizeQuery(query));
    }

    public StandardDocument detail(AuthUser actor, long id) {
        requireActor(actor);
        return store.findDocument(actor.tenantId(), id)
                .orElseThrow(() -> new ArchitectureNotFoundException("架构规范文档不存在"));
    }

    public List<StandardVersion> versions(AuthUser actor, long id) {
        requireActor(actor);
        StandardDocument document = detail(actor, id);
        if (document.status() == DocumentStatus.DRAFT && document.currentVersion() == 0) {
            return List.of();
        }
        return store.listVersions(actor.tenantId(), id);
    }

    @Transactional
    public StandardDocument create(AuthUser actor, StandardCommand command) {
        requireActor(actor);
        StandardCommand normalized = normalizeCommand(command);
        validateCategory(actor, normalized.categoryCode());
        long id = System.currentTimeMillis() * 1_000 + actor.id() % 1_000;
        while (store.findDocument(actor.tenantId(), id).isPresent()) {
            id = id + 1;
        }
        store.createDocument(id, actor.tenantId(), normalized, actor.id(), actor.displayName());
        return store.findDocument(actor.tenantId(), id).orElseThrow();
    }

    @Transactional
    public StandardDocument update(AuthUser actor, long id, long rowVersion, StandardCommand command) {
        requireActor(actor);
        StandardCommand normalized = normalizeCommand(command);
        validateCategory(actor, normalized.categoryCode());
        StandardDocument current = store.findDocument(actor.tenantId(), id)
                .orElseThrow(() -> new ArchitectureNotFoundException("架构规范文档不存在"));
        if (current.status() == DocumentStatus.OFFLINE) {
            throw new BusinessException(ErrorCode.CONFLICT, "已下线文档不能编辑，请先重新发布");
        }
        store.updateDocument(actor.tenantId(), id, rowVersion, normalized, actor.id());
        return store.findDocument(actor.tenantId(), id).orElseThrow();
    }

    /** 发布草稿或重新发布已下线文档，均追加新版本快照。 */
    @Transactional
    public StandardVersion publish(AuthUser actor, long id, long rowVersion) {
        requireActor(actor);
        StandardDocument current = store.findDocument(actor.tenantId(), id)
                .orElseThrow(() -> new ArchitectureNotFoundException("架构规范文档不存在"));
        if (current.status() == DocumentStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.CONFLICT, "文档已发布，无需重复发布");
        }
        return store.publish(actor.tenantId(), id, rowVersion, actor.id(), actor.displayName());
    }

    @Transactional
    public StandardDocument offline(AuthUser actor, long id, long rowVersion) {
        requireActor(actor);
        StandardDocument current = store.findDocument(actor.tenantId(), id)
                .orElseThrow(() -> new ArchitectureNotFoundException("架构规范文档不存在"));
        if (current.status() != DocumentStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有已发布文档可以下线");
        }
        store.offline(actor.tenantId(), id, rowVersion, actor.id());
        return store.findDocument(actor.tenantId(), id).orElseThrow();
    }

    @Transactional
    public void delete(AuthUser actor, long id, long rowVersion) {
        requireActor(actor);
        StandardDocument current = store.findDocument(actor.tenantId(), id)
                .orElseThrow(() -> new ArchitectureNotFoundException("架构规范文档不存在"));
        if (current.status() != DocumentStatus.DRAFT) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有从未发布的草稿可以删除");
        }
        store.deleteDraft(actor.tenantId(), id, rowVersion, actor.id());
    }

    // ---------- 附件（platform/attachment 公开契约） ----------

    public List<AttachmentItem> attachments(AuthUser actor, long id) {
        requireActor(actor);
        detail(actor, id);
        PageResult<AttachmentItem> result = attachmentPort.list(BUSINESS_TYPE, id, actor.tenantId(),
                new PageQuery(1, 100), null);
        return result.records();
    }

    /** 绑定当前用户上传的临时附件到文档；只有发布维护用户可达。 */
    @Transactional
    public void bindAttachment(AuthUser actor, long id, long attachmentId) {
        requireActor(actor);
        detail(actor, id);
        if (attachmentId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件标识无效");
        }
        attachmentGateway.bind(new AttachmentBindingCommand(attachmentId, BUSINESS_TYPE,
                String.valueOf(id), null), actor);
    }

    @Transactional
    public void deleteAttachment(AuthUser actor, long id, long attachmentId) {
        requireActor(actor);
        detail(actor, id);
        if (attachmentId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件标识无效");
        }
        attachmentGateway.deleteBound(attachmentId, BUSINESS_TYPE, String.valueOf(id), actor);
    }

    private StandardQuery normalizeQuery(StandardQuery query) {
        StandardQuery source = query == null ? StandardQuery.empty() : query;
        String status = normalizeOptional(source.status());
        if (status != null) {
            status = status.toUpperCase(Locale.ROOT);
            if (!ALLOWED_STATUSES.contains(status)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "状态仅支持 DRAFT、PUBLISHED 或 OFFLINE");
            }
        }
        return new StandardQuery(normalizeOptional(source.title()), normalizeOptional(source.categoryCode()), status);
    }

    private StandardCommand normalizeCommand(StandardCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文档内容不能为空");
        }
        String title = requireText(command.title(), "标题", 200);
        String categoryCode = requireText(command.categoryCode(), "类别", 64).toUpperCase(Locale.ROOT);
        String summary = limit(normalizeOptional(command.summary()), 2000);
        String content = normalizeOptional(command.content());
        return new StandardCommand(title, categoryCode, summary, content);
    }

    private void validateCategory(AuthUser actor, String categoryCode) {
        boolean exists = referenceQuery.activeParameters(actor, CATEGORY_CODE).stream()
                .anyMatch(option -> option.code().equalsIgnoreCase(categoryCode));
        if (!exists) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "架构规范类别不存在或已停用");
        }
    }

    private void requireActor(AuthUser actor) {
        if (actor == null || actor.id() <= 0 || actor.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的认证用户和租户");
        }
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "不能为空");
        }
        return limit(normalized, maxLength);
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

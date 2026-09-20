package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.requirement.integration.RequirementSystemDirectory;
import com.ccb.requirement.support.RequirementValues;
import com.ccb.security.model.AuthUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 需求管理的"涉及系统"只读门面。
 * <p>系统主数据统一由架构管理的物理子系统维护，需求管理不再自建系统清单；
 * 查询通过 {@link RequirementSystemDirectory}（由 platform/boot 适配）完成。
 */
@Service
public class RequirementSystemService {
    /** 组合根注入；单元测试直接构造本服务时可以为空，此时列表返回空集合。 */
    @Autowired(required = false)
    private RequirementSystemDirectory systemDirectory;

    public RequirementSystemService() {
        // 系统主数据统一取架构管理物理子系统，需求模块不再持有系统清单表（依赖由组合根注入）。
    }

    /** 按当前项目列出架构管理里的有效物理子系统，字段名保持前端既有口径。 */
    public List<Map<String, Object>> list(long projectId, String keyword, AuthUser user) {
        if (systemDirectory == null) {
            return List.of();
        }
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少当前项目，无法加载涉及系统");
        }
        return systemDirectory.searchActive(user, projectId, keyword).stream().map(system -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", system.id());
            row.put("system_code", system.code());
            row.put("system_name", system.name());
            row.put("conglomerate", system.businessGroup());
            row.put("status", system.status());
            return row;
        }).toList();
    }

    /** 系统编码 → 架构物理子系统主键；不存在或目录未就绪时返回 0。 */
    public long resolveSystemId(String systemCode, long projectId, AuthUser user) {
        if (systemCode == null || systemCode.isBlank() || projectId <= 0 || systemDirectory == null) {
            return 0L;
        }
        return systemDirectory.findByCode(user, projectId, systemCode)
                .map(RequirementSystemDirectory.SystemRef::id).orElse(0L);
    }

    /**
     * 校验并解析"涉及物理子系统"：优先按主键，其次按编码；
     * 需求侧保存物理子系统主键与编码/名称快照，供历史展示与跨模块只读来源使用。
     */
    public SystemSelection resolveSelection(long projectId, Map<String, Object> values, AuthUser user) {
        Long rawId = longValue(values.get("physical_subsystem_id"));
        if (rawId == null) {
            rawId = longValue(values.get("system_id"));
        }
        String code = RequirementValues.text(values, "subsystem_code");
        if (code == null) {
            code = RequirementValues.text(values, "system_code");
        }
        String name = RequirementValues.text(values, "subsystem_name");
        if (name == null) {
            name = RequirementValues.text(values, "system_name");
        }
        Long ownerId = longValue(values.get("owner_user_id"));
        String ownerName = RequirementValues.text(values, "owner_user_name");
        if (systemDirectory == null) {
            // 单元测试等无组合根场景：直接使用请求携带的快照，不做架构侧校验
            return new SystemSelection(rawId, code, name, ownerId, ownerName);
        }
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少当前项目，无法解析涉及物理子系统");
        }
        if (rawId == null && code == null) {
            // 仅保留历史快照文本（例如跨项目沿用旧快照）：不做架构侧存在性校验
            return new SystemSelection(null, null, name, ownerId, ownerName);
        }
        RequirementSystemDirectory.SystemRef found = null;
        if (rawId != null && rawId > 0) {
            found = systemDirectory.find(user, projectId, rawId).orElse(null);
        } else if (code != null) {
            found = systemDirectory.findByCode(user, projectId, code).orElse(null);
        }
        if (found == null) {
            // 历史快照（架构侧已停用或编码变更）保留展示：编码失效但仍有名称时按快照保存
            if (name != null) {
                return new SystemSelection(null, code, name, ownerId, ownerName);
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "涉及物理子系统在架构管理中不存在或已停用，请重新选择");
        }
        return new SystemSelection(found.id(), found.code(), found.name(),
                ownerId == null ? found.ownerId() : ownerId, ownerName);
    }

    private static Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value).trim());
    }

    /** 解析后的物理子系统选择（主键 + 编码/名称快照 + 负责人）。 */
    public record SystemSelection(Long subsystemId, String subsystemCode, String subsystemName,
                                  Long ownerUserId, String ownerUserName) {
    }
}

package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据迁移模块业务码值统一入口：全部经“系统管理/参数管理”维护，
 * 服务端不再维护硬编码的业务码值集合。
 */
@Service
public class DataMigrationCodeValueService {
    public static final String DM_TOPIC_GRANULARITY = "DM_TOPIC_GRANULARITY";
    public static final String DM_PLAN_GRANULARITY = "DM_PLAN_GRANULARITY";
    public static final String DM_PLAN_TYPE = "DM_PLAN_TYPE";
    public static final String DM_ISSUE_GRANULARITY = "DM_ISSUE_GRANULARITY";
    public static final String DM_ISSUE_SOURCE = "DM_ISSUE_SOURCE";
    public static final String DM_DEFECT_TYPE = "DM_DEFECT_TYPE";
    public static final String DM_ISSUE_FREQUENCY = "DM_ISSUE_FREQUENCY";
    public static final String DM_MEETING_GRANULARITY = "DM_MEETING_GRANULARITY";
    public static final String DM_MEETING_SOURCE = "DM_MEETING_SOURCE";
    public static final String DM_REPORT_PERIOD = "DM_REPORT_PERIOD";
    public static final String DM_TARGET_TABLE_CATEGORY = "DM_TARGET_TABLE_CATEGORY";
    public static final String DM_RELEASE_DRILL_GRANULARITY = "DM_RELEASE_DRILL_GRANULARITY";
    public static final String DM_RELEASE_DRILL_TYPE = "DM_RELEASE_DRILL_TYPE";
    public static final String DM_PROGRAM_TYPE = "DM_PROGRAM_TYPE";
    public static final String DM_MAPPING_TYPE = "DM_MAPPING_TYPE";
    public static final String DM_RULE_TARGET_TYPE = "DM_RULE_TARGET_TYPE";
    public static final String DM_RULE_CATEGORY = "DM_RULE_CATEGORY";
    public static final String DM_PARAMETER_TYPE = "DM_PARAMETER_TYPE";
    public static final String DM_PARAMETER_SCOPE = "DM_PARAMETER_SCOPE";
    public static final String DM_PARAMETER_FIELD_TYPE = "DM_PARAMETER_FIELD_TYPE";

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            DM_TOPIC_GRANULARITY, DM_PLAN_GRANULARITY, DM_PLAN_TYPE,
            DM_ISSUE_GRANULARITY, DM_ISSUE_SOURCE, DM_DEFECT_TYPE, DM_ISSUE_FREQUENCY,
            DM_MEETING_GRANULARITY, DM_MEETING_SOURCE, DM_REPORT_PERIOD, DM_TARGET_TABLE_CATEGORY,
            DM_RELEASE_DRILL_GRANULARITY, DM_RELEASE_DRILL_TYPE, DM_PROGRAM_TYPE, DM_MAPPING_TYPE,
            DM_RULE_TARGET_TYPE, DM_RULE_CATEGORY,
            DM_PARAMETER_TYPE, DM_PARAMETER_SCOPE, DM_PARAMETER_FIELD_TYPE);

    private final SystemReferenceQuery systemReferences;

    public DataMigrationCodeValueService(SystemReferenceQuery systemReferences) {
        this.systemReferences = systemReferences;
    }

    public List<Map<String, Object>> options(String category, AuthUser user) {
        String normalized = requireCategory(category);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SystemParameterReference parameter : systemReferences.activeParameters(user, normalized)) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("value", businessCode(parameter.code(), normalized));
            option.put("label", parameter.label());
            result.add(option);
        }
        return result;
    }

    public Set<String> activeCodes(String category, AuthUser user) {
        String normalized = requireCategory(category);
        Set<String> codes = new LinkedHashSet<>();
        for (SystemParameterReference parameter : systemReferences.activeParameters(user, normalized)) {
            codes.add(businessCode(parameter.code(), normalized));
        }
        return codes;
    }

    public boolean isActive(String category, String value, AuthUser user) {
        if (value == null || value.isBlank()) return true;
        return activeCodes(category, user).contains(value);
    }

    /** 写入校验：非空值必须是当前启用参数项；停用/未知编码直接拒绝。 */
    public void requireActive(String category, String fieldLabel, String value, AuthUser user) {
        if (value == null || value.isBlank()) return;
        if (!isActive(category, value, user)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldLabel + "值无效或已停用，请从系统管理/参数管理启用后重试");
        }
    }

    private static String requireCategory(String category) {
        if (category == null || !ALLOWED_CATEGORIES.contains(category.trim().toUpperCase(java.util.Locale.ROOT))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的数据迁移码值类别");
        }
        return category.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** sys_config.config_key 在平台内全局唯一，数据迁移模块按 {@code CATEGORY_CODE} 前缀存储，业务编码取前缀后段。 */
    private static String businessCode(String configKey, String category) {
        if (configKey == null) return null;
        String prefix = category + ".";
        if (configKey.startsWith(prefix)) return configKey.substring(prefix.length());
        return configKey;
    }
}

package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataMigrationCodeValueServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "developer", "", "Developer", 11L, true);

    @Test
    void optionsStripGlobalUniquePrefixToBusinessCodes() {
        DataMigrationCodeValueService service = prefixedService();

        List<Map<String, Object>> options = service.options(DataMigrationCodeValueService.DM_PLAN_GRANULARITY, USER);

        assertEquals("PROJECT", options.get(0).get("value"));
        assertEquals("项目级", options.get(0).get("label"));
        assertEquals(List.of("PROJECT", "SYSTEM"), new java.util.ArrayList<>(service.activeCodes(DataMigrationCodeValueService.DM_PLAN_GRANULARITY, USER)));
    }

    @Test
    void requireActiveRejectsUnknownOrDisabledCode() {
        DataMigrationCodeValueService service = prefixedService();

        service.requireActive(DataMigrationCodeValueService.DM_PLAN_GRANULARITY, "资产颗粒度", "SYSTEM", USER);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requireActive(DataMigrationCodeValueService.DM_PLAN_GRANULARITY, "资产颗粒度", "UNKNOWN", USER));
        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertTrue(error.getMessage().contains("资产颗粒度"));
    }

    @Test
    void rejectsUnsupportedCategory() {
        DataMigrationCodeValueService service = prefixedService();
        assertThrows(BusinessException.class, () -> service.options("NOT_A_DM_CATEGORY", USER));
    }

    @Test
    void releaseDrillGranularityAndTypeAreAvailableFromParameterManagement() {
        DataMigrationCodeValueService service = TestDataMigrationCodeValues.service();

        List<Map<String, Object>> granularity = service.options(DataMigrationCodeValueService.DM_RELEASE_DRILL_GRANULARITY, USER);
        assertEquals(List.of("PROJECT", "COMPONENT"), new java.util.ArrayList<>(service.activeCodes(DataMigrationCodeValueService.DM_RELEASE_DRILL_GRANULARITY, USER)));
        assertTrue(granularity.stream().anyMatch(item -> "COMPONENT".equals(item.get("value")) && "组件级".equals(item.get("label"))));

        List<Map<String, Object>> types = service.options(DataMigrationCodeValueService.DM_RELEASE_DRILL_TYPE, USER);
        assertEquals(8, types.size());
        service.requireActive(DataMigrationCodeValueService.DM_RELEASE_DRILL_TYPE, "资料类型", "RELEASE_PLAN", USER);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requireActive(DataMigrationCodeValueService.DM_RELEASE_DRILL_TYPE, "资料类型", "UNKNOWN_TYPE", USER));
        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertTrue(error.getMessage().contains("资料类型"));
    }

    private static DataMigrationCodeValueService prefixedService() {
        SystemReferenceQuery query = new SystemReferenceQuery() {
            @Override
            public com.ccb.common.api.PageResult<com.ccb.system.capability.SystemUserReference> searchActiveUsers(
                    AuthUser actor, com.ccb.common.api.PageQuery page, String keyword) {
                return new com.ccb.common.api.PageResult<>(List.of(), 0L, 1, 20);
            }

            @Override
            public java.util.Optional<com.ccb.system.capability.SystemUserReference> findUser(AuthUser actor, long userId, boolean activeOnly) {
                return java.util.Optional.empty();
            }

            @Override
            public List<SystemParameterReference> activeParameters(AuthUser actor, String categoryCode) {
                if (DataMigrationCodeValueService.DM_PLAN_GRANULARITY.equals(categoryCode)) {
                    return List.of(new SystemParameterReference("DM_PLAN_GRANULARITY.PROJECT", "项目级"),
                            new SystemParameterReference("DM_PLAN_GRANULARITY.SYSTEM", "系统级"));
                }
                return List.of();
            }
        };
        return new DataMigrationCodeValueService(query);
    }
}

package com.ccb.datamigration.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.service.DataMigrationCodeValueService;
import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据迁移模块参数管理选项：业务码值统一由“系统管理/参数管理”维护。
 */
@RestController("dataMigrationParameterOptionsController")
@RequestMapping("/api/data-migration/options")
@PreAuthorize("hasAnyAuthority('data-migration:access','data-migration:write','data-migration:manage','system:admin')")
public class DataMigrationParameterOptionsController {
    private final DataMigrationCodeValueService codeValues;

    public DataMigrationParameterOptionsController(DataMigrationCodeValueService codeValues) {
        this.codeValues = codeValues;
    }

    @GetMapping("/{category}")
    public ApiResponse<List<Map<String, Object>>> options(@PathVariable String category,
                                                          @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(codeValues.options(category, user), TraceId.getOrCreate());
    }
}

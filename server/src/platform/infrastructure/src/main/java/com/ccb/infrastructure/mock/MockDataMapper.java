package com.ccb.infrastructure.mock;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Mapper
public interface MockDataMapper {
    int upsert(@Param("table") String table, @Param("columns") String columns, @Param("values") List<Object> values, @Param("updates") String updates);
    int saveDatasetState(@Param("id") long id, @Param("datasetKey") String datasetKey, @Param("datasetVersion") String datasetVersion, @Param("checksum") String checksum);
    Long activeTenantRootCount(@Param("tenantId") long tenantId);
    String activeOrganizationName(@Param("tenantId") long tenantId, @Param("organizationId") long organizationId);
    Long activeUserCount(@Param("tenantId") long tenantId, @Param("userId") long userId);
    Long parameterCount(@Param("tenantId") long tenantId, @Param("categoryCode") String categoryCode, @Param("configKey") String configKey);
}

@Configuration
@MapperScan(basePackageClasses = MockDataMapper.class)
class MockDataMapperConfiguration {
}

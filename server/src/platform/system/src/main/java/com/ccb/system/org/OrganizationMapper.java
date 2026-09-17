package com.ccb.system.org;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrganizationMapper {
    List<Map<String,Object>> selectOrganizations(@Param("tenantId") long tenantId);
    List<Map<String,Object>> selectUsers(@Param("tenantId") long tenantId);
}

@Configuration
@MapperScan(basePackageClasses = OrganizationMapper.class)
class OrganizationMapperConfiguration {}

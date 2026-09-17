package com.ccb.system.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.Map;

@Mapper
public interface SystemUserDirectoryMapper {
    List<Map<String, Object>> selectActive(@Param("tenantId") long tenantId, @Param("keyword") String keyword, @Param("limit") int limit);
    Map<String, Object> selectActiveById(@Param("tenantId") long tenantId, @Param("userId") long userId);
}

@Configuration
@MapperScan(basePackageClasses = SystemUserDirectoryMapper.class)
class SystemUserDirectoryMapperConfiguration {
}

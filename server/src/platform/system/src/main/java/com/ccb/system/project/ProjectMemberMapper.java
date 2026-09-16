package com.ccb.system.project;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProjectMemberMapper {
    List<Map<String, Object>> selectMembers(@Param("projectId") long projectId, @Param("tenantId") long tenantId);

    List<Map<String, Object>> selectRolesByMemberIds(@Param("memberIds") List<Long> memberIds,
                                                      @Param("tenantId") long tenantId);
}

@Configuration
@MapperScan(basePackageClasses = ProjectMemberMapper.class)
class ProjectMemberMapperConfiguration {
}

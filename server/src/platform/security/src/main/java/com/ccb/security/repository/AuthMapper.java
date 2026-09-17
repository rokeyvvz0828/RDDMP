package com.ccb.security.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Mapper
public interface AuthMapper {
    Map<String, Object> findByUsername(@Param("username") String username);
    Map<String, Object> findById(@Param("id") long id, @Param("tenantId") long tenantId);
    List<String> findRoles(@Param("userId") long userId, @Param("tenantId") long tenantId);
    List<String> findSystemPermissions(@Param("userId") long userId, @Param("tenantId") long tenantId);
    List<String> findProjectPermissions(@Param("userId") long userId, @Param("tenantId") long tenantId, @Param("projectId") long projectId);
    int countProjectManagementPermissions(@Param("userId") long userId, @Param("tenantId") long tenantId);
    int countProjectAccess(@Param("userId") long userId, @Param("tenantId") long tenantId, @Param("projectId") long projectId);
    List<Map<String, Object>> findRoutes(@Param("tenantId") long tenantId);
    void recordLogin(@Param("id") long id, @Param("username") String username, @Param("success") boolean success, @Param("reason") String reason, @Param("clientIp") String clientIp, @Param("userAgent") String userAgent);
    void updateLastLogin(@Param("userId") long userId, @Param("updatedAt") Timestamp updatedAt);
    int updatePassword(@Param("userId") long userId, @Param("tenantId") long tenantId, @Param("passwordHash") String passwordHash, @Param("updatedAt") Timestamp updatedAt);
    String findAvatarObjectKey(@Param("userId") long userId, @Param("tenantId") long tenantId);
    int updateAvatarObjectKey(@Param("userId") long userId, @Param("tenantId") long tenantId, @Param("objectKey") String objectKey, @Param("updatedAt") Timestamp updatedAt);
}

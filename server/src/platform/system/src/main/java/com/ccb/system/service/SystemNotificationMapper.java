package com.ccb.system.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SystemNotificationMapper {
    int insertNotification(@Param("id") long id, @Param("tenantId") long tenantId, @Param("eventId") String eventId,
                           @Param("moduleCode") String moduleCode, @Param("moduleName") String moduleName,
                           @Param("businessType") String businessType, @Param("businessKey") String businessKey,
                           @Param("title") String title, @Param("content") String content, @Param("level") String level,
                           @Param("sourceName") String sourceName, @Param("actionPath") String actionPath,
                           @Param("projectRef") String projectRef, @Param("projectName") String projectName,
                           @Param("actorUserId") Long actorUserId, @Param("createdAt") LocalDateTime createdAt);

    Long selectNotificationIdForUpdate(@Param("tenantId") long tenantId, @Param("businessType") String businessType,
                                       @Param("eventId") String eventId);
    int insertUserNotification(@Param("notificationId") long notificationId, @Param("tenantId") long tenantId, @Param("userId") long userId);
    long countNotifications(@Param("tenantId") long tenantId, @Param("userId") long userId, @Param("view") String view,
                            @Param("moduleCode") String moduleCode);
    List<Map<String, Object>> selectNotifications(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                                   @Param("view") String view, @Param("moduleCode") String moduleCode,
                                                   @Param("offset") long offset, @Param("limit") long limit);
    List<Map<String, Object>> selectModules(@Param("tenantId") long tenantId, @Param("userId") long userId, @Param("view") String view);
    Long countUnread(@Param("tenantId") long tenantId, @Param("userId") long userId);
    int markRead(@Param("now") LocalDateTime now, @Param("tenantId") long tenantId, @Param("userId") long userId, @Param("notificationId") long notificationId);
    int markAllRead(@Param("now") LocalDateTime now, @Param("tenantId") long tenantId, @Param("userId") long userId);
    int archiveReadNotification(@Param("now") LocalDateTime now, @Param("tenantId") long tenantId, @Param("userId") long userId, @Param("notificationId") long notificationId);
    Boolean selectReadState(@Param("tenantId") long tenantId, @Param("userId") long userId, @Param("notificationId") long notificationId);
    int restore(@Param("tenantId") long tenantId, @Param("userId") long userId, @Param("notificationId") long notificationId);
    int archiveAllRead(@Param("now") LocalDateTime now, @Param("tenantId") long tenantId, @Param("userId") long userId);
    List<Long> selectActiveUserIds(@Param("tenantId") long tenantId, @Param("userIds") List<Long> userIds);
    int insertAudit(@Param("id") long id, @Param("tenantId") long tenantId, @Param("operatorId") long operatorId, @Param("requestPath") String requestPath);
}

@Configuration
@MapperScan(basePackageClasses = SystemNotificationMapper.class)
class SystemNotificationMapperConfiguration {
}

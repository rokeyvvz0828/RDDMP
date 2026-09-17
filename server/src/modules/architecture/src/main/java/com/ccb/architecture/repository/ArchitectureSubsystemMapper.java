package com.ccb.architecture.repository;
import org.apache.ibatis.annotations.Mapper;
import java.util.List; import java.util.Map;
@Mapper public interface ArchitectureSubsystemMapper { Long countPhysical(Map<String,Object> p); List<Map<String,Object>> physicals(Map<String,Object> p); Map<String,Object> physical(Map<String,Object> p); Long countByCode(Map<String,Object> p); Long countByName(Map<String,Object> p); int insertPhysical(Map<String,Object> p); int updatePhysical(Map<String,Object> p); int softDeletePhysical(Map<String,Object> p); }

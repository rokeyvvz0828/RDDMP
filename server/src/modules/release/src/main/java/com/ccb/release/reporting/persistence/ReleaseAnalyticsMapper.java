package com.ccb.release.reporting.persistence;
import org.apache.ibatis.annotations.Mapper;
import java.util.List; import java.util.Map;
@Mapper public interface ReleaseAnalyticsMapper { Long applications(Map<String,Object> p); Long subsystems(Map<String,Object> p); Long units(Map<String,Object> p); Long fileMedia(Map<String,Object> p); Long requirements(Map<String,Object> p); Long windows(Map<String,Object> p); List<Map<String,Object>> versionTypes(Map<String,Object> p); List<Map<String,Object>> results(Map<String,Object> p); Long drilldownCount(Map<String,Object> p); List<Map<String,Object>> drilldown(Map<String,Object> p); }

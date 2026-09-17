package com.ccb.release.window.persistence;

import com.ccb.release.window.model.ReleaseWindow;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReleaseWindowMapper {
    Long count(Map<String,Object> p); List<ReleaseWindow> page(Map<String,Object> p); ReleaseWindow find(Map<String,Object> p); ReleaseWindow findForUpdate(Map<String,Object> p);
    Long tenantId(Map<String,Object> p); List<Long> lockProjectWindows(Map<String,Object> p); Long overlapCount(Map<String,Object> p); String latestCode(Map<String,Object> p);
    int insert(Map<String,Object> p); int update(Map<String,Object> p); int appendChange(Map<String,Object> p);
}

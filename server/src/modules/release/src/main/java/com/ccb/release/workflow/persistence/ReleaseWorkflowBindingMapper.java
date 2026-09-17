package com.ccb.release.workflow.persistence;

import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.Binding;
import com.ccb.release.workflow.model.ReleaseWorkflowBindingModels.BindingHistoryView;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReleaseWorkflowBindingMapper {
    List<Binding> findProject(Map<String, Object> params); Binding find(Map<String, Object> params); Binding findForUpdate(Map<String, Object> params);
    int insert(Map<String, Object> params); int update(Map<String, Object> params); int appendHistory(Map<String, Object> params);
    List<BindingHistoryView> history(Map<String, Object> params); List<Binding> references(Map<String, Object> params);
}

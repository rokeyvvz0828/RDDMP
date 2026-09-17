package com.ccb.architecture.standard.persistence;
import com.ccb.architecture.standard.model.StandardModels.StandardDocument;
import com.ccb.architecture.standard.model.StandardModels.StandardVersion;
import org.apache.ibatis.annotations.Mapper;
import java.util.List; import java.util.Map;
@Mapper public interface StandardMapper { Long countDocuments(Map<String,Object> p); List<StandardDocument> documents(Map<String,Object> p); StandardDocument document(Map<String,Object> p); StandardDocument lockedDocument(Map<String,Object> p); int insertDocument(Map<String,Object> p); int updateDocument(Map<String,Object> p); int publishDocument(Map<String,Object> p); int insertVersion(Map<String,Object> p); int offlineDocument(Map<String,Object> p); int deleteDraft(Map<String,Object> p); List<StandardVersion> versions(Map<String,Object> p); }

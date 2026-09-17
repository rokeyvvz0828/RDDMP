package com.ccb.release.integration;

import com.ccb.release.integration.ReleaseWorkflowStore.AttachmentSnapshot;
import com.ccb.release.integration.ReleaseWorkflowStore.RoundSnapshot;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReleaseWorkflowMapper {
    Integer nextRoundNo(Map<String,Object> p); int insertStartingRound(Map<String,Object> p); int completeWorkflowStart(Map<String,Object> p);
    int transitionApplicationToReview(Map<String,Object> p); int insertAttachment(Map<String,Object> p); List<AttachmentSnapshot> activeAttachments(Map<String,Object> p);
    int retireAttachment(Map<String,Object> p); int bumpEditableApplicationVersion(Map<String,Object> p); RoundSnapshot latestRound(Map<String,Object> p);
    RoundSnapshot latestRoundForUpdate(Map<String,Object> p); RoundSnapshot roundByInstanceForUpdate(Map<String,Object> p); Integer latestRoundNo(Map<String,Object> p);
    int markWithdrawalRequested(Map<String,Object> p); int markCancelRequested(Map<String,Object> p); int completeRound(Map<String,Object> p); int markApproved(Map<String,Object> p);
    int transitionApplication(Map<String,Object> p); Long startedWindowCount(Map<String,Object> p); Long currentWindow(Map<String,Object> p); Long futureWindow(Map<String,Object> p);
    int beginReceipt(Map<String,Object> p); int completeReceipt(Map<String,Object> p);
}

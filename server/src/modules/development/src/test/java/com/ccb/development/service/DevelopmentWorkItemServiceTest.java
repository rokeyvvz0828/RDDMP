package com.ccb.development.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.development.model.DevelopmentWorkItemModels.WorkItemStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DevelopmentWorkItemServiceTest {
    @Test
    void transitionRequiresReviewAndPreservesExplicitReturnAndReopen(){
        assertEquals(WorkItemStatus.IN_PROGRESS,DevelopmentWorkItemService.transition(WorkItemStatus.TODO,"START"));
        assertEquals(WorkItemStatus.IN_REVIEW,DevelopmentWorkItemService.transition(WorkItemStatus.IN_PROGRESS,"SUBMIT"));
        assertEquals(WorkItemStatus.DONE,DevelopmentWorkItemService.transition(WorkItemStatus.IN_REVIEW,"ACCEPT"));
        assertEquals(WorkItemStatus.IN_PROGRESS,DevelopmentWorkItemService.transition(WorkItemStatus.IN_REVIEW,"RETURN"));
        assertEquals(WorkItemStatus.IN_PROGRESS,DevelopmentWorkItemService.transition(WorkItemStatus.DONE,"REOPEN"));
        for(WorkItemStatus state:WorkItemStatus.values())if(state!=WorkItemStatus.IN_REVIEW){
            assertThrows(BusinessException.class,()->DevelopmentWorkItemService.transition(state,"ACCEPT"));
        }
        assertThrows(BusinessException.class,()->DevelopmentWorkItemService.transition(WorkItemStatus.TODO,"DONE"));
    }
}

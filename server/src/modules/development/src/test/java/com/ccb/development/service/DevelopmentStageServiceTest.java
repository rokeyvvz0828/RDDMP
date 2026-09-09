package com.ccb.development.service;

import com.ccb.development.model.DevelopmentTaskModels.SourceMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DevelopmentStageServiceTest {
    @Test
    void onlyExplicitTestOnlyLinkedSourcesAreExemptFromDevelopmentEvidence(){
        assertTrue(DevelopmentStageService.requiresCodeWalk(SourceMode.STANDALONE,List.of()));
        assertTrue(DevelopmentStageService.requiresCodeWalk(SourceMode.LINKED,List.of()));
        assertTrue(DevelopmentStageService.requiresCodeWalk(SourceMode.LINKED,List.of("LEAD","TEST")));
        assertTrue(DevelopmentStageService.requiresCodeWalk(SourceMode.LINKED,List.of("CHANGE","TEST")));
        assertFalse(DevelopmentStageService.requiresCodeWalk(SourceMode.LINKED,List.of("TEST")));
    }
}

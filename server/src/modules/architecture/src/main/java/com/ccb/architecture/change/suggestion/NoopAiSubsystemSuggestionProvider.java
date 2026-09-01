package com.ccb.architecture.change.suggestion;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 首期 AI 建议占位实现：不读取配置、不访问网络，始终不返回候选值。
 */
@Component
public final class NoopAiSubsystemSuggestionProvider implements SubsystemSuggestionProvider {

    @Override
    public List<Suggestion> suggest(SuggestionRequest request) {
        return List.of();
    }
}

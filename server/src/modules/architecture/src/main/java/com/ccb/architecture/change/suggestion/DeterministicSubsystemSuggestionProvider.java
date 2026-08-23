package com.ccb.architecture.change.suggestion;

import java.util.List;
import java.util.Objects;

/**
 * 基于固定本地规则的确定性建议 provider。
 */
public final class DeterministicSubsystemSuggestionProvider implements SubsystemSuggestionProvider {
    private final List<Suggestion> configuredSuggestions;

    public DeterministicSubsystemSuggestionProvider(List<Suggestion> configuredSuggestions) {
        this.configuredSuggestions = List.copyOf(Objects.requireNonNull(configuredSuggestions, "configuredSuggestions 不能为空"));
    }

    @Override
    public List<Suggestion> suggest(SuggestionRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        return configuredSuggestions.stream()
                .filter(suggestion -> !request.hasText(suggestion.field()))
                .toList();
    }
}

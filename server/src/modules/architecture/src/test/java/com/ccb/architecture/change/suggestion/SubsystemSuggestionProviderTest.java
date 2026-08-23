package com.ccb.architecture.change.suggestion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubsystemSuggestionProviderTest {

    @Test
    void keepsSuggestionRequestAndSuggestionAsImmutableValues() {
        Map<String, String> input = new HashMap<>();
        input.put("shortName", "");
        SubsystemSuggestionProvider.SuggestionRequest request =
                new SubsystemSuggestionProvider.SuggestionRequest(input);
        SubsystemSuggestionProvider.Suggestion suggestion = new SubsystemSuggestionProvider.Suggestion(
                " shortName ", " 账户管理 ", " deterministic ", " 本地受控规则 ");
        input.put("shortName", "人工填写");

        assertThat(SubsystemSuggestionProvider.SuggestionRequest.class.isRecord()).isTrue();
        assertThat(SubsystemSuggestionProvider.Suggestion.class.isRecord()).isTrue();
        assertThat(request.hasText("shortName")).isFalse();
        assertThat(suggestion).isEqualTo(new SubsystemSuggestionProvider.Suggestion(
                "shortName", "账户管理", "deterministic", "本地受控规则"));
        assertThatThrownBy(() -> request.fieldValues().put("name", "不可写"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnsStableLocalSuggestionsWithoutReplacingManualValues() {
        SubsystemSuggestionProvider.Suggestion shortName = new SubsystemSuggestionProvider.Suggestion(
                "shortName", "账户管理", "deterministic", "固定本地规则");
        SubsystemSuggestionProvider.Suggestion name = new SubsystemSuggestionProvider.Suggestion(
                "name", "账户管理子系统", "deterministic", "固定本地规则");
        List<SubsystemSuggestionProvider.Suggestion> configured = new ArrayList<>(List.of(shortName, name));
        DeterministicSubsystemSuggestionProvider provider =
                new DeterministicSubsystemSuggestionProvider(configured);
        configured.clear();

        SubsystemSuggestionProvider.SuggestionRequest emptyRequest =
                new SubsystemSuggestionProvider.SuggestionRequest(Map.of());
        List<SubsystemSuggestionProvider.Suggestion> first = provider.suggest(emptyRequest);
        List<SubsystemSuggestionProvider.Suggestion> second = provider.suggest(emptyRequest);

        assertThat(first).containsExactly(shortName, name);
        assertThat(second).isEqualTo(first);
        assertThat(provider.suggest(new SubsystemSuggestionProvider.SuggestionRequest(
                Map.of("shortName", "人工填写"))))
                .containsExactly(name);
        assertThatThrownBy(() -> first.add(shortName))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void noopAiProviderAlwaysReturnsAnEmptyImmutableList() {
        NoopAiSubsystemSuggestionProvider provider = new NoopAiSubsystemSuggestionProvider();

        assertThat(provider.suggest(null)).isEmpty();
        assertThat(provider.suggest(new SubsystemSuggestionProvider.SuggestionRequest(
                Map.of("name", "任意值")))).isEmpty();
    }
}

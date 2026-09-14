package com.ccb.architecture.integration;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchitectureProjectDeletionGuardTest {
    @Mock
    private JdbcTemplate jdbc;

    @Test
    void rejectsDeletionWhenAnyArchitectureReferenceExists() {
        ArchitectureProjectDeletionGuard guard = new ArchitectureProjectDeletionGuard(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class)))
                .thenReturn(false, false, true);

        assertThatThrownBy(() -> guard.requireNoReferences(11L, 22L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT))
                .hasMessage("项目仍有关联的架构数据，不能删除");

        verify(jdbc, times(3)).queryForObject(anyString(), eq(Boolean.class), any(Object[].class));
    }

    @Test
    void allowsDeletionWhenAllArchitectureReferencesAreAbsent() {
        ArchitectureProjectDeletionGuard guard = new ArchitectureProjectDeletionGuard(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(false);

        guard.requireNoReferences(11L, 22L);

        verify(jdbc, times(13)).queryForObject(anyString(), eq(Boolean.class), any(Object[].class));
    }

    @Test
    void scopesEveryReferenceQueryByTenantAndProject() {
        ArchitectureProjectDeletionGuard guard = new ArchitectureProjectDeletionGuard(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(false);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);

        guard.requireNoReferences(11L, 22L);

        verify(jdbc, times(13)).queryForObject(sql.capture(), eq(Boolean.class), parameters.capture());
        assertThat(sql.getAllValues()).allSatisfy(statement -> {
            assertThat(statement).contains("tenant_id = ?");
            assertThat(statement).contains("project_id = ?");
        });
        assertThat(parameters.getAllValues()).allSatisfy(arguments ->
                assertThat(List.of(arguments)).containsExactly(11L, 22L));
        assertThat(sql.getAllValues().get(0)).contains("deleted = 0");
    }
}

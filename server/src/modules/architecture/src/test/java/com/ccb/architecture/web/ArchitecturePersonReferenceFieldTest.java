/*
 * 文件：server/src/modules/architecture/src/test/java/com/ccb/architecture/web/ArchitecturePersonReferenceFieldTest.java
 * 说明：架构模块人员引用字段的响应暴露测试。
 * 用途：确保事项列表、材料与标准文档详情响应携带人员标识，使前端人员卡片能够取到 userId。
 * 作者：Codex
 */
package com.ccb.architecture.web;

import com.ccb.architecture.decision.model.DecisionModels.DecisionMatter;
import com.ccb.architecture.decision.model.DecisionModels.MaterialKind;
import com.ccb.architecture.decision.model.DecisionModels.MaterialRecord;
import com.ccb.architecture.decision.model.DecisionModels.MatterStatus;
import com.ccb.architecture.decision.web.ArchitectureDecisionController;
import com.ccb.architecture.standard.model.StandardModels.DocumentStatus;
import com.ccb.architecture.standard.model.StandardModels.StandardDocument;
import com.ccb.architecture.standard.web.ArchitectureStandardController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArchitecturePersonReferenceFieldTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 12, 10, 0);

    @Test
    void 事项列表响应暴露提出人标识() {
        DecisionMatter matter = mock(DecisionMatter.class);
        when(matter.id()).thenReturn(5L);
        when(matter.matterNo()).thenReturn("DM-001");
        when(matter.title()).thenReturn("示例事项");
        when(matter.status()).thenReturn(MatterStatus.PUBLISHED);
        when(matter.receivedAt()).thenReturn(CREATED_AT);
        when(matter.firstHandlingDeadline()).thenReturn(LocalDate.of(2026, 9, 20));
        when(matter.proposerId()).thenReturn(21L);
        when(matter.proposerName()).thenReturn("张伟");
        when(matter.updatedAt()).thenReturn(CREATED_AT);

        ArchitectureDecisionController.MatterSummaryResponse response =
                from(ArchitectureDecisionController.MatterSummaryResponse.class, DecisionMatter.class, matter);

        assertThat(response.proposerId()).isEqualTo(21L);
        assertThat(response.proposerName()).isEqualTo("张伟");
        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.updatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void 材料响应暴露创建人标识() {
        MaterialRecord record = mock(MaterialRecord.class);
        when(record.id()).thenReturn(6L);
        when(record.matterId()).thenReturn(5L);
        when(record.kind()).thenReturn(MaterialKind.SOLUTION);
        when(record.content()).thenReturn("材料内容");
        when(record.createdBy()).thenReturn(31L);
        when(record.createdByName()).thenReturn("李娜");
        when(record.createdAt()).thenReturn(CREATED_AT);

        ArchitectureDecisionController.MaterialResponse response =
                from(ArchitectureDecisionController.MaterialResponse.class, MaterialRecord.class, record);

        assertThat(response.createdBy()).isEqualTo(31L);
        assertThat(response.createdByName()).isEqualTo("李娜");
        assertThat(response.id()).isEqualTo(6L);
    }

    @Test
    void 标准文档详情响应暴露创建人标识() {
        StandardDocument document = mock(StandardDocument.class);
        when(document.id()).thenReturn(7L);
        when(document.title()).thenReturn("示例规范");
        when(document.categoryCode()).thenReturn("ARCH");
        when(document.status()).thenReturn(DocumentStatus.DRAFT);
        when(document.createdBy()).thenReturn(41L);
        when(document.createdByName()).thenReturn("王强");
        when(document.createdAt()).thenReturn(CREATED_AT);
        when(document.updatedAt()).thenReturn(CREATED_AT);

        ArchitectureStandardController.DocumentDetailResponse response =
                from(ArchitectureStandardController.DocumentDetailResponse.class, StandardDocument.class, document);

        assertThat(response.createdBy()).isEqualTo(41L);
        assertThat(response.createdByName()).isEqualTo("王强");
        assertThat(response.id()).isEqualTo(7L);
    }

    @Test
    void 三个响应都保持既有人员姓名字段() {
        assertThat(componentNames(ArchitectureDecisionController.MatterSummaryResponse.class))
                .contains("proposerId", "proposerName");
        assertThat(componentNames(ArchitectureDecisionController.MaterialResponse.class))
                .contains("createdBy", "createdByName");
        assertThat(componentNames(ArchitectureStandardController.DocumentDetailResponse.class))
                .contains("createdBy", "createdByName");
    }

    private static List<String> componentNames(Class<?> responseType) {
        return Arrays.stream(responseType.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
    }

    // 关键逻辑：响应 record 的 from 是包内静态工厂，本测试位于 com.ccb.architecture.web 而非控制器所在包，
    // 无法直接调用。这里用反射调用真实工厂，以验证人员标识确实从模型传递到了响应，而不是只断言字段存在。
    @SuppressWarnings("unchecked")
    private static <T> T from(Class<?> responseType, Class<?> modelType, Object model) {
        try {
            Method factory = responseType.getDeclaredMethod("from", modelType);
            factory.setAccessible(true);
            return (T) factory.invoke(null, model);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("调用 " + responseType.getSimpleName() + ".from 失败", exception);
        }
    }
}

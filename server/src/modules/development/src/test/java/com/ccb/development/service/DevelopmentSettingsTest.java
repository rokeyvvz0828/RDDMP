package com.ccb.development.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.development.config.DevelopmentSettings;
import com.ccb.development.config.DevelopmentSettings.CalendarDefinition;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 针对 com.ccb.development.config.DevelopmentSettings 的聚焦单元测试。
 * 覆盖系统映射、编号模板与工作日历三大类参数的安全读取、默认回退与输入边界校验。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DevelopmentSettings 配置领域服务单元测试")
class DevelopmentSettingsTest {

    private static final String CATEGORY_SYSTEM_MAPPING = "DEVELOPMENT_SYSTEM_MAPPING";
    private static final String CATEGORY_NUMBERING = "DEVELOPMENT_NUMBERING";
    private static final String CATEGORY_CALENDAR = "DEVELOPMENT_CALENDAR";

    private static final AuthUser ACTOR = new AuthUser(
            1L, 100L, "dev_admin", "hash", "开发管理员", 10L, true
    );

    @Mock
    private SystemReferenceQuery references;

    private ObjectMapper objectMapper;
    private DevelopmentSettings settings;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        settings = new DevelopmentSettings(references, objectMapper);
    }

    @Nested
    @DisplayName("系统映射配置 (DEVELOPMENT_SYSTEM_MAPPING)")
    class SystemMappingsTests {

        @Test
        @DisplayName("未配置参数时返回空映射")
        void returnsEmptyMapWhenNoParametersConfigured() {
            when(references.activeParameters(ACTOR, CATEGORY_SYSTEM_MAPPING))
                    .thenReturn(List.of());

            Map<String, Long> result = settings.systemMappings(ACTOR);

            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("正常解析多条需求系统到物理系统的有效映射")
        void parsesValidSystemMappings() {
            when(references.activeParameters(ACTOR, CATEGORY_SYSTEM_MAPPING))
                    .thenReturn(List.of(
                            new SystemParameterReference("REQ_CORE", "1001"),
                            new SystemParameterReference("REQ_PAY", "2002")
                    ));

            Map<String, Long> result = settings.systemMappings(ACTOR);

            assertThat(result).hasSize(2)
                    .containsEntry("REQ_CORE", 1001L)
                    .containsEntry("REQ_PAY", 2002L);
        }

        @Test
        @DisplayName("重复的需求系统编码抛出 BusinessException")
        void rejectsDuplicateSystemCode() {
            when(references.activeParameters(ACTOR, CATEGORY_SYSTEM_MAPPING))
                    .thenReturn(List.of(
                            new SystemParameterReference("REQ_CORE", "1001"),
                            new SystemParameterReference("REQ_CORE", "1002")
                    ));

            assertThrows(BusinessException.class, () -> settings.systemMappings(ACTOR));
        }

        @Test
        @DisplayName("物理系统 ID 为 0 时抛出 BusinessException")
        void rejectsZeroSystemId() {
            when(references.activeParameters(ACTOR, CATEGORY_SYSTEM_MAPPING))
                    .thenReturn(List.of(
                            new SystemParameterReference("REQ_CORE", "0")
                    ));

            assertThrows(BusinessException.class, () -> settings.systemMappings(ACTOR));
        }

        @Test
        @DisplayName("物理系统 ID 为负数时抛出 BusinessException")
        void rejectsNegativeSystemId() {
            when(references.activeParameters(ACTOR, CATEGORY_SYSTEM_MAPPING))
                    .thenReturn(List.of(
                            new SystemParameterReference("REQ_CORE", "-1")
                    ));

            assertThrows(BusinessException.class, () -> settings.systemMappings(ACTOR));
        }

        @Test
        @DisplayName("物理系统 ID 为非数值字符串时抛出 BusinessException")
        void rejectsNonNumericSystemId() {
            when(references.activeParameters(ACTOR, CATEGORY_SYSTEM_MAPPING))
                    .thenReturn(List.of(
                            new SystemParameterReference("REQ_CORE", "not_a_number")
                    ));

            assertThrows(BusinessException.class, () -> settings.systemMappings(ACTOR));
        }

        @Test
        @DisplayName("物理系统 ID 包含小数时抛出 BusinessException")
        void rejectsDecimalSystemId() {
            when(references.activeParameters(ACTOR, CATEGORY_SYSTEM_MAPPING))
                    .thenReturn(List.of(
                            new SystemParameterReference("REQ_CORE", "1001.5")
                    ));

            assertThrows(BusinessException.class, () -> settings.systemMappings(ACTOR));
        }

        @Test
        @DisplayName("物理系统 ID 为空字符串或空白字符时抛出 BusinessException")
        void rejectsBlankSystemId() {
            when(references.activeParameters(ACTOR, CATEGORY_SYSTEM_MAPPING))
                    .thenReturn(List.of(
                            new SystemParameterReference("REQ_CORE", "   ")
                    ));

            assertThrows(BusinessException.class, () -> settings.systemMappings(ACTOR));
        }
    }

    @Nested
    @DisplayName("编号模板配置 (DEVELOPMENT_NUMBERING)")
    class NumberingTemplateTests {

        @Test
        @DisplayName("未配置时关联任务返回默认模板 RW_{requirementNo}_{seq}")
        void returnsDefaultLinkedTemplateWhenNotConfigured() {
            when(references.activeParameters(ACTOR, CATEGORY_NUMBERING))
                    .thenReturn(List.of());

            String template = settings.numberingTemplate(ACTOR, true);

            assertThat(template).isEqualTo("RW_{requirementNo}_{seq}");
        }

        @Test
        @DisplayName("未配置时自主任务返回默认模板 RW_ZZ_{date}_{seq}")
        void returnsDefaultStandaloneTemplateWhenNotConfigured() {
            when(references.activeParameters(ACTOR, CATEGORY_NUMBERING))
                    .thenReturn(List.of());

            String template = settings.numberingTemplate(ACTOR, false);

            assertThat(template).isEqualTo("RW_ZZ_{date}_{seq}");
        }

        @Test
        @DisplayName("仅配置关联任务模板时自主任务仍回退默认模板")
        void linkedConfiguredWhileStandaloneFallsBackToDefault() {
            when(references.activeParameters(ACTOR, CATEGORY_NUMBERING))
                    .thenReturn(List.of(
                            new SystemParameterReference("LINKED", "CUSTOM_{requirementNo}_{seq}")
                    ));

            assertThat(settings.numberingTemplate(ACTOR, true)).isEqualTo("CUSTOM_{requirementNo}_{seq}");
            assertThat(settings.numberingTemplate(ACTOR, false)).isEqualTo("RW_ZZ_{date}_{seq}");
        }

        @Test
        @DisplayName("仅配置自主任务模板时关联任务仍回退默认模板")
        void standaloneConfiguredWhileLinkedFallsBackToDefault() {
            when(references.activeParameters(ACTOR, CATEGORY_NUMBERING))
                    .thenReturn(List.of(
                            new SystemParameterReference("STANDALONE", "SELF_{date}_{seq}")
                    ));

            assertThat(settings.numberingTemplate(ACTOR, false)).isEqualTo("SELF_{date}_{seq}");
            assertThat(settings.numberingTemplate(ACTOR, true)).isEqualTo("RW_{requirementNo}_{seq}");
        }

        @Test
        @DisplayName("关联任务支持包含 date 和 seq 变量的自定义模板")
        void customLinkedTemplateWithDateAndSeq() {
            when(references.activeParameters(ACTOR, CATEGORY_NUMBERING))
                    .thenReturn(List.of(
                            new SystemParameterReference("LINKED", "DEV_{requirementNo}_{date}_{seq}")
                    ));

            String template = settings.numberingTemplate(ACTOR, true);

            assertThat(template).isEqualTo("DEV_{requirementNo}_{date}_{seq}");
        }

        @Test
        @DisplayName("模板缺少必需变量 seq 抛出 BusinessException")
        void rejectsTemplateMissingSeq() {
            when(references.activeParameters(ACTOR, CATEGORY_NUMBERING))
                    .thenReturn(List.of(
                            new SystemParameterReference("LINKED", "DEV_{requirementNo}_{date}")
                    ));

            assertThrows(BusinessException.class, () -> settings.numberingTemplate(ACTOR, true));
        }

        @Test
        @DisplayName("自主任务模板使用 requirementNo 变量抛出 BusinessException")
        void rejectsStandaloneTemplateUsingRequirementNo() {
            when(references.activeParameters(ACTOR, CATEGORY_NUMBERING))
                    .thenReturn(List.of(
                            new SystemParameterReference("STANDALONE", "DEV_{requirementNo}_{date}_{seq}")
                    ));

            assertThrows(BusinessException.class, () -> settings.numberingTemplate(ACTOR, false));
        }

        @Test
        @DisplayName("模板使用未知变量抛出 BusinessException")
        void rejectsTemplateWithUnknownVariable() {
            when(references.activeParameters(ACTOR, CATEGORY_NUMBERING))
                    .thenReturn(List.of(
                            new SystemParameterReference("LINKED", "DEV_{requirementNo}_{seq}_{unknownVar}")
                    ));

            assertThrows(BusinessException.class, () -> settings.numberingTemplate(ACTOR, true));
        }

        @Test
        @DisplayName("存在未知配置键抛出 BusinessException")
        void rejectsUnknownConfigKey() {
            when(references.activeParameters(ACTOR, CATEGORY_NUMBERING))
                    .thenReturn(List.of(
                            new SystemParameterReference("UNKNOWN_KEY", "DEV_{seq}")
                    ));

            assertThrows(BusinessException.class, () -> settings.numberingTemplate(ACTOR, true));
        }

        @Test
        @DisplayName("存在重复配置键抛出 BusinessException")
        void rejectsDuplicateConfigKey() {
            when(references.activeParameters(ACTOR, CATEGORY_NUMBERING))
                    .thenReturn(List.of(
                            new SystemParameterReference("LINKED", "T1_{requirementNo}_{seq}"),
                            new SystemParameterReference("LINKED", "T2_{requirementNo}_{seq}")
                    ));

            assertThrows(BusinessException.class, () -> settings.numberingTemplate(ACTOR, true));
        }
    }

    @Nested
    @DisplayName("工作日历配置 (DEVELOPMENT_CALENDAR)")
    class CalendarTests {

        @Test
        @DisplayName("未配置日历参数时返回缺省日历（周一至周五，工作日与调休日为空）")
        void returnsDefaultCalendarWhenNotConfigured() {
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of());

            CalendarDefinition cal = settings.calendar(ACTOR);

            assertThat(cal).isNotNull();
            assertThat(cal.weekdays()).containsExactly(1, 2, 3, 4, 5);
            assertThat(cal.workingDates()).isEmpty();
            assertThat(cal.restDates()).isEmpty();
        }

        @Test
        @DisplayName("DEFAULT 参数为空 JSON 对象时应用缺省值")
        void appliesDefaultsWhenEmptyJsonObjectConfigured() {
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", "{}")
                    ));

            CalendarDefinition cal = settings.calendar(ACTOR);

            assertThat(cal).isNotNull();
            assertThat(cal.weekdays()).containsExactly(1, 2, 3, 4, 5);
            assertThat(cal.workingDates()).isEmpty();
            assertThat(cal.restDates()).isEmpty();
        }

        @Test
        @DisplayName("正常解析自定义日历配置（weekdays、workingDates、restDates）")
        void parsesValidCustomCalendar() {
            String json = """
                    {
                      "weekdays": [1, 2, 3, 4, 5, 6],
                      "workingDates": ["2026-09-13"],
                      "restDates": ["2026-10-01", "2026-10-02"]
                    }
                    """;
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", json)
                    ));

            CalendarDefinition cal = settings.calendar(ACTOR);

            assertThat(cal).isNotNull();
            assertThat(cal.weekdays()).containsExactly(1, 2, 3, 4, 5, 6);
            assertThat(cal.workingDates()).containsExactly(LocalDate.of(2026, 9, 13));
            assertThat(cal.restDates()).containsExactly(
                    LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 10, 2)
            );
        }

        @Test
        @DisplayName("JSON 包含未知属性抛出 BusinessException")
        void rejectsUnknownPropertiesInCalendarJson() {
            String json = """
                    {
                      "weekdays": [1, 2, 3, 4, 5],
                      "unknownField": "forbidden"
                    }
                    """;
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", json)
                    ));

            assertThrows(BusinessException.class, () -> settings.calendar(ACTOR));
        }

        @Test
        @DisplayName("JSON 包含重复键抛出 BusinessException")
        void rejectsDuplicateKeysInCalendarJson() {
            String json = """
                    {
                      "weekdays": [1, 2, 3, 4, 5],
                      "weekdays": [1, 2, 3]
                    }
                    """;
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", json)
                    ));

            assertThrows(BusinessException.class, () -> settings.calendar(ACTOR));
        }

        @Test
        @DisplayName("JSON 格式损坏或非 JSON 字符串抛出 BusinessException")
        void rejectsMalformedJson() {
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", "{invalid_json:")
                    ));

            assertThrows(BusinessException.class, () -> settings.calendar(ACTOR));
        }

        @Test
        @DisplayName("weekdays 包含小于 1 的越界数字抛出 BusinessException")
        void rejectsWeekdayBelowMinimum() {
            String json = """
                    {
                      "weekdays": [0, 1, 2, 3, 4, 5]
                    }
                    """;
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", json)
                    ));

            assertThrows(BusinessException.class, () -> settings.calendar(ACTOR));
        }

        @Test
        @DisplayName("weekdays 包含大于 7 的越界数字抛出 BusinessException")
        void rejectsWeekdayAboveMaximum() {
            String json = """
                    {
                      "weekdays": [1, 2, 3, 4, 5, 8]
                    }
                    """;
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", json)
                    ));

            assertThrows(BusinessException.class, () -> settings.calendar(ACTOR));
        }

        @Test
        @DisplayName("weekdays 包含重复值抛出 BusinessException")
        void rejectsDuplicateWeekdays() {
            String json = """
                    {
                      "weekdays": [1, 1, 2, 3, 4, 5]
                    }
                    """;
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", json)
                    ));

            assertThrows(BusinessException.class, () -> settings.calendar(ACTOR));
        }

        @Test
        @DisplayName("日期字符串格式无效抛出 BusinessException")
        void rejectsInvalidDateFormat() {
            String json = """
                    {
                      "workingDates": ["not-a-date"]
                    }
                    """;
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", json)
                    ));

            assertThrows(BusinessException.class, () -> settings.calendar(ACTOR));
        }

        @Test
        @DisplayName("日期非公历合法日期抛出 BusinessException")
        void rejectsNonExistentCalendarDate() {
            String json = """
                    {
                      "restDates": ["2026-02-30"]
                    }
                    """;
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", json)
                    ));

            assertThrows(BusinessException.class, () -> settings.calendar(ACTOR));
        }

        @Test
        @DisplayName("同一日期同时出现在 workingDates 与 restDates 抛出 BusinessException")
        void rejectsSameDateInWorkingAndRestDates() {
            String json = """
                    {
                      "workingDates": ["2026-10-01"],
                      "restDates": ["2026-10-01"]
                    }
                    """;
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", json)
                    ));

            assertThrows(BusinessException.class, () -> settings.calendar(ACTOR));
        }

        @Test
        @DisplayName("workingDates 包含重复日期抛出 BusinessException")
        void rejectsDuplicateWorkingDates() {
            String json = """
                    {
                      "workingDates": ["2026-09-13", "2026-09-13"]
                    }
                    """;
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", json)
                    ));

            assertThrows(BusinessException.class, () -> settings.calendar(ACTOR));
        }

        @Test
        @DisplayName("restDates 包含重复日期抛出 BusinessException")
        void rejectsDuplicateRestDates() {
            String json = """
                    {
                      "restDates": ["2026-10-01", "2026-10-01"]
                    }
                    """;
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", json)
                    ));

            assertThrows(BusinessException.class, () -> settings.calendar(ACTOR));
        }

        @Test
        @DisplayName("存在除 DEFAULT 之外的配置键抛出 BusinessException")
        void rejectsCalendarConfigKeyOtherThanDefault() {
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("CUSTOM", "{}")
                    ));

            assertThrows(BusinessException.class, () -> settings.calendar(ACTOR));
        }

        @Test
        @DisplayName("存在多个 DEFAULT 配置键抛出 BusinessException")
        void rejectsMultipleCalendarParameters() {
            when(references.activeParameters(ACTOR, CATEGORY_CALENDAR))
                    .thenReturn(List.of(
                            new SystemParameterReference("DEFAULT", "{}"),
                            new SystemParameterReference("DEFAULT", "{}")
                    ));

            assertThrows(BusinessException.class, () -> settings.calendar(ACTOR));
        }
    }
}

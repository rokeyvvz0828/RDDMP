package com.ccb.development.config;

import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemReferenceQuery;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ccb.development.model.DevelopmentTaskModels.*;

@Component
public class DevelopmentSettings {
    private final SystemReferenceQuery references;
    private final ObjectMapper json;

    public DevelopmentSettings(SystemReferenceQuery references, ObjectMapper json) {
        this.references = references;
        this.json = json.copy().findAndRegisterModules().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }

    public Map<String, Long> systemMappings(AuthUser actor) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (var entry : parameters(actor, "DEVELOPMENT_SYSTEM_MAPPING").entrySet()) {
            try {
                if (!entry.getValue().matches("[0-9]+")) throw new NumberFormatException();
                long id = Long.parseLong(entry.getValue());
                if (id <= 0) throw new NumberFormatException();
                result.put(entry.getKey(), id);
            } catch (NumberFormatException error) { throw conflict("系统映射必须使用有效的物理系统标识"); }
        }
        return Map.copyOf(result);
    }

    public String numberingTemplate(AuthUser actor, boolean linked) {
        var values = parameters(actor, "DEVELOPMENT_NUMBERING");
        for (var entry : values.entrySet()) {
            if (!Set.of("LINKED", "STANDALONE").contains(entry.getKey())) throw conflict("编号配置键无效");
            String template = entry.getValue();
            String literals = template.replace("{requirementNo}", "").replace("{date}", "").replace("{seq}", "");
            if (template.length() > 180 || !template.contains("{seq}") || literals.contains("{") || literals.contains("}")
                    || template.chars().anyMatch(Character::isISOControl)
                    || ("STANDALONE".equals(entry.getKey()) && template.contains("{requirementNo}"))) {
                throw conflict("编号模板包含无效变量或缺少序号");
            }
        }
        return values.getOrDefault(linked ? "LINKED" : "STANDALONE", linked ? "RW_{requirementNo}_{seq}" : "RW_ZZ_{date}_{seq}");
    }

    public CalendarDefinition calendar(AuthUser actor) {
        var values = parameters(actor, "DEVELOPMENT_CALENDAR");
        if (!Set.of("DEFAULT").containsAll(values.keySet())) throw conflict("工作日历配置键无效");
        try {
            return json.readValue(values.getOrDefault("DEFAULT", "{}"), CalendarDefinition.class);
        } catch (Exception error) { throw conflict("工作日历配置无效，请检查日期、星期和重复配置"); }
    }

    private Map<String, String> parameters(AuthUser actor, String category) {
        Map<String, String> result = new LinkedHashMap<>();
        for (var parameter : references.activeParameters(actor, category)) {
            String key = required(parameter.code(), 128, "参数键");
            String value = required(parameter.label(), 32000, "参数值");
            if (result.putIfAbsent(key, value) != null) throw conflict("参数配置存在重复键");
        }
        return result;
    }

    public record CalendarDefinition(List<Integer> weekdays, List<LocalDate> workingDates, List<LocalDate> restDates) {
        public CalendarDefinition {
            weekdays = weekdays == null ? List.of(1, 2, 3, 4, 5) : List.copyOf(weekdays);
            workingDates = workingDates == null ? List.of() : List.copyOf(workingDates);
            restDates = restDates == null ? List.of() : List.copyOf(restDates);
            if (new HashSet<>(weekdays).size() != weekdays.size() || weekdays.stream().anyMatch(day -> day < 1 || day > 7)
                    || new HashSet<>(workingDates).size() != workingDates.size() || new HashSet<>(restDates).size() != restDates.size()
                    || workingDates.stream().anyMatch(restDates::contains)) throw new IllegalArgumentException("工作日历冲突");
            for (var date : workingDates) dates(date, date);
            for (var date : restDates) dates(date, date);
        }
    }
}

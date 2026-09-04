package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Generates immutable business identifiers for the nine data-migration content types. */
@Component
public class ContentDocCodeGenerator {
    private static final Map<String, String> PREFIXES = Map.ofEntries(
            Map.entry("PLAN", "PLAN"),
            Map.entry("MAPPING_DOC", "MAP"),
            Map.entry("DEPENDENCY", "DEP"),
            Map.entry("SCRIPT", "SCRIPT"),
            Map.entry("TOPIC", "TOPIC"),
            Map.entry("RELEASE_DRILL", "DRILL"),
            Map.entry("REPORT", "REPORT"),
            Map.entry("RULE", "RULE"),
            Map.entry("PARAMETER", "PARAM"));

    public String generate(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toUpperCase(Locale.ROOT);
        String prefix = PREFIXES.get(normalized);
        if (prefix == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported content type for document code");
        }
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
    }
}

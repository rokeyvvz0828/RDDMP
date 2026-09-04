package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccb.common.exception.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentDocCodeGeneratorTest {
    private final ContentDocCodeGenerator generator = new ContentDocCodeGenerator();

    @Test
    void generatesRegisteredPrefixesWithLowercaseUuidWithoutHyphens() {
        Map<String, String> prefixes = Map.ofEntries(
                Map.entry("PLAN", "PLAN"),
                Map.entry("MAPPING_DOC", "MAP"),
                Map.entry("DEPENDENCY", "DEP"),
                Map.entry("SCRIPT", "SCRIPT"),
                Map.entry("TOPIC", "TOPIC"),
                Map.entry("RELEASE_DRILL", "DRILL"),
                Map.entry("REPORT", "REPORT"),
                Map.entry("RULE", "RULE"),
                Map.entry("PARAMETER", "PARAM"));

        prefixes.forEach((type, prefix) -> {
            String first = generator.generate(type);
            String second = generator.generate(type);
            assertTrue(first.matches(prefix + "-[0-9a-f]{32}"), first);
            assertNotEquals(first, second);
        });
    }

    @Test
    void rejectsTypesOutsideTheNineContentTables() {
        assertThrows(BusinessException.class, () -> generator.generate("MEETING"));
    }
}

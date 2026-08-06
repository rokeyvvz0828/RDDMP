package com.ccb.infrastructure.storage;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinioStoragePropertiesTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsMissingExternalConfiguration() {
        Set<String> invalidFields = validator.validate(new MinioStorageProperties()).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertEquals(Set.of("endpoint", "accessKey", "secretKey", "bucket", "presignedExpirySeconds"), invalidFields);
    }

    @Test
    void acceptsValidExternalConfiguration() {
        MinioStorageProperties properties = validProperties();

        assertTrue(validator.validate(properties).isEmpty());
    }

    @Test
    void rejectsExpiryOutsideMinioRange() {
        MinioStorageProperties properties = validProperties();
        properties.setPresignedExpirySeconds(0);
        assertEquals("presignedExpirySeconds", validator.validate(properties).iterator().next().getPropertyPath().toString());

        properties.setPresignedExpirySeconds(604801);
        assertEquals("presignedExpirySeconds", validator.validate(properties).iterator().next().getPropertyPath().toString());
    }

    private MinioStorageProperties validProperties() {
        MinioStorageProperties properties = new MinioStorageProperties();
        properties.setEndpoint("http://127.0.0.1:9000");
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        properties.setBucket("test-bucket");
        properties.setPresignedExpirySeconds(3600);
        return properties;
    }
}

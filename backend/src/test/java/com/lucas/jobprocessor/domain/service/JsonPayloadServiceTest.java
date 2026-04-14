package com.lucas.jobprocessor.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucas.jobprocessor.api.exception.InvalidJobRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonPayloadServiceTest {

    private final JsonPayloadService jsonPayloadService = new JsonPayloadService(new ObjectMapper());

    @Test
    void shouldCanonicalizeNestedObjects() {
        String payload = """
                {
                  "b": 1,
                  "a": {
                    "d": 4,
                    "c": 3
                  }
                }
                """;

        String canonical = jsonPayloadService.canonicalize(payload);

        assertEquals("{\"a\":{\"c\":3,\"d\":4},\"b\":1}", canonical);
    }

    @Test
    void shouldRejectInvalidJsonPayload() {
        assertThrows(InvalidJobRequestException.class, () -> jsonPayloadService.canonicalize("{invalid"));
    }
}

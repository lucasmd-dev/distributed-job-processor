package com.lucas.jobprocessor.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldDegradeGracefullyWhenRedisReadFails() {
        IdempotencyService service = new IdempotencyService(redisTemplate, 24);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

        when(redisTemplate.hasKey("idempotency:test-key")).thenThrow(new RuntimeException("redis down"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        assertFalse(service.exists("test-key"));
        assertDoesNotThrow(() -> service.markAsProcessed(UUID.randomUUID()));
    }
}

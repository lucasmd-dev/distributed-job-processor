package com.lucas.jobprocessor.worker;

import com.lucas.jobprocessor.messaging.dto.JobMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobHandlerRouterTest {

    @Test
    void shouldRouteMessageToRegisteredHandler() {
        JobHandlerRouter router = new JobHandlerRouter(List.of(new TestHandler("EMAIL_SEND", "{\"status\":\"ok\"}")));

        String output = router.handle(new JobMessage(UUID.randomUUID(), 1, "EMAIL_SEND", "{}", 1, 3));

        assertEquals("{\"status\":\"ok\"}", output);
    }

    @Test
    void shouldFailWhenHandlerTypeIsUnknown() {
        JobHandlerRouter router = new JobHandlerRouter(List.of(new TestHandler("EMAIL_SEND", "{}")));

        assertThrows(IllegalArgumentException.class, () ->
                router.handle(new JobMessage(UUID.randomUUID(), 1, "WEBHOOK_DISPATCH", "{}", 1, 3)));
    }

    private record TestHandler(String type, String output) implements JobHandler {

        @Override
        public String handle(JobMessage message) {
            return output;
        }

        @Override
        public String getType() {
            return type;
        }
    }
}

package com.lucas.jobprocessor.worker;

import com.lucas.jobprocessor.messaging.dto.JobMessage;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JobHandlerRouter {

    private final Map<String, JobHandler> handlers;

    public JobHandlerRouter(java.util.List<JobHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(JobHandler::getType, Function.identity()));
    }

    public String handle(JobMessage message) {
        JobHandler handler = handlers.get(message.type());
        if (handler == null) {
            throw new IllegalArgumentException("No handler found for job type: " + message.type());
        }
        return handler.handle(message);
    }

    public boolean supportsType(String type) {
        return handlers.containsKey(type);
    }

    public Set<String> supportedTypes() {
        return handlers.keySet();
    }
}

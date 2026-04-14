package com.lucas.jobprocessor.worker;

import com.lucas.jobprocessor.messaging.dto.JobMessage;

public interface JobHandler {

    String handle(JobMessage message);

    String getType();
}

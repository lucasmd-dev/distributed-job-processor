package com.lucas.jobprocessor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucas.jobprocessor.domain.model.Job;
import com.lucas.jobprocessor.domain.model.JobExecution;
import com.lucas.jobprocessor.domain.model.JobStatus;
import com.lucas.jobprocessor.domain.repository.JobExecutionRepository;
import com.lucas.jobprocessor.domain.repository.JobRepository;
import com.lucas.jobprocessor.domain.service.AuditLogService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RetryManagerTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobExecutionRepository jobExecutionRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private Channel channel;

    @Test
    void shouldReuseExecutionRowWhenSchedulingRetry() throws Exception {
        RetryManager retryManager = new RetryManager(
                jobRepository,
                jobExecutionRepository,
                rabbitTemplate,
                new ObjectMapper(),
                auditLogService,
                1
        );

        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setType("WEBHOOK_DISPATCH");
        job.setPayload("{\"forceFailure\":true}");
        job.setRunNumber((short) 1);
        job.setAttempt((short) 1);
        job.setMaxAttempts((short) 2);

        JobExecution execution = new JobExecution();
        execution.setJob(job);
        execution.setRunNumber((short) 1);
        execution.setAttempt((short) 1);
        execution.setStatus(JobStatus.RUNNING);

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(10L);
        Message message = new Message(new byte[0], properties);

        JobStatus status = retryManager.handle(job, execution, new RuntimeException("boom"), channel, message, "worker-1");

        ArgumentCaptor<JobExecution> executionCaptor = ArgumentCaptor.forClass(JobExecution.class);
        verify(jobExecutionRepository).save(executionCaptor.capture());
        verify(rabbitTemplate).send(eq("job.retry.exchange"), eq("retry"), any(Message.class));
        verify(channel).basicAck(10L, false);

        assertEquals(JobStatus.FAILED, status);
        assertEquals(JobStatus.FAILED, executionCaptor.getValue().getStatus());
        assertEquals((short) 1, executionCaptor.getValue().getAttempt());
    }

    @Test
    void shouldMoveJobToDlqWhenAttemptsAreExhausted() throws Exception {
        RetryManager retryManager = new RetryManager(
                jobRepository,
                jobExecutionRepository,
                rabbitTemplate,
                new ObjectMapper(),
                auditLogService,
                1
        );

        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setType("WEBHOOK_DISPATCH");
        job.setPayload("{\"forceFailure\":true}");
        job.setRunNumber((short) 1);
        job.setAttempt((short) 2);
        job.setMaxAttempts((short) 2);

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(20L);
        Message message = new Message(new byte[0], properties);

        JobStatus status = retryManager.handle(job, null, new RuntimeException("boom"), channel, message, "worker-1");

        verify(rabbitTemplate).send(eq("job.dlx"), eq(""), any(Message.class));
        verify(channel).basicAck(20L, false);
        assertEquals(JobStatus.DEAD, status);
    }
}

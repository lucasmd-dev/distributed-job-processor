package com.lucas.jobprocessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucas.jobprocessor.domain.model.Job;
import com.lucas.jobprocessor.domain.model.JobExecution;
import com.lucas.jobprocessor.domain.model.JobStatus;
import com.lucas.jobprocessor.domain.repository.AuditLogRepository;
import com.lucas.jobprocessor.domain.repository.JobExecutionRepository;
import com.lucas.jobprocessor.domain.repository.JobRepository;
import com.lucas.jobprocessor.domain.service.JobSchedulerService;
import com.lucas.jobprocessor.messaging.config.RabbitMQConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class JobProcessorIntegrationTest {

    private static final String API_KEY = "test-secret-key";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server --appendonly no");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.api-key", () -> API_KEY);
        registry.add("app.rate-limit.enabled", () -> false);
        registry.add("app.job.retry-backoff-base-seconds", () -> 1);
        registry.add("app.scheduler.enabled", () -> true);
        registry.add("app.messaging.consumer-enabled", () -> true);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private JobExecutionRepository jobExecutionRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;
    @Autowired
    private JobSchedulerService jobSchedulerService;

    @AfterEach
    void cleanUp() {
        rabbitAdmin.purgeQueue(RabbitMQConfig.QUEUE, true);
        rabbitAdmin.purgeQueue(RabbitMQConfig.RETRY_QUEUE, true);
        rabbitAdmin.purgeQueue(RabbitMQConfig.DLQ, true);
        jobExecutionRepository.deleteAll();
        auditLogRepository.deleteAll();
        jobRepository.deleteAll();

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @Test
    void shouldProcessJobSuccessfully() throws Exception {
        JsonNode response = createJob(Map.of(
                "type", "EMAIL_SEND",
                "payload", payload(Map.of("to", "user@example.com"))
        ));

        UUID jobId = UUID.fromString(response.get("id").asText());

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Job job = jobRepository.findById(jobId).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCESS);
                    List<JobExecution> executions = jobExecutionRepository.findByJobIdOrderByRunNumberAscAttemptAsc(jobId);
                    assertThat(executions).hasSize(1);
                    assertThat(executions.get(0).getAttempt()).isEqualTo((short) 1);
                    assertThat(executions.get(0).getOutput()).contains("SENT");
                });

        assertThat(auditLogRepository.count()).isGreaterThan(0);
    }

    @Test
    void shouldRejectDuplicateJobByIdempotencyKey() throws Exception {
        Map<String, Object> request = Map.of(
                "type", "EMAIL_SEND",
                "payload", payload(Map.of("to", "user@example.com")),
                "idempotencyKey", "welcome-email-1",
                "scheduledAt", OffsetDateTime.now().plusMinutes(5).toString()
        );

        mockMvc.perform(post("/api/v1/jobs")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/jobs")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRetryFailedJobAndMoveItToDlqWithoutDuplicateExecutionRows() throws Exception {
        JsonNode response = createJob(Map.of(
                "type", "WEBHOOK_DISPATCH",
                "payload", payload(Map.of("forceFailure", true, "delayMs", 10)),
                "maxAttempts", 2
        ));

        UUID jobId = UUID.fromString(response.get("id").asText());

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    Job job = jobRepository.findById(jobId).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD);

                    List<JobExecution> executions = jobExecutionRepository.findByJobIdOrderByRunNumberAscAttemptAsc(jobId);
                    assertThat(executions).hasSize(2);
                    assertThat(executions).extracting(JobExecution::getAttempt).containsExactly((short) 1, (short) 2);
                    assertThat(executions).extracting(JobExecution::getStatus).containsExactly(JobStatus.FAILED, JobStatus.DEAD);
                });
    }

    @Test
    void shouldCreateNewRunWhenManualRetryIsTriggered() throws Exception {
        JsonNode response = createJob(Map.of(
                "type", "WEBHOOK_DISPATCH",
                "payload", payload(Map.of("forceFailure", true, "delayMs", 10)),
                "maxAttempts", 1
        ));

        UUID jobId = UUID.fromString(response.get("id").asText());

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.DEAD));

        mockMvc.perform(post("/api/v1/jobs/{id}/retry", jobId)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isAccepted());

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Job job = jobRepository.findById(jobId).orElseThrow();
                    assertThat(job.getRunNumber()).isEqualTo((short) 2);
                    assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD);

                    List<JobExecution> executions = jobExecutionRepository.findByJobIdOrderByRunNumberAscAttemptAsc(jobId);
                    assertThat(executions).hasSize(2);
                    assertThat(executions).extracting(JobExecution::getRunNumber).containsExactly((short) 1, (short) 2);
                    assertThat(executions).extracting(JobExecution::getAttempt).containsExactly((short) 1, (short) 1);
                });
    }

    @Test
    void shouldDispatchScheduledJobOnceWhenSchedulerRuns() throws Exception {
        JsonNode response = createJob(Map.of(
                "type", "REPORT_GENERATE",
                "payload", payload(Map.of("report", "daily")),
                "scheduledAt", OffsetDateTime.now().plusSeconds(2).toString()
        ));

        UUID jobId = UUID.fromString(response.get("id").asText());

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.SCHEDULED));

        Thread.sleep(2200);
        jobSchedulerService.dispatchScheduledJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Job job = jobRepository.findById(jobId).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCESS);
                    assertThat(jobExecutionRepository.findByJobIdOrderByRunNumberAscAttemptAsc(jobId)).hasSize(1);
                });

        mockMvc.perform(get("/api/v1/jobs/stats").header("X-API-Key", API_KEY))
                .andExpect(status().isOk());
    }

    private JsonNode createJob(Map<String, Object> request) throws Exception {
        String content = mockMvc.perform(post("/api/v1/jobs")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(content);
    }

    private String payload(Map<String, Object> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }
}

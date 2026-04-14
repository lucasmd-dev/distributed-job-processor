CREATE TABLE job_executions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id        UUID NOT NULL REFERENCES jobs(id),
    attempt       SMALLINT NOT NULL,
    status        VARCHAR(20) NOT NULL,
    worker_id     VARCHAR(255),
    started_at    TIMESTAMPTZ NOT NULL,
    finished_at   TIMESTAMPTZ,
    output        JSONB,
    error_message TEXT,
    stack_trace   TEXT,
    UNIQUE (job_id, attempt)
);

CREATE INDEX idx_executions_job_id ON job_executions(job_id);

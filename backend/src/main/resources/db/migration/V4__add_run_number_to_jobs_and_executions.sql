ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS run_number SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE job_executions
    ADD COLUMN IF NOT EXISTS run_number SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE job_executions
    DROP CONSTRAINT IF EXISTS job_executions_job_id_attempt_key;

ALTER TABLE job_executions
    ADD CONSTRAINT uk_job_executions_job_run_attempt UNIQUE (job_id, run_number, attempt);

CREATE INDEX IF NOT EXISTS idx_executions_job_run ON job_executions(job_id, run_number);

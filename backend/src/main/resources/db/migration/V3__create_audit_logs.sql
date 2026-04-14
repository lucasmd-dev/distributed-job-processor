CREATE TABLE audit_logs (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id     UUID REFERENCES jobs(id),
    event      VARCHAR(100) NOT NULL,
    actor      VARCHAR(50) NOT NULL,
    details    JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_job_id ON audit_logs(job_id);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at DESC);

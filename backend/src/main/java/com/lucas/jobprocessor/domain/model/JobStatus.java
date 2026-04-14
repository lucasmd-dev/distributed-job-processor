package com.lucas.jobprocessor.domain.model;

public enum JobStatus {
    PENDING, SCHEDULED, RUNNING, SUCCESS, FAILED, DEAD, CANCELLED;

    public boolean canTransitionTo(JobStatus next) {
        return switch (this) {
            case PENDING -> next == RUNNING || next == CANCELLED || next == SCHEDULED;
            case SCHEDULED -> next == PENDING;
            case RUNNING -> next == SUCCESS || next == FAILED;
            case FAILED -> next == PENDING || next == DEAD || next == CANCELLED;
            case DEAD -> next == PENDING;
            default -> false;
        };
    }
}

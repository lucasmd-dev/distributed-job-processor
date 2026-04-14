package com.lucas.jobprocessor.api.exception;

public class DuplicateJobException extends RuntimeException {

    public DuplicateJobException(String message) {
        super(message);
    }
}

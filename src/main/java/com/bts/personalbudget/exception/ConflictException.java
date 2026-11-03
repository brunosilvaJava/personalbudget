package com.bts.personalbudget.exception;

/**
 * The request is valid but conflicts with the current state: the version it read is no longer the
 * current one, or the operation is not allowed in the state the resource is in. Answered with 409.
 * The message is written for the user.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(final String message) {
        super(message);
    }
}

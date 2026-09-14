package io.github.johneliud.identity_service.exception;

public class RoleNotAssignedException extends RuntimeException {

    public RoleNotAssignedException(String message) {
        super(message);
    }
}

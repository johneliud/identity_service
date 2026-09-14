package io.github.johneliud.identity_service.exception;

public class RoleAlreadyAssignedException extends RuntimeException {

    public RoleAlreadyAssignedException(String message) {
        super(message);
    }
}

package com.example.aichat.application.common;

/**
 * A requested resource does not exist or is not visible to the current user (reported as 404 to avoid leaking
 * the existence of other users' data).
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resource;

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found" + (id == null ? "" : ": " + id));
        this.resource = resource;
    }

    public String resource() {
        return resource;
    }
}

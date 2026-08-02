package com.example.aichat.application.auth;

/**
 * Wrong username/e-mail or password. Deliberately does not reveal which one.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}

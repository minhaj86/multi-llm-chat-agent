package com.example.aichat.domain.conversation;

public enum MessageStatus {
    /** Assistant answer is being generated. */
    STREAMING,
    /** Final message. */
    COMPLETE,
    /** Generation was stopped by the user; content holds the partial answer. */
    CANCELLED,
    /** Generation failed; error code/message describe why. */
    ERROR
}

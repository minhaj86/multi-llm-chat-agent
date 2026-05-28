package com.example.aichat.domain.conversation;

/**
 * Origin of a conversation title. User-chosen titles are never overwritten by generated ones.
 */
public enum TitleSource {
    DEFAULT,
    GENERATED,
    USER
}

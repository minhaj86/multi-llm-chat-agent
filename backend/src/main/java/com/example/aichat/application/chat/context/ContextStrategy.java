package com.example.aichat.application.chat.context;

/**
 * How much of a conversation is sent to the model.
 */
public enum ContextStrategy {
    /** Every eligible message (no trimming). Only suitable for short conversations. */
    FULL_CONTEXT,
    /** The last N messages, additionally capped by the token budget. */
    LAST_N_MESSAGES,
    /** As many recent messages as fit into the token budget. */
    TOKEN_WINDOW,
    /** A rolling summary of older messages plus the most recent messages that fit into the token budget. */
    SUMMARY_PLUS_RECENT
}

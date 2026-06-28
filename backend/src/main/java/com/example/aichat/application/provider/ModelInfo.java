package com.example.aichat.application.provider;

/**
 * A selectable model of a provider.
 */
public record ModelInfo(String id, String name, String ownedBy, Integer contextWindow, boolean isDefault) {
}

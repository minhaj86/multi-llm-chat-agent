package com.example.aichat.rag.application;

import com.example.aichat.rag.domain.KnowledgeCollection;
import java.util.UUID;

/**
 * Decides which knowledge collections a signed-in user may use in chat and read through citations. Administrators
 * manage every collection through the admin API regardless of this policy.
 *
 * <p>The default policy ({@link EnabledCollectionsAccessPolicy}) lets every user read every enabled collection.
 * Collection-level permissions (roles, groups, per-collection grants) are added by registering another bean of this
 * type; retrieval, chat and the citation endpoints apply it without further changes.
 */
public interface KnowledgeAccessPolicy {

    /** Whether the user may retrieve from, and read citations of, this (enabled) collection. */
    boolean canRead(UUID userId, KnowledgeCollection collection);
}

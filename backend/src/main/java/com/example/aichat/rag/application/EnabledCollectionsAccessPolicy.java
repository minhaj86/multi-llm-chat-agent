package com.example.aichat.rag.application;

import com.example.aichat.rag.domain.KnowledgeCollection;
import java.util.UUID;

/** Default {@link KnowledgeAccessPolicy}: every signed-in user may use every enabled collection. */
public class EnabledCollectionsAccessPolicy implements KnowledgeAccessPolicy {

    @Override
    public boolean canRead(UUID userId, KnowledgeCollection collection) {
        return collection.enabled();
    }
}

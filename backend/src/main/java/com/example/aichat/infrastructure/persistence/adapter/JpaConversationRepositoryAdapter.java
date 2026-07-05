package com.example.aichat.infrastructure.persistence.adapter;

import com.example.aichat.domain.conversation.Conversation;
import com.example.aichat.domain.conversation.ConversationRepository;
import com.example.aichat.domain.conversation.TitleSource;
import com.example.aichat.infrastructure.persistence.entity.ConversationEntity;
import com.example.aichat.infrastructure.persistence.jpa.ConversationJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaConversationRepositoryAdapter implements ConversationRepository {

    private final ConversationJpaRepository jpa;

    public JpaConversationRepositoryAdapter(ConversationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Conversation save(Conversation conversation) {
        return jpa.save(ConversationEntity.from(conversation)).toDomain();
    }

    @Override
    public Optional<Conversation> findById(UUID id) {
        return jpa.findById(id).map(ConversationEntity::toDomain);
    }

    @Override
    public Optional<Conversation> findByIdAndUserId(UUID id, UUID userId) {
        return jpa.findOwned(id, userId).map(ConversationEntity::toDomain);
    }

    @Override
    public List<Conversation> findByUserId(UUID userId, int limit) {
        return jpa.findRecent(userId, Limit.of(limit)).stream().map(ConversationEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    @Transactional
    public void touch(UUID id, String provider, String model, Instant now) {
        jpa.touch(id, provider, model, now);
    }

    @Override
    @Transactional
    public boolean updateGeneratedTitle(UUID id, String title) {
        return jpa.updateTitleIfSource(id, title, TitleSource.GENERATED, TitleSource.DEFAULT) > 0;
    }

    @Override
    @Transactional
    public void updateSummary(UUID id, String summary, Instant summarizedUntil) {
        jpa.updateSummary(id, summary, summarizedUntil);
    }
}

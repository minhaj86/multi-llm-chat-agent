package com.example.aichat.infrastructure.persistence.jpa;

import com.example.aichat.domain.conversation.TitleSource;
import com.example.aichat.infrastructure.persistence.entity.ConversationEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationJpaRepository extends JpaRepository<ConversationEntity, UUID> {

    @Query("select c from ConversationEntity c where c.id = :id and c.userId = :userId")
    Optional<ConversationEntity> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select c from ConversationEntity c where c.userId = :userId order by c.updatedAt desc, c.id desc")
    List<ConversationEntity> findRecent(@Param("userId") UUID userId, Limit limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ConversationEntity c set c.updatedAt = :now, c.lastProvider = :provider, c.lastModel = :model "
            + "where c.id = :id")
    int touch(@Param("id") UUID id, @Param("provider") String provider, @Param("model") String model,
              @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ConversationEntity c set c.title = :title, c.titleSource = :newSource "
            + "where c.id = :id and c.titleSource = :expectedSource")
    int updateTitleIfSource(@Param("id") UUID id, @Param("title") String title,
                            @Param("newSource") TitleSource newSource,
                            @Param("expectedSource") TitleSource expectedSource);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ConversationEntity c set c.summary = :summary, c.summarizedUntil = :until where c.id = :id")
    int updateSummary(@Param("id") UUID id, @Param("summary") String summary, @Param("until") Instant until);
}

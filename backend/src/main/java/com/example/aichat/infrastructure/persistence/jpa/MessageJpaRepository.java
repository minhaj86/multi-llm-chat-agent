package com.example.aichat.infrastructure.persistence.jpa;

import com.example.aichat.domain.conversation.MessageStatus;
import com.example.aichat.infrastructure.persistence.entity.MessageEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageJpaRepository extends JpaRepository<MessageEntity, UUID> {

    @Query("select m from MessageEntity m where m.conversationId = :conversationId order by m.createdAt asc, m.id asc")
    List<MessageEntity> findByConversation(@Param("conversationId") UUID conversationId);

    @Query("select count(m) from MessageEntity m where m.conversationId = :conversationId")
    long countByConversation(@Param("conversationId") UUID conversationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MessageEntity m set m.status = :failed, m.errorCode = :code, m.errorMessage = :message, "
            + "m.updatedAt = :now where m.status = :streaming and m.updatedAt < :olderThan")
    int failStale(@Param("streaming") MessageStatus streaming, @Param("failed") MessageStatus failed,
                  @Param("code") String code, @Param("message") String message,
                  @Param("olderThan") Instant olderThan, @Param("now") Instant now);
}

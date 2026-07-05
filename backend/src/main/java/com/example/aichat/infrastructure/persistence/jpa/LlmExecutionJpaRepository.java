package com.example.aichat.infrastructure.persistence.jpa;

import com.example.aichat.infrastructure.persistence.entity.LlmExecutionEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmExecutionJpaRepository extends JpaRepository<LlmExecutionEntity, UUID> {

    @Query("select e from LlmExecutionEntity e where e.messageId in :messageIds")
    List<LlmExecutionEntity> findByMessageIds(@Param("messageIds") Collection<UUID> messageIds);
}

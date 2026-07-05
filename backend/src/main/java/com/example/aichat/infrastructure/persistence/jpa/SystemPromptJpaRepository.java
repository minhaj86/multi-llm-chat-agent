package com.example.aichat.infrastructure.persistence.jpa;

import com.example.aichat.infrastructure.persistence.entity.SystemPromptEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemPromptJpaRepository extends JpaRepository<SystemPromptEntity, UUID> {

    @Query("select p from SystemPromptEntity p order by p.sortOrder asc, lower(p.name) asc")
    List<SystemPromptEntity> findAllOrdered();

    @Query("select p from SystemPromptEntity p where p.defaultPrompt = true")
    Optional<SystemPromptEntity> findDefault();

    @Query("select count(p) > 0 from SystemPromptEntity p where lower(p.name) = lower(:name)")
    boolean existsByName(@Param("name") String name);

    @Query("select count(p) > 0 from SystemPromptEntity p where lower(p.name) = lower(:name) and p.id <> :excludingId")
    boolean existsByNameExcluding(@Param("name") String name, @Param("excludingId") UUID excludingId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SystemPromptEntity p set p.defaultPrompt = false where p.defaultPrompt = true")
    int clearDefault();
}

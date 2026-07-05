package com.example.aichat.infrastructure.persistence.jpa;

import com.example.aichat.domain.user.UserRole;
import com.example.aichat.infrastructure.persistence.entity.UserEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    @Query("select u from UserEntity u where lower(u.username) = lower(:login) or lower(u.email) = lower(:login)")
    List<UserEntity> findByLogin(@Param("login") String login);

    @Query("select count(u) > 0 from UserEntity u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    @Query("select count(u) > 0 from UserEntity u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    @Query("select count(u) from UserEntity u where u.role = :role")
    long countByRole(@Param("role") UserRole role);
}

package com.example.aichat.infrastructure.persistence.jpa;

import com.example.aichat.infrastructure.persistence.entity.ProviderSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderSettingsJpaRepository extends JpaRepository<ProviderSettingsEntity, String> {
}

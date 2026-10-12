package com.example.aichat.rag.infrastructure.security;

import com.example.aichat.rag.application.EnabledCollectionsAccessPolicy;
import com.example.aichat.rag.application.KnowledgeAccessPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the default knowledge access policy unless the application defines its own. */
@Configuration(proxyBeanMethods = false)
public class KnowledgeAccessConfiguration {

    @Bean
    @ConditionalOnMissingBean(KnowledgeAccessPolicy.class)
    KnowledgeAccessPolicy knowledgeAccessPolicy() {
        return new EnabledCollectionsAccessPolicy();
    }
}

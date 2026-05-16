package com.example.aichat.configuration;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Core infrastructure beans: UTC clock (replaceable in tests) and scheduling (provider health checks, cleanup).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class CoreConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}

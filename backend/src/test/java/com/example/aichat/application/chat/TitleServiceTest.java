package com.example.aichat.application.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TitleServiceTest {

    @Test
    void sanitizesModelOutputIntoAShortSingleLineTitle() {
        assertThat(TitleService.sanitize("\"Spring Security JWT Setup.\"")).isEqualTo("Spring Security JWT Setup");
        assertThat(TitleService.sanitize("Title: **Kafka vs RabbitMQ**\n\nExplanation...")).isEqualTo("Kafka vs RabbitMQ");
        assertThat(TitleService.sanitize("<think>\nthe user asks...\n</think>\n\nJava Virtual Threads")).isEqualTo("Java Virtual Threads");
        assertThat(TitleService.sanitize("# Heading")).isEqualTo("Heading");
        assertThat(TitleService.sanitize("   ")).isNull();
        assertThat(TitleService.sanitize(null)).isNull();
        assertThat(TitleService.sanitize("x".repeat(200))).hasSize(TitleService.MAX_TITLE_LENGTH);
    }

    @Test
    void provisionalTitleUsesTheFirstLineOfTheMessage() {
        assertThat(TitleService.provisionalTitle("How do I\nconfigure   CORS?")).isEqualTo("How do I configure CORS?");
        assertThat(TitleService.provisionalTitle("a".repeat(100))).hasSize(TitleService.PROVISIONAL_MAX_LENGTH).endsWith("…");
        assertThat(TitleService.provisionalTitle("  ")).isEqualTo("New chat");
    }
}

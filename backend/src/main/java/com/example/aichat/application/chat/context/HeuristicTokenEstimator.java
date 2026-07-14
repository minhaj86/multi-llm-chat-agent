package com.example.aichat.application.chat.context;

import com.example.aichat.application.chat.ChatProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Character based token estimate. Deliberately conservative (default 3.5 characters per token) so that the
 * context stays within limits for code-heavy conversations and for non-English text.
 */
@Component
public class HeuristicTokenEstimator implements TokenEstimator {

    private final double charsPerToken;

    @Autowired
    public HeuristicTokenEstimator(ChatProperties properties) {
        this(properties.context().charsPerToken());
    }

    public HeuristicTokenEstimator(double charsPerToken) {
        if (charsPerToken <= 0) {
            throw new IllegalArgumentException("charsPerToken must be positive");
        }
        this.charsPerToken = charsPerToken;
    }

    @Override
    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / charsPerToken);
    }
}

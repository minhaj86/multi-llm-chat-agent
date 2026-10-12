package com.example.aichat.rag.application;

import com.example.aichat.application.common.ValidationException;
import com.example.aichat.common.Texts;
import com.example.aichat.rag.domain.KnowledgeSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Normalisation and validation of administrator input (titles, tags, source URLs).
 */
final class KnowledgeInputs {

    private static final int MAX_URL_LENGTH = 2000;

    private KnowledgeInputs() {
    }

    static String title(String title, String fallback) {
        String value = Texts.hasText(title) ? title : fallback;
        if (!Texts.hasText(value)) {
            throw new ValidationException("TITLE_REQUIRED", "A title is required");
        }
        String clean = value.replaceAll("\\s+", " ").strip();
        return clean.length() <= KnowledgeSource.MAX_TITLE_LENGTH ? clean
                : clean.substring(0, KnowledgeSource.MAX_TITLE_LENGTH);
    }

    /** Lower-case, trimmed, de-duplicated tags (at most 20 of at most 50 characters). */
    static List<String> tags(Collection<String> tags) {
        if (tags == null) {
            return List.of();
        }
        Set<String> clean = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String value = tag.replaceAll("[\\p{Cntrl}]", "").replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
            if (value.startsWith("#")) {
                value = value.substring(1).strip();
            }
            if (value.isEmpty()) {
                continue;
            }
            if (value.length() > KnowledgeSource.MAX_TAG_LENGTH) {
                throw new ValidationException("INVALID_TAG", "Tags must not exceed "
                        + KnowledgeSource.MAX_TAG_LENGTH + " characters");
            }
            clean.add(value);
        }
        if (clean.size() > KnowledgeSource.MAX_TAGS) {
            throw new ValidationException("TOO_MANY_TAGS", "At most " + KnowledgeSource.MAX_TAGS + " tags are allowed");
        }
        return List.copyOf(clean);
    }

    /** Optional http(s) URL; other schemes (javascript:, file:, data:) are rejected because the UI renders links. */
    static String sourceUrl(String url) {
        if (!Texts.hasText(url)) {
            return null;
        }
        String value = url.strip();
        if (value.length() > MAX_URL_LENGTH) {
            throw new ValidationException("INVALID_URL", "The source URL is too long");
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
                throw new ValidationException("INVALID_URL", "The source URL must be an http(s) address");
            }
        } catch (URISyntaxException e) {
            throw new ValidationException("INVALID_URL", "The source URL is not valid");
        }
        return value;
    }
}

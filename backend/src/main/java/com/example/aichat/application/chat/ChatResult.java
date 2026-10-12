package com.example.aichat.application.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated outcome of a non-streaming chat request.
 */
public record ChatResult(ChatEvent.Started started,
                         String content,
                         String reasoning,
                         ChatEvent.Completed completed,
                         ChatEvent.Failed failed,
                         List<ChatEvent.TitleUpdated> titles,
                         ChatEvent.KnowledgeRetrieved knowledge) {

    public static ChatResult from(List<ChatEvent> events) {
        ChatEvent.Started started = null;
        ChatEvent.Completed completed = null;
        ChatEvent.Failed failed = null;
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<ChatEvent.TitleUpdated> titles = new ArrayList<>();
        ChatEvent.KnowledgeRetrieved knowledge = null;
        for (ChatEvent event : events) {
            switch (event) {
                case ChatEvent.Started s -> started = s;
                case ChatEvent.Delta d -> content.append(d.text());
                case ChatEvent.Reasoning r -> reasoning.append(r.text());
                case ChatEvent.Completed c -> completed = c;
                case ChatEvent.Failed f -> failed = f;
                case ChatEvent.TitleUpdated t -> titles.add(t);
                case ChatEvent.KnowledgeRetrieved k -> knowledge = k;
            }
        }
        return new ChatResult(started, content.toString(), reasoning.isEmpty() ? null : reasoning.toString(),
                completed, failed, List.copyOf(titles), knowledge);
    }
}

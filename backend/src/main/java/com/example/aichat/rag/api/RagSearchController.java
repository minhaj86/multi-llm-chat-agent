package com.example.aichat.rag.api;

import com.example.aichat.application.auth.CurrentUser;
import com.example.aichat.common.CorrelationIdFilter;
import com.example.aichat.rag.api.KnowledgeDtos.SearchRequest;
import com.example.aichat.rag.api.KnowledgeDtos.SearchResponse;
import com.example.aichat.rag.api.KnowledgeDtos.TestAnswerRequest;
import com.example.aichat.rag.api.KnowledgeDtos.TestAnswerResponse;
import com.example.aichat.rag.application.RagTestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin RAG test console: {@code /search} exercises retrieval only (no LLM), {@code /test-answer} runs the whole
 * question -> retrieval -> context -> selected LLM flow and returns every intermediate result.
 */
@RestController
@RequestMapping("/api/admin/knowledge")
public class RagSearchController {

    private final RagTestService testService;

    public RagSearchController(RagTestService testService) {
        this.testService = testService;
    }

    @PostMapping("/search")
    public SearchResponse search(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SearchRequest request) {
        return SearchResponse.of(testService.search(user.id(), request.query(), request.options()).retrieval());
    }

    @PostMapping("/test-answer")
    public TestAnswerResponse testAnswer(@AuthenticationPrincipal CurrentUser user,
                                         @Valid @RequestBody TestAnswerRequest request,
                                         HttpServletRequest httpRequest) {
        return TestAnswerResponse.of(testService.answer(user.id(), request.question(), request.options(),
                request.provider(), request.model(), CorrelationIdFilter.current(httpRequest)));
    }
}

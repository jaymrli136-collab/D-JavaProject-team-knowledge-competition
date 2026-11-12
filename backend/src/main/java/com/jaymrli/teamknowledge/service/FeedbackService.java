package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.FeedbackRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.FeedbackSummary;
import com.jaymrli.teamknowledge.api.ApiDtos.FeedbackView;
import com.jaymrli.teamknowledge.domain.Domain.FeedbackRating;
import com.jaymrli.teamknowledge.domain.Domain.RunFeedback;
import com.jaymrli.teamknowledge.repository.FeedbackRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class FeedbackService {
    private final FeedbackRepository repository;
    private final KnowledgeAgentHarness harness;

    public FeedbackService(FeedbackRepository repository, KnowledgeAgentHarness harness) {
        this.repository = repository;
        this.harness = harness;
    }

    public FeedbackView submit(String userId, String runId, FeedbackRequest request) {
        harness.getRun(userId, runId);
        FeedbackRating rating;
        try {
            rating = FeedbackRating.valueOf(request.rating().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的反馈类型: " + request.rating());
        }
        RunFeedback existing = repository.find(runId, userId).orElse(null);
        RunFeedback saved = repository.save(new RunFeedback(
                existing == null ? "fb_" + UUID.randomUUID() : existing.id(), runId, userId, rating,
                request.comment() == null ? "" : request.comment().trim(), Instant.now()
        ));
        return toView(saved);
    }

    public FeedbackSummary summary() {
        var items = repository.all();
        long helpful = items.stream().filter(item -> item.rating() == FeedbackRating.HELPFUL).count();
        long total = items.size();
        double rate = total == 0 ? 1.0 : Math.round((double) helpful / total * 10000.0) / 10000.0;
        return new FeedbackSummary(total, helpful, total - helpful, rate);
    }

    private FeedbackView toView(RunFeedback item) {
        return new FeedbackView(item.id(), item.runId(), item.userId(), item.rating().name(),
                item.comment(), item.createdAt());
    }
}

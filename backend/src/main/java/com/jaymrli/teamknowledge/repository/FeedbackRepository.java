package com.jaymrli.teamknowledge.repository;

import com.jaymrli.teamknowledge.domain.Domain.RunFeedback;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class FeedbackRepository {
    private final Map<String, RunFeedback> feedback = new ConcurrentHashMap<>();

    public void replaceAll(java.util.Collection<RunFeedback> restored) {
        feedback.clear();
        restored.forEach(item -> feedback.put(item.id(), item));
    }

    public RunFeedback save(RunFeedback item) {
        feedback.values().removeIf(existing -> existing.runId().equals(item.runId())
                && existing.userId().equals(item.userId()));
        feedback.put(item.id(), item);
        return item;
    }

    public Optional<RunFeedback> find(String runId, String userId) {
        return feedback.values().stream()
                .filter(item -> item.runId().equals(runId) && item.userId().equals(userId))
                .findFirst();
    }

    public List<RunFeedback> all() {
        return feedback.values().stream()
                .sorted(Comparator.comparing(RunFeedback::createdAt).reversed())
                .toList();
    }
}

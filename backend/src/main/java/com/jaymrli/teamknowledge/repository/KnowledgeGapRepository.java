package com.jaymrli.teamknowledge.repository;

import com.jaymrli.teamknowledge.domain.Domain.KnowledgeGap;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeGapStatus;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class KnowledgeGapRepository {
    private final Map<String, KnowledgeGap> gaps = new ConcurrentHashMap<>();

    public void replaceAll(java.util.Collection<KnowledgeGap> restored) {
        gaps.clear();
        restored.forEach(item -> gaps.put(item.id(), item));
    }

    public KnowledgeGap save(KnowledgeGap gap) {
        gaps.put(gap.id(), gap);
        return gap;
    }

    public Optional<KnowledgeGap> find(String id) {
        return Optional.ofNullable(gaps.get(id));
    }

    public Optional<KnowledgeGap> findOpen(String userId, String question, Set<String> projectIds) {
        return gaps.values().stream()
                .filter(item -> item.status() == KnowledgeGapStatus.OPEN || item.status() == KnowledgeGapStatus.IN_REVIEW)
                .filter(item -> item.createdBy().equals(userId) && item.question().equals(question))
                .filter(item -> item.projectIds().equals(projectIds))
                .findFirst();
    }

    public List<KnowledgeGap> all() {
        return gaps.values().stream()
                .sorted(Comparator.comparing(KnowledgeGap::lastSeenAt).reversed())
                .toList();
    }
}

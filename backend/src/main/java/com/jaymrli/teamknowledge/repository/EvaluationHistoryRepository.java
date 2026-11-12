package com.jaymrli.teamknowledge.repository;

import com.jaymrli.teamknowledge.domain.Domain.EvaluationSnapshot;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class EvaluationHistoryRepository {
    private final Map<String, EvaluationSnapshot> snapshots = new ConcurrentHashMap<>();

    public void replaceAll(java.util.Collection<EvaluationSnapshot> restored) {
        snapshots.clear();
        restored.forEach(item -> snapshots.put(item.id(), item));
    }

    public EvaluationSnapshot save(EvaluationSnapshot snapshot) {
        snapshots.put(snapshot.id(), snapshot);
        return snapshot;
    }

    public List<EvaluationSnapshot> recent(int limit) {
        return snapshots.values().stream()
                .sorted(Comparator.comparing(EvaluationSnapshot::createdAt).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }
}

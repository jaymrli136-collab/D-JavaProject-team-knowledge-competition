package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.domain.Domain.MemoryHint;
import com.jaymrli.teamknowledge.domain.Domain.MemoryStatus;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.domain.Domain.ProjectMemory;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import com.jaymrli.teamknowledge.repository.ProjectMemoryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectMemoryContextService {
    private static final Set<String> STOP = Set.of(
            "什么", "多少", "怎么", "为什么", "如何", "当前", "现在", "请问", "一下", "这个", "那个",
            "项目", "时候", "哪些", "是否", "可以", "以及", "需要", "进行", "已经"
    );

    private final ProjectMemoryRepository memoryRepository;
    private final KnowledgeStore knowledgeStore;
    private final TextAnalyzer analyzer;

    public ProjectMemoryContextService(ProjectMemoryRepository memoryRepository,
                                       KnowledgeStore knowledgeStore,
                                       TextAnalyzer analyzer) {
        this.memoryRepository = memoryRepository;
        this.knowledgeStore = knowledgeStore;
        this.analyzer = analyzer;
    }

    public List<MemoryHint> retrieve(String question, Set<String> projectIds, int limit) {
        Set<String> queryTokens = new HashSet<>(analyzer.tokenize(question).stream()
                .filter(this::isUsefulToken).toList());
        if (queryTokens.isEmpty()) return List.of();

        List<ScoredMemory> scored = new ArrayList<>();
        for (ProjectMemory memory : memoryRepository.all()) {
            if (!projectIds.contains(memory.projectId()) || memory.status() != MemoryStatus.ACTIVE) continue;
            Set<String> memoryTokens = new HashSet<>(analyzer.tokenize(memory.title() + " " + memory.content()).stream()
                    .filter(this::isUsefulToken).toList());
            long overlap = queryTokens.stream().filter(memoryTokens::contains).count();
            if (overlap == 0) continue;
            double score = Math.min(1.0, overlap / Math.max(1.0, queryTokens.size()) + 0.15);
            Project project = knowledgeStore.findProject(memory.projectId()).orElseThrow();
            scored.add(new ScoredMemory(memory, project, Math.round(score * 10000.0) / 10000.0));
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredMemory::score).reversed())
                .limit(Math.max(0, limit))
                .map(item -> new MemoryHint(item.memory().id(), item.memory().projectId(), item.project().name(),
                        item.memory().type().name(), item.memory().title(), item.memory().content(),
                        item.score(), item.memory().sourceEvidenceIds()))
                .toList();
    }

    private boolean isUsefulToken(String token) {
        return !STOP.contains(token) && (token.length() > 1 || token.chars().allMatch(code -> code < 128));
    }

    private record ScoredMemory(ProjectMemory memory, Project project, double score) {
    }
}
